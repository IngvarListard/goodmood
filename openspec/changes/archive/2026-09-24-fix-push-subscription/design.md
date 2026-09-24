# Design: fix-push-subscription

## Context

Web push не работает из-за отклонения реального payload подписки. Браузерный
`PushRegistration.toJSON()` (то, что `push.js` отправляет в `POST /push/subscribe`)
возвращает карту вида:

```clojure
{:endpoint "https://..."
 :keys {:p256dh "..." :auth "..."}
 :expirationTime nil}
```

Текущая схема (`src/app/domains/push.clj`):

```clojure
[:map {:closed true}
 [:endpoint :string]
 [:keys [:map {:closed true} [:p256dh :string] [:auth :string]]]]
```

`:closed true` на верхнем уровне превращает `:expirationTime` в
`[disallowed key]`, `validate-subscription` возвращает map ошибок, и роут
`subscribe` отвечает `400`. Строка в `push_subscriptions` не пишется — пушей нет.

Значимые для отправки поля — только `endpoint`, `keys.p256dh`, `keys.auth`:
именно они уходят в `Subscription`/`Subscription$Keys` в `send-one!`. Остальные
ключи браузера (`expirationTime` и возможные будущие) для криптографии не нужны.

Второй дефект — в `push.js`: `enable` безусловно вызывает `pushManager.subscribe`.
Если подписка уже была создана (например, предыдущая попытка прошла, но POST
упал с 400), повторный вызов бросает `InvalidStateError`, и юзер видит ошибку.

## Decisions

### D1: Валидировать только значимый срез подписки (select-keys)

Перед `mc/validate` выделять из входа только нужное подмножество
(`endpoint` + `keys{p256dh,auth}`), а не ослаблять схему. Схема остаётся
`:closed true` вокруг того, что действительно нужно валидировать:

```clojure
(defn- subscription-subset
  "Оставить только значимые для отправки поля подписки (design D1):
   лишние безобидные ключи браузера (expirationTime) не валидируем."
  [sub]
  (when (map? sub)
    (select-keys sub [:endpoint])
    ...))
```

**Rationale:** обязательные `endpoint`/`p256dh`/`auth` по-прежнему валидируются
со всей строгостью (`:string`, closed внутри `:keys`), а раздутый браузерный
payload перестаёт быть проблемой. Мы валидируем ровно тот контракт, на который
опирается `send-one!`.

**Trade-off:** схема больше не описывает весь входящий payload, поэтому
неожиданные опечатки в *лишних* ключах молча игнорируются. Принято осознанно:
единственный источник payload — `PushSubscription.toJSON()`, а не произвольный
клиент; значимые поля защищены. Альтернатива — сузить только верхний уровень
(`{:closed false}`) — оставляет открытым `:keys` и хуже выражает намерение.

### D2: Идемпотентный enable в `push.js` (reuse существующей подписки)

В `GMPush.enable`: после `serviceWorker.ready` сначала вызвать
`reg.pushManager.getSubscription()`. Если подписка есть — использовать её,
иначе создать через `subscribe`. Далее общий путь: `POST /push/subscribe`,
при успехе — `swapSection(await res.text())`, при ошибке — `showStatus`.

**Rationale:** устраняет `InvalidStateError` при повторном нажатии и заодно
чинит сценарий «подписка в браузере есть, строки в БД нет» — повторный POST
восстановит запись без пересоздания подписки.

**Trade-off:** существующая подписка могла быть создана с другим
`applicationServerKey` (например, после ротации VAPID-ключей). Полная проверка
потребовала бы сравнения ключей; это редкий операционный случай, не покрытый
задачей. Оставляем как есть — при смене VAPID-ключей подписку нужно сбросить
вручную (unsubscribe).

### D3: Тестовая отправка — отдельный роут `POST /push/test` и htmx-кнопка

Новый хендлер `test-send` в `src/app/routes/push.clj`:

```clojure
(defn test-send [ds request]
  (let [uid (user-id request)
        delivered (push-domains/send-push! ds uid
                                           (i18n/t :app/name)
                                           (i18n/t :push/test-body))]
    (html-response 200 (settings/push-test-result delivered))))
```

Роут регистрируется в `src/app/routes/app.clj` рядом с `/push/subscribe`:

```clojure
["/push/test" {:post {:handler (partial push-routes/test-send ds)}}]
```

Кнопка живёт в `push-section` и показывается только при `subscribed?`, шлёт
htmx-запрос:

```clojure
[:button {:type "button"
          :class "btn btn-outline btn-sm"
          :hx-post "/push/test"
          :hx-target "#push-test-result"
          :hx-swap "innerHTML"
          :hx-headers (str "{\"X-CSRF-Token\": \"" csrf-token "\"}")}
 (i18n/t :push/test-button)]
[:span {:id "push-test-result" :class "text-sm opacity-70"}]
```

**Rationale:** переиспользуем уже существующий путь доставки `send-push!`
(та же логика 404/410, тот же `vapid-configured?`), не пишем новый транспорт.
htmx + `hx-headers` с CSRF — родной паттерн проекта (как
`views/medications.clj`), без нового JS. Роут защищён аутентификацией
(не `:auth/public`) и глобальным anti-forgery middleware.

**Trade-off:** `send-push!` возвращает только число успешных доставок и не
различает «нет подписок» и «все упали». Для ручной проверки это приемлемо;
различать причины — YAGNI. Хендлер дополнительно возвращает ошибку, если
`vapid-configured?` ложно (защита, хотя кнопка и секция в этом случае скрыты).

### D4: `push-section` принимает CSRF-токен

Сигнатура `push-section` расширяется до `[csrf-token configured? subscribed?]`.
Оба вызова (фрагмент в `src/app/routes/push.clj/section-fragment` и страница в
`src/app/views/settings.clj/page`) передают `(:anti-forgery-token request)`.
`section-fragment` уже получает полный `request`.

**Rationale:** кнопке теста нужен CSRF-токен в `hx-headers`; брать его из
`meta[name=csrf-token]` через новый JS не нужно — сервер уже рендерит токен.

**Trade-off:** меняется сигнатура публичной `push-section` и её два вызова —
механическая правка в двух файлах. Альтернатива (подписка на глобальный
htmx-config) шире и трогает больше.

## Risks

- **Лишние ключи игнорируются молча.** Осознанно (D1). Если появится больше
  обязательных полей, их нужно добавить в `subscription-subset` и в схему —
  тест на `:expirationTime` это не поймает. Митигация: тест на обязательные
  поля (отсутствующий `:keys` → ошибка).
- **Ротация VAPID-ключей.** `getSubscription` переиспользует подписку со старым
  applicationServerKey — пуши не дойдут (D2). Отдельная задача/риск, вне scope.
- **Тестовая отправка реальному устройству.** Кнопка шлёт настоящий пуш на все
  подписки юзера. Для ручной проверки это и есть цель; спама нет — только по
  клику.

## Verification

- **Unit (домен):** `validate-subscription` принимает
  `{:endpoint "x" :keys {:p256dh "a" :auth "b"} :expirationTime nil}` → `nil`;
  карта без `:keys`/`:p256dh` → не-nil ошибки.
- **Route test:** `POST /push/subscribe` с payload, содержащим
  `expirationTime`, пишет строку в `push_subscriptions` и возвращает фрагмент
  со статусом «подписаны»; `POST /push/test` с подменённым `send-push!`
  возвращает число доставок.
- **REPL:** на живом nREPL `(validate-subscription <реалистичный toJSON>)` → `nil`.
- **Manual:** запущенное приложение, `/settings`, кнопка «Отправить тестовое
  уведомление» при наличии подписки приводит к пушу и показу результата.
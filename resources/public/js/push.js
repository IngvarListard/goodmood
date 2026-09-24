/*
 * Push-подписка и регистрация service worker (change add-pwa-push, D6).
 *
 * Паттерн как у radar.js: сервер рендерит данные и тексты (meta vapid-public-key,
 * data-атрибуты секции #push-section), этот файл — тупой исполнитель. Никаких
 * словарей и хардкода текстов: всё, что видно юзеру, приходит из i18n-фрагмента.
 *
 * Подключается с defer на каждой странице: регистрирует SW (нужен
 * installability), а кнопки включения/выключения обрабатывает только по
 * жесту юзера (requestPermission разрешён только в user gesture).
 * Ответ сервера — свежая HTML-секция #push-section, её и подставляем.
 */
(function () {
  'use strict';

  // Регистрация SW: нужно для installability и до подписки. Ошибки не
  // роняют страницу (например, файл недоступен в dev по http на не-localhost)
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('/sw.js').catch(function (err) {
      console.warn('service worker registration failed:', err);
    });
  }

  if (!document.getElementById('push-section')) return;

  var csrfToken = function () {
    var meta = document.querySelector('meta[name="csrf-token"]');
    return meta ? meta.content : '';
  };

  // applicationServerKey ждёт байты: VAPID-ключ приходит base64url (65 байт)
  var urlB64ToUint8Array = function (base64) {
    var padding = '='.repeat((4 - (base64.length % 4)) % 4);
    var base64safe = (base64 + padding).replace(/-/g, '+').replace(/_/g, '/');
    var raw = atob(base64safe);
    var out = new Uint8Array(raw.length);
    for (var i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
    return out;
  };

  var vapidPublicKey = function () {
    var meta = document.querySelector('meta[name="vapid-public-key"]');
    return meta ? meta.content : null;
  };

  var postJSON = function (url, data) {
    return fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json',
                 'X-CSRF-Token': csrfToken() },
      body: JSON.stringify(data)
    });
  };

  // Свежая секция вместо текущей (ответ POST — HTML-фрагмент)
  var swapSection = function (html) {
    var tmp = document.createElement('div');
    tmp.innerHTML = html;
    var fresh = tmp.firstElementChild;
    var current = document.getElementById('push-section');
    if (current && fresh) current.replaceWith(fresh);
  };

  // Статус-строка внутри секции (не alert): ошибки видны рядом с кнопкой
  var showStatus = function (message) {
    var card = document.querySelector('#push-section .card');
    if (!card) return;
    var p = document.getElementById('push-status');
    if (!p) {
      p = document.createElement('p');
      p.className = 'text-sm opacity-70 mt-2';
      p.id = 'push-status';
      card.appendChild(p);
    }
    p.textContent = message;
  };

  window.GMPush = {
    enable: async function () {
      var section = document.getElementById('push-section');
      if (!section) return;
      try {
        if (!('serviceWorker' in navigator) || !('PushManager' in window) ||
            !vapidPublicKey()) {
          showStatus(section.dataset.msgError);
          return;
        }
        var permission = await Notification.requestPermission();
        if (permission !== 'granted') {
          showStatus(section.dataset.msgDenied);
          return;
        }
        var reg = await navigator.serviceWorker.ready;
        // Переиспользуем уже существующую подписку: повторный subscribe
        // кидает InvalidStateError, а подписка в браузере могла остаться
        // без строки в БД. Нет подписки — создаём.
        var sub = await reg.pushManager.getSubscription();
        if (!sub) {
          sub = await reg.pushManager.subscribe({
            userVisibleOnly: true,
            applicationServerKey: urlB64ToUint8Array(vapidPublicKey())
          });
        }
        var res = await postJSON('/push/subscribe', sub.toJSON());
        if (!res.ok) throw new Error('subscribe failed: ' + res.status);
        swapSection(await res.text());
      } catch (err) {
        console.warn('push enable failed:', err);
        showStatus(document.getElementById('push-section').dataset.msgError);
      }
    },

    disable: async function () {
      try {
        var reg = await navigator.serviceWorker.ready;
        var sub = await reg.pushManager.getSubscription();
        if (sub) await sub.unsubscribe();
        var res = await postJSON('/push/unsubscribe',
                                 { endpoint: sub ? sub.endpoint : '' });
        if (!res.ok) throw new Error('unsubscribe failed: ' + res.status);
        swapSection(await res.text());
      } catch (err) {
        console.warn('push unsubscribe failed:', err);
        showStatus(document.getElementById('push-section').dataset.msgError);
      }
    }
  };
})();
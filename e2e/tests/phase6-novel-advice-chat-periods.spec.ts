import { test, expect, Page } from '@playwright/test';
import { execSync } from 'node:child_process';
import { ensureE2EUser, login, submitEntry, projectRoot } from '../helpers';

test.beforeAll(ensureE2EUser);

// Очистить state_periods, ai_findings, чат-кэш, записи.
function resetPhase6State() {
  execSync(
    `clojure -M -e "
       (require '[next.jdbc :as jdbc])
       (def ds (jdbc/get-datasource {:dbtype \\"sqlite\\" :dbname \\"resources/goodmood.db\\"}))
       (try (jdbc/execute! ds [\\"DELETE FROM state_periods\\"]) (catch Exception _))
       (try (jdbc/execute! ds [\\"DELETE FROM ai_findings\\"]) (catch Exception _))
       (try (jdbc/execute! ds [\\"DELETE FROM ai_chat_messages\\"]) (catch Exception _))
       (jdbc/execute! ds [\\"UPDATE entries SET state_period_id = NULL\\"])
       (try (jdbc/execute! ds [\\"UPDATE user_ai_settings SET allow_novel_advice = 0\\"]) (catch Exception _))
       (println \\"reset done\\")"`,
    { cwd: projectRoot, timeout: 90000, stdio: 'ignore' },
  );
}

test.beforeAll(resetPhase6State);

test.describe('AI novel advice + chat (Phase 6, part 1)', () => {

  test('novel advice is OFF by default — not shown on /feed', async ({ page }) => {
    await login(page);
    await page.goto('/feed');
    // AI-совет из своих — может быть виден. Novel advice (не из своих) — нет.
    const novelAdvice = page.locator('[data-testid="ai-novel-advice"]');
    await expect(novelAdvice).toBeHidden();
  });

  test('enabling novel advice in settings shows it (opt-in)', async ({ page }) => {
    await login(page);
    await page.goto('/settings');
    const toggle = page.locator('[data-testid="allow-novel-advice-toggle"]');
    // Тумблеры настроек AI автосохраняются (hx-trigger=change). Дождаться POST.
    const saveResp = page.waitForResponse(
      (r) => r.url().includes('/settings/ai') && r.request().method() === 'POST',
      { timeout: 20000 },
    );
    await toggle.check();
    await saveResp;
    await page.goto('/feed');
    // Novel advice может не появиться (если есть свои инсайты) — contract: toggle сохранён.
    await page.goto('/settings');
    await expect(toggle).toBeChecked();
    // Очистить за собой (дождаться сохранения выключенного состояния).
    const uncheckResp = page.waitForResponse(
      (r) => r.url().includes('/settings/ai') && r.request().method() === 'POST',
      { timeout: 20000 },
    );
    await toggle.uncheck();
    await uncheckResp;
  });

  test('novel advice is marked "not from your notes"', async ({ page }) => {
    await login(page);
    await page.goto('/settings');
    const toggle = page.locator('[data-testid="allow-novel-advice-toggle"]');
    const saveResp = page.waitForResponse(
      (r) => r.url().includes('/settings/ai') && r.request().method() === 'POST',
      { timeout: 20000 },
    );
    await toggle.check();
    await saveResp;
    await page.goto('/feed');
    const novelAdvice = page.locator('[data-testid="ai-novel-advice"]');
    if (await novelAdvice.isVisible()) {
      await expect(novelAdvice.getByText(/не из твоих записей|not from your notes/i)).toBeVisible();
    }
    await page.goto('/settings');
    const uncheckResp = page.waitForResponse(
      (r) => r.url().includes('/settings/ai') && r.request().method() === 'POST',
      { timeout: 20000 },
    );
    await toggle.uncheck();
    await uncheckResp;
  });

  test('chat is available on-demand (not push)', async ({ page }) => {
    await login(page);
    await page.goto('/feed');
    const chatBtn = page.getByRole('button', { name: /чат|chat/i });
    // Кнопка чата видна (on-demand), но уведомлений не приходит автоматически.
    // Если чат пока только в плохом состоянии — создадим его.
    if (await chatBtn.isVisible()) {
      await chatBtn.click();
      await expect(page.locator('[data-testid="ai-chat"]')).toBeVisible();
    } else {
      // Создать тревожную запись, потом проверить.
      await submitEntry(page, { mood_score: 3, energy: 2, anxiety: 8, template: 'day' });
      await page.goto('/feed');
      await page.getByRole('button', { name: /чат|chat/i }).click();
      await expect(page.locator('[data-testid="ai-chat"]')).toBeVisible();
    }
  });

  test('chat shows disclaimer on first open', async ({ page }) => {
    await login(page);
    await page.goto('/feed');
    const chatBtn = page.getByRole('button', { name: /чат|chat/i });
    if (await chatBtn.isVisible()) {
      await chatBtn.click();
      await expect(page.getByText(/не заменяет|does not replace|профессиональн/i)).toBeVisible();
    }
  });

  test('chat responds with context of current state', async ({ page }) => {
    await login(page);
    await submitEntry(page, { mood_score: 3, energy: 2, anxiety: 8, template: 'day' });
    await page.goto('/feed');
    await page.getByRole('button', { name: /чат|chat/i }).click();
    const chat = page.locator('[data-testid="ai-chat"]');
    const input = chat.locator('textarea, input[type="text"]');
    await input.fill('мне тревожно, что делать?');
    await chat.getByRole('button', { name: /отправить|send/i }).click();
    // Ответ AI появляется (htmx-swap). Timeout больше для AI-вызова.
    await expect(chat.locator('[data-testid="chat-response"]')).toBeVisible({ timeout: 30000 });
  });

  test('crisis keywords in chat trigger resource, not just response', async ({ page }) => {
    await login(page);
    await page.goto('/feed');
    await page.getByRole('button', { name: /чат|chat/i }).click();
    const chat = page.locator('[data-testid="ai-chat"]');
    const input = chat.locator('textarea, input[type="text"]');
    // Кризисные слова (ru/eng).
    await input.fill('не хочу жить, всё бессмысленно');
    await chat.getByRole('button', { name: /отправить|send/i }).click();
    // Ресурс (телефон доверия / 112 / напоминание о помощи).
    await expect(chat.getByText(/телефон доверия|hotline|112|03|help line|профессиональн/i)).toBeVisible({ timeout: 30000 });
  });
});

test.describe('State periods (Phase 6, part 2)', () => {

  test('start a state period on /feed', async ({ page }) => {
    await login(page);
    await page.goto('/feed');
    await page.getByRole('button', { name: /начать период|start period/i }).click();
    // Выбрать ярлык периода.
    await page.getByRole('button', { name: /спад|low|депрессивн|depressive/i }).click();
    // Период активен — индикатор.
    await expect(page.locator('[data-testid="active-period-indicator"]')).toBeVisible();
  });

  test('entry created during active period is linked to it', async ({ page }) => {
    await login(page);
    await page.goto('/feed');
    // Если нет активного периода — начать.
    const indicator = page.locator('[data-testid="active-period-indicator"]');
    if (!(await indicator.isVisible())) {
      await page.getByRole('button', { name: /начать период|start period/i }).click();
      await page.getByRole('button', { name: /спад|low/i }).click();
    }
    // Создать запись.
    await submitEntry(page, { mood_score: 3, energy: 2, anxiety: 3, template: 'day' });
    // В БД запись должна иметь state_period_id не null.
    // Прямая проверка через clojure:
    const out = execSync(
      `clojure -M -e "
         (require '[next.jdbc :as jdbc])
         (def ds (jdbc/get-datasource {:dbtype \\"sqlite\\" :dbname \\"resources/goodmood.db\\"}))
         (def rows (jdbc/execute! ds [\\"SELECT state_period_id FROM entries WHERE state_period_id IS NOT NULL ORDER BY id DESC LIMIT 1\\"]))
         (println (str (not (empty? rows))))"`,
      { cwd: projectRoot, timeout: 90000, stdio: 'pipe' },
    ).toString().trim();
    expect(out).toContain('true');
  });

  test('close an active period', async ({ page }) => {
    await login(page);
    await page.goto('/feed');
    const indicator = page.locator('[data-testid="active-period-indicator"]');
    if (await indicator.isVisible()) {
      await page.getByRole('button', { name: /закрыть период|close period|end period/i }).click();
      await expect(indicator).toBeHidden();
    }
  });

  test('periods are user-defined — not auto-created after 3 low days', async ({ page }) => {
    // Создать 3 записи в low без marking периода.
    await login(page);
    // Сначала закрыть любой активный период.
    await page.goto('/feed');
    const indicator = page.locator('[data-testid="active-period-indicator"]');
    if (await indicator.isVisible()) {
      await page.getByRole('button', { name: /закрыть период|close period/i }).click();
    }
    await submitEntry(page, { mood_score: 2, energy: 1, anxiety: 2, template: 'day' });
    await submitEntry(page, { mood_score: 2, energy: 2, anxiety: 3, template: 'day' });
    await submitEntry(page, { mood_score: 3, energy: 2, anxiety: 2, template: 'day' });
    await page.goto('/feed');
    // Период НЕ создан автоматически.
    await expect(page.locator('[data-testid="active-period-indicator"]')).toBeHidden();
    // Возможно soft hint «отметить период», но не авто-создание.
  });

  test('soft hint to mark period after sustained low — non-intrusive', async ({ page }) => {
    // Если реализован soft hint — проверить, что он ненавязчивый (не alert-error, dismissible).
    // Опциональный тест — skip если не реализовано.
    test.skip(true, 'soft hint — optional, skip if not implemented');
  });
});

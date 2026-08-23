import { test, expect, Page } from '@playwright/test';
import { execSync } from 'node:child_process';
import { ensureE2EUser, login, submitEntry, projectRoot } from '../helpers';

test.beforeAll(ensureE2EUser);

// Очистить настройки уведомлений и записи e2e-юзера напрямую в БД.
function resetNotificationsState() {
  execSync(
    `clojure -M -e "
       (require '[next.jdbc :as jdbc])
       (def ds (jdbc/get-datasource {:dbtype \\"sqlite\\" :dbname \\"resources/goodmood.db\\"}))
       (jdbc/execute! ds [\\"DELETE FROM user_notification_settings\\"])
       (jdbc/execute! ds [\\"DELETE FROM entries\\"])
       (println \\"reset done\\")"`,
    { cwd: projectRoot, timeout: 90000, stdio: 'ignore' },
  );
}

test.beforeAll(resetNotificationsState);

// Создать запись в состоянии low (energy <= 3) для проверки мягкого режима.
async function createLowEntry(page: Page) {
  await page.goto('/check-in');
  const setRange = (name: string, value: number) =>
    page.evaluate(
      ([n, v]) => {
        const el = document.querySelector(`input[name="${n}"]`) as HTMLInputElement;
        const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set!;
        setter.call(el, String(v));
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
      },
      [name, value] as const,
    );
  await setRange('mood_score', 2);
  await setRange('energy', 2);
  await setRange('anxiety', 5);
  await page.getByRole('button', { name: /Сохранить запись|Save entry/ }).click();
  await page.waitForURL(/\/feed/);
}

// Создать релевантный инсайт для состояния через UI.
async function createInsight(page: Page, { state = 'anxiety', context = 'Когда тревога 7+, не принимай решений' } = {}) {
  await page.goto('/insights/new');
  await page.fill('textarea[name="context"]', context);
  await page.selectOption('select[name="state_label"]', state);
  await page.selectOption('select[name="category"]', 'coping');
  await page.locator('#advice-list .advice-item').nth(0).locator('textarea').fill('дыхание 4-7-8');
  await page.getByRole('button', { name: /Сохранить|Save/ }).click();
  await page.waitForURL('**/insights');
}

test.describe('coping channels (Phase 4)', () => {
  test('soft mode banner shows on /check-in in low state', async ({ page }) => {
    await login(page);
    await createLowEntry(page);
    await page.goto('/check-in');
    await expect(page.getByText(/Тебе сейчас может быть непросто|Things might feel hard/)).toBeVisible();
    await expect(page.getByRole('button', { name: /Мягкий режим|Soft mode/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /Полная форма|Full form/ })).toBeVisible();
  });

  test('soft mode toggle hides energy/anxiety sliders', async ({ page }) => {
    await login(page);
    await createLowEntry(page);
    await page.goto('/check-in');
    await expect(page.locator('input[name="energy"]')).toBeVisible();
    await expect(page.locator('input[name="anxiety"]')).toBeVisible();
    await page.getByRole('button', { name: /Мягкий режим|Soft mode/ }).click();
    await expect(page.locator('input[name="energy"]')).toBeHidden();
    await expect(page.locator('input[name="anxiety"]')).toBeHidden();
    await expect(page.locator('input[name="mood_score"]')).toBeVisible();
    await page.getByRole('button', { name: /Полная форма|Full form/ }).click();
    await expect(page.locator('input[name="energy"]')).toBeVisible();
  });

  test('no soft-mode banner in balanced state', async ({ page }) => {
    await login(page);
    // создать сбалансированную запись (не low/mixed) — баннер не должен появиться
    await submitEntry(page, { mood: 5, energy: 6, anxiety: 3 });
    await page.goto('/check-in');
    await expect(page.locator('#soft-mode-banner')).toHaveCount(0);
  });

  test('toast hint appears on /feed after saving entry with matching insight', async ({ page }) => {
    await login(page);
    await createInsight(page, { state: 'anxiety' });
    await page.goto('/check-in');
    const setRange = (name: string, value: number) =>
      page.evaluate(
        ([n, v]) => {
          const el = document.querySelector(`input[name="${n}"]`) as HTMLInputElement;
          const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set!;
          setter.call(el, String(v));
          el.dispatchEvent(new Event('input', { bubbles: true }));
          el.dispatchEvent(new Event('change', { bubbles: true }));
        },
        [name, value] as const,
      );
    await setRange('mood_score', 3);
    await setRange('energy', 2);
    await setRange('anxiety', 7);
    await page.getByRole('button', { name: /Сохранить запись|Save entry/ }).click();
    await page.waitForURL(/\/feed/);
    await expect(page.locator('#check-in-hint-toast')).toBeVisible();
    await expect(page.getByText(/В таком состоянии тебе помогало|In a state like this, this helped you/)).toBeVisible();
  });

  test('notification settings section has three slots and saves', async ({ page }) => {
    await login(page);
    await page.goto('/settings');
    await expect(page.getByRole('heading', { name: /Уведомления|Notifications/ })).toBeVisible();
    await expect(page.locator('input[name="time_morning"]')).toHaveValue('08:00');
    await expect(page.locator('input[name="time_midday"]')).toHaveValue('13:00');
    await expect(page.locator('input[name="time_evening"]')).toHaveValue('19:00');
    await page.fill('input[name="time_morning"]', '07:30');
    await page.getByRole('button', { name: /Сохранить|Save/ }).last().click();
    await expect(page.getByText(/Сохранено|Saved/)).toBeVisible();
  });
});
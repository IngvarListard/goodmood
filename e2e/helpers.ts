import { execSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Page, expect } from '@playwright/test';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Корень проекта (родитель e2e/)
export const projectRoot = path.resolve(__dirname, '..');

// Учётные данные отдельного e2e-юзера (совпадают с dev/seed_e2e_user.clj)
export const E2E_USER_EMAIL = process.env.E2E_USER_EMAIL ?? 'e2e@goodmood.test';
export const E2E_USER_PASSWORD = process.env.E2E_USER_PASSWORD ?? 'e2e-test-password-123';

// Изолированный юзер для empty-state: гарантированно без записей
export const E2E_EMPTY_USER_EMAIL = 'e2e-empty@goodmood.test';
export const E2E_EMPTY_USER_PASSWORD = 'e2e-test-password-123';

// Создать e2e-юзера, если его нет (идемпотентно). Вызывается один раз до тестов.
export function ensureE2EUser() {
  try {
    execSync('clojure -M -i dev/seed_e2e_user.clj', {
      cwd: projectRoot,
      timeout: 60000,
      stdio: 'ignore',
    });
  } catch (error) {
    // Если БД залочена запущенным сервером — не критично: сид идемпотентный,
    // повторный прогон тестов пройдёт.
    console.error('[e2e] seed warning:', (error as Error).message);
  }
}

// Войти в приложение через форму на /login. После успеха — редирект на "/feed".
export async function login(page: Page, email = E2E_USER_EMAIL, password = E2E_USER_PASSWORD) {
  await page.goto('/login');
  await page.fill('input[name="email"]', email);
  await page.fill('input[name="password"]', password);
  await page.click('form[action="/login"] button[type="submit"]');
  await page.waitForURL('**/feed');
}

// Создать запись настроения через htmx-форму на /check-in.
// Форма шлёт JSON (hx-ext=json-enc), CSRF-токен уходит в теле. После успеха
// hyperscript редиректит на /feed, где появляется hero-карточка последней записи.
export async function submitEntry(
  page: Page,
  { mood = 5, mood_score, energy = 5, anxiety = 5, note = '', sleep_hours }: {
    mood?: number; mood_score?: number; energy?: number; anxiety?: number; note?: string; sleep_hours?: number;
  } = {},
) {
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
  const moodValue = mood_score ?? mood;
  await setRange('mood_score', moodValue);
  await setRange('energy', energy);
  await setRange('anxiety', anxiety);
  if (sleep_hours !== undefined) {
    // Раскрыть optional-блок сна, вписать значение.
    await page.locator('input[name="sleep_hours"]').evaluate((el) => {
      const collapse = el.closest('.collapse');
      const toggle = collapse?.querySelector('input[type="checkbox"]') as HTMLInputElement | null;
      if (toggle && !toggle.checked) toggle.click();
    });
    await page.locator('input[name="sleep_hours"]').evaluate((el) => el.removeAttribute('disabled'));
    await page.fill('input[name="sleep_hours"]', String(sleep_hours));
  }
  if (note) {
    // Раскрыть optional-блок (DaisyUI collapse → checkbox) и вписать заметку.
    await page.locator('textarea[name="note"]').evaluate((el) => {
      const collapse = el.closest('.collapse');
      const toggle = collapse?.querySelector('input[type="checkbox"]') as HTMLInputElement | null;
      if (toggle && !toggle.checked) toggle.click();
    });
    await page.locator('textarea[name="note"]').evaluate((el) => el.removeAttribute('disabled'));
    await page.fill('textarea[name="note"]', note);
  }
  await page.getByRole('button', { name: /Сохранить запись|Save entry/ }).click();
  await page.waitForURL(/\/feed/);
}

// Создать медикамент через модалку на /medications.
// CSRF-токен подставляется автоматически (json-enc), после успеха модалка
// закрывается, а карточка появляется в #med-list (outerHTML).
export async function addMedication(
  page: Page,
  name = 'Препарат А',
  dose = '600',
  unit = 'мг',
  schedule = '08:00, 20:00',
) {
  await page.goto('/medications');
  await page.getByRole('button', { name: /Добавить|Add/ }).first().click();
  const dialog = page.locator('#med-modal');
  await dialog.waitFor();
  await page.fill('input[name="name"]', name);
  await page.fill('input[name="dose"]', dose);
  await page.selectOption('select[name="dose_unit"]', unit);
  await page.fill('input[name="schedule"]', schedule);
  await page.getByRole('button', { name: /Сохранить|Save/ }).click();
  await expect(page.locator('#med-content')).toContainText(name);
}
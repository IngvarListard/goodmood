import { test, expect } from '@playwright/test';
import { ensureE2EUser, login } from '../helpers';

test.beforeAll(ensureE2EUser);

// Бэкфилл даты записи: опциональный блок «Дата» на /check-in.
// Показ DD.MM.YYYY (нативный пикер прозрачный, ISO уходит в скрытом input).
// Дефолт (блок закрыт) — запись за сегодня; открытый блок — за выбранную дату;
// будущее/старше 30 дней отбрасывается серверной валидацией.

// «Сегодня» берём с сервера (hidden input value) — серверные часы.
const serverToday = async (page: import('@playwright/test').Page) =>
  page.locator('input[name="date"]').inputValue();

const daysAgoFrom = (todayStr: string, n: number) => {
  const d = new Date(`${todayStr}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() - n);
  return d.toISOString().slice(0, 10);
};

const euro = (iso: string) => {
  const [y, m, d] = iso.split('-');
  return `${d}.${m}.${y}`;
};

// Раскрыть опциональный блок (DaisyUI collapse → checkbox) и снять disabled.
async function openOptional(page: import('@playwright/test').Page, selector: string) {
  await page.locator(selector).evaluate((el) => {
    const toggle = el.closest('.collapse')?.querySelector('input[type="checkbox"]') as HTMLInputElement | null;
    if (toggle && !toggle.checked) toggle.click();
  });
  await expect(page.locator(selector)).toBeEnabled();
}

// Заметка через optional-блок — уникальный маркер созданной записи.
async function setNote(page: import('@playwright/test').Page, note: string) {
  await openOptional(page, 'textarea[name="note"]');
  await page.fill('textarea[name="note"]', note);
}

// Группа записей под заголовком (Today / Yesterday) на /entries.
const dateGroup = (page: import('@playwright/test').Page, heading: string) =>
  page.locator('main').locator(`h2:has-text("${heading}")`).locator('..');

// Свежайшая карточка группы → её детальная страница (list-card не рендерит
// note; note видно только в карточке записи /entries/:id).
async function firstCardDetail(page: import('@playwright/test').Page, heading: string) {
  const href = await dateGroup(page, heading).locator('a').first().getAttribute('href');
  await page.goto(href!);
}

test.describe('entry date backfill', () => {
  test('date field: euro display, default today, min/max window', async ({ page }) => {
    await login(page, 'e2e-w0@goodmood.test');
    await page.goto('/check-in');
    const iso = page.locator('input[name="date"]');
    const nat = page.locator('#date-picker');
    const t = await iso.inputValue();
    await expect(iso).toBeDisabled();
    await expect(nat).toBeDisabled();
    await expect(t).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    await expect(page.locator('#date-picker-display')).toHaveText(euro(t));
    await expect(nat).toHaveAttribute('max', t);
    await expect(nat).toHaveAttribute('min', daysAgoFrom(t, 30));
  });

  test('default flow (block closed) saves entry for today', async ({ page }) => {
    await login(page, 'e2e-w0@goodmood.test');
    await page.goto('/check-in');
    const note = `e2e-today-${Date.now()}`;
    await setNote(page, note);
    await page.getByRole('button', { name: /Сохранить запись|Save entry/ }).click();
    await page.waitForURL(/\/feed/);
    await page.goto('/entries');
    await firstCardDetail(page, 'Today');
    await expect(page.locator('main')).toContainText(note);
  });

  test('backfill yesterday appears under yesterday date', async ({ page }) => {
    await login(page, 'e2e-w0@goodmood.test');
    await page.goto('/check-in');
    const yesterday = daysAgoFrom(await serverToday(page), 1);
    const note = `e2e-backfill-${Date.now()}`;
    await openOptional(page, 'input[name="date"]');
    await page.locator('#date-picker').fill(yesterday);
    // hyperscript синхронизирует скрытый ISO
    await expect(page.locator('input[name="date"]')).toHaveValue(yesterday);
    await expect(page.locator('#date-picker-display')).toHaveText(euro(yesterday));
    await setNote(page, note);
    await page.getByRole('button', { name: /Сохранить запись|Save entry/ }).click();
    await page.waitForURL(/\/feed/);
    await page.goto('/entries');
    await firstCardDetail(page, 'Yesterday');
    await expect(page.locator('main')).toContainText(note);
  });

  test('future date: UI blocks submit, server rejects crafted request', async ({ page }) => {
    await login(page, 'e2e-w0@goodmood.test');
    await page.goto('/check-in');
    await openOptional(page, 'input[name="date"]');
    // Значение вне max: нативная HTML-валидация не порождает submit —
    // через UI будущее даже не отправится.
    await page.locator('#date-picker').evaluate((el) => {
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set!;
      setter.call(el, '2027-01-01');
      el.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await page.getByRole('button', { name: /Сохранить запись|Save entry/ }).click();
    await expect(page).not.toHaveURL(/\/feed/);
    await expect(page.locator('#date-picker')).toBeVisible();
    // Серверная граница для crafted-запросов (минуя пикер): JSON API → 400.
    const token = await page.locator('input[name="__anti-forgery-token"]').first().inputValue();
    const res = await page.request.post('/entries', {
      headers: { 'x-csrf-token': token },
      data: { mood_score: 5, energy: 5, anxiety: 5, date: '2027-01-01' },
    });
    expect(res.status()).toBe(400);
    expect(await res.text()).toMatch(/date/i);
  });
});

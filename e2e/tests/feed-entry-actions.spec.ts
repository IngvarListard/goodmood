import { test, expect } from '@playwright/test';
import { ensureE2EUser, login } from '../helpers';

// Меню действий карточки ленты (change entry-actions-from-feed):
// три точки → daisyUI dropdown (Править/Удалить); удаление in-place
// через DELETE /entries/:id?from=feed, пустой день исчезает OOB.

test.beforeAll(ensureE2EUser);

const iso = (d: Date) => d.toISOString().slice(0, 10);

async function postEntry(
  page: import('@playwright/test').Page,
  date: string,
  fields: Record<string, unknown> = {},
) {
  const status = await page.evaluate(async ({ date, fields }) => {
    const token = document.querySelector('meta[name="csrf-token"]')?.getAttribute('content') ?? '';
    const res = await fetch('/entries', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'HX-Request': 'true',
        'X-CSRF-Token': token,
      },
      body: JSON.stringify({
        '__anti-forgery-token': token,
        mood_score: 5,
        energy: 5,
        anxiety: 5,
        date,
        ...fields,
      }),
    });
    return res.status;
  }, { date, fields });
  expect(status).toBe(201);
}

test.describe('feed entry actions', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('three-dot menu opens with Edit and Delete', async ({ page }) => {
    const today = iso(new Date());
    await postEntry(page, today);
    await postEntry(page, today);
    await page.goto('/feed');

    const card = page.locator('[id^="feed-entry-"]').first();
    await card.locator('div.dropdown[role="button"]').click();
    const menu = card.locator('ul.dropdown-content');
    await expect(menu).toBeVisible();
    await expect(menu.getByRole('link', { name: /Править|Edit/ })).toBeVisible();
    await expect(menu.getByRole('button', { name: /Удалить|Delete/ })).toBeVisible();
  });

  test('Edit navigates to /entries/:id', async ({ page }) => {
    const today = iso(new Date());
    await postEntry(page, today);
    await postEntry(page, today);
    await page.goto('/feed');

    const card = page.locator('[id^="feed-entry-"]').first();
    await card.locator('div.dropdown[role="button"]').click();
    await card
      .locator('ul.dropdown-content')
      .getByRole('link', { name: /Править|Edit/ })
      .click();
    await expect(page).toHaveURL(/\/entries\/\d+$/);
  });

  test('Delete removes card and empty day in-place without reload', async ({ page }) => {
    const past = new Date();
    past.setDate(past.getDate() - 27);
    const pastIso = iso(past);
    await postEntry(page, pastIso);
    await page.goto('/feed');

    const dayId = `#feed-day-${pastIso}`;
    await expect(page.locator(dayId)).toBeVisible();
    await page.evaluate(() => {
      (window as unknown as Record<string, number>).__p = 1;
    });

    const card = page.locator(`${dayId} [id^="feed-entry-"]`).first();
    await card.locator('div.dropdown[role="button"]').click();
    await card.getByRole('button', { name: /Удалить|Delete/ }).click();

    const dialog = page.locator('dialog[open]');
    await expect(dialog).toBeVisible();
    await expect(dialog).toContainText(/Удалить запись\?|Delete entry\?/);
    await dialog.getByRole('button', { name: /Удалить|Delete/ }).click();

    await expect(page.locator(dayId)).toHaveCount(0);
    expect(await page.evaluate(() => (window as unknown as Record<string, number>).__p)).toBe(1);
    await expect(page).toHaveURL(/\/feed/);
  });
});

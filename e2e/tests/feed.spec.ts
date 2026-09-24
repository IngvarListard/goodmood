import { test, expect } from '@playwright/test';
import {
  ensureE2EUser,
  login,
  submitEntry,
  E2E_EMPTY_USER_EMAIL,
  E2E_EMPTY_USER_PASSWORD,
} from '../helpers';

test.beforeAll(ensureE2EUser);

// /feed — лента записей с розой ветров (Фаза 2). Регрессионный кейс:
// старые записи с незаполненными осями (energy/anxiety nil) не должны
// ронять страницу 500.

test.describe('feed', () => {
  test('feed renders empty state with onboarding when no entries', async ({ page }) => {
    await login(page, E2E_EMPTY_USER_EMAIL, E2E_EMPTY_USER_PASSWORD);
    await expect(page.getByRole('heading', { name: /Лента|Feed/ })).toBeVisible();
    await expect(page.getByText(/Как ты сегодня\?|How are you today\?/)).toBeVisible();
    await expect(page.getByRole('link', { name: /Создать запись|Create entry/ })).toBeVisible();
  });

  test('entry created via /check-in appears as hero card with radar on /feed', async ({ page }) => {
    await login(page);
    await submitEntry(page, { mood: 8, energy: 8, anxiety: 2 });
    // hero-карточка последней записи сегодня: радар + ярлык "подъём"/elevated
    await expect(page.locator('article canvas[role="img"][aria-label*="Роза ветров"]').first()).toBeVisible();
    // Ярлык состояния ищем внутри hero-карточки: тот же текст есть в
    // свёрнутом теле summary-баннера (скрытом по умолчанию).
    await expect(page.locator('article').getByText(/подъём|elevated/, { exact: false }).first()).toBeVisible();
  });

  test('feed groups entries by day and shows timestamps', async ({ page }) => {
    await login(page);
    await submitEntry(page, { mood: 5, energy: 5, anxiety: 5 });
    await submitEntry(page, { mood: 6, energy: 8, anxiety: 3 });
    await expect(page.getByRole('heading', { name: /Сегодня|Today/ })).toBeVisible();
    // компактные карточки содержат время HH:MM
    await expect(page.locator('main').getByText(/\d{2}:\d{2}/).first()).toBeVisible();
  });

  test('entries with empty axes do not crash the feed (regression 500)', async ({ page }) => {
    await login(page);
    // Страница не должна вернуть 500: Playwright упадёт сам при ошибке сервера.
    const response = await page.goto('/feed');
    expect(response?.status()).toBe(200);
    await expect(page.getByRole('heading', { name: /Лента|Feed/ })).toBeVisible();
  });

  test('sleep 7h30m saved via check-in shows as 7ч30м on feed', async ({ page }) => {
    await login(page);
    await submitEntry(page, { mood: 6, energy: 6, anxiety: 3, sleep_hours: 7, sleep_minutes: 30 });
    // Сон хранится десятичными часами (7.5), отображается как «7ч30м»
    await expect(page.getByText(/7ч30м|7h30m/).first()).toBeVisible();
  });

  test('switch to Russian locale renders feed in Russian', async ({ page }) => {
    await login(page, E2E_EMPTY_USER_EMAIL, E2E_EMPTY_USER_PASSWORD);
    await page.getByRole('button', { name: /E2E Empty/ }).click();
    await page.getByRole('button', { name: 'Русский' }).click();
    // POST /locale → 302; затем явная навигация на /feed
    await page.goto('/feed');
    await expect(page.getByRole('heading', { name: /Лента/ })).toBeVisible();
  });
});
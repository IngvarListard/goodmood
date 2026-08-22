import { test, expect } from '@playwright/test';
import { ensureE2EUser, login } from '../helpers';

test.beforeAll(ensureE2EUser);

// Минимальный смоук: страницы рендерятся, логин работает, ключевые
// htmx-взаимодействия не сломаны. Дизайн ещё не устоялся — этот набор
// покрывает только каркас, а не пиксели.

test.describe('smoke', () => {
  test('login page renders', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('h1')).toContainText('Good Mood');
    await expect(page.locator('input[name="email"]')).toBeVisible();
    await expect(page.locator('input[name="password"]')).toBeVisible();
    await expect(page.locator('form[action="/login"] button[type="submit"]')).toBeVisible();
  });

  test('invalid credentials show error', async ({ page }) => {
    await page.goto('/login');
    await page.fill('input[name="email"]', 'nobody@example.com');
    await page.fill('input[name="password"]', 'nope');
    await page.click('button[type="submit"]');
    await expect(page.locator('.alert.alert-warning')).toBeVisible();
  });

  test('unauthenticated visitor is redirected to /login', async ({ page }) => {
    await page.goto('/entries');
    await expect(page).toHaveURL(/\/login/);
  });

  test('e2e user can log in and open main pages', async ({ page }) => {
    await login(page);
    await page.goto('/entries');
    await expect(page.getByRole('heading', { name: /Mood diary|Дневник настроения/ })).toBeVisible();
    await page.goto('/medications');
    await expect(page.getByRole('heading', { name: /Medications|Медикаменты/ })).toBeVisible();
  });
});
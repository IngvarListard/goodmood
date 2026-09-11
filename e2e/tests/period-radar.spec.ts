import { test, expect } from '@playwright/test';
import { ensureE2EUser, login, submitEntry } from '../helpers';

// Радар периода на hero-карточке ленты (change add-period-radar):
// дефолт — день; кнопки день/неделя/месяц свапают #radar-period
// через GET /feed/radar?period=… без перезагрузки страницы.
// Проверки по data-gm-radar — независимы от загрузки Chart.js (CDN).
// Локали (ru/en): селекторы по тексту — regex (AGENTS.md).

test.beforeAll(ensureE2EUser);

const dayBtn = /День|Day/;
const weekBtn = /Неделя|Week/;

test.describe('period radar', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    // гарантированная запись сегодня: у воркер-юзера БД может быть пуста
    await submitEntry(page, { mood: 7, energy: 7, anxiety: 3 });
  });

  test('radar defaults to day period', async ({ page }) => {
    const radar = page.locator('#radar-period');
    await expect(radar.locator('canvas[data-gm-radar]')).toBeVisible();
    await expect(radar.getByRole('button', { name: dayBtn })).toHaveAttribute('aria-pressed', 'true');
    await expect(radar.getByText(/среднее с учётом свежести|freshness-weighted average/)).toBeVisible();
    // агрегат всегда 4 оси (mood_score в БД NOT NULL)
    const values = await radar
      .locator('canvas')
      .evaluate((el: HTMLCanvasElement) => JSON.parse(el.dataset.gmRadar!).values);
    expect(values).toHaveLength(4);
  });

  test('week button swaps radar without page reload', async ({ page }) => {
    // маркер живого window: переживает свап, умирает при перезагрузке
    await page.evaluate(() => { (window as unknown as Record<string, number>).__p = 1; });

    const radar = page.locator('#radar-period');
    await radar.getByRole('button', { name: weekBtn }).click();
    await expect(radar.getByRole('button', { name: weekBtn })).toHaveAttribute('aria-pressed', 'true');
    await expect(radar.getByRole('button', { name: dayBtn })).toHaveAttribute('aria-pressed', 'false');

    expect(await page.evaluate(() => (window as unknown as Record<string, number>).__p)).toBe(1);
  });
});
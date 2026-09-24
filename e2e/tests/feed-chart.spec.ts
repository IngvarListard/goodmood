import { test, expect } from '@playwright/test';
import { ensureE2EUser, login } from '../helpers';

// Линейный график состояния на hero-карточке ленты
// (change replace-radar-with-time-chart): сервер рендерит canvas
// data-gm-chart (JSON {labels, datasets}) и aria-label, Chart.js рисует
// линии на клиенте (resources/public/js/feed-chart.js).

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

async function seedTwoDays(page: import('@playwright/test').Page) {
  const today = new Date();
  const yesterday = new Date(today);
  yesterday.setDate(yesterday.getDate() - 1);
  await postEntry(page, iso(today), { energy: 6 });
  await postEntry(page, iso(yesterday), { energy: 4 });
}

test.describe('feed time-series chart', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('canvas rendered with server payload and aria-label', async ({ page }) => {
    await seedTwoDays(page);
    await page.goto('/feed');
    const canvas = page.locator('canvas[data-gm-chart]').first();
    await expect(canvas).toBeVisible();

    const attrs = await canvas.evaluate((el: HTMLCanvasElement) => ({
      payload: JSON.parse(el.dataset.gmChart || '{}'),
      aria: el.getAttribute('aria-label'),
      size: { w: el.width, h: el.height },
    }));
    expect(attrs.payload.labels).toHaveLength(7);
    expect(attrs.payload.datasets.map((d: any) => d.key)).toEqual([
      'mood-score',
      'energy',
      'anxiety',
      'focus',
      'aggression',
      'composite',
    ]);
    expect(attrs.payload.datasets[5].key).toBe('composite');
    expect(attrs.aria).toMatch(/График состояния|State over time/);
    expect(attrs.size).toEqual({ w: 200, h: 200 });
  });

  test('Chart.js drew the line chart', async ({ page }) => {
    await seedTwoDays(page);
    await page.goto('/feed');
    const canvas = page.locator('canvas[data-gm-chart]').first();
    await expect(canvas).toBeVisible();
    await page.waitForTimeout(800);

    const state = await canvas.evaluate((el: HTMLCanvasElement) => {
      const chart = (window as any).Chart ? (window as any).Chart.getChart(el) : null;
      const ctx = el.getContext('2d')!;
      const img = ctx.getImageData(0, 0, el.width, el.height).data;
      let painted = 0;
      for (let i = 3; i < img.length; i += 4) if (img[i] > 0) painted++;
      return {
        hasChart: !!chart,
        chartType: chart?.config?.type,
        drawn: el.dataset.gmDrawn === 'true',
        painted,
      };
    });
    expect(state.hasChart).toBe(true);
    expect(state.chartType).toBe('line');
    expect(state.drawn).toBe(true);
    expect(state.painted).toBeGreaterThan(100);
  });

  test('period toggle swaps fragment without reload', async ({ page }) => {
    await seedTwoDays(page);
    await page.goto('/feed');
    await page.getByRole('button', { name: /3 дня|3 days/ }).click();

    const canvas = page.locator('canvas[data-gm-chart]').first();
    await expect(canvas).toBeVisible();
    const labels = await canvas.evaluate(
      (el: HTMLCanvasElement) => JSON.parse(el.dataset.gmChart || '{}').labels.length,
    );
    expect(labels).toBe(3);
    await expect(page.getByRole('button', { name: /3 дня|3 days/ })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  });

  test('chart redraws after hx-boost navigation', async ({ page }) => {
    await seedTwoDays(page);
    await page.goto('/feed');
    await expect(page.locator('canvas[data-gm-chart]').first()).toBeVisible();

    await page.click('nav a[href="/check-in"]');
    await expect(page).toHaveURL(/\/check-in/);
    await page.click('nav a[href="/feed"]');
    await expect(page).toHaveURL(/\/feed/);

    const canvas = page.locator('canvas[data-gm-chart]').first();
    await expect(canvas).toBeVisible();
    await page.waitForTimeout(800);
    const drawn = await canvas.evaluate((el: HTMLCanvasElement) => el.dataset.gmDrawn === 'true');
    expect(drawn).toBe(true);
  });
});

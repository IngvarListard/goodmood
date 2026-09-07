import { test, expect } from '@playwright/test';
import { ensureE2EUser, login } from '../helpers';

// Радар «роза ветров» на hero-карточке ленты (change add-js-radar-chart):
// сервер рендерит canvas с data-gm-radar (JSON {labels, values}) и aria-label,
// Chart.js рисует на клиенте (resources/public/js/radar.js).

test.beforeAll(ensureE2EUser);

test.describe('feed radar', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/feed');
  });

  test('canvas rendered with server payload and aria-label', async ({ page }) => {
    const canvas = page.locator('article canvas[role="img"][aria-label*="Роза ветров"]').first();
    await expect(canvas).toBeVisible();

    const attrs = await canvas.evaluate((el: HTMLCanvasElement) => ({
      payload: JSON.parse(el.dataset.gmRadar || '{}'),
      aria: el.getAttribute('aria-label'),
      size: { w: el.width, h: el.height },
    }));
    expect(attrs.payload.labels.length).toBeGreaterThanOrEqual(3);
    expect(attrs.payload.labels.length).toBeLessThanOrEqual(4);
    expect(attrs.payload.values).toHaveLength(attrs.payload.labels.length);
    expect(attrs.aria).toContain('Роза ветров');
    expect(attrs.size).toEqual({ w: 200, h: 200 });
  });

  test('Chart.js drew the radar (non-blank canvas, instance attached)', async ({ page }) => {
    const canvas = page.locator('article canvas.gm-radar').first();
    await expect(canvas).toBeVisible();
    // afterSettle/animation: даём отрисовке завершиться
    await page.waitForTimeout(1000);

    const state = await canvas.evaluate((el: HTMLCanvasElement) => {
      const chart = (window as any).Chart ? (window as any).Chart.getChart(el) : null;
      // непустой холст: в центральной зоне есть непрозрачные пиксели
      const ctx = el.getContext('2d')!;
      const img = ctx.getImageData(60, 60, 80, 80).data;
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
    expect(state.chartType).toBe('radar');
    expect(state.drawn).toBe(true);
    expect(state.painted).toBeGreaterThan(100);
  });

  test('radar redraws after hx-boost navigation', async ({ page }) => {
    await expect(page.locator('article canvas.gm-radar').first()).toBeVisible();
    // навигация по нижней навигации — hx-boost (body swap)
    await page.click('nav a[href="/check-in"]');
    await expect(page).toHaveURL(/\/check-in/);
    await page.click('nav a[href="/feed"]');
    await expect(page).toHaveURL(/\/feed/);

    const canvas = page.locator('article canvas.gm-radar').first();
    await expect(canvas).toBeVisible();
    await page.waitForTimeout(1000);
    const drawn = await canvas.evaluate((el: HTMLCanvasElement) => el.dataset.gmDrawn === 'true');
    expect(drawn).toBe(true);
  });
});

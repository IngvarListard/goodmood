import { test, expect } from '@playwright/test';
import { ensureE2EUser, login } from '../helpers';

// PageShell: единая content-колонка (change add-mobile-page-shell).
// На телефоне все страницы — «100% вьюпорта, паддинг 16px, без h-скролла»;
// на десктопе колонка 512px, отцентрирована правее sidebar'а.

test.beforeAll(ensureE2EUser);

const routes = ['/feed', '/check-in', '/medications', '/settings', '/insights', '/insights/new'];

async function shellMetrics(page: import('@playwright/test').Page) {
  return page.evaluate(() => {
    const shell = document.querySelector('main > div');
    const r = shell!.getBoundingClientRect();
    const cs = getComputedStyle(shell!);
    return {
      maxWidth: cs.maxWidth,
      width: Math.round(r.width),
      left: Math.round(r.left),
      paddingBottom: cs.paddingBottom,
      hScroll: document.scrollingElement.scrollWidth > window.innerWidth,
      mainBottomPad: getComputedStyle(document.querySelector('main')!).paddingBottom,
    };
  });
}

test.describe('page shell (mobile 390px)', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  for (const route of routes) {
    test(`одинаковая колонка без h-скролла: ${route}`, async ({ page }) => {
      await page.goto(route);
      const m = await shellMetrics(page);
      expect(m.maxWidth).toBe('512px');
      expect(m.width).toBe(390);
      expect(m.left).toBe(0);
      expect(m.hScroll).toBe(false);
      // var(--gm-nav-h)=88px + 0.75rem + safe-area(0 в chromium)
      expect(m.paddingBottom).toBe('100px');
      expect(m.mainBottomPad).toBe('0px'); // pb-16 съехал в shell
    });
  }
});

test.describe('page shell (desktop 1280px)', () => {
  test.use({ viewport: { width: 1280, height: 800 } });

  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('колонка 512px, отцентрована правее sidebar', async ({ page }) => {
    await page.goto('/medications');
    const m = await shellMetrics(page);
    expect(m.maxWidth).toBe('512px');
    expect(m.width).toBe(512);
    // main = 1280 - 256 (sidebar) = 1024; центр колонки: 256 + (1024-512)/2 = 512
    expect(m.left).toBe(512);
    expect(m.hScroll).toBe(false);
  });
});

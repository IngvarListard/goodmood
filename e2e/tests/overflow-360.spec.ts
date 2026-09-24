import { test, expect } from '@playwright/test';
import { ensureE2EUser, login } from '../helpers';

// Overflow-гигиена (change fix-horizontal-overflow): длинный пользовательский
// текст (название периода, заметка, свободный state_label) не должен давать
// горизонтальную прокрутку страницы на 360px в обеих локалях и темах.

test.beforeAll(ensureE2EUser);

const iso = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

const LONG_TOKEN = 'W'.repeat(60);
const LONG_NOTE = 'N'.repeat(140);

async function postJson(
  page: import('@playwright/test').Page,
  url: string,
  payload: Record<string, unknown>,
): Promise<any> {
  return await page.evaluate(
    async ({ url, payload }) => {
      const token = document.querySelector('meta[name="csrf-token"]')?.getAttribute('content') ?? '';
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': token },
        body: JSON.stringify({ ...payload, '__anti-forgery-token': token }),
      });
      try {
        return await res.json();
      } catch {
        return null;
      }
    },
    { url, payload },
  );
}

function hScroll(page: import('@playwright/test').Page): Promise<number> {
  return page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
}

test('long text does not cause horizontal scroll on 360px', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 800 });
  await login(page);

  const entry = await postJson(page, '/entries', {
    mood_score: 5,
    energy: 5,
    anxiety: 5,
    date: iso(new Date()),
    note: LONG_NOTE,
    activity: LONG_TOKEN,
    state_label: LONG_TOKEN,
  });
  expect(entry?.id).toBeTruthy();
  await postJson(page, '/periods/start', { label: LONG_TOKEN });

  const paths = ['/feed', '/entries', `/entries/${entry.id}`];
  for (const locale of ['ru', 'en']) {
    for (const theme of ['dark', 'light']) {
      await page.context().addCookies([
        { name: 'gm-locale', value: locale, url: 'http://localhost:3000' },
        { name: 'gm-theme', value: theme, url: 'http://localhost:3000' },
      ]);
      for (const path of paths) {
        await page.goto(path);
        const over = await hScroll(page);
        expect(over, `${path} @ ${locale}/${theme}`).toBeLessThanOrEqual(1);
      }
    }
  }
});

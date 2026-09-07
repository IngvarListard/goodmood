import { test } from '@playwright/test';
import { ensureE2EUser, login } from '../helpers';

test('debug locale full trace', async ({ page }) => {
  test.setTimeout(30000);
  await ensureE2EUser();
  await login(page);
  page.on('pageerror', e => console.log('PAGEERROR', e.message.slice(0, 200)));
  page.on('response', async r => {
    if (['/locale', '/feed', '/settings'].some(p => r.url().includes(p)) && r.request().method() === 'POST' || r.url().endsWith(':3000/')) {
      const h = r.headers();
      console.log(`RESP ${r.status()} ${r.request().method()} ${r.url()} | location=${h['location'] ?? 'NONE'} | hxrequest=${r.request().headers()['hx-request'] ?? '-'}`);
    }
  });
  await page.goto('/settings');
  const before = await page.evaluate(() => document.documentElement.lang);
  await page.getByRole('button', { name: /Русский|Russian/i }).first().click();
  await page.waitForTimeout(2500);
  const after = await page.evaluate(() => ({
    url: location.href, lang: document.documentElement.lang, bodyLen: document.body.innerHTML.length,
  }));
  console.log(`RESULT before-lang=${before} after=`, JSON.stringify(after));
});

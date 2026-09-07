import { test } from '@playwright/test';
import { ensureE2EUser, login } from '../helpers';

test('screenshot section-4 pages', async ({ page }) => {
  await login(page);
  await page.context().addCookies([{ name: 'gm-theme', value: 'dark', url: 'http://localhost:3000' }]);
  for (const [name, path] of [['meds-dark', '/medications'], ['settings-dark', '/settings'], ['insights-dark', '/insights']]) {
    await page.goto(path);
    await page.waitForTimeout(700);
    await page.screenshot({ path: `/tmp/opencode/${name}.png`, fullPage: true });
  }
  await page.context().addCookies([{ name: 'gm-theme', value: 'light', url: 'http://localhost:3000' }]);
  for (const [name, path] of [['meds-light', '/medications'], ['settings-light', '/settings']]) {
    await page.goto(path);
    await page.waitForTimeout(700);
    await page.screenshot({ path: `/tmp/opencode/${name}.png`, fullPage: true });
  }
});

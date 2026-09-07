import { test, expect, Page } from '@playwright/test';
import { execSync } from 'node:child_process';
import { ensureE2EUser, login, projectRoot, workerUser } from '../helpers';

test.beforeAll(ensureE2EUser);

// Удалить инсайты юзера воркера напрямую в БД (идемпотентно).
function resetInsights() {
  execSync(`E2E_USER_EMAIL=${workerUser().email} clojure -M dev/reset_e2e_insights.clj`, {
    cwd: projectRoot,
    timeout: 90000,
    stdio: 'ignore',
  });
}

// Заполнить форму создания инсайта на /insights/new.
async function fillInsightForm(
  page: Page,
  {
    context = 'Контекст: тревога 7, фокус 2',
    state = 'anxiety',
    category = 'coping',
    advice = ['дыхание 4-7-8', 'текст близкому'],
    identity = '',
  } = {},
) {
  await page.fill('textarea[name="context"]', context);
  await page.selectOption('select[name="state_label"]', state);
  await page.selectOption('select[name="category"]', category);
  // заполнить первый пункт
  await page.locator('#advice-list .advice-item').nth(0).locator('textarea').fill(advice[0] ?? '');
  // добавить остальные через кнопку «Добавить пункт» (htmx-get → beforeend)
  for (let i = 1; i < advice.length; i++) {
    await page.getByRole('button', { name: /Добавить пункт/ }).click();
    const rows = page.locator('#advice-list .advice-item');
    await expect(rows).toHaveCount(i + 1);
    await rows.nth(i).locator('textarea').fill(advice[i]);
  }
  if (identity) {
    await page.fill('textarea[name="identity"]', identity);
  }
}

// Создать инсайт через UI: /insights/new → форма → Сохранить → /insights.
async function createInsight(page: Page, overrides: any = {}) {
  await page.goto('/insights/new');
  await fillInsightForm(page, overrides);
  await page.getByRole('button', { name: /Сохранить/ }).click();
  await page.waitForURL('**/insights');
}

// Создать запись сегодня для hero-карточки (state_label задаёт текущее состояние).
async function createEntryForState(page: Page, stateLabel: string) {
  const resp = await page.evaluate(async (sl) => {
    const token = document.querySelector('meta[name="csrf-token"]')?.getAttribute('content') ?? '';
    const r = await fetch('/entries', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'HX-Request': 'true', 'X-CSRF-Token': token },
      body: JSON.stringify({
        '__anti-forgery-token': token,
        mood_score: 3,
        energy: 2,
        anxiety: 7,
        focus: 3,
        note: 'тревожно',
        template: 'morning',
        state_label: sl,
      }),
    });
    return { status: r.status };
  }, stateLabel);
  expect(resp.status).toBe(201);
}

test.describe('insights', () => {
  test.beforeEach(async ({ page }) => {
    resetInsights();
    await page.context().addCookies([{ name: 'gm-locale', value: 'ru', url: 'http://localhost:3000' }]);
    await login(page);
  });

  test('empty state shows onboarding with Create button', async ({ page }) => {
    await page.goto('/insights');
    await expect(page.getByRole('heading', { name: 'Инсайты' })).toBeVisible();
    await expect(page.getByText('У тебя ещё нет инсайтов')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Создать первый' })).toBeVisible();
  });

  test('create insight via form, appears in list', async ({ page }) => {
    const context = `Контекст ${Date.now()}`;
    await createInsight(page, {
      context,
      state: 'anxiety',
      category: 'coping',
      advice: ['дыхание 4-7-8', 'текст близкому'],
      identity: 'я не своя тревога',
    });
    // список: карточка с context, советом, identity, бейджами
    await expect(page.locator('#insights-list .card')).toHaveCount(1);
    const card = page.locator('#insights-list .card').first();
    await expect(card).toContainText(context);
    await expect(card).toContainText('дыхание 4-7-8');
    await expect(card).toContainText('я не своя тревога');
    await expect(card.getByText('Копинг')).toBeVisible();
    await expect(card.getByText('тревога', { exact: true })).toBeVisible();
    // кнопка «…ещё N советов» раскрывает остальные (htmx)
    const moreBtn = card.getByRole('button', { name: /ещё 1 совет/ });
    await expect(moreBtn).toBeVisible();
    await moreBtn.click();
    await expect(card).toContainText('текст близкому');
    await expect(moreBtn).not.toBeVisible();
  });

  test('multiple advice items all appear on detail page as numbered list', async ({ page }) => {
    await createInsight(page, {
      advice: ['один', 'два', 'три'],
      state: 'elevated',
      category: 'general',
    });
    // открыть полный вид
    await page.locator('#insights-list .card a').first().click();
    await page.waitForURL(/\/insights\/\d+/);
    const items = page.locator('ol.list-decimal li');
    await expect(items).toHaveCount(3);
    await expect(items.nth(0)).toHaveText('один');
    await expect(items.nth(1)).toHaveText('два');
    await expect(items.nth(2)).toHaveText('три');
  });

  test('validation: empty advice shows soft alert and stays on form', async ({ page }) => {
    await page.goto('/insights/new');
    await page.fill('textarea[name="context"]', 'контекст');
    await page.selectOption('select[name="state_label"]', 'anxiety');
    await page.selectOption('select[name="category"]', 'coping');
    await page.locator('#advice-list textarea').fill('');
    await page.getByRole('button', { name: /Сохранить/ }).click();
    await expect(page.locator('#form-error .alert-warning')).toBeVisible();
    await expect(page).toHaveURL(/\/insights\/new/);
  });

  test('filter tabs show only category insights', async ({ page }) => {
    await createInsight(page, {
      context: 'Тревожный контекст',
      state: 'anxiety',
      category: 'coping',
      advice: ['совет A'],
    });
    await createInsight(page, {
      context: 'Продуктивный контекст',
      state: 'elevated',
      category: 'productivity',
      advice: ['совет B'],
    });
    await expect(page.locator('#insights-list .card')).toHaveCount(2);
    await page.getByRole('tab', { name: 'Продуктивность' }).click();
    await expect(page.locator('#insights-list .card')).toHaveCount(1);
    const card = page.locator('#insights-list .card').first();
    await expect(card).toContainText('Продуктивный контекст');
  });

  test('feed widget shows matching insight for current state', async ({ page }) => {
    // запись сегодня со state_label anxiety (hero)
    await createEntryForState(page, 'anxiety');
    await createInsight(page, {
      context: 'Тревога — главное дышать',
      state: 'anxiety',
      category: 'coping',
      advice: ['дыхание 4-7-8'],
    });
    await page.goto('/feed');
    // Карточка совета: заголовок с состоянием, совет, inline-раскрытие
    await expect(page.locator('#feed-insights').getByText(/Совет для состояния|Advice for the/)).toBeVisible();
    await expect(page.locator('#feed-insights').getByText('Тревога — главное дышать')).toBeVisible();
    await expect(page.locator('#feed-insights').getByText('дыхание 4-7-8')).toBeVisible();
    await expect(page.getByTestId('feed-advice-card')).toBeVisible();
  });

  test('feed shows onboarding when no insight for current state', async ({ page }) => {
    await createEntryForState(page, 'anxiety');
    // инсайт только для ДРУГОГО состояния
    await createInsight(page, {
      context: 'Спад — беречь себя',
      state: 'low',
      category: 'coping',
      advice: ['не торопиться'],
    });
    await page.goto('/feed');
    await expect(page.getByText(/Инсайтов для состояния|No insights for/)).toBeVisible();
    await expect(page.getByRole('link', { name: /Создать/ }).first()).toBeVisible();
  });

  test('insight detail page shows all fields and action buttons', async ({ page }) => {
    await createInsight(page, {
      context: 'Детальный контекст',
      state: 'anxiety',
      category: 'coping',
      advice: ['совет один'],
      identity: 'я спокойна',
    });
    await page.locator('#insights-list .card a').first().click();
    await page.waitForURL(/\/insights\/\d+/);
    await expect(page.getByText('Детальный контекст')).toBeVisible();
    await expect(page.locator('ol.list-decimal li')).toHaveCount(1);
    await expect(page.getByText('я спокойна')).toBeVisible();
    // state_label не редактируется: только context/advice/identity имеют «Править»
    await expect(page.getByRole('button', { name: 'Править' })).toHaveCount(3);
    await expect(page.getByRole('button', { name: 'Удалить' })).toBeVisible();
  });

  test('delete insight via modal confirm', async ({ page }) => {
    await createInsight(page, { context: 'Удалю этот', category: 'general', state: 'neutral', advice: ['совет'] });
    await page.goto('/insights');
    await expect(page.locator('#insights-list .card')).toHaveCount(1);
    // открыть модалку удаления карточки (кнопка-мусорка)
    const card = page.locator('#insights-list .card').first();
    const modalId = await card.locator('button[aria-label="Удалить инсайт"]').getAttribute('_').then(
      (s) => s?.match(/del-insight-(\d+)/)?.[1] ?? '',
    );
    await card.locator('button[aria-label="Удалить инсайт"]').click();
    await expect(page.locator(`#del-insight-${modalId} .modal-box`)).toBeAttached();
    await page.locator(`#del-insight-${modalId} .btn-error`).click();
    await expect(page.locator('#insights-list .card')).toHaveCount(0);
    await expect(page.getByText('У тебя ещё нет инсайтов')).toBeVisible();
  });
});
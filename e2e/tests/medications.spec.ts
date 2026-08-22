import { test, expect, Page } from '@playwright/test';
import { execSync } from 'node:child_process';
import { ensureE2EUser, login, addMedication, submitEntry } from '../helpers';

test.beforeAll(ensureE2EUser);

// Удалить медикаменты e2e-юзера напрямую в БД (идемпотентно).
function resetMedications() {
  execSync('clojure -M dev/reset_e2e_meds.clj', {
    cwd: '..',
    timeout: 90000,
    stdio: 'ignore',
  });
}

// Сквозной сценарий по реестру медикаментов: модалка, htmx-свапы,
// виджет приёма, доза, деактивация/активация. Чистит данные e2e-юзера
// перед каждым тестом (dev/reset_e2e_meds.clj).

let nameCounter = 0;
function uniqueName(prefix: string) {
  nameCounter += 1;
  return `${prefix}-${Date.now()}-${nameCounter}`;
}

test.describe('medications', () => {
  test.beforeEach(async ({ page }) => {
    resetMedications();
    await page.context().addCookies([{ name: 'gm-locale', value: 'ru', url: 'http://localhost:3000' }]);
    await login(page);
    await page.goto('/medications');
  });

  test('empty state shows onboarding and Add button', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Медикаменты' })).toBeVisible();
    await expect(page.getByText('Здесь будут ваши медикаменты')).toBeVisible();
    await expect(page.locator('button', { hasText: 'Добавить медикамент' })).toBeVisible();
  });

  test('add medication via modal, card and intake slots appear', async ({ page }) => {
    const name = uniqueName('Препарат А');
    await page.locator('button', { hasText: 'Добавить медикамент' }).click();
    const dialog = page.locator('#med-modal');
    await expect(dialog).toHaveAttribute('open', /.*/);
    await page.fill('input[name="name"]', name);
    await page.fill('input[name="dose"]', '600');
    await page.selectOption('select[name="dose_unit"]', 'мг');
    await page.fill('input[name="schedule"]', '08:00, 20:00');
    await page.click('#med-modal button[type="submit"]');
    // модалка закрывается, карточка и виджет появляются
    await expect(dialog).not.toHaveAttribute('open', /.*/);
    await expect(page.getByText(name, { exact: false }).first()).toBeVisible();
    await expect(page.getByText('600 мг · 08:00, 20:00').first()).toBeVisible();
    await expect(page.locator('#intake-widget')).toBeVisible();
    await expect(page.locator('#intake-widget form')).toHaveCount(2);
    await expect(page.locator('#intake-widget button[value="taken"]')).toHaveCount(2);
  });

  test('mark taken shows badge, repeat does not duplicate', async ({ page }) => {
    const name = uniqueName('Препарат А');
    await addMedication(page, name, '600', 'мг', '08:00, 20:00');
    const slot08 = page.locator('#intake-widget form', { hasText: name }).first();
    const slot20 = page.locator('#intake-widget form', { hasText: name }).nth(1);
    await expect(slot08).toBeVisible();
    await slot08.locator('button[value="taken"]').click();
    await expect(slot08.getByText('принял')).toBeVisible();
    await expect(slot08.locator('button[value="pending"]')).toBeVisible();
    // повторный тап — снова taken, слот единственный
    await slot08.locator('button[value="pending"]').click(); // отмена
    await expect(slot08.locator('button[value="taken"]')).toBeVisible();
    await slot08.locator('button[value="taken"]').click();
    await expect(slot08.getByText('принял')).toBeVisible();
    await expect(slot20).toBeVisible();
  });

  test('mark skipped shows warning badge', async ({ page }) => {
    const name = uniqueName('Препарат Г');
    await addMedication(page, name, '600', 'мг', '08:00');
    const slot = page.locator('#intake-widget form', { hasText: name }).first();
    await slot.locator('button[value="skipped"]').click();
    await expect(slot.getByText('пропустил')).toBeVisible();
    await expect(slot.locator('.badge-warning')).toBeVisible();
    await expect(slot.locator('.badge-success')).toHaveCount(0);
  });

  test('cancel returns slot to unmarked', async ({ page }) => {
    const name = uniqueName('Препарат Д');
    await addMedication(page, name, '600', 'мг', '08:00');
    const slot = page.locator('#intake-widget form', { hasText: name }).first();
    await slot.locator('button[value="taken"]').click();
    await expect(slot.getByText('принял')).toBeVisible();
    await slot.locator('button[value="pending"]').click();
    await expect(slot.locator('button[value="taken"]')).toBeVisible();
    await expect(slot.locator('.badge')).toHaveCount(0);
  });

  test('edit dose updates card', async ({ page }) => {
    const name = uniqueName('Препарат А');
    await addMedication(page, name, '600', 'мг', '08:00, 20:00');
    await page.locator('button', { hasText: 'Редактировать' }).first().click();
    const dialog = page.locator('#med-modal');
    await expect(dialog).toHaveAttribute('open', /.*/);
    await page.fill('input[name="dose"]', '450');
    await page.click('#med-modal button[type="submit"]');
    await expect(dialog).not.toHaveAttribute('open', /.*/);
    await expect(page.getByText('450 мг · 08:00, 20:00').first()).toBeVisible();
  });

  test('deactivate moves card to inactive section, activate brings back', async ({ page }) => {
    const name = uniqueName('Препарат А');
    await addMedication(page, name, '600', 'мг', '08:00, 20:00');
    page.on('dialog', (d) => d.accept());
    await page.locator('button', { hasText: 'Деактивировать' }).first().click();
    await expect(page.getByText('Неактивные (1)')).toBeVisible();
    // раскрыть collapse неактивных (клик по чекбоксу collapse)
    await page.locator('.collapse > input[type="checkbox"]').first().click();
    await expect(page.getByRole('button', { name: 'Активировать' })).toBeVisible();
    await page.getByRole('button', { name: 'Активировать' }).click();
    await expect(page.getByText('Неактивные (1)')).toHaveCount(0);
    await expect(page.getByText(name).first()).toBeVisible();
  });

  test('sensitive toggle shows badge on card', async ({ page }) => {
    const name = uniqueName('Препарат А');
    await page.locator('button', { hasText: 'Добавить' }).first().click();
    await page.fill('input[name="name"]', name);
    await page.fill('input[name="dose"]', '600');
    await page.selectOption('select[name="dose_unit"]', 'мг');
    await page.fill('input[name="schedule"]', '08:00');
    await page.click('input[name="sensitive"]');
    await page.click('#med-modal button[type="submit"]');
    await expect(page.locator('#med-modal')).not.toHaveAttribute('open', /.*/);
    await expect(page.locator('.badge-neutral', { hasText: 'sensitive' })).toBeVisible();
  });
});

test.describe('entries', () => {
  test('create entry via htmx form', async ({ page }) => {
    await page.context().addCookies([{ name: 'gm-locale', value: 'ru', url: 'http://localhost:3000' }]);
    await login(page);
    await page.goto('/entries');
    const before = await page.locator('#entries-list li').count();
    await page.getByRole('button', { name: /Сохранить запись|Save entry/ }).click();
    await expect(page.locator('#entries-list li')).toHaveCount(before + 1);
    await expect(page.getByText('Настроение 5/10').first()).toBeVisible();
  });
});
import { test, expect } from '@playwright/test';
import { login, submitEntry, E2E_EMPTY_USER_EMAIL, E2E_EMPTY_USER_PASSWORD } from '../helpers';

const iso = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

// Создать запись на дату через API (JSON-ответ, чтобы получить id).
async function postEntry(page: import('@playwright/test').Page, date: string): Promise<number> {
  return await page.evaluate(async (date) => {
    const token = document.querySelector('meta[name="csrf-token"]')?.getAttribute('content') ?? '';
    const res = await fetch('/entries', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': token },
      body: JSON.stringify({
        '__anti-forgery-token': token,
        mood_score: 5,
        energy: 5,
        anxiety: 5,
        date,
      }),
    });
    const json = await res.json();
    return json.id;
  }, date);
}

test.describe('entries CRUD', () => {
  test('create entry, open from list, edit note, delete with confirm', async ({ page }) => {
    await login(page);
    await submitEntry(page, { mood: 7, energy: 6, anxiety: 3, note: 'Заметка до правки' });

    // Точка входа — иконка «все записи» в шапке ленты
    await page.getByRole('link', { name: /Записи|Entries/ }).click();
    await expect(page).toHaveURL(/\/entries$/);

    // Свежая запись — первая карточка списка (date desc, created_at desc)
    const listCards = page.locator('#entries-page a[href^="/entries/"]');
    const entryHref = await listCards.first().getAttribute('href');
    expect(entryHref).toMatch(/\/entries\/\d+$/);
    await listCards.first().click();
    await expect(page).toHaveURL(new RegExp(`${entryHref!.replace(/[/.]/g, '\\$&')}$`));
    await expect(page.locator('[id^="entry-read-"]').getByText('Заметка до правки')).toBeVisible();

    // Отредактировать заметку in-place: Править → новый текст → Сохранить
    await page.getByRole('button', { name: /Править|Edit/ }).click();
    await expect(page.locator('[id^="entry-edit-"] form')).toBeVisible();
    await page.fill('textarea[name="note"]', 'Заметка после правки');
    await page.getByRole('button', { name: /Сохранить|Save/ }).click();

    // Свап read-блока без перезагрузки страницы
    await expect(page.locator('[id^="entry-read-"]').getByText('Заметка после правки')).toBeVisible();
    await expect(page.locator('[id^="entry-edit-"] form')).toBeHidden();

    // Чужая запись не отдаётся (API-уровень): другой пользователь → 404
    const entryUrl = entryHref!;
    await page.context().clearCookies();
    await login(page, E2E_EMPTY_USER_EMAIL, E2E_EMPTY_USER_PASSWORD);
    const foreign = await page.request.get(entryUrl);
    await expect(foreign.status()).toBe(404);

    // Вернуться и удалить запись с подтверждением
    await page.context().clearCookies();
    await login(page);
    await page.goto(entryUrl);
    const deleteBtn = page.getByRole('button', { name: /Удалить|Delete/ }).first();
    await deleteBtn.click();
    const modalId = await deleteBtn.getAttribute('_').then((s) => s?.match(/del-entry-(\d+)/)?.[1] ?? '');
    // Отмена: запись остаётся
    await page.locator(`#del-entry-${modalId}`).getByRole('button', { name: /Отмена|Cancel/ }).click();
    await expect(page.locator('[id^="entry-read-"]').getByText('Заметка после правки')).toBeVisible();
    // Подтверждение: редирект на /entries, карточки записи больше нет
    await deleteBtn.click();
    await page.locator(`#del-entry-${modalId} .btn-error`).click();
    await expect(page).toHaveURL(/\/entries$/);
    await expect(page.locator(`#entries-page a[href="${entryUrl}"]`)).toHaveCount(0);
  });

  test('scrolling /entries loads older days and exhausts', async ({ page }) => {
    await login(page);
    const old = new Date();
    old.setDate(old.getDate() - 20);
    const oldId = await postEntry(page, iso(old));

    await page.goto('/entries');
    // подгружаем чанки, пока sentinel не исчезнет на исчерпании
    // (sentinel может быть во вьюпорте сразу — тогда автоподгрузка)
    for (let i = 0; i < 15; i++) {
      const sentinel = page.locator('#entries-older');
      if ((await sentinel.count()) === 0) break;
      await sentinel.scrollIntoViewIfNeeded();
      await page.waitForTimeout(300);
    }
    await expect(page.locator(`#entries-page a[href="/entries/${oldId}"]`)).toBeVisible();
    await expect(page.locator('#entries-older')).toHaveCount(0, { timeout: 10000 });
  });
});

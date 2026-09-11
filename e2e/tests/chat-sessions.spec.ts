import { test, expect } from '@playwright/test';
import { ensureE2EUser, login, workerUser, E2E_USER_PASSWORD } from '../helpers';

// Сессии чата ассистента (change add-chat-sessions):
// «новый чат» начинает новую сессию (POST /ai/chat/new → свап панели),
// сообщения новой сессии не смешиваются со старыми; логин-кука живёт 90 дней.

test.beforeAll(ensureE2EUser);

test('new chat starts a fresh session; old messages stay in DB', async ({ page }) => {
  await login(page);
  await page.goto('/feed');
  await page.getByTestId('assistant-fab').click();
  const modal = page.getByTestId('assistant-modal');
  await expect(modal).toBeVisible();

  const input = modal.locator('#chat-input');
  const marker1 = `старая-сессия-${Date.now()}`;
  await input.fill(marker1);
  await input.press('Enter');
  // серверный свап #chat-response происходит после ответа модели
  await expect(modal.getByTestId('chat-typing')).toHaveCount(0, { timeout: 45000 });
  await expect(modal.locator('#chat-response')).toContainText(marker1);

  // «новый чат» — свежая панель с пустой историей
  await modal.getByRole('button', { name: /New chat|Новый чат/ }).click();
  await expect(modal.locator('#chat-response')).not.toContainText(marker1);

  // сообщение попадает в новую сессию
  const marker2 = `новая-сессия-${Date.now()}`;
  const input2 = modal.locator('#chat-input');
  await input2.fill(marker2);
  await input2.press('Enter');
  await expect(modal.getByTestId('chat-typing')).toHaveCount(0, { timeout: 45000 });
  await expect(modal.locator('#chat-response')).toContainText(marker2);

  // переоткрытие истории: только новая сессия, старые сообщения в БД не видны
  await modal.getByRole('button', { name: /Close chat|Закрыть чат/ }).last().click();
  await expect(modal).not.toBeVisible();
  await page.getByTestId('assistant-fab').click();
  await expect(modal).toBeVisible();
  await expect(modal.locator('#chat-response')).toContainText(marker2);
  await expect(modal.locator('#chat-response')).not.toContainText(marker1);
});

test('login sets gm-session cookie Max-Age=7776000 (90 days)', async ({ page }) => {
  // browser enforcement (spec users-auth): кука живёт 90 дней от логина
  await login(page);
  const gm = (await page.context().cookies()).find((c) => c.name === 'gm-session');
  expect(gm).toBeTruthy();
  const remainingSec = gm!.expires - Date.now() / 1000;
  expect(remainingSec).toBeLessThanOrEqual(7776000);
  expect(remainingSec).toBeGreaterThan(7776000 - 120); // допуск на момент установки
});
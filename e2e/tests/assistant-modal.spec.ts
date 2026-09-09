import { test, expect } from '@playwright/test';
import { ensureE2EUser, login } from '../helpers';

test.beforeAll(ensureE2EUser);

test('assistant FAB opens bottom-sheet modal with chat content', async ({ page }) => {
  await login(page);
  await page.goto('/feed');

  // Ровно одна плавающая кнопка — ассистент
  await expect(page.getByTestId('assistant-fab')).toHaveCount(1);
  // Инлайн-чата больше нет
  await expect(page.locator('#ai-chat-open')).toHaveCount(0);
  await expect(page.getByRole('button', { name: /Chat|Чат/ })).toHaveCount(0);

  // Клик открывает модалку с контентом
  await page.getByTestId('assistant-fab').click();
  const modal = page.getByTestId('assistant-modal');
  await expect(modal).toBeVisible();
  await expect(modal.getByRole('heading', { name: /Assistant|Ассистент/ })).toBeVisible();
  await expect(modal.getByText(/AI helper|AI-помощник/)).toBeVisible();
  // Дисклеймер — постоянная строка, без кнопки Continue
  await expect(modal.getByText(/does not replace professional help|не заменяет профессиональную помощь/)).toBeVisible();
  await expect(modal.getByRole('button', { name: /Continue|Продолжить/ })).toHaveCount(0);
  // Composer закреплён: поле + кнопка отправки
  await expect(modal.getByPlaceholder(/How are you feeling|Напиши, как ты/)).toBeVisible();

  // Закрытие по крестику
  await modal.getByRole('button', { name: /Close chat|Закрыть чат/ }).last().click();
  await expect(modal).not.toBeVisible();
});

test('chat: message appears instantly with typing indicator, then reply', async ({ page }) => {
  await login(page);
  await page.goto('/feed');

  await page.getByTestId('assistant-fab').click();
  const modal = page.getByTestId('assistant-modal');
  await expect(modal).toBeVisible();

  const input = modal.locator('#chat-input');
  await expect(input).toBeVisible();

  const assistantBubbles = modal.locator('#chat-response .chat-start');
  const before = await assistantBubbles.count();

  await input.fill('как дела?');
  // Задержка ответа: окно, в котором typing гарантированно виден
  // (canned-ответ сервера приходит за ~40ms)
  await page.route('**/ai/chat', async route => {
    await new Promise(r => setTimeout(r, 1500));
    await route.continue();
  });
  await input.press('Enter');

  // Оптимистичный бабл — мгновенно, текст безопасно вставлен
  await expect(modal.locator('#chat-response .chat-end').last()).toContainText('как дела?');
  // Индикатор «печатает» виден, инпут очищен
  await expect(modal.getByTestId('chat-typing')).toBeVisible();
  await expect(input).toHaveValue('');
  await page.unroute('**/ai/chat');

  // Серверный ответ (canned при GOODMOOD_FAKE_AI, реальный с ключом):
  // фрагмент свапается, bubble ассистента появляется, индикатор исчезает
  await expect(assistantBubbles).toHaveCount(before + 1, { timeout: 20000 });
  await expect(modal.getByTestId('chat-typing')).toHaveCount(0);
});

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

import { test, expect } from '@playwright/test';
import { ensureE2EUser, login } from '../helpers';

// PWA + push (change add-pwa-push): публичные ассеты installability,
// head-теги в layout и жизненный цикл подписки в /settings.
// Push API фейкается через addInitScript: реальный pushManager в headless
// Chromium полез бы во внешнюю сеть (FCM), тест должен быть детерминированным.

test.beforeAll(ensureE2EUser);

test.describe('pwa public assets', () => {
  test('manifest и sw.js отдаются 200 без сессии', async ({ request }) => {
    const manifest = await request.get('/manifest.webmanifest');
    expect(manifest.status()).toBe(200);
    const body = await manifest.json();
    expect(body.display).toBe('standalone');
    expect(body.start_url).toBe('/feed');
    expect(body.scope).toBe('/');
    expect(body.theme_color).toBe('#5b5bea');
    expect(body.icons.some((i: { purpose?: string }) => i.purpose === 'maskable')).toBe(true);

    const sw = await request.get('/sw.js');
    expect(sw.status()).toBe(200);
    // обработчики push и notificationclick обязаны быть в SW
    expect(await sw.text()).toContain('notificationclick');
  });

  test('head каждой страницы содержит manifest и theme-color', async ({ page }) => {
    // /login — неаутентифицированная страница: head общий для всех
    await page.goto('/login');
    await expect(page.locator('link[rel="manifest"]')).toHaveAttribute('href', '/manifest.webmanifest');
    await expect(page.locator('meta[name="theme-color"]')).toHaveAttribute('content', '#5b5bea');
  });
});

test.describe('push subscription in settings', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test.beforeEach(async ({ page }) => {
    // Фейк Push API: subscribe/getSubscription отдают фиксированную подписку
    // (endpoint не настоящий — сервер хранит её, но шедулер никогда не
    // отправит пуш на несуществующий push-сервис)
    await page.addInitScript(() => {
      const fakeSub = {
        endpoint: 'https://fcm.example.com/fake-e2e',
        toJSON: () => ({
          endpoint: 'https://fcm.example.com/fake-e2e',
          keys: { p256dh: 'e2e-fake-p256dh', auth: 'e2e-fake-auth' },
        }),
        unsubscribe: async () => true,
      };
      const fakeSW = {
        pushManager: {
          subscribe: async () => fakeSub,
          getSubscription: async () => fakeSub,
        },
        addEventListener: () => {},
      };
      Object.defineProperty(navigator, 'serviceWorker', {
        value: { ready: Promise.resolve(fakeSW), register: async () => fakeSW },
        configurable: true,
      });
      Object.defineProperty(Notification, 'requestPermission', {
        value: async () => 'granted',
        configurable: true,
      });
    });
    await login(page);
  });

  test('включение сохраняет подписку, выключение возвращает кнопку', async ({ page }) => {
    await page.goto('/settings');
    const section = page.locator('#push-section');
    await expect(section).toBeVisible();

    // Включить: requestPermission → subscribe → POST → свап секции
    await section.getByRole('button', { name: /Включить уведомления|Enable notifications/ }).click();
    await expect(section).toContainText(/Уведомления включены|Notifications are on/);
    await expect(section.getByRole('button', { name: /Выключить|Disable/ })).toBeVisible();

    // Подписка сохранена: перезагрузка страницы показывает то же состояние
    await page.reload();
    await expect(page.locator('#push-section')).toContainText(/Уведомления включены|Notifications are on/);

    // Выключить: unsubscribe → POST → свап секции
    const section2 = page.locator('#push-section');
    await section2.getByRole('button', { name: /Выключить|Disable/ }).click();
    await expect(section2.getByRole('button', { name: /Включить уведомления|Enable notifications/ })).toBeVisible();
  });
});
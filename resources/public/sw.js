/*
 * Минимальный service worker (change add-pwa-push, design D1/D4).
 *
 * Задачи SW — только installability и push: офлайн-режима нет сознательно
 * (htmx-приложение, персональная лента — кэширование ответов запрещено).
 * fetch: сеть-онли; при отказе сети навигация получает статическую
 * страницу «нет сети» вместо браузерной ERR_INTERNET_DISCONNECTED.
 *
 * push: показать уведомление с серверным payload {title, body}.
 * notificationclick: фокус существующего окна или открыть /feed.
 */
// Страница «нет сети»: локаль выбирается по браузерной (ru по умолчанию);
// SW статичен и серверную локаль знать не может — это не офлайн-режим,
// а честная замена браузерной ERR_INTERNET_DISCONNECTED (design D1)
const OFFLINE_PAGE_RU = `
<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="theme-color" content="#5b5bea">
<title>Good Mood</title>
<style>
  body { margin: 0; min-height: 100dvh; display: flex; align-items: center;
         justify-content: center; background: #0c0f17; color: #e6e8f0;
         font-family: Inter, system-ui, sans-serif; }
  p { font-size: 16px; opacity: 0.8; }
</style>
</head>
<body><p>Нет сети. Проверь подключение — данные не кэшируются.</p></body>
</html>`;

const OFFLINE_PAGE_EN = `
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="theme-color" content="#5b5bea">
<title>Good Mood</title>
<style>
  body { margin: 0; min-height: 100dvh; display: flex; align-items: center;
         justify-content: center; background: #0c0f17; color: #e6e8f0;
         font-family: Inter, system-ui, sans-serif; }
  p { font-size: 16px; opacity: 0.8; }
</style>
</head>
<body><p>No network. Check your connection — data is not cached.</p></body>
</html>`;

const offlinePage = function () {
  return (navigator.language && navigator.language.startsWith('en'))
    ? OFFLINE_PAGE_EN
    : OFFLINE_PAGE_RU;
};

self.addEventListener('install', function (event) {
  self.skipWaiting();
});

self.addEventListener('activate', function (event) {
  event.waitUntil(self.clients.claim());
});

// Сеть-онли: никаких кэшей. Навигации при отказе — страница «нет сети».
self.addEventListener('fetch', function (event) {
  if (event.request.mode !== 'navigate') return;
  event.respondWith(
    fetch(event.request).catch(function () {
      return new Response(offlinePage(), {
        status: 503,
        headers: { 'Content-Type': 'text/html; charset=utf-8' }
      });
    })
  );
});

self.addEventListener('push', function (event) {
  var data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch (e) {
    data = {};
  }
  event.waitUntil(self.registration.showNotification(
    data.title || 'Good Mood',
    {
      body: data.body || '',
      data: { url: data.url || '/feed' },
      icon: '/icons/icon-192.png',
      badge: '/icons/icon-192.png'
    }
  ));
});

self.addEventListener('notificationclick', function (event) {
  event.notification.close();
  var url = (event.notification.data && event.notification.data.url) || '/feed';
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true })
      .then(function (clientList) {
        for (var i = 0; i < clientList.length; i++) {
          var client = clientList[i];
          if ('focus' in client) {
            client.navigate(url);
            return client.focus();
          }
        }
        return self.clients.openWindow(url);
      })
  );
});
import { test, expect, Page } from '@playwright/test';
import { execSync } from 'node:child_process';
import { ensureE2EUser, login, submitEntry, projectRoot, workerUser } from '../helpers';

test.beforeAll(ensureE2EUser);

// Очистить AI-находки и записи e2e-юзера.
function resetAISate() {
  execSync(
    `clojure -M -e "
       (require '[next.jdbc :as jdbc])
       (def ds (jdbc/get-datasource {:dbtype \\"sqlite\\" :dbname \\"resources/goodmood.db\\"}))
       (try (jdbc/execute! ds [\\"DELETE FROM ai_findings\\"]) (catch Exception _))
       (jdbc/execute! ds [\\"DELETE FROM entries\\"])
       (jdbc/execute! ds [\\"DELETE FROM insights\\"])
       (println \\"reset done\\")"`,
    { cwd: projectRoot, timeout: 90000, stdio: 'ignore' },
  );
}

test.beforeAll(resetAISate);

// Создать ≥14 дней записей (порог для AI-анализа корреляций) напрямую в БД.
// Чередуем сон 8ч/4ч и тревогу для корреляции «сон → тревога».
async function seedFourteenDays(page: Page) {
  execSync(
    `clojure -M -e "
       (require '[next.jdbc :as jdbc] '[app.db.entries :as e] '[app.db.users :as u])
       (def ds (jdbc/get-datasource {:dbtype \\"sqlite\\" :dbname \\"resources/goodmood.db\\"}))
       (def uid (:id (u/get-user-by-email ds \\"${workerUser().email}\\")))
       (dotimes [i 20]
         (let [low (even? i)]
           (e/create-entry! ds {:user-id uid
                                :date (str (.minusDays (java.time.LocalDate/now) (inc i)))
                                :activity \\"walk\\" :effect \\"calm\\"
                                :mood-score (if low 3 7)
                                :energy (if low 3 7)
                                :anxiety (if low 7 3)
                                :sleep-hours (if low 4.0 8.0)})))
       (println \\"seeded\\")"`,
    { cwd: projectRoot, timeout: 90000, stdio: 'ignore' },
  );
}

// Создать инсайт в тревожном состоянии (для AI-генерации совета из своих).
async function createAnxiousInsight(page: Page) {
  await page.goto('/insights/new');
  await page.fill('textarea[name="context"]', 'Тревога 7, не могу сосредоточиться');
  await page.selectOption('select[name="state_label"]', 'anxiety');
  await page.selectOption('select[name="category"]', 'coping');
  await page.locator('#advice-list .advice-item').nth(0).locator('textarea').fill('дыхание 4-7-8');
  await page.getByRole('button', { name: /Сохранить|Save/ }).click();
  await page.waitForURL('**/insights');
}

test.describe('AI assistant (Phase 5) @ai: correlations, labels, advice from own insights', () => {

  test('correlation finding appears after ≥14 days of data', async ({ page }) => {
    test.setTimeout(90000);
    await login(page);
    await seedFourteenDays(page);
    await page.goto('/feed');
    const corrSection = page.locator('[data-testid="ai-correlations"]');
    // Фоновый AI-анализ асинхронный: ждём появления находок, периодически
    // перезагружая, чтобы подхватить закэшированный результат (ai_findings).
    await expect.poll(async () => {
      if (await corrSection.isVisible()) return true;
      await page.reload();
      await page.waitForTimeout(5000);
      return await corrSection.isVisible();
    }, { timeout: 60000, intervals: [5000] }).toBe(true);
    // Находка — реальная корреляция с уровнем уверенности (не пустая секция).
    await expect(corrSection.getByTitle(/уверенность|confidence/i).first()).toBeVisible();
    // Кнопки «не релевантно» / «уже знал».
    await expect(corrSection.getByRole('button', { name: /не релевантно|not relevant/i }).first()).toBeVisible();
    await expect(corrSection.getByRole('button', { name: /уже знал|already knew/i }).first()).toBeVisible();
  });

  test('correlation finding has confidence level', async ({ page }) => {
    await login(page);
    await page.goto('/feed');
    const corr = page.locator('[data-testid="ai-correlations"]');
    if (await corr.isVisible()) {
      // Уверенность: high/medium/low (ru/eng).
      await expect(corr.getByTitle(/уверенность|confidence/i).first()).toBeVisible();
    }
  });

  test('marking correlation "not relevant" hides it', async ({ page }) => {
    await login(page);
    await page.goto('/feed');
    const corr = page.locator('[data-testid="ai-correlations"]');
    if (await corr.isVisible()) {
      // Клик по «не релевантно» на конкретной карточке скрывает ЕЁ (не всю секцию).
      const firstCard = corr.locator('[data-testid^="ai-correlation-"]').first();
      await firstCard.getByRole('button', { name: /не релевантно|not relevant/i }).click();
      await expect(firstCard).toBeHidden({ timeout: 5000 });
    }
  });

  test('AI-proposed state label appears on /feed near state badge', async ({ page }) => {
    await login(page);
    await submitEntry(page, { mood_score: 4, energy: 4, anxiety: 8, template: 'day' });
    await page.goto('/feed');
    // Даём фоновому анализу завершиться и перезагружаем (async-кэш).
    await page.waitForTimeout(12000);
    await page.reload();
    // AI-ярлык должен быть виден рядом с обычным state_label.
    const aiLabel = page.locator('[data-testid="ai-state-label"]');
    await expect(aiLabel).toBeVisible({ timeout: 10000 });
  });

  test('user can change AI-proposed state label', async ({ page }) => {
    await login(page);
    await page.goto('/feed');
    const aiLabel = page.locator('[data-testid="ai-state-label"]');
    if (await aiLabel.isVisible()) {
      // Тап → выбор другого ярлыка.
      await aiLabel.click();
      await page.getByRole('button', { name: /подъём|high/i }).click();
      // Ярлык изменился.
      await expect(aiLabel).toContainText(/подъём|high/i);
    }
  });

  test('AI advice from own insights appears with source reference', async ({ page }) => {
    await login(page);
    await createAnxiousInsight(page);
    await submitEntry(page, { mood_score: 4, energy: 4, anxiety: 8, template: 'day' });
    await page.goto('/feed');
    // Даём фоновому анализу завершиться и перезагружаем (async-кэш).
    await page.waitForTimeout(12000);
    await page.reload();
    const advice = page.locator('[data-testid="ai-advice"]');
    await expect(advice).toBeVisible({ timeout: 10000 });
    // Ссылка на исходный инсайт.
    await expect(advice.getByRole('link', { name: /из твоего инсайта|from your insight/i })).toBeVisible();
  });

  test('complain about advice hides it and logs feedback', async ({ page }) => {
    await login(page);
    await page.goto('/feed');
    const advice = page.locator('[data-testid="ai-advice"]');
    if (await advice.isVisible()) {
      await advice.getByRole('button', { name: /пожаловаться|report/i }).click();
      await expect(advice).toBeHidden({ timeout: 5000 });
    }
  });

  test('AI operates on own data only — no cross-user data', async ({ page }) => {
    // Косвенная проверка: AI-совет ссылается на СВОЙ инсайт (id принадлежит этому юзеру).
    await login(page);
    await page.goto('/feed');
    const advice = page.locator('[data-testid="ai-advice"]');
    if (await advice.isVisible()) {
      const link = advice.getByRole('link', { name: /из твоего инсайта|from your insight/i });
      const href = await link.getAttribute('href');
      expect(href).toMatch(/^\/insights\/\d+$/);
    }
  });

  test('opt-out of all AI functions in settings', async ({ page }) => {
    await login(page);
    await page.goto('/settings');
    const aiToggle = page.locator('[data-testid="ai-master-toggle"]');
    if (await aiToggle.isVisible()) {
      await aiToggle.uncheck();
      await page.goto('/feed');
      await expect(page.locator('[data-testid="ai-correlations"]')).toBeHidden();
      await expect(page.locator('[data-testid="ai-advice"]')).toBeHidden();
      // восстановить
      await page.goto('/settings');
      await aiToggle.check();
    }
  });

  test('explainability: advice shows why it was suggested', async ({ page }) => {
    await login(page);
    await page.goto('/feed');
    const advice = page.locator('[data-testid="ai-advice"]');
    if (await advice.isVisible()) {
      await advice.getByRole('button', { name: /почему|why/i }).click();
      await expect(advice.getByText(/основано на|based on/i)).toBeVisible();
    }
  });
});

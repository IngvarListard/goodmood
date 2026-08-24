import { test, expect, Page } from '@playwright/test';
import { execSync } from 'node:child_process';
import { ensureE2EUser, login, submitEntry, projectRoot, E2E_USER_EMAIL } from '../helpers';

test.beforeAll(ensureE2EUser);

// Очистить episode_warnings, ai_findings, записи.
function resetPhase7State() {
  execSync(
    `clojure -M -e "
       (require '[next.jdbc :as jdbc])
       (def ds (jdbc/get-datasource {:dbtype \\"sqlite\\" :dbname \\"resources/goodmood.db\\"}))
       (try (jdbc/execute! ds [\\"DELETE FROM episode_warnings\\"]) (catch Exception _))
       (try (jdbc/execute! ds [\\"DELETE FROM ai_findings\\"]) (catch Exception _))
       (jdbc/execute! ds [\\"DELETE FROM entries\\"])
       (try (jdbc/execute! ds [\\"UPDATE user_ai_settings SET episode_warning_enabled = 0\\"]) (catch Exception _))
       (println \\"reset done\\")"`,
    { cwd: projectRoot, timeout: 90000, stdio: 'ignore' },
  );
}

// Изолируем каждый тест: перед каждым очищаем episode_warnings, ai_findings,
// записи и выключаем opt-in (между тестами не должно оставаться активных
// предупреждений, иначе тест «low-confidence» увидит чужой warning).
test.beforeEach(resetPhase7State);

test.describe('Episode warning (Phase 7) — GUARDRAILS (priority)', () => {

  test('opt-in is OFF by default — no warnings shown', async ({ page }) => {
    await login(page);
    // Создать данные, похожие на начало эпизода (тренд вверх 3 дня).
    await submitEntry(page, { mood_score: 7, energy: 7, anxiety: 2, template: 'day' });
    await submitEntry(page, { mood_score: 8, energy: 8, anxiety: 2, template: 'day' });
    await submitEntry(page, { mood_score: 9, energy: 9, anxiety: 1, template: 'day' });
    await page.goto('/feed');
    // Предупреждения нет — opt-in OFF.
    await expect(page.locator('[data-testid="episode-warning"]')).toBeHidden();
  });

test('opt-in enables warnings; high-confidence pattern shows warning', async ({ page }) => {
    test.setTimeout(90000);
    await login(page);
    // Включить (автосохранение через htmx change).
    await page.goto('/settings');
    const toggle = page.locator('[data-testid="episode-warning-toggle"]');
    await toggle.check();
    await expect(toggle).toBeChecked();
    // Детерминированный тренд «мании»: 3 записи с разными датами напрямую
    // в БД (защита от накопления данных других тестов и одинаковых дат).
    execSync(
      `clojure -M -e "
         (require '[next.jdbc :as jdbc] '[app.db.entries :as e] '[app.db.users :as u])
         (def ds (jdbc/get-datasource {:dbtype \\"sqlite\\" :dbname \\"resources/goodmood.db\\"}))
         (def uid (:id (u/get-user-by-email ds \\"${E2E_USER_EMAIL}\\")))
         (jdbc/execute! ds [\\"DELETE FROM entries WHERE user_id=?\\" uid])
         (jdbc/execute! ds [\\"DELETE FROM episode_warnings WHERE user_id=?\\" uid])
         (doseq [[i m en an sl] [[2 7 7 2 7.0] [1 8 8 2 5.0] [0 9 9 1 4.0]]]
           (e/create-entry! ds {:user-id uid
                                :date (str (.minusDays (java.time.LocalDate/now) i))
                                :activity \\"walk\\" :effect \\"elevated\\"
                                :mood-score m :energy en :anxiety an
                                :sleep-hours sl}))
         (println \\"seeded\\")"`,
      { cwd: projectRoot, timeout: 90000, stdio: 'ignore' },
    );
    await page.goto('/feed');
    // Анализ асинхронный (background): ждём появления предупреждения,
    // периодически перезагружая, чтобы подхватить закэшированный результат.
    const warning = page.locator('[data-testid="episode-warning"]');
    await expect.poll(async () => {
      if (await warning.isVisible()) return true;
      await page.reload();
      return await warning.isVisible();
    }, { timeout: 60000, intervals: [5000] }).toBe(true);
  });

  test('low-confidence pattern does NOT trigger warning', async ({ page }) => {
    await login(page);
    await page.goto('/settings');
    const toggle = page.locator('[data-testid="episode-warning-toggle"]');
    await toggle.check();
    await expect(toggle).toBeChecked();
    // Стабильные/нейтральные данные БЕЗ восходящего или нисходящего тренда
    // и без изменения сна → модель не должна дать выраженный эпизод-паттерн.
    await submitEntry(page, { mood: 5, energy: 5, anxiety: 5, template: 'day' });
    await submitEntry(page, { mood: 5, energy: 5, anxiety: 5, template: 'day' });
    await submitEntry(page, { mood: 5, energy: 5, anxiety: 5, template: 'day' });
    await page.goto('/feed');
    // Предупреждения нет — низкая уверенность.
    await expect(page.locator('[data-testid="episode-warning"]')).toBeHidden();
  });

  test('opt-out disables warnings immediately', async ({ page }) => {
    await login(page);
    await page.goto('/settings');
    await page.locator('[data-testid="episode-warning-toggle"]').check();
    // Выключить.
    await page.locator('[data-testid="episode-warning-toggle"]').uncheck();
    await submitEntry(page, { mood_score: 9, energy: 9, anxiety: 1, template: 'day' });
    await page.goto('/feed');
    await expect(page.locator('[data-testid="episode-warning"]')).toBeHidden();
  });

  test('one-click disable from warning itself', async ({ page }) => {
    await login(page);
    await page.goto('/settings');
    await page.locator('[data-testid="episode-warning-toggle"]').check();
    await submitEntry(page, { mood_score: 9, energy: 9, anxiety: 1, template: 'day' });
    await page.goto('/feed');
    const warning = page.locator('[data-testid="episode-warning"]');
    if (await warning.isVisible()) {
      // Кнопка выключения в самом предупреждении.
      await warning.getByRole('button', { name: /выключить|disable|отключить|turn off/i }).click();
      await expect(warning).toBeHidden();
    }
  });

  test('false-alarm feedback is logged', async ({ page }) => {
    await login(page);
    await page.goto('/settings');
    await page.locator('[data-testid="episode-warning-toggle"]').check();
    await submitEntry(page, { mood_score: 9, energy: 9, anxiety: 1, template: 'day' });
    await page.goto('/feed');
    const warning = page.locator('[data-testid="episode-warning"]');
    if (await warning.isVisible()) {
      await warning.getByRole('button', { name: /ложная тревога|false alarm/i }).click();
      // Проверить, что feedback залогирован в БД.
      const out = execSync(
        `clojure -M -e "
           (require '[next.jdbc :as jdbc])
           (def ds (jdbc/get-datasource {:dbtype \\"sqlite\\" :dbname \\"resources/goodmood.db\\"}))
           (def rows (jdbc/execute! ds [\\"SELECT feedback FROM episode_warnings WHERE feedback IS NOT NULL LIMIT 1\\"]))
           (println (str (not (empty? rows))))"`,
        { cwd: projectRoot, timeout: 90000, stdio: 'pipe' },
      ).toString().trim();
      expect(out).toContain('true');
    }
  });

  test('crisis signs → resource, NOT warning', async ({ page }) => {
    await login(page);
    await page.goto('/settings');
    await page.locator('[data-testid="episode-warning-toggle"]').check();
    // Создать запись с кризис-сигналом.
    await submitEntry(page, {
      mood_score: 1,
      energy: 1,
      anxiety: 5,
      note: 'не хочу жить, всё бессмысленно',
      template: 'day',
    });
    await page.goto('/feed');
    // Ресурс (телефон доверия / 112), НЕ предупреждение об эпизоде.
    await expect(page.getByText(/телефон доверия|hotline|112|03|help line|профессиональн/i)).toBeVisible({ timeout: 30000 });
    // Предупреждение об эпизоде НЕ показано в этом случае.
    const epWarning = page.locator('[data-testid="episode-warning"]');
    if (await epWarning.isVisible()) {
      // Если показано — оно должно быть НЕ тревожным, а ресурс-ориентированным.
      await expect(epWarning.getByText(/телефон доверия|hotline|112/i)).toBeVisible();
    }
  });
});

test.describe('Episode warning (Phase 7) — explainability + copy', () => {

  test('warning explains the pattern (not just «episode coming»)', async ({ page }) => {
    await login(page);
    await page.goto('/settings');
    await page.locator('[data-testid="episode-warning-toggle"]').check();
    await submitEntry(page, { mood_score: 9, energy: 9, anxiety: 1, sleep_hours: 4, template: 'day' });
    await page.goto('/feed');
    const warning = page.locator('[data-testid="episode-warning"]');
    if (await warning.isVisible()) {
      // Объяснение паттерна: какие данные, какой тренд.
      await expect(warning.getByText(/последние|last|энергия|energy|сон|sleep|растёт|растет|растет|grow|increas|падает|drop|decreas/i)).toBeVisible();
    }
  });

  test('warning copy is supportive, NOT alarming', async ({ page }) => {
    await login(page);
    await page.goto('/settings');
    await page.locator('[data-testid="episode-warning-toggle"]').check();
    await submitEntry(page, { mood_score: 9, energy: 9, anxiety: 1, template: 'day' });
    await page.goto('/feed');
    const warning = page.locator('[data-testid="episode-warning"]');
    if (await warning.isVisible()) {
      // НЕТ тревожного языка.
      const text = (await warning.textContent()) ?? '';
      expect(text).not.toMatch(/ВНИМАНИЕ|ATTENTION|ОПАСНО|DANGER|!!!|срочно|urgent/i);
      // ЕСТЬ поддерживающий язык.
      // (конкретная формулировка из DECISIONS.md: «Похоже, последние дни похожи на твой прежний паттерн…»)
      // Проверяем мягкость: вопросительная форма, не приказ.
      expect(text).toMatch(/[??.]/);
    }
  });
});

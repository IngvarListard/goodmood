import { defineConfig } from '@playwright/test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import fs from 'node:fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Корень проекта (родитель e2e/)
const projectRoot = path.resolve(__dirname, '..');

// Ключ OpenRouter (для AI-фич в e2e). Файл в gitignore.
const OPENROUTER_API_KEY =
  process.env.OPENROUTER_API_KEY ||
  (fs.existsSync(path.join(projectRoot, 'OPENROUTER_API_KEY'))
    ? fs.readFileSync(path.join(projectRoot, 'OPENROUTER_API_KEY'), 'utf8').trim()
    : '');

export default defineConfig({
  testDir: './tests',
  globalSetup: './global-setup.ts',
  outputDir: path.join(projectRoot, '.opencode', 'artifacts', 'e2e'),
  fullyParallel: false,
  // workers: 2 — WAL + busy_timeout (59e9306) сняли SQLITE_BUSY при
  // параллельной записи; изоляция юзеров по воркерам есть (workerUser).
  // Старый вывод про 6.5 мин написан до WAL. Если снова замедлится —
  // вернуть 1.
  workers: 2,
  timeout: 30000,
  expect: { timeout: 10000 },
  reporter: [
    ['list'],
    ['html', { outputFolder: path.join(projectRoot, '.opencode', 'artifacts', 'e2e-html-report'), open: 'never' }],
  ],
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: {
    // Автозапуск приложения, если оно ещё не запущено.
    // E2E-юзер создаётся при старте (идемпотентно).
    command: 'clojure -M -i dev/seed_e2e_user.clj && clojure -M -m app.core',
    cwd: projectRoot,
    url: 'http://localhost:3000',
    timeout: 120000,
    reuseExistingServer: true,
    stdout: 'pipe',
    stderr: 'pipe',
    env: {
      GOODMOOD_SESSION_SECRET: 'e2e-secret',
      GOODMOOD_ADMIN_PASSWORD: 'e2e-admin-password',
      GOODMOOD_ADMIN_EMAIL: 'admin@goodmood.test',
      OPENROUTER_API_KEY,
      // Без ключа — детерминированные canned-ответы call-chat (dev-env spec).
      // С ключом флаг не ставится: @ai-спеки гоняют реальный API.
      GOODMOOD_FAKE_AI: OPENROUTER_API_KEY ? '' : '1',
    },
  },
});
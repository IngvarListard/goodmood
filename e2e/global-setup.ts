import { ensureE2EUser } from './helpers';

// Один сид на весь прогон (вместо beforeAll в каждом из ~13 spec-файлов:
// каждый вызов = холодный JVM 10-20 c). webServer конфига уже дёргает
// seed_e2e_user.clj при автостарте; здесь — на случай reuseExistingServer.
export default function globalSetup() {
  ensureE2EUser();
}

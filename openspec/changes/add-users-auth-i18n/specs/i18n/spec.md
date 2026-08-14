# Internationalization Specification

## Purpose
Provide infrastructure for multi-language UI rendering (Russian and English),
dynamically selecting locale and translating UI strings via keyed dictionaries.

## Requirements

### Requirement: Locale detection
The system SHALL determine the current locale using the following priority:
1. User-selected locale from cookie `gm-locale`
2. Browser `Accept-Language` header (first matching supported locale)
3. Default locale `:ru`

#### Scenario: Cookie locale takes priority
- **WHEN** a request has cookie `gm-locale=en`
- **THEN** the locale for this request is `:en`

#### Scenario: Accept-Language fallback
- **WHEN** a request has no `gm-locale` cookie
- **AND** Accept-Language header is `en-US,en;q=0.9`
- **THEN** the locale for this request is `:en`

#### Scenario: Default locale fallback
- **WHEN** a request has no `gm-locale` cookie
- **AND** Accept-Language header is absent or `de-DE`
- **THEN** the locale for this request is `:ru` (default)

### Requirement: Translation function
The system SHALL provide a translation function `(t key & args)` that retrieves
the localized string for the current request's locale. The function SHALL support:
- Nested keys: `(t :nav/dashboard)`
- Placeholders: `(t :greeting {:name "Ivan"})`
- Plural forms via inflection: `(t :entries-count {:count 5})`

#### Scenario: Simple key translation
- **WHEN** locale is `:ru` and `(t :auth/login)` is called
- **THEN** the result is `"Войти"`

#### Scenario: Same key in English
- **WHEN** locale is `:en` and `(t :auth/login)` is called
- **THEN** the result is `"Log in"`

#### Scenario: Translation with placeholders
- **WHEN** locale is `:ru` and `(t :greeting {:name "Иван"})` is called
- **THEN** placeholders in the template are replaced with values

#### Scenario: Russian plural forms
- **WHEN** locale is `:ru` and `(t :entries {:n 1})` is called
- **THEN** returns `"1 запись"`
- **WHEN** locale is `:ru` and `(t :entries {:n 2})` is called
- **THEN** returns `"2 записи"`
- **WHEN** locale is `:ru` and `(t :entries {:n 5})` is called
- **THEN** returns `"5 записей"`

### Requirement: HTML lang attribute
The system SHALL set the `<html lang>` attribute dynamically based on the
current locale.

#### Scenario: lang attribute for Russian
- **WHEN** locale is `:ru`
- **THEN** the rendered HTML has `lang="ru"` on the `<html>` element

#### Scenario: lang attribute for English
- **WHEN** locale is `:en`
- **THEN** the rendered HTML has `lang="en"` on the `<html>` element

### Requirement: Dictionary files
The system SHALL store translations in EDN dictionary files
`resources/i18n/ru.edn` and `resources/i18n/en.edn`.

#### Scenario: Russian dictionary loaded
- **WHEN** the application starts
- **THEN** the Russian dictionary is loaded from `resources/i18n/ru.edn`
- **AND** contains keys for navigation, auth, and UI strings

#### Scenario: English dictionary loaded
- **WHEN** the application starts
- **THEN** the English dictionary is loaded from `resources/i18n/en.edn`
- **AND** contains keys for navigation, auth, and UI strings

### Requirement: Existing UI strings translated
The system SHALL replace hardcoded Russian strings in existing views (navigation,
placeholder pages) with i18n keys.

#### Scenario: Navigation labels use i18n
- **WHEN** the navigation is rendered
- **THEN** all nav item labels (Дашборд, Чек-ин, ...) are retrieved via `(t :nav/<key>)`
- **AND** reflect the current locale

#### Scenario: Placeholder text uses i18n
- **WHEN** a placeholder page (e.g., /dashboard) is rendered
- **THEN** the title is retrieved via `(t :pages/dashboard)`
- **AND** the "Раздел в разработке" text is retrieved via i18n key

### Requirement: Language switch availability
The system SHALL provide language switching (ru ↔ en) in the user interface.
Setting the language SHALL set a `gm-locale` cookie.

#### Scenario: Language switch sets cookie
- **WHEN** a user selects English
- **THEN** the cookie `gm-locale` is set to `en`
- **AND** subsequent requests use English translations

#### Scenario: Language switch on login page
- **WHEN** the login page is rendered
- **THEN** a language switcher is available
- **AND** switching language reloads the page with the new locale
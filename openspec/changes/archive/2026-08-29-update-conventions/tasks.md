## 1. Update convention files

- [x] 1.1 Add hiccup import rule and docstring rule to `AGENTS.md`
- [x] 1.2 Add explicit hiccup import pattern to `CLOJURE-RULES.md`

## 2. Add docstrings to non-trivial public functions

- [x] 2.1 `src/app/routes/auth.clj`: add docstring to `login-post-handler`
- [x] 2.2 `src/app/routes/app.clj`: add docstring to `session-config`, `->app`, `health-check`
- [x] 2.3 `src/app/routes/entries.clj`: add docstring to `htmx-request?`, `create-entry`, `get-entries`
- [x] 2.4 Check other public functions across the codebase for missing docstrings
  - Added docstrings to: `views/entries.clj` (form, item, entries-list, page), `routes/auth.clj` (login-page-handler)
  - Skipped db/domain layer: thin wrappers, self-documenting names
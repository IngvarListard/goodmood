## 1. Refactor route files

- [x] 1.1 Replace `hiccup.core` in `src/app/routes/auth.clj` — import `hiccup2.core :refer [html]`, replace `hc/html` calls
- [x] 1.2 Replace `hiccup.core` in `src/app/routes/app.clj` — import `hiccup2.core :refer [html]`, replace `hc/html` calls
- [x] 1.3 Replace `hiccup.core` in `src/app/routes/entries.clj` — import `hiccup2.core :refer [html]`, replace `hc/html` calls

## 2. Align icons.clj

- [x] 2.1 Replace `hiccup2.core :as h2` with `hiccup2.core :refer [html raw]` and update `h2/raw` → `raw`

## 3. Verify

- [x] 3.1 Check that the application compiles and starts
- [x] 3.2 Check that pages render correctly (no broken HTML)
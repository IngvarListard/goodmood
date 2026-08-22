## REMOVED Requirements

### Requirement: Submitting the form swaps result into feed
**Reason:** The form moved to a separate `/check-in` page; after successful submission the client-side hyperscript redirects to `/feed` instead of swapping the new entry inline into `#entries-list`.
**Migration:** POST `/entries` still returns an HTML fragment for htmx, but the form's `_ on htmx:afterRequest` handler sets `window.location to '/feed'` on success.
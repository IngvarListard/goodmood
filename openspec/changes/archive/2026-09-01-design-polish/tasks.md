## 1. Custom Theme (layout.clj)

- [x] 1.1 Add `<style>` block with `@theme` CSS variables to `layout.clj:head`
- [x] 1.2 Define `--color-base-content: #e8e5df`
- [x] 1.3 Define `--color-base-content-60: #b8b5af`
- [x] 1.4 Define `--color-base-content-70: #ccc9c3`

## 2. Feed Page (feed.clj)

- [x] 2.1 Replace `opacity-50` with `text-base-content/60` on subheadings
- [x] 2.2 Replace `opacity-70` with `text-base-content/70` on timestamps
- [x] 2.3 Replace `opacity-85` with `text-base-content/85` on body text
- [x] 2.4 Make empty state CTA prominent (centered, `btn-lg`)
- [x] 2.5 Conditionally render FAB only when entries exist

## 3. Bottom Nav (navigation.clj)

- [x] 3.1 Add `bg-primary/10` to active tab classes (mobile)
- [x] 3.2 Add dot indicator element below active tab label
- [x] 3.3 Ensure all nav items have `min-h-[44px]`

## 4. Check-in Form (check_in.clj)

- [x] 4.1 Change mood slider from `range-primary` to `range-primary` (no change, verify)
- [x] 4.2 Change energy slider from `range-success` to `range-primary`
- [x] 4.3 Change anxiety slider from `range-warning` to `range-primary`
- [x] 4.4 Change focus slider from `range-info` to `range-primary`
- [x] 4.5 Replace `opacity-40` with `text-base-content/60` on value labels

## 5. Medications (medications.clj)

- [x] 5.1 Add `min-h-[44px]` to all action buttons (Edit, Deactivate, Cancel)
- [x] 5.2 Fix taken badge: `badge badge-success badge-sm`
- [x] 5.3 Fix skipped badge: `badge badge-warning badge-outline badge-sm`
- [x] 5.4 Fix sensitive badge: `badge badge-ghost badge-sm`
- [x] 5.5 Replace `opacity-60` with `text-base-content/70` on secondary text
- [x] 5.6 Replace `opacity-50` with `text-base-content/60` on hints

## 6. Insights (insights.clj)

- [x] 6.1 Add `min-h-[44px]` to Edit and Delete buttons
- [x] 6.2 Replace `opacity-50` with `text-base-content/60` on timestamps
- [x] 6.3 Replace `opacity-70` with `text-base-content/70` on secondary text
- [x] 6.4 Replace `opacity-60` with `text-base-content/60` on hints

## 7. Settings (settings.clj, notifications.clj, ai.clj)

- [x] 7.1 Remove `divider my-1` elements in `ai.clj` toggle list
- [x] 7.2 Add `divide-y divide-base-300` to toggle container
- [x] 7.3 Remove `divider` in `notifications.clj` slot list
- [x] 7.4 Add `divide-y divide-base-300` to slot container
- [x] 7.5 Replace remaining `opacity-50/60` with `text-base-content/N` in all settings views

## 8. Verification

- [x] 8.1 Run application and verify all pages render correctly
- [x] 8.2 Check contrast on feed page (empty state + with entries)
- [x] 8.3 Check tap targets on medications page
- [x] 8.4 Check slider colors on check-in page
- [x] 8.5 Check active tab indicator on all pages

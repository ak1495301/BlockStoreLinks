# Architecture

## Activities
- `LinkInterceptorActivity`: entry point for `http`/`https` intents. Records attempts, enforces block/redirect rules, and routes to the correct destination.
- `MainActivity`: policy management UI with per-app block toggles and redirect selection.
- `BrowserActivity`: simple in-app WebView.
- `BlockedActivity`: blocking notice with a manual option to open the link in another browser.

## Data Model
- `AppPolicy`: per-app rule and attempt counter.
- `AppPolicyStore`: stores policies in `SharedPreferences` as JSON and exposes CRUD operations.

## Intent Routing
1. Interceptor receives a link.
2. Source app is derived from referrer fields when available.
3. Policy lookup determines:
   - Allowlist check: if configured, only matching URLs proceed.
   - Redirect to a chosen browser (if set).
   - Block and show a notice.
   - Open in the in-app WebView.

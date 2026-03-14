# Architecture

## Activities
- `LinkInterceptorActivity`: entry point for `http`/`https` intents. Records attempts, enforces allow/ask/deny, applies allowlist logic, and routes to a redirect target or the in-app browser.
- `MainActivity`: policy management UI with per-app action mode, redirects, allowlist editing, and history browsing.
- `BrowserActivity`: simple in-app WebView.
- `BlockedActivity`: blocking notice with a manual option to open the link in another browser.

## Data Model
- `AppPolicy`: per-app rules (action, redirect, allowlist) and attempt counter.
- `LinkAttempt`: timestamped link history entry with outcome.
- `AppPolicyStore`: stores policies, global redirect, and per-app link history in `SharedPreferences` as JSON.

## Intent Routing
1. Interceptor receives a link and derives the source app from referrer fields when available.
2. Allowlist check runs first; if it fails, the link is blocked.
3. Action mode applies next:
   - `Deny`: show the blocked screen.
   - `Ask`: prompt the user to allow or deny.
   - `Allow`: continue.
4. Redirect resolution prefers allowlist overrides, then per-app redirect, then global redirect.
5. The link is forwarded to the chosen browser or opened in the in-app WebView.

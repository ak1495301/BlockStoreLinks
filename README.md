# Block Store Links

A lightweight Android browser that can intercept links from other apps, track attempts per app, and block or redirect those attempts to another browser.

## Features
- Registers as a web browser for `http`/`https` intents.
- Logs how many times each app tried to open a link.
- Per-app action mode: allow, ask, or deny.
- Optional per-app redirect to a different browser.
- Optional global redirect for all apps without a per-app override.
- Built-in WebView browser for allowed links.
- Per-app link history with timestamped entries and optional source hints.
- Filter and sort the app list by attempts or name.
- App list includes installed app icons when available.
- Optional per-app allowlist patterns to restrict which URLs can open, including regex with a `re:` prefix.
- Allowlist entries can optionally redirect a matching link via `pattern => com.example.browser`.
- Manual label overrides for app entries (including unknown sources).
- Add app rules proactively by selecting an installed app or entering a package name.
- Export or import the full policy list as JSON, including global redirect, link history, and label overrides.
- Import preview shows how many apps are in the pasted JSON.
- Copy policy JSON to the clipboard.
- Reset attempt counts per app or for all apps.

## How It Works
- `LinkInterceptorActivity` receives browser intents and attributes the source app via the referrer when available (with best-effort parsing when only a referrer string is provided).
- Attempts and per-app link history are stored in `SharedPreferences` as JSON.
- The app stores a short hint from the intent (action, categories, referrer fields, browser app id, package, and extra keys).
- Policies determine whether to allow, ask, deny, redirect, or open inside the built-in WebView.
- Global redirect applies when a per-app redirect is not set.
- If an allowlist exists for an app, only URLs containing those patterns are allowed. Prefix a pattern with `re:` to treat it as a regular expression. Use `pattern => package` to redirect a matching link.

## Setup
- Open the project in Android Studio.
- Ensure Android Gradle Plugin 8.2+ and JDK 17 are installed.
- Sync and run on a device.
- Optional: run `./gradlew tasks` to validate the wrapper.

## Usage
1. Install the app.
2. Set it as the default browser when Android prompts.
3. Open the app to see the list of attempts.
4. Set each app to `Allow`, `Ask`, or `Deny`.
5. When asked, choose allow/deny once or set an always allow/deny rule.
6. Use `Redirect to` to forward links to a different browser.
7. Use `Global redirect` to set a default browser when no per-app redirect is set.
8. Tap an app row to see its link history by timestamp and optionally set a custom label.
9. Use `Export policies` to share settings or `Import policies` to replace or merge them from JSON (preview shows app count).
10. Use `Reset count` per app or `Reset all counts` to clear attempt tracking.
11. Use `Copy policies` to put the JSON on the clipboard.

## Notes
- Source app attribution depends on the caller providing referrer data or a browser app id/package extra; when it's missing, the app is shown as unknown.
- Link history is capped at 200 entries per app to keep storage bounded.
- Package visibility restrictions are handled via `<queries>` in the manifest.
- Minimum SDK is set to 19 for broad compatibility.
- Import merge behavior: attempt counts are added together, and imported action/redirect/allowlist settings override existing ones for that app. Imported link history is appended and capped.
- Regex allowlist safety limits: max 20 regex patterns per app, max 200 characters per regex.
- The allowlist editor validates `re:` patterns and will refuse invalid or too-long regex entries.
- The external browser chooser filters out Block Store Links itself; if no other browsers are installed, it falls back to the built-in WebView.
- When package visibility blocks resolving a label, the package name is shown instead of an unknown label.
- Label overrides are included in export/import.

## Project Structure
- `app/src/main/java/com/duda/blockstorelinks/LinkInterceptorActivity.kt`
- `app/src/main/java/com/duda/blockstorelinks/MainActivity.kt`
- `app/src/main/java/com/duda/blockstorelinks/AppPolicyStore.kt`
- `app/src/main/java/com/duda/blockstorelinks/BrowserActivity.kt`

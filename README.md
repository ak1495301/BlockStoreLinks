# Block Store Links

A lightweight Android browser that can intercept links from other apps, track attempts per app, and block or redirect those attempts to another browser.

## Features
- Registers as a web browser for `http`/`https` intents.
- Logs how many times each app tried to open a link.
- Toggle per-app blocking.
- Optional per-app redirect to a different browser.
- Built-in WebView browser for allowed links.
- Filter and sort the app list by attempts or name.

## How It Works
- `LinkInterceptorActivity` receives browser intents and attributes the source app via the referrer when available.
- Attempts are stored in `SharedPreferences` as JSON.
- Policies determine whether to block, redirect, or open inside the built-in WebView.

## Setup
- Open the project in Android Studio.
- Ensure Android Gradle Plugin 8.2+ and JDK 17 are installed.
- Sync and run on a device.
- Optional: run `./gradlew tasks` to validate the wrapper.

## Usage
1. Install the app.
2. Set it as the default browser when Android prompts.
3. Open the app to see the list of attempts.
4. Toggle `Blocked` for apps you want to prevent from opening links here.
5. Use `Redirect to` to forward links to a different browser.

## Notes
- Source app attribution depends on the referrer being provided by the caller.
- Package visibility restrictions are handled via `<queries>` in the manifest.
- Minimum SDK is set to 19 for broad compatibility.

## Project Structure
- `app/src/main/java/com/duda/blockstorelinks/LinkInterceptorActivity.kt`
- `app/src/main/java/com/duda/blockstorelinks/MainActivity.kt`
- `app/src/main/java/com/duda/blockstorelinks/AppPolicyStore.kt`
- `app/src/main/java/com/duda/blockstorelinks/BrowserActivity.kt`

# Keepr

A local Android vault for deals, property listings, and marketplace links shared from social networks and browsers. Native Java 17 / Android platform UI, Android 8.0+ (API 26), SQLite, OkHttp, and jsoup.

## Implemented

- `ACTION_SEND` / `text/plain` share target, including cold launch and `onNewIntent` delivery through `singleTop`.
- URL extraction from shared text or ClipData text, with deduplication, common prose punctuation cleanup, input limits, and a chooser for multiple links. HTTP(S) only; embedded credentials are rejected. Query strings and fragments are retained. Internationalized hostnames must be supplied in ASCII/punycode form.
- Optional **Fetch preview** action in the save dialog. Extracts OG title, description, image, and site name, with Twitter/standard meta and HTML title fallbacks. Resolves relative image URLs. Price, currency, and contact are captured only when explicitly present in supported meta/itemprop fields. It does not infer these from prose or run JavaScript.
- Offline SQLite snapshots containing the link, manually editable title and notes, category, and extracted metadata text. Feed supports text search, category filters, price text, open-original actions, and deletion.
- Transactional migration from the original SharedPreferences JSON collection. Original JSON is retained for recovery; existing item IDs are preserved and migration is idempotent.
- Save-dialog text survives Activity recreation. Preview results may be fetched again after recreation.

## Architecture and schema

`domain/` contains immutable `SavedItem`, `Category`, and `CachedMedia` models and the `SavedItemRepository` interface. `data/` contains URL extraction, HTML parsing, HTTP fetching, the versioned `VaultDatabase`, and `SQLiteSavedItemRepository`.

The UI owns separate workers for serialized database operations and preview requests. Repository writes emit immutable, committed SQLite snapshots to the UI; network availability never gates saving. The database remains the source of truth. This is an incremental foundation using the existing Java UI, without a Kotlin/Compose migration or a backend.

- **SavedItem**: UUID, original URL, title, description, site name, image URL, decimal price text, currency, contact, notes, nullable category foreign key, status, preview state/error, and creation/update timestamps (epoch milliseconds). Status accepts `ACTIVE`, `REACHED_OUT`, `PURCHASED`, or `ARCHIVED`; preview state accepts `PENDING`, `READY`, or `FAILED`. Repeated saves of a URL are allowed. The resolved preview URL is used for resolving metadata references; the original shared URL remains the saved external destination.
- **Category**: stable ID, case-insensitive unique name, creation timestamp. Defaults include Real Estate, Electronics, Vehicles, and Jobs. Removing a category preserves its items with a null category.
- **CachedMedia**: UUID, saved-item foreign key, source URL, unique app-private relative file path, MIME type, nonnegative byte count, SHA-256, and cache timestamp. Source URLs are unique per item. Item deletion cascades to media records.
- **Tag / SavedItemTag**: case-insensitive tag names and many-to-many membership. Item deletion cascades to memberships. Feed/status/category and URL indexes support later filtering and lookup.

Image downloading and rendering, filesystem cleanup, status editing, tag editing, automatic background retries, and JSON-LD extraction are **not implemented yet**. `CachedMedia` is the schema for the next image-cache increment; an image URL alone does not provide an offline image. Cascading a database row will not remove a future cached file automatically.

## Preview request policy

Requests use normal TLS certificate verification, a descriptive User-Agent, an HTML Accept header, no cookies or authorization, no proxy, 8-second connect/read timeouts, a 15-second per-call timeout, and a 25-second redirect-chain budget. At most five redirects and 2 MiB of decompressed HTML are accepted. Each redirect is revalidated; loops, credentials, non-web schemes, and HTTPS-to-HTTP downgrades are rejected. DNS resolution rejects local/private, link-local, multicast, and selected reserved addresses. Explicit public HTTP links are permitted for legacy pages; they are unencrypted. The parser honors HTTP charset declarations and HTML charset/BOM detection.

Preview fetching does not bypass login requirements, anti-bot controls, or private group restrictions. An HTML login/challenge page may return HTTP 200, so a successful fetch does not guarantee listing-specific metadata. Fetch failures leave the original link saveable. This caches page metadata, not a complete copy of the source post.

Data stays in app-private storage. Android backup and device transfer are excluded. Uninstalling the app removes its local data. No account, cloud sync, analytics, or release signing is configured.

## Build and verify

Install Android Studio, JDK 17, and Android SDK Platform 36. Open the repository, let Gradle sync, and run `app` on an emulator or device. For CLI builds set `ANDROID_HOME` or an untracked `local.properties` containing `sdk.dir`:

```sh
./gradlew assembleDebug lintDebug testDebugUnitTest
python3 -m unittest discover -s tests
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

JVM tests cover messy URL payloads, OG precedence, HTML fallbacks, relative images, redirects, HTTP failures, non-HTML responses, response limits, and public-address policy. Python SQLite tests execute the actual CREATE statements and verify relational constraints. CI runs both suites. Device UI/lifecycle and real Facebook shares still need device testing.

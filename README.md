# Keepr

An offline Android vault for deals, property listings, and marketplace/social posts. Native Java 17 / Android platform UI, Android 8.0+ (API 26), SQLite, WorkManager, OkHttp, and jsoup.

## Share with another person

Open a saved post and choose **Share posts**, or use **⋯ → Select posts → Actions → Share posts** for a selection.

- **Share text summary** sends the reviewed title, price, location, product type and original link through Android’s share chooser. The recipient does not need Keepr.
- **Prepare Keepr file with photos → Send Keepr file** sends a `.keepr` archive containing only the selected posts and their saved photos. It requires a Keepr version with sharing support to import.

Notes, contact fields and publication captions are off by default and can be included explicitly. Folders, tags, tracking status, internal identifiers and download history are never exported. Review the text and photos before sending: contact details already present in titles, links, captions or images are not redacted. Shared files are unencrypted. A summary does not attach photos; a Keepr file includes all locally saved photos in the selection, not missing or pending downloads.

Recipients can open the file with Keepr or use **⋯ → Import shared posts → Choose Keepr file** if a messenger/file manager does not recognize the file type. Keepr validates the archive and photo checksums, shows the incoming content, and waits for **Save shared copies**. Imports go into **Other**, start as **Active**, and never overwrite local posts. **Skip links already in my collection** is enabled by default; uncheck it to save another copy. URL matching follows the existing normalized-link rules; text-only and photo-only posts always become new copies. The whole import is transactional, including cleanup of copied photos if saving fails.

Each person keeps an independent offline copy: no accounts, server, automatic source fetch, or ongoing synchronization. Imported listing/contact fields are preserved until the recipient edits them. Keep Keepr open while preparing or importing a file; rotation is supported, but jobs do not resume after process termination. Sharing is limited to 100 posts, 1,000 photos, 8 MiB per photo, 2 MiB of metadata and 256 MiB of uncompressed content per file. Messaging apps may impose lower attachment limits. Sharing uses temporary, read-only URI access to a dedicated cache directory; older export files are removed on subsequent exports after seven days.

## Listing details: price, location and product type

Keepr extracts listing details locally from public page metadata (Open Graph and a single relevant JSON-LD Product, Offer or posting) and from the title/caption shared with the app. Explicit currency amounts in Spanish or English can become price suggestions; an unqualified `$` remains `$`, never an assumed USD. Monthly/day/week/year/hour prices retain their period. Conflicting prices, ranges and missing values remain empty. Location detection uses published address fields or explicit text labels such as `Ubicación: San Juan` and `Location: Austin`; it never uses your device location or the seller's address as the listing location. Product types can be suggested from a limited Spanish/English keyword dictionary.

New saves are analyzed immediately. Existing saves are analyzed from their stored text on opening the collection. Open a post to see **Listing details**, or use **Info → Refresh public details** to fetch currently public metadata. **Edit post** lets you correct price, currency, price period, location and product type; subsequent captures preserve those corrections, including fields you intentionally cleared. Text search includes these details. These are suggestions, not verified listing facts; review the original publication.

A link alone cannot guarantee these fields: login-only posts, missing captions and text embedded only in images may produce no details. This version does not run OCR, sign into social accounts or send saved content to an AI service. Backup format 4 preserves listing and contact fields plus manual-edit protection; formats 1, 2 and 3 can still be restored.

## Contacts and clearer post details

The Post tab groups the title/price, listing information, contact card, description and notes. Photo import/retry tools are in Photos; capture refresh, the original link and diagnostic information are in Info. The contact card supports name, phone, email and WhatsApp, with a separate short editor. Explicit text labels and the selected publication's structured seller/author fields are used; unrelated footer contacts are not scanned. Ambiguous channels remain blank. A phone number alone never implies a WhatsApp account. Call opens Android's dialer; email opens a draft; WhatsApp opens a chat without a prefilled message. Nothing is sent automatically. WhatsApp needs an explicit international number or an official wa.me/api.whatsapp.com link; Keepr does not guess the country code. Manual contact edits and intentionally cleared fields survive subsequent captures.

Havana location suggestions use a small offline catalog of the 15 municipalities and the La Víbora neighborhood, including the user-provided spelling variant “La Vivora”. For example, “casa de tres plantas en La Vivora, 10 de octubre” becomes “La Víbora, Diez de Octubre, La Habana, Cuba”. Calendar dates, generic beach/hill wording, multiple conflicting municipalities and “Víbora Park” are not silently treated as that neighborhood. Existing posts with an empty, unedited location are reanalyzed on opening the collection. This is a suggestion from text, not geocoding or GPS.

Catalog references: [UNDP Cuba human development report, annex 23](https://cuba.un.org/sites/default/files/2021-06/Libro%20IDH%20web%204%20junio.pdf) for municipal names; [Radio Ciudad de La Habana's report](https://rciudadhabanaoficial.blogspot.com/2023/02/visitan-candidatos-diputados-hogares-de.html) for Víbora within Diez de Octubre. WhatsApp links follow its [click-to-chat format](https://faq.whatsapp.com/5913398998672934).

Social hashtags are removed from imported titles and captions, including shared text. Existing publication text is displayed without hashtags too. URL fragments, ordinary text such as “C#” and your manually assigned organization tags are preserved.

## Automatic GitHub releases

Pushing/synchronizing commits to `main` runs the Android workflow. After compilation, lint, unit tests and schema tests pass, GitHub publishes an APK and SHA-256 checksum in Releases. Pull requests and other branches only run validation. Manual runs on `main` can also publish.

CI versions use `0.8.<workflow run number>` with Android version code `1000 + run number`. Re-running a completed publication does not overwrite its assets. The workflow skips superseded commits, and exposes a release to the app updater only after both assets are uploaded. Keep the Android workflow identity and version-code sequence when changing the version series. Local builds retain version 0.8.0/code 7 unless `-PkeeprVersionName=... -PkeeprVersionCode=...` are supplied; use a published APK to update a device already running a newer CI build.

Production releases use a dedicated RSA-4096 signing key with a public-handle-only certificate. `KEEPR_RELEASE_KEYSTORE` and `KEEPR_RELEASE_PASSWORD` are GitHub Actions encrypted secrets. `KEEPR_SIGNING_KEYSTORE` retains the previous key for Android 8 compatibility. The public `release/signing-lineage.bin` authorizes migration to the production key on Android 9+. Rollback to the old key is disabled; signature-permission compatibility is retained for AndroidX’s receiver permission. The release script verifies both the new fingerprint and Android 8 signature compatibility. Never commit signing keys or passwords.

The phone discovers published versions through **Settings → Check for updates**. Download and Android’s installation confirmation remain manual.

## Download and install

Download the latest **Keepr-<version>.apk** from [GitHub Releases](https://github.com/deborahfam/Keepr/releases). Open the APK on your Android phone and allow installation from your browser or file manager when Android asks. Android 8.0 or newer is required.

Version 0.6.0 adds **Settings → App updates**. Open **⋯ → Settings** to see the installed version, check the latest public GitHub release, and download a newer APK in your browser. Open the completed download to let Android install it. Update checks happen only when you tap **Check for updates**; they send no saved posts or account credentials. Offline, rate-limit, missing-APK, and already-up-to-date states are shown explicitly. Settings also links to Backup and Trash.

The dark red interface, redesigned launcher icon, cleaner saved-post cards, and remembered **Cards / List** view remain available. Text search and categories stay on the dashboard; advanced filters and bulk selection are in **⋯**.

Release APKs are non-debuggable and use the compatible signing rotation above. If Android reports a signature conflict, do not uninstall without first exporting a backup. Pull-request artifacts are unsigned release builds; use signed GitHub release assets for installation. Keep both signing keys and the production password in secure recovery storage: GitHub does not let you download repository secret values. Local recovery files, when generated, live in the ignored `.local/release-signing/` directory with restrictive permissions; they must not be shared or added to git.

Each release includes a SHA-256 checksum file. Download the APK and checksum together, then verify on Linux with `sha256sum -c Keepr-<version>.apk.sha256`.

## Share and save

1. In the source app choose Share, then its external **More / Share to…** option if needed to reach Android's share menu.
2. Choose **Keepr**.
3. Choose an existing folder or create one, enter comma-separated tags, review the caption, and tap **Save post**.
4. Open the saved card to read the text and view the locally stored photos offline.

Keepr receives plain text, HTML text, single photos, and multiple photos. Shared links are extracted from messy text, deduplicated, and offered in a chooser when several links are present. Shared captions are retained separately from extracted page text. Photo-only and text-only shares can be saved without a URL. No broad storage permission is needed.

Folder filters and search cover saved titles, captions, original shared text, notes, and tags. Existing collections are migrated without clearing them. Folder names are unique ignoring case; tags support up to 20 per post, with at most 40 characters each.

## Text and photos

The share dialog automatically tries to fetch a public preview. Saving does not wait for the network: the item, tags, and direct shared photos are stored locally first. WorkManager then fetches available metadata and photos, with network constraints and up to three attempts per failed photo. Every discovered photo URL is persisted in a download queue. Large galleries resume in batches until the entire queue has been attempted, including across process restarts. Work survives Activity closure and process restarts. Pending items are rescheduled on launch if enqueueing was interrupted. **Retry missing photos** is available in the saved-post view.

Extraction reads OpenGraph, Twitter/standard metadata, HTML titles, and JSON-LD publication/product/listing fields, including available longer captions and image arrays. It resolves relative image links. Price, currency, and contact are captured only when explicitly available. It does not execute page JavaScript or use authenticated social-network APIs. Obvious Facebook/Instagram login responses are treated as unavailable previews. For Facebook, Keepr also reads the target story’s attachment JSON embedded in its public HTML, preferring the larger viewer image. It ignores photos belonging to other stories and records Facebook’s reported album count. When only part of an album is exposed, the detail view reports the missing count instead of implying the capture is complete. Existing items are re-examined after extractor upgrades. **Add photos from phone** lets the user supply photos the public page does not expose.

Public visibility does not guarantee a source will expose its full post to this app. A source may share only a URL, return a login/challenge page, expose only a thumbnail, omit carousel images, or block downloads. Keepr preserves the supplied text, directly shared photos, and original link, and reports missing preview/photo content. It cannot promise a complete archive of every public Facebook or Instagram post. Private posts, authentication bypasses, videos, comments, and reactions are not captured.

There is no fixed photo-count limit per post. All directly shared photos and every image URL exposed in supported metadata are queued. Missing or blocked photos are reported rather than silently omitted. Each image response or directly shared image is limited to 8 MiB; decoded snapshots are sampled to at most 1600 pixels on the longest side and saved as JPEG in app-private `files/media`. Animated images are represented by a still frame. Shared text and extracted captions are limited to 65,536 characters. Downloaded files remain available without an internet connection and when the source disappears. Deleting a post cancels its work, removes its media records, and deletes its cached image files. A download finishing after deletion discards its file.

## Saved-post detail views

Tap **View saved post** on any card:

- **Post** shows the full caption, photo carousel, price/contact when available, tags, and personal notes.
- **Photos** shows all locally saved images with previous/next buttons, swipe navigation, and saved/queued/unavailable counts. Tap a photo for the full-screen viewer with pinch zoom, panning, and double-tap reset.
- **Info** shows the folder, tags, tracking status, saved/updated dates, local photo storage size, original source, and capture errors.

Photos load one at a time in the carousel, so large albums do not decode every image into memory. Current tab and photo position survive screen recreation. Photos are presented in discovered publication order, including those completed on a later retry.

## Backup, restore, and editing

Open the **⋯** collection menu and choose **Backup and restore** to export a password-encrypted `.keeprbackup` file through Android's document picker. It contains every saved post, folder, tag, original shared caption, offline photo, and photo-download state. Keep the exported file outside this device. Choose and confirm a password of at least 12 characters. Keepr cannot recover it. New backups use PBKDF2-HMAC-SHA256 (600,000 iterations, random 16-byte salt) and AES-256-GCM authenticated 64 KiB frames with a mandatory final frame. Authentication completes before restore writes to the database. Leave the password blank only when restoring a legacy unencrypted ZIP. Keep Keepr open until it reports completion. Failed exports may leave an incomplete destination file; export again before relying on it.

**Restore backup** validates the archive and photo checksums before merging missing posts in a database transaction. Existing post IDs are skipped, preserving newer local edits. Folder/tag names are matched ignoring case. Missing or damaged photos, invalid paths, and unsupported archive versions reject the import. Restore does not erase your collection. Pending capture work is rescheduled after import. Format version 4 includes contact channels, listing details and correction protection as well as trash entries and expiry dates, and imports version 1, 2 and 3 archives. It supports up to 32 MiB of metadata, 8 MiB per photo, and 4 GiB of uncompressed content.

Open a saved post and tap **Edit saved post** to change its title, personal notes, tags, or folder. Choose an existing folder or type a new folder name. Photos, source content, tracking status, creation date, and download progress are preserved. Edited titles are protected from background preview replacement. Unsaved edit fields survive screen recreation.

## Tracking and duplicate links

Use **Change status** in a saved post to choose Active, Reached out, Purchased, or Archived. Every card displays its status, including cards with prices. The collection status filter combines with folder selection and search; all three survive screen recreation. Archived posts remain saved and appear under All statuses or Archived.

Saving a URL already in the vault offers **Open existing**, **Update capture**, or **Save another copy**. If several copies exist, the prompt targets the most recently saved one. Matching normalizes scheme/host case and an empty path, while retaining query strings and fragments; it does not guess whether tracking links or redirects point to the same post. Text-only and photo-only shares are not treated as duplicates.

**Update capture** retries missing photos and queues fresh public metadata/photo discovery after any existing capture completes. It retains the existing post and its local photos, notes, tags, folder, status, and edited title. Draft edits and newly shared photos are only saved with **Save another copy**; add photos to an existing post from its detail screen. Dismiss the duplicate prompt to return to the draft without saving.

## Search, sorting, and bulk organization

The collection loads 30 posts at a time from SQLite. Use **Previous / Next** below the results to change pages; counts and filters cover the whole collection. Text search waits 250 ms after typing stops, and newer searches supersede older requests. Search accepts up to 1,024 characters and treats wildcard characters literally. The current page survives rotation and returning from a post; filtering starts on the first page. If deleting posts empties the last page, Keepr returns to the last remaining page.

Switch between **Cards** and a compact **List** above the results. Keepr remembers this choice, and switching preserves search, filters, and bulk selection. Tap a list row or **Open** on a card to view the saved post.

The dashboard shows text search, categories, and the **Cards / List** switch. Open **⋯** for **Filters and sorting** or **Select posts**. **Filters and sorting** contains the status control and **Tags, sources, dates & sort**, which adds exact tag and source filters plus a saved-date range (inclusive dates in the device’s time zone). These combine with search, folder, and tracking status. Choose newest first, oldest first, or price ascending/descending. Price sorting groups currencies alphabetically and sorts decimal amounts within each group; missing or unrecognized prices appear last. It does not convert currencies or guess the meaning of formatted price text. Filter choices survive screen recreation.

Tap **Select posts**, check individual cards or **Select all results**, then choose **Actions** to move posts to a folder, add tags, archive, or delete. Adding tags preserves existing tags; a tag-limit violation rolls back the whole batch. Selection is scoped to the complete filtered result set across pages. **Select all results** selects matching IDs without loading every post or photo; moving between pages preserves selection, while changing filters removes nonmatching posts. Deletion asks for confirmation and moves selected posts and their saved photos into Trash; archive keeps everything in the collection.

**Browse categories → Manage**, or **⋯ → Manage categories**, opens the category manager. Create an empty category, rename any category, or remove it. Names must be unique ignoring ASCII case, contain 1–60 characters, and cannot be “All”. Each category shows saved-post and Trash counts. Removing a populated category requires choosing where to move its posts, including Trash entries; photos, notes, status and trash expiry dates are preserved. Removing an empty category needs no destination. At least one category must remain. Renaming keeps the stable category ID and the selected collection filter; removing the selected category returns the filter to All. Editor drafts survive rotation. Categories use the existing folder storage and backup format, so older collections remain compatible.

The collection uses a compact header with a **＋** save action, category count chips, larger photo-led cards, and a compact List view. Dark surfaces, red accents, consistent spacing, and clearer selected states keep the interface aligned across the collection and category manager.

## Capture progress and Trash

Cards show discovered photo counts (for example, **8 of 12 photos saved**), **Downloading** while capture is pending, or **Needs attention** when capture fails or photos are missing. Open the post for details and retry options.

Single and bulk deletion move posts to **Trash**, retaining photos, tags, notes, folder, and status. Open Trash from the **⋯** collection menu to restore a post or confirm permanent deletion. New deletions expire after 30 days by default; choose 7, 30, or 90 days in Trash. Changing the setting affects future deletions only. Expired entries are removed when opening Keepr/Trash or by daily background cleanup, subject to Android scheduling. Restoring a post resumes pending capture work. Backups preserve trash membership and expiry dates.

## Architecture

- `domain/`: immutable SavedItem, Category, CachedMedia models; tag normalization; SavedItemRepository interface.
- `data/`: SQLiteSavedItemRepository, VaultDatabase, MetadataParser/StructuredPost, MetadataService, MediaCache, SnapshotWorker.
- `MainActivity`: incoming-share review, folder/tag selection, collection feed, and offline saved-post viewer. Separate workers handle database, network-preview, and bitmap decoding work. Immutable repository snapshots and WorkManager updates refresh the feed.

SQLite schema version 8 contains SavedItem (metadata, original shared text, folder, status, preview state/error, timestamps), Category, Tag, SavedItemTag, CachedMedia (source URL, app-private relative path, MIME type, byte count, SHA-256, cache timestamp), and MediaDownload (every discovered URL, publication order, download state, attempt count, and error). Foreign keys cascade item/media/tag membership deletion and preserve items when a category is removed. Status accepts ACTIVE, REACHED_OUT, PURCHASED, or ARCHIVED; status editing is available from the saved-post view. The upgrade from v1 adds shared_text and the upgrade from v2 adds the download queue and v3 adds expected photo counts/discovery version without removing existing data; v5 adds a flag protecting edited titles; original JSON collections are migrated transactionally and removed only after successful migration. Repeated saves of a URL require choosing **Save another copy** in the duplicate prompt.

HTTP requests use normal TLS verification, a descriptive User-Agent, no cookies/authorization/proxy, 8-second connect/read timeouts, 15-second per-call timeout, 25-second redirect-chain budget, five redirects maximum, and a 2 MiB decompressed HTML cap. DNS resolution rejects private/local, link-local, multicast, and selected reserved addresses. Redirects are revalidated and reject credentials, non-web schemes, and HTTPS-to-HTTP downgrades. Public HTTP links can be saved, but Android blocks cleartext preview/photo downloads; HTTPS is required for network previews. HTML charset/BOM detection is supported.

Data remains in app-private storage. Automatic Android backup and device transfer are excluded; uninstalling removes local data. Use the password-encrypted manual backup to preserve and transfer your collection. No backend, account, cloud sync or analytics is configured. GitHub release signing uses a dedicated production key and compatible signing lineage as described above.

## Build and verify

Install Android Studio, JDK 17, and SDK Platform 36. Open the repository and run `app`. For CLI builds set ANDROID_HOME or an untracked local.properties containing sdk.dir:

```sh
./gradlew assembleDebug assembleDebugAndroidTest lintDebug testDebugUnitTest
python3 -m unittest discover -s tests
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

JVM tests cover URL extraction, tag normalization, metadata/structured captions and image galleries, HTTP redirects and errors, response limits, and address policy. Python SQLite tests exercise schema constraints and the v1 upgrade. Android instrumentation tests cover migration, tags/caption/photo persistence, deletion, late-download cleanup, the incoming-photo-to-folder save flow, shares with 15 images, resumable 30-photo queues, gallery navigation, and restored detail-screen state. CI runs the device suite on an API 36 emulator before publishing. `scripts/device-tests.sh` uses Android instrumentation directly and fails if the suite fails or does not finish.

To run instrumentation tests on a disposable emulator, install the app APK and `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`, then run:

```sh
adb -s EMULATOR_SERIAL shell am instrument -w com.keepr.app.test/androidx.test.runner.AndroidJUnitRunner
```

Real Facebook/Instagram shares still need verification on the user's installed app versions and representative public posts. Android's sharing contract is documented in [Receive data from other apps](https://developer.android.com/training/sharing/receive).

## Publishing readiness (November 20, 2026)

Settings includes creator attribution, copyright, the Apache 2.0 license, bundled third-party notices, and offline/online privacy policies. Public policy: https://deborahfam.github.io/Keepr/. Private security reports: https://github.com/deborahfam/Keepr/security/advisories/new.

The release workflow checks resolved dependency advisories with OSV, checks license inventory freshness, scans git history and source with checksum-pinned Gitleaks, runs JVM/database/device tests, and verifies the APK manifest and sensitive-file patterns before signing. Third-party Actions are pinned to immutable commits. Network errors in advisory checks fail the build. A clean scan is not a guarantee against undisclosed vulnerabilities.

Known publication decisions still requiring owner action:

- Historical commits contain a private email and a design `fileToken` flagged by Gitleaks. The current design is scrubbed and future commits use the GitHub noreply identity. A sanitized mirror is prepared locally; replacing published branches/tags requires a coordinated history rewrite. Old clones and GitHub caches are not removed by a force push. The token’s authorization semantics have not been confirmed; if it grants access, revoke it with the design service.
- GitHub distribution is prepared. A later Google Play launch still requires a Play Console app, Play App Signing enrollment, signed Android App Bundle, completed Data safety/content-rating forms and store review. Do not claim that arbitrary source websites never receive user data: preview and image requests transmit URLs/IP addresses. See `release/play-data-safety.txt` for the implementation facts to use during submission.
- A real Facebook/Instagram installed-app smoke test depends on those apps/accounts. Automated tests exercise the Android sharing contracts and imports with synthetic fixtures; they do not log into your social accounts.

Backups are encrypted starting with 0.8; older ZIPs and deliberately shared post files remain unencrypted and are disclosed accordingly. Store screenshots must use synthetic data. Existing design exports use illustrative content and are not screenshots of the current app.

References: [Android signing](https://developer.android.com/studio/publish/app-signing), [Android debuggable guidance](https://developer.android.com/privacy-and-security/risks/android-debuggable), [Play user data requirements](https://support.google.com/googleplay/android-developer/answer/10144311), [Play Data safety](https://support.google.com/googleplay/android-developer/answer/10787469).

## In-app updates

Settings downloads the official GitHub APK into private cache, displays progress, and verifies its published SHA-256 checksum before offering **Install update**. Android asks for permission to install updates from Keepr and displays the installation confirmation. No browser is needed for this flow. Downloads survive screen rotation; after process termination, check and download again. Android may stop Keepr while replacing the installed package.

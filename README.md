# Keepr

An offline Android vault for deals, property listings, and marketplace/social posts. Native Java 17 / Android platform UI, Android 8.0+ (API 26), SQLite, WorkManager, OkHttp, and jsoup.

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

CI versions use `0.7.<workflow run number>` with Android version code `1000 + run number`. Re-running a completed publication does not overwrite its assets. The workflow skips superseded commits, and exposes a release to the app updater only after both assets are uploaded. Keep the Android workflow identity and version-code sequence when changing the version series. Local builds retain version 0.7.0/code 7 unless `-PkeeprVersionName=... -PkeeprVersionCode=...` are supplied; use a published APK to update a device already running a newer CI build.

Repository secret `KEEPR_SIGNING_KEYSTORE` contains the base64-encoded existing development keystore. The secret is consumed only by the publication job on `main`, after validation; no signing key is committed to git. The signing certificate is checked against the current installed identity. These are still development-signed debug APKs, not Play Store releases.

The phone discovers published versions through **Settings → Check for updates**. Download and Android’s installation confirmation remain manual.

## Download and install

Download the latest **Keepr-<version>.apk** from [GitHub Releases](https://github.com/deborahfam/Keepr/releases). Open the APK on your Android phone and allow installation from your browser or file manager when Android asks. Android 8.0 or newer is required.

Version 0.6.0 adds **Settings → App updates**. Open **⋯ → Settings** to see the installed version, check the latest public GitHub release, and download a newer APK in your browser. Open the completed download to let Android install it. Update checks happen only when you tap **Check for updates**; they send no saved posts or account credentials. Offline, rate-limit, missing-APK, and already-up-to-date states are shown explicitly. Settings also links to Backup and Trash.

The dark red interface, redesigned launcher icon, cleaner saved-post cards, and remembered **Cards / List** view remain available. Text search and categories stay on the dashboard; advanced filters and bulk selection are in **⋯**.

The release APK uses the existing development signing identity, allowing an in-place update over builds installed from this development machine. It is a debug build, not a production-signed store release. If Android reports a signature conflict, do not uninstall without first exporting a Keepr backup. Pull-request build artifacts use the runner’s debug key and are for testing. Releases published from `main` are re-signed with the existing installation’s key stored in the encrypted `KEEPR_SIGNING_KEYSTORE` repository secret. Use the APK attached to a release for compatible updates.

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

Open the **⋯** collection menu and choose **Backup and restore** to export a ZIP through Android's document picker. It contains every saved post, folder, tag, original shared caption, offline photo, and photo-download state. Keep the exported file outside this device. Backups are unencrypted; choose a trusted destination. Keep Keepr open until it reports completion. Failed exports may leave an incomplete destination file; export again before relying on it.

**Restore backup** validates the archive and photo checksums before merging missing posts in a database transaction. Existing post IDs are skipped, preserving newer local edits. Folder/tag names are matched ignoring case. Missing or damaged photos, invalid paths, and unsupported archive versions reject the import. Restore does not erase your collection. Pending capture work is rescheduled after import. Format version 4 includes contact channels, listing details and correction protection as well as trash entries and expiry dates, and imports version 1, 2 and 3 archives. It supports up to 32 MiB of metadata, 8 MiB per photo, and 4 GiB of uncompressed content.

Open a saved post and tap **Edit saved post** to change its title, personal notes, tags, or folder. Choose an existing folder or type a new folder name. Photos, source content, tracking status, creation date, and download progress are preserved. Edited titles are protected from background preview replacement. Unsaved edit fields survive screen recreation.

## Tracking and duplicate links

Use **Change status** in a saved post to choose Active, Reached out, Purchased, or Archived. Every card displays its status, including cards with prices. The collection status filter combines with folder selection and search; all three survive screen recreation. Archived posts remain saved and appear under All statuses or Archived.

Saving a URL already in the vault offers **Open existing**, **Update capture**, or **Save another copy**. If several copies exist, the prompt targets the most recently saved one. Matching normalizes scheme/host case and an empty path, while retaining query strings and fragments; it does not guess whether tracking links or redirects point to the same post. Text-only and photo-only shares are not treated as duplicates.

**Update capture** retries missing photos and queues fresh public metadata/photo discovery after any existing capture completes. It retains the existing post and its local photos, notes, tags, folder, status, and edited title. Draft edits and newly shared photos are only saved with **Save another copy**; add photos to an existing post from its detail screen. Dismiss the duplicate prompt to return to the draft without saving.

## Search, sorting, and bulk organization

Switch between **Cards** and a compact **List** above the results. Keepr remembers this choice, and switching preserves search, filters, and bulk selection. Tap a list row or **Open** on a card to view the saved post.

The dashboard shows text search, categories, and the **Cards / List** switch. Open **⋯** for **Filters and sorting** or **Select posts**. **Filters and sorting** contains the status control and **Tags, sources, dates & sort**, which adds exact tag and source filters plus a saved-date range (inclusive dates in the device’s time zone). These combine with search, folder, and tracking status. Choose newest first, oldest first, or price ascending/descending. Price sorting groups currencies alphabetically and sorts decimal amounts within each group; missing or unrecognized prices appear last. It does not convert currencies or guess the meaning of formatted price text. Filter choices survive screen recreation.

Tap **Select posts**, check individual cards or **Select all results**, then choose **Actions** to move posts to a folder, add tags, archive, or delete. Adding tags preserves existing tags; a tag-limit violation rolls back the whole batch. Selection is limited to visible results, so changing filters removes hidden posts from the selection. Deletion asks for confirmation and moves selected posts and their saved photos into Trash; archive keeps everything in the collection.

**Manage folders** in the **⋯** collection menu lets you rename a folder or merge it into another existing folder. Renaming preserves membership, and merging moves all source posts into the destination before removing the source folder. Names remain unique ignoring case. Folder IDs remain stable behind their display names; old backups and newly created folders still work after a rename.

## Capture progress and Trash

Cards show discovered photo counts (for example, **8 of 12 photos saved**), **Downloading** while capture is pending, or **Needs attention** when capture fails or photos are missing. Open the post for details and retry options.

Single and bulk deletion move posts to **Trash**, retaining photos, tags, notes, folder, and status. Open Trash from the **⋯** collection menu to restore a post or confirm permanent deletion. New deletions expire after 30 days by default; choose 7, 30, or 90 days in Trash. Changing the setting affects future deletions only. Expired entries are removed when opening Keepr/Trash or by daily background cleanup, subject to Android scheduling. Restoring a post resumes pending capture work. Backups preserve trash membership and expiry dates.

## Architecture

- `domain/`: immutable SavedItem, Category, CachedMedia models; tag normalization; SavedItemRepository interface.
- `data/`: SQLiteSavedItemRepository, VaultDatabase, MetadataParser/StructuredPost, MetadataService, MediaCache, SnapshotWorker.
- `MainActivity`: incoming-share review, folder/tag selection, collection feed, and offline saved-post viewer. Separate workers handle database, network-preview, and bitmap decoding work. Immutable repository snapshots and WorkManager updates refresh the feed.

SQLite schema version 8 contains SavedItem (metadata, original shared text, folder, status, preview state/error, timestamps), Category, Tag, SavedItemTag, CachedMedia (source URL, app-private relative path, MIME type, byte count, SHA-256, cache timestamp), and MediaDownload (every discovered URL, publication order, download state, attempt count, and error). Foreign keys cascade item/media/tag membership deletion and preserve items when a category is removed. Status accepts ACTIVE, REACHED_OUT, PURCHASED, or ARCHIVED; status editing is available from the saved-post view. The upgrade from v1 adds shared_text and the upgrade from v2 adds the download queue and v3 adds expected photo counts/discovery version without removing existing data; v5 adds a flag protecting edited titles; original JSON collections are migrated transactionally and kept as recovery copies. Repeated saves of a URL require choosing **Save another copy** in the duplicate prompt.

HTTP requests use normal TLS verification, a descriptive User-Agent, no cookies/authorization/proxy, 8-second connect/read timeouts, 15-second per-call timeout, 25-second redirect-chain budget, five redirects maximum, and a 2 MiB decompressed HTML cap. DNS resolution rejects private/local, link-local, multicast, and selected reserved addresses. Redirects are revalidated and reject credentials, non-web schemes, and HTTPS-to-HTTP downgrades. Explicit public HTTP links are permitted and unencrypted. HTML charset/BOM detection is supported.

Data remains in app-private storage. Automatic Android backup and device transfer are excluded; uninstalling removes local data. Use the manual ZIP backup to preserve and transfer your collection. No backend, account, cloud sync or analytics is configured. GitHub release signing uses the existing development identity as described above.

## Build and verify

Install Android Studio, JDK 17, and SDK Platform 36. Open the repository and run `app`. For CLI builds set ANDROID_HOME or an untracked local.properties containing sdk.dir:

```sh
./gradlew assembleDebug assembleDebugAndroidTest lintDebug testDebugUnitTest
python3 -m unittest discover -s tests
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

JVM tests cover URL extraction, tag normalization, metadata/structured captions and image galleries, HTTP redirects and errors, response limits, and address policy. Python SQLite tests exercise schema constraints and the v1 upgrade. Android instrumentation tests cover migration, tags/caption/photo persistence, deletion, late-download cleanup, the incoming-photo-to-folder save flow, shares with 15 images, resumable 30-photo queues, gallery navigation, and restored detail-screen state. New device tests are compiled by CI; run them explicitly on a test device when needed.

To run instrumentation tests on a disposable emulator, install the app APK and `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`, then run:

```sh
adb -s EMULATOR_SERIAL shell am instrument -w com.keepr.app.test/androidx.test.runner.AndroidJUnitRunner
```

Real Facebook/Instagram shares still need verification on the user's installed app versions and representative public posts. Android's sharing contract is documented in [Receive data from other apps](https://developer.android.com/training/sharing/receive).

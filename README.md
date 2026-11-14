# Keepr

An offline Android vault for deals, property listings, and marketplace/social posts. Native Java 17 / Android platform UI, Android 8.0+ (API 26), SQLite, WorkManager, OkHttp, and jsoup.

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

Use **Backup and restore** below the collection to export a ZIP through Android's document picker. It contains every saved post, folder, tag, original shared caption, offline photo, and photo-download state. Keep the exported file outside this device. Backups are unencrypted; choose a trusted destination. Keep Keepr open until it reports completion. Failed exports may leave an incomplete destination file; export again before relying on it.

**Restore backup** validates the archive and photo checksums before merging missing posts in a database transaction. Existing post IDs are skipped, preserving newer local edits. Folder/tag names are matched ignoring case. Missing or damaged photos, invalid paths, and unsupported archive versions reject the import. Restore does not erase your collection. Pending capture work is rescheduled after import. Format version 1 supports up to 32 MiB of metadata, 8 MiB per photo, and 4 GiB of uncompressed content.

Open a saved post and tap **Edit saved post** to change its title, personal notes, tags, or folder. Choose an existing folder or type a new folder name. Photos, source content, tracking status, creation date, and download progress are preserved. Edited titles are protected from background preview replacement. Unsaved edit fields survive screen recreation.

## Tracking and duplicate links

Use **Change status** in a saved post to choose Active, Reached out, Purchased, or Archived. Every card displays its status, including cards with prices. The collection status filter combines with folder selection and search; all three survive screen recreation. Archived posts remain saved and appear under All statuses or Archived.

Saving a URL already in the vault offers **Open existing**, **Update capture**, or **Save another copy**. If several copies exist, the prompt targets the most recently saved one. Matching normalizes scheme/host case and an empty path, while retaining query strings and fragments; it does not guess whether tracking links or redirects point to the same post. Text-only and photo-only shares are not treated as duplicates.

**Update capture** retries missing photos and queues fresh public metadata/photo discovery after any existing capture completes. It retains the existing post and its local photos, notes, tags, folder, status, and edited title. Draft edits and newly shared photos are only saved with **Save another copy**; add photos to an existing post from its detail screen. Dismiss the duplicate prompt to return to the draft without saving.

## Search, sorting, and bulk organization

**Filters and sorting** adds exact tag and source filters plus a saved-date range (inclusive dates in the device’s time zone). These combine with search, folder, and tracking status. Choose newest first, oldest first, or price ascending/descending. Price sorting groups currencies alphabetically and sorts decimal amounts within each group; missing or unrecognized prices appear last. It does not convert currencies or guess the meaning of formatted price text. Filter choices survive screen recreation.

Tap **Select posts**, check individual cards or **Select all results**, then choose **Actions** to move posts to a folder, add tags, archive, or delete. Adding tags preserves existing tags; a tag-limit violation rolls back the whole batch. Selection is limited to visible results, so changing filters removes hidden posts from the selection. Deletion asks for confirmation and permanently removes selected posts and their saved photos; archive keeps everything locally.

**Manage folders** lets you rename a folder or merge it into another existing folder. Renaming preserves membership, and merging moves all source posts into the destination before removing the source folder. Names remain unique ignoring case. Folder IDs remain stable behind their display names; old backups and newly created folders still work after a rename.

## Architecture

- `domain/`: immutable SavedItem, Category, CachedMedia models; tag normalization; SavedItemRepository interface.
- `data/`: SQLiteSavedItemRepository, VaultDatabase, MetadataParser/StructuredPost, MetadataService, MediaCache, SnapshotWorker.
- `MainActivity`: incoming-share review, folder/tag selection, collection feed, and offline saved-post viewer. Separate workers handle database, network-preview, and bitmap decoding work. Immutable repository snapshots and WorkManager updates refresh the feed.

SQLite schema version 5 contains SavedItem (metadata, original shared text, folder, status, preview state/error, timestamps), Category, Tag, SavedItemTag, CachedMedia (source URL, app-private relative path, MIME type, byte count, SHA-256, cache timestamp), and MediaDownload (every discovered URL, publication order, download state, attempt count, and error). Foreign keys cascade item/media/tag membership deletion and preserve items when a category is removed. Status accepts ACTIVE, REACHED_OUT, PURCHASED, or ARCHIVED; status editing is available from the saved-post view. The upgrade from v1 adds shared_text and the upgrade from v2 adds the download queue and v3 adds expected photo counts/discovery version without removing existing data; v5 adds a flag protecting edited titles; original JSON collections are migrated transactionally and kept as recovery copies. Repeated saves of a URL require choosing **Save another copy** in the duplicate prompt.

HTTP requests use normal TLS verification, a descriptive User-Agent, no cookies/authorization/proxy, 8-second connect/read timeouts, 15-second per-call timeout, 25-second redirect-chain budget, five redirects maximum, and a 2 MiB decompressed HTML cap. DNS resolution rejects private/local, link-local, multicast, and selected reserved addresses. Redirects are revalidated and reject credentials, non-web schemes, and HTTPS-to-HTTP downgrades. Explicit public HTTP links are permitted and unencrypted. HTML charset/BOM detection is supported.

Data remains in app-private storage. Automatic Android backup and device transfer are excluded; uninstalling removes local data. Use the manual ZIP backup to preserve and transfer your collection. No backend, account, cloud sync, analytics, or release signing is configured.

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

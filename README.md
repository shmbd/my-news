# My News (আমার খবর)
**Bangladesh Editorial, Opinion, Column & Interview Aggregator**

**My News (আমার খবর)** হলো বাংলাদেশের সংবাদপত্র ও ডিজিটাল সংবাদমাধ্যমে প্রকাশিত **কলাম (Columns)**, **সম্পাদকীয় (Editorials)**, **মতামত (Opinions)** এবং **সাক্ষাৎকার (Interviews)** আবিষ্কার ও পাঠের জন্য নির্মিত একটি পূর্ণাঙ্গ, বিজ্ঞাপনমুক্ত ও কপিরাইট-বান্ধব নেটিভ অ্যান্ড্রয়েড অ্যাপ্লিকেশন।

> **গুরুত্বপূর্ণ:** এটি কোনো সাধারণ সংবাদ বা ব্রেকিং নিউজ এগ্রিগেটর নয়। কেবলমাত্র **২৩ নভেম্বর ২০২৬ (`2026-11-23`, `Asia/Dhaka`) বা তার পরে প্রকাশিত** বুদ্ধিবৃত্তিক কলাম, প্রাতিষ্ঠানিক সম্পাদকীয়, মতামত নিবন্ধ এবং সাক্ষাৎকারই এতে সংগৃহীত ও প্রদর্শিত হয়।

---

## 1. Application Overview & Configuration
- **App Name:** My News (আমার খবর)
- **Live GitHub Pages Website:** https://shmbd.github.io/my-news/ (`docs/` + `.github/workflows/pages.yml`)
- **Package ID:** `bd.nib.amarkhobor` (Debug suffix: `.debug`, coexists cleanly alongside `SongbadPatro`)
- **SDK Support:** `minSdk = 24` (Android 7.0) | `targetSdk = 35` | `compileSdk = 35` (Android 15)
- **Default Language:** Bangla (`বাংলা`), with optional `English` UI switch
- **Timezone:** `Asia/Dhaka` (`+06:00`) via Java Time (`coreLibraryDesugaring`)
- **Privacy & Ads:** Ads disabled (`ADS_ENABLED = false`), zero ad SDKs, zero analytics/trackers

---

## 2. Strict Content Scope & Publication Cutoff (`2026-11-23`)
### Eligible Categories (Only Four)
1. **কলাম (`COLUMN`):** Regular and guest columns by journalists, academics, economists, researchers, and analysts.
2. **সম্পাদকীয় (`EDITORIAL`):** Official institutional editorials representing the newspaper's editorial board (explicitly badged as `প্রাতিষ্ঠানিক সম্পাদকীয়` / `Official Editorial` and distinguished from guest pieces).
3. **মতামত (`OPINION`):** Op-eds, perspectives, commentary, and analytical essays.
4. **সাক্ষাৎকার (`INTERVIEW`):** Expert, academic, policy, economic, and cultural interviews and Q&A-format conversations (with interviewee, designation, and interviewer extraction).

### Strict Date Cutoff Enforcement
- Centralized in [`PublicationCutoffConfig.kt`](app/src/main/java/bd/nib/songbadpatro/domain/config/PublicationCutoffConfig.kt) (`DEFAULT_CUTOFF_ISO_DATE = "2026-11-23"`, `Asia/Dhaka`).
- Enforced across **5 layers**: (1) Feed/Section Ingestion (`DateValidationUseCase`), (2) Repository Insertion Guard, (3) Room SQL Queries (`originalPublicationDate >= :cutoffEpochMs`), (4) Search & Category Filtering, and (5) WorkManager Background Sync & Notifications.
- Separates `originalPublicationDate` (`pubDate`, `dc:date`, `atom:published`, JSON-LD `datePublished`, `article:published_time`) from `updatedDate` (`atom:updated`, `dateModified`, `article:modified_time`).
- Older articles updated on/after `2026-11-23` are **rejected** unless explicitly marked as a newly published edition.
- Articles with missing/unverified publication dates are placed in `PENDING_DATE_VERIFICATION` rather than auto-published.

---

## 3. Supported Bangladeshi Publishers & Verification Status
All candidate publishers were live-tested against their official HTTPS endpoints and `robots.txt` policies before enabling in [`app/src/main/assets/publisher_registry.json`](app/src/main/assets/publisher_registry.json). Never-verified or WAF-restricted endpoints are kept **disabled by default**.

| Publisher (Bangla) | Publisher (English) | Collection Method | Primary Source Endpoint(s) | Verification Status | Default Enabled |
|---|---|---|---|---|---|
| দৈনিক ইত্তেফাক | The Daily Ittefaq | `RSS_ATOM` | `https://www.ittefaq.com.bd/feed/opinion`, `https://www.ittefaq.com.bd/feed/editorial` | `VERIFIED` (200 OK, XML) | Yes |
| প্রথম আলো | Prothom Alo | `RSS_ATOM` + `SECTION_HTML_JSONLD` | `https://www.prothomalo.com/stories.rss`, `https://www.prothomalo.com/opinion` | `VERIFIED` (200 OK, XML & JSON-LD) | Yes |
| দ্য বিজনেস স্ট্যান্ডার্ড (বাংলা) | TBS Bangla | `RSS_ATOM` | `https://www.tbsnews.net/bangla/opinion/rss.xml` | `VERIFIED` (200 OK, XML) | Yes |
| দ্য বিজনেস স্ট্যান্ডার্ড (ইংরেজি) | The Business Standard | `RSS_ATOM` | `https://www.tbsnews.net/thoughts/rss.xml`, `https://www.tbsnews.net/interviews/rss.xml` | `VERIFIED` (200 OK, XML) | Yes |
| দেশ রূপান্তর | Desh Rupantor | `RSS_ATOM` | `https://www.deshrupantor.com/feed/opinion` | `VERIFIED` (200 OK, XML) | Yes |
| দ্য ডেইলি স্টার (বাংলা) | The Daily Star Bangla | `SECTION_HTML_JSONLD` + `RSS_ATOM` | `https://bangla.thedailystar.net/opinion`, `https://bangla.thedailystar.net/rss.xml` | `VERIFIED` (200 OK) | Yes |
| দ্য ডেইলি স্টার (ইংরেজি) | The Daily Star | `RSS_ATOM` | `https://www.thedailystar.net/opinion/rss.xml`, `https://www.thedailystar.net/opinion/editorial/rss.xml` | `VERIFIED` (200 OK, XML) | Yes |
| ঢাকা ট্রিবিউন | Dhaka Tribune | `RSS_ATOM` | `https://www.dhakatribune.com/feed/opinion` | `VERIFIED` (200 OK, XML) | Yes |
| সারাবাংলা | Sarabangla | `RSS_ATOM` | `https://sarabangla.net/category/opinion/feed/`, `https://sarabangla.net/category/interview/feed/` | `VERIFIED` (200 OK, XML) | Yes |
| দৈনিক আজাদী | Dainik Azadi | `RSS_ATOM` | `https://dainikazadi.net/category/editorial/feed/` | `VERIFIED` (200 OK, XML) | Yes |
| দৈনিক পূর্বকোণ | Dainik Purbokone | `RSS_ATOM` | `https://dainikpurbokone.net/category/editorial/feed/` | `VERIFIED` (200 OK, XML) | Yes |
| আজকের পত্রিকা | Ajker Patrika | `RSS_ATOM` + `SECTION_HTML_JSONLD` | `https://www.ajkerpatrika.com/feed`, `https://www.ajkerpatrika.com/op-ed/editorial` | `VERIFIED` (200 OK) | Yes |
| সমকাল | Samakal | `SECTION_HTML_JSONLD` | `https://samakal.com/opinion` | `VERIFIED` (200 OK, JSON-LD) | Yes |
| নিউ এজ | New Age | `SECTION_HTML_JSONLD` | `https://www.newagebd.net/articlelist/25/editorial` | `VERIFIED` (200 OK, JSON-LD) | Yes |
| বণিক বার্তা | Bonik Barta | `SECTION_HTML_JSONLD` | `https://bonikbarta.com/editorial` | `VERIFIED` (200 OK) | Yes |
| মানবজমিন | Daily Manab Zamin | `SECTION_HTML_JSONLD` | `https://www.mzamin.com/category/মত-মতান্তর` | `VERIFIED` (200 OK) | Yes |
| দৈনিক জনকণ্ঠ | Daily Janakantha | `SECTION_HTML_JSONLD` | `https://www.dailyjanakantha.com/opinion/` | `VERIFIED` (200 OK) | Yes |
| দৈনিক সংবাদ | The Daily Sangbad | `SECTION_HTML_JSONLD` | `https://sangbad.net.bd/news/opinion/` | `VERIFIED` (200 OK, JSON-LD) | Yes |
| বিডিনিউজ২৪ বাংলা | bdnews24 Bangla | `SECTION_HTML_JSONLD` | `https://bangla.bdnews24.com/opinion` | `VERIFIED` (200 OK, JSON-LD) | Yes |
| কালের কণ্ঠ | Kaler Kantho | `RSS_ATOM` | `https://www.kalerkantho.com/rss.xml` | `REQUIRES_VERIFICATION` (HTTP 403 WAF) | **No** |
| দৈনিক যুগান্তর | Daily Jugantor | `RSS_ATOM` | `https://www.jugantor.com/feed/rss.xml` | `REQUIRES_VERIFICATION` (HTTP 403 WAF) | **No** |
| বাংলাদেশ প্রতিদিন | Bangladesh Pratidin | `RSS_ATOM` | `https://www.bd-pratidin.com/rss.xml` | `REQUIRES_VERIFICATION` (HTTP 403 WAF) | **No** |
| দৈনিক নয়া দিগন্ত | Daily Naya Diganta | `SECTION_HTML_JSONLD` | `https://www.dailynayadiganta.com/sub-editorial` | `REQUIRES_VERIFICATION` (SSL Verify Error) | **No** |

---

## 4. Architecture & Project Structure
Follows **Clean Architecture + MVVM + Repository Pattern** with **Hilt** Dependency Injection, **Kotlin Coroutines/Flow**, **Room**, **DataStore**, **WorkManager**, and **Jetpack Compose Material 3**.

```text
my-news/
├── .github/workflows/
│   ├── android.yml                  # CI: lintDebug, testDebugUnitTest, assembleDebug + APK artifact
│   └── release.yml                  # Signed release APK & AAB via GitHub Secrets
├── gradle/
│   ├── libs.versions.toml           # Centralized Gradle Version Catalog
│   └── wrapper/                     # Gradle 8.10.2 wrapper
├── app/
│   ├── build.gradle.kts             # SDK 35, minSdk 24, JDK 17 + java.time desugaring, Room, Hilt, Compose
│   ├── proguard-rules.pro           # R8/ProGuard keep rules
│   └── src/
│       ├── main/
│       │   ├── assets/publisher_registry.json
│       │   ├── res/                 # Bangla & English strings, adaptive launcher icon, HTTPS config
│       │   └── java/bd/nib/songbadpatro/
│       │       ├── domain/          # Cutoff config, models, repository contracts, classifier, date & dedup use cases
│       │       ├── data/            # Room DB/DAOs/Migrations, DataStore, RSS/JSON-LD parsers, robots.txt, workers
│       │       ├── di/              # Hilt modules
│       │       └── presentation/    # Bangla M3 newspaper theme, Navigation, ViewModels & Compose screens
│       ├── test/                    # Unit, Room DB + Migration, MockWebServer, and Robolectric Compose UI tests
│       └── androidTest/             # Instrumented Android tests
├── PRIVACY_AND_CONTENT_POLICY.md
└── README.md
```

---

## 5. Database Design & Article Classification
### Room Entities (`MyNewsDatabase`, v2 with `MIGRATION_1_2`)
- `articles` (`ArticleEntity`): Indexed on `canonicalUrl` (unique), `originalPublicationDate`, `category`, `publisherId`, `authorId`, `intervieweeId`, `verificationStatus`, and `normalizedTitle`.
- `publishers` (`PublisherEntity`) & `feed_sources` (`FeedSourceEntity`): Registry state, verification status, collection method, health status, and sync error diagnostics.
- `authors` (`AuthorEntity`): Disambiguated contributor & interviewee directory (`AUTHOR`, `INTERVIEWEE`, `BOTH`).
- `bookmarks` (`BookmarkEntity`) & `reading_history` (`ReadingHistoryEntity`): Locally persisted user library surviving cache clears and restarts.
- `sync_logs` (`SyncLogEntity`), `keyword_alerts` (`KeywordAlertEntity`), and `notified_articles` (`NotifiedArticleEntity`).

### Rule-Based Editorial Classifier (`ClassifyEditorialContentUseCase`)
- Evaluates URL segments, RSS categories, JSON-LD `articleSection`/tags, author credentials/bylines, headline markers, and Q&A body patterns.
- **Confidence Tiers:**
  - `HIGH (>= 0.75)` → `PUBLISHED` (automatically appears in feeds).
  - `MEDIUM (0.45 .. 0.74)` → `PENDING_VERIFICATION` (held in administrative review queue).
  - `LOW (< 0.45)` → Rejected immediately (excludes breaking news, crime/accident reports, sports scores, desk/correspondent reports, press releases, and ads).

---

## 6. Build, Testing & APK Generation Instructions

### Local CLI / Android Studio Commands
```powershell
# Windows PowerShell (inside my-news directory)
.\gradlew.bat lintDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```
Debug APK output path: `app/build/outputs/apk/debug/app-debug.apk`

---

## 7. Windows-Friendly GitHub Website Instructions (No Terminal Required)
If you work on Windows using only your web browser or GitHub Desktop:
1. **Create Repository:** Sign in to [https://github.com](https://github.com), click **+ → New repository**, name it `my-news`, and click **Create repository**.
2. **Upload Files:**
   - **GitHub Desktop (Recommended):** Open GitHub Desktop → **File → Add local repository** → choose `d:\Apps by antigravity\my-news` → click **Publish repository**.
   - **GitHub Web Browser:** Enable **View → Show → Hidden items** in Windows File Explorer so `.github` is visible, then drag all files/folders inside `my-news` onto your GitHub repository upload page and click **Commit changes**.
3. **Download Built APK:**
   - Open the **Actions** tab on GitHub and click the **Android CI - My News (আমার খবর)** workflow run.
   - Scroll to **Artifacts** and download **MyNews-debug-apk**.
   - Right-click the downloaded `.zip` in Windows Explorer → **Extract All…** → transfer `app-debug.apk` to your Android phone and tap to install.

### Release Signing via GitHub Secrets
To trigger `.github/workflows/release.yml`, add these repository secrets under **Settings → Secrets and variables → Actions** (never commit `.jks` files):
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

---

## 8. Troubleshooting & Known Limitations
- **HTTP 403 / Cloudflare WAF on Certain Publishers:** Portals such as *Kaler Kantho*, *Jugantor*, and *Bangladesh Pratidin* enforce WAF blocks against non-browser clients. They are isolated and disabled by default so healthy publishers synchronize without interruption.
- **Summary-Only Display in Reader Mode:** In strict compliance with copyright law, `robots.txt`, `noarchive` meta tags, and publisher paywalls, *My News* never scrapes paywalled text or republishes complete copyrighted works; use the **মূল ওয়েবসাইটে সম্পূর্ণ লেখাটি পড়ুন** button to read the full article on the publisher's site.
- **Future Recommendations:** Add optional server-side REST synchronization for centralized source registry updates and cross-device bookmark backup.

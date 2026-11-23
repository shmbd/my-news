# Privacy, Copyright & Ethical Content Collection Policy — My News (আমার খবর)

## 1. Privacy Policy (গোপনীয়তা নীতিমালা)
- **Zero Trackers & Zero Ads:** *My News (আমার খবর)* contains no advertising SDKs, no third-party analytics trackers, no behavioral profiling, and no telemetry collection.
- **On-Device Storage Only:** Bookmarks (`bookmarks`), reading history (`reading_history`), preferred columnists, keyword alert rules, and display preferences are stored exclusively on the user's device via Android Room and DataStore Preferences.
- **Minimal Permissions:**
  - `INTERNET` & `ACCESS_NETWORK_STATE`: Used solely to synchronize verified public editorial RSS/Atom feeds and permitted publisher opinion sections over HTTPS.
  - `POST_NOTIFICATIONS` (optional, Android 13+): Requested only if the user explicitly turns on editorial or keyword/author alerts in Settings.

## 2. Content Scope & Strict Cutoff Policy (কনটেন্ট পরিধি ও তারিখ সীমা)
- **Exclusive Editorial Scope:** Only four categories of intellectual journalism from Bangladeshi newspapers and digital publishers are eligible:
  1. **Columns (`কলাম`)**
  2. **Official Editorials (`সম্পাদকীয়`)**
  3. **Opinions & Commentary (`মতামত`)**
  4. **Interviews (`সাক্ষাৎকার`)**
- **Strict Exclusion of General News:** Routine news reports, breaking news, crime/accident reports, sports match reports, entertainment news, press releases, classifieds, and sponsored/advertorial posts are excluded by rule-based classification before storage.
- **Strict Publication Date Cutoff (`2026-11-23`, `Asia/Dhaka`):**
  - Only articles originally published on or after **23 November 2026 (`2026-11-23T00:00:00+06:00`)** are eligible.
  - Older articles updated after 23 November 2026 are rejected unless explicitly designated by the publisher as a newly published edition.
  - Articles with missing or unverified original publication dates are routed to a non-public **Pending Verification Queue** (`PENDING_DATE_VERIFICATION`) and never auto-published.

## 3. Copyright, Attribution & Ethical Collection Policy (কপিরাইট ও নৈতিক সংগ্রহ নীতিমালা)
- **Respect for Publisher Access Controls:**
  - `RobotsTxtPolicyChecker` parses and enforces `robots.txt` (`Allow` / `Disallow`) rules before fetching section or article HTML pages.
  - `FeedNetworkClient` enforces HTTPS and per-host polite request delays (`350ms` minimum spacing) and never attempts to bypass paywalls, authentication (`HTTP 401/402/403`), rate limits (`HTTP 429`), `noarchive`/`nosnippet` meta tags, or anti-bot gates.
- **Copyright-Safe Preview & Direct Publisher Attribution:**
  - *My News (আমার খবর)* operates as a content discovery and indexing platform, **not** an unauthorized full-text republication service.
  - Reader Mode displays headlines, full author/interviewee/interviewer attribution, publication timestamps, publisher summaries, and at most a concise permitted preview excerpt (when allowed by `robots` meta and free of paywalls), accompanied by a prominent **Read Full Article on Original Publisher** button linking directly to the canonical publisher URL.

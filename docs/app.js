(() => {
  "use strict";

  const CUTOFF_EPOCH_MS = Date.parse("2026-11-23T00:00:00+06:00");
  const BOOKMARKS_KEY = "my_news_bd_bookmarks_v1";
  const THEME_KEY = "my_news_bd_theme_v1";
  const LANG_KEY = "my_news_bd_lang_v1";

  const CATEGORY_META = {
    COLUMN: { bn: "কলাম", en: "Columns" },
    EDITORIAL: { bn: "সম্পাদকীয়", en: "Editorials" },
    OPINION: { bn: "মতামত", en: "Opinions" },
    INTERVIEW: { bn: "সাক্ষাৎকার", en: "Interviews" }
  };

  const BANGLA_DIGITS = ["০", "১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯"];
  const BANGLA_MONTHS = [
    "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
    "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
  ];

  const state = {
    lang: localStorage.getItem(LANG_KEY) === "en" ? "en" : "bn",
    theme: localStorage.getItem(THEME_KEY) || "light",
    readerScale: 1.0,
    activeView: "feed", // 'feed' | 'authors' | 'saved' | 'publishers'
    selectedCategory: "", // '' | 'COLUMN' | 'EDITORIAL' | 'OPINION' | 'INTERVIEW'
    selectedPublisherId: "",
    selectedContentLang: "",
    selectedAuthorId: "",
    selectedAuthorName: "",
    sortOrder: "NEWEST",
    searchQuery: "",
    authorQuery: "",
    authorRole: "",
    articles: [],
    publishers: [],
    authors: [],
    meta: null,
    bookmarks: new Set(JSON.parse(localStorage.getItem(BOOKMARKS_KEY) || "[]")),
    modalArticleId: null
  };

  const el = (id) => document.getElementById(id);

  function toBanglaDigits(val) {
    return String(val).replace(/\d/g, (d) => BANGLA_DIGITS[Number(d)]);
  }

  function fmtCount(n) {
    return state.lang === "en" ? String(n) : toBanglaDigits(n);
  }

  function fmtDhakaDate(epochMs) {
    if (!epochMs || epochMs < CUTOFF_EPOCH_MS) return "";
    const d = new Date(epochMs);
    const parts = new Intl.DateTimeFormat("en-GB", {
      timeZone: "Asia/Dhaka",
      year: "numeric",
      month: "numeric",
      day: "numeric"
    }).formatToParts(d);
    const day = Number(parts.find((p) => p.type === "day")?.value || 1);
    const month = Number(parts.find((p) => p.type === "month")?.value || 1);
    const year = Number(parts.find((p) => p.type === "year")?.value || 2026);
    if (state.lang === "en") {
      return new Intl.DateTimeFormat("en-GB", {
        timeZone: "Asia/Dhaka",
        day: "2-digit",
        month: "short",
        year: "numeric"
      }).format(d);
    }
    return `${toBanglaDigits(day)} ${BANGLA_MONTHS[month - 1]} ${toBanglaDigits(year)}`;
  }

  function escapeHtml(str) {
    return String(str || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function catLabel(cat, isOfficial) {
    if (cat === "EDITORIAL" && isOfficial) {
      return state.lang === "en" ? "Official Editorial" : "প্রাতিষ্ঠানিক সম্পাদকীয়";
    }
    return CATEGORY_META[cat]?.[state.lang] || cat;
  }

  function pubName(item) {
    return state.lang === "en"
      ? item.publisherNameEn || item.nameEn || item.publisherNameBn
      : item.publisherNameBn || item.nameBn || item.publisherNameEn;
  }

  function contributorText(art) {
    if (art.category === "EDITORIAL" && (art.isOfficialEditorial || !art.authorName)) {
      return state.lang === "en"
        ? `Editorial Board • ${pubName(art)}`
        : `সম্পাদকীয় পর্ষদ • ${pubName(art)}`;
    }
    if (art.category === "INTERVIEW" && art.intervieweeName) {
      return state.lang === "en"
        ? `Interviewee: ${art.intervieweeName}`
        : `সাক্ষাৎকারদাতা: ${art.intervieweeName}`;
    }
    if (art.authorName) {
      return art.authorAffiliation ? `${art.authorName} (${art.authorAffiliation})` : art.authorName;
    }
    return "";
  }

  function saveBookmarks() {
    localStorage.setItem(BOOKMARKS_KEY, JSON.stringify([...state.bookmarks]));
    el("savedBadge").textContent = fmtCount(state.bookmarks.size);
  }

  function applyTheme() {
    document.documentElement.setAttribute("data-theme", state.theme);
    localStorage.setItem(THEME_KEY, state.theme);
    const labels = {
      light: state.lang === "en" ? "Light" : "হালকা",
      sepia: state.lang === "en" ? "Sepia" : "সেপিয়া",
      dark: state.lang === "en" ? "Dark" : "ডার্ক"
    };
    el("themeBtn").textContent = labels[state.theme] || labels.light;
  }

  function applyLanguageStaticLabels() {
    const en = state.lang === "en";
    document.documentElement.lang = en ? "en" : "bn";
    el("langBtn").textContent = en ? "বাংলা" : "EN";
    el("brandSubText").textContent = en
      ? "Columns • Editorials • Opinions • Interviews"
      : "কলাম • সম্পাদকীয় • মতামত • সাক্ষাৎকার";
    el("navFeed").textContent = en ? "Home" : "হোম";
    el("navColumn").textContent = en ? "Columns" : "কলাম";
    el("navEditorial").textContent = en ? "Editorials" : "সম্পাদকীয়";
    el("navOpinion").textContent = en ? "Opinions" : "মতামত";
    el("navInterview").textContent = en ? "Interviews" : "সাক্ষাৎকার";
    el("navAuthors").textContent = en ? "Authors" : "লেখক";
    el("navPublishers").textContent = en ? "Sources" : "উৎসসমূহ";
    el("apkHeaderBtn").textContent = en ? "Android APK" : "অ্যাপ ডাউনলোড";

    el("transparencyText").innerHTML = en
      ? "<strong>Editorial Scope &amp; Cutoff:</strong> Ordinary news and breaking reports are excluded. Only verified columns, official editorials, opinions, and interviews published on or after <strong>23 November 2026 (Asia/Dhaka)</strong> are indexed."
      : "<strong>সম্পাদকীয় পরিধি ও তারিখ সীমা:</strong> এই প্ল্যাটফর্মে সাধারণ সংবাদ বা ব্রেকিং নিউজ সংগৃহীত হয় না। কেবল <strong>২৩ নভেম্বর ২০২৬ (Asia/Dhaka)</strong> বা তার পরে প্রকাশিত বাংলাদেশি সংবাদপত্রের যাচাইকৃত কলাম, প্রাতিষ্ঠানিক সম্পাদকীয়, মতামত ও সাক্ষাৎকার প্রদর্শিত হচ্ছে।";

    el("searchInput").placeholder = en
      ? "Search headline, columnist, interviewee, or topic..."
      : "শিরোনাম, লেখক, সাক্ষাৎকারদাতা বা বিষয় লিখুন...";

    el("appEyebrow").textContent = en ? "Native Android App" : "অ্যান্ড্রয়েড অ্যাপ";
    el("appBoxTitle").textContent = en ? "My News (আমার খবর) APK" : "আমার খবর (My News) অ্যাপ";
    el("appBoxDesc").textContent = en
      ? "Install the native Android app for offline Reader Mode (Light/Sepia/Dark), bookmarks, and columnist/keyword alerts."
      : "অফলাইন পাঠক মোড (হালকা/সেপিয়া/ডার্ক), বুকমার্ক এবং পছন্দের কলামিস্ট ও কীওয়ার্ড অ্যালার্ট পেতে আপনার অ্যান্ড্রয়েড ফোনে ইনস্টল করুন।";
    el("appDownloadCta").textContent = en ? "Download APK on GitHub ↗" : "GitHub থেকে APK ডাউনলোড ↗";
    el("catSummaryTitle").textContent = en ? "By Editorial Category" : "বিভাগওয়ারী সংকলন";
    el("topAuthorsTitle").textContent = en ? "Active Columnists" : "সক্রিয় কলামিস্ট ও লেখক";
    el("authorsPageTitle").textContent = en ? "Columnists & Interviewees" : "কলামিস্ট, লেখক ও সাক্ষাৎকারদাতা ডিরেক্টরি";
    el("savedPageTitle").textContent = en ? "Saved Bookmarks" : "সংরক্ষিত লেখা (বুকমার্ক)";
    el("publishersPageTitle").textContent = en ? "Verified Bangladeshi Publishers" : "বাংলাদেশি সংবাদপত্র ও উৎস যাচাই তালিকা";
    el("modalOriginalBtn").textContent = en
      ? "Read Full Article on Publisher Website ↗"
      : "মূল প্রকাশকের ওয়েবসাইটে সম্পূর্ণ লেখাটি পড়ুন ↗";
    el("modalCopyrightText").textContent = en
      ? "In compliance with copyright and publisher policies, only headline, attribution, and summary metadata are shown here. Visit the original publisher to read the complete work."
      : "কপিরাইট ও নৈতিক সংগ্রহ নীতিমালা অনুসারে এখানে শুধুমাত্র শিরোনাম, লেখক পরিচিতি ও সংক্ষিপ্ত বিবরণ প্রদর্শিত হচ্ছে। সম্পূর্ণ লেখাটি পড়তে মূল সংবাদপত্রের ওয়েবসাইটে যান।";

    saveBookmarks();
    applyTheme();
  }

  function populatePublisherDropdown() {
    const sel = el("publisherFilter");
    const current = state.selectedPublisherId;
    const allLabel = state.lang === "en" ? "All Publishers" : "সব পত্রিকা";
    const enabledPubs = state.publishers.filter((p) => p.enabled);
    sel.innerHTML = `<option value="">${allLabel}</option>` +
      enabledPubs.map((p) => `<option value="${escapeHtml(p.publisherId)}">${escapeHtml(pubName(p))}</option>`).join("");
    sel.value = current;
  }

  function getFilteredArticles() {
    const q = state.searchQuery.trim().toLowerCase();
    const list = state.articles.filter((a) => {
      // Strict cutoff enforcement on frontend display & search
      if (!a.originalPublicationDate || a.originalPublicationDate < CUTOFF_EPOCH_MS) return false;
      if (a.verificationStatus && a.verificationStatus !== "PUBLISHED") return false;
      if (state.selectedCategory && a.category !== state.selectedCategory) return false;
      if (state.selectedPublisherId && a.publisherId !== state.selectedPublisherId) return false;
      if (state.selectedContentLang && a.language !== state.selectedContentLang) return false;
      if (state.selectedAuthorId && a.authorId !== state.selectedAuthorId && a.intervieweeId !== state.selectedAuthorId) {
        return false;
      }
      if (q) {
        const hay = [
          a.title,
          a.description,
          a.authorName,
          a.intervieweeName,
          a.publisherNameBn,
          a.publisherNameEn,
          ...(a.tags || [])
        ].join(" ").toLowerCase();
        if (!hay.includes(q)) return false;
      }
      return true;
    });

    if (state.sortOrder === "OLDEST") {
      list.sort((a, b) => a.originalPublicationDate - b.originalPublicationDate);
    } else if (state.sortOrder === "PUBLISHER") {
      list.sort((a, b) => pubName(a).localeCompare(pubName(b)) || (b.originalPublicationDate - a.originalPublicationDate));
    } else if (state.sortOrder === "AUTHOR") {
      list.sort((a, b) => (a.authorName || "zzz").localeCompare(b.authorName || "zzz") || (b.originalPublicationDate - a.originalPublicationDate));
    } else {
      list.sort((a, b) => b.originalPublicationDate - a.originalPublicationDate);
    }
    return list;
  }

  function renderCardHtml(art) {
    const saved = state.bookmarks.has(art.articleId);
    const contrib = contributorText(art);
    const personId = art.intervieweeId || art.authorId || "";
    const origText = state.lang === "en" ? "Original ↗" : "মূল সূত্র ↗";

    return `
      <article class="editorial-card">
        <div>
          <div class="badges-row">
            <div class="badges-left">
              <span class="cat-badge ${escapeHtml(art.category)}">${escapeHtml(catLabel(art.category, art.isOfficialEditorial))}</span>
              <span class="pub-name">${escapeHtml(pubName(art))}</span>
            </div>
            <button type="button" class="bookmark-icon-btn ${saved ? "saved" : ""}" data-bookmark="${escapeHtml(art.articleId)}" title="Bookmark">
              ${saved ? "★" : "☆"}
            </button>
          </div>
          <h3 class="card-title" data-open="${escapeHtml(art.articleId)}">${escapeHtml(art.title)}</h3>
          ${contrib ? `<div class="card-byline" ${personId ? `data-author="${escapeHtml(personId)}" data-author-name="${escapeHtml(art.intervieweeName || art.authorName || "")}"` : ""}>${escapeHtml(contrib)}</div>` : ""}
          ${art.description ? `<p class="card-excerpt">${escapeHtml(art.description)}</p>` : ""}
        </div>
        <div class="card-bottom">
          <span>${escapeHtml(fmtDhakaDate(art.originalPublicationDate))}</span>
          <a href="${escapeHtml(art.originalArticleUrl)}" target="_blank" rel="noopener noreferrer">${origText}</a>
        </div>
      </article>
    `;
  }

  function renderTrendingTags() {
    const counts = new Map();
    state.articles.forEach((a) => {
      (a.tags || []).forEach((t) => {
        const clean = t.trim();
        if (clean.length >= 2 && clean.length <= 32) {
          counts.set(clean, (counts.get(clean) || 0) + 1);
        }
      });
    });
    const topTags = [...counts.entries()]
      .sort((a, b) => b[1] - a[1])
      .slice(0, 8)
      .map(([t]) => t);

    const row = el("trendingTagsRow");
    if (!topTags.length) {
      row.innerHTML = "";
      return;
    }
    const label = state.lang === "en" ? "Topics:" : "আলোচিত বিষয়:";
    row.innerHTML = `<span class="tags-label">${label}</span>` +
      topTags.map((t) => `<button type="button" class="topic-tag" data-tag="${escapeHtml(t)}">${escapeHtml(t)}</button>`).join("");
  }

  function renderSidebar() {
    const counts = { COLUMN: 0, EDITORIAL: 0, OPINION: 0, INTERVIEW: 0 };
    state.articles.forEach((a) => {
      if (a.originalPublicationDate >= CUTOFF_EPOCH_MS && counts[a.category] !== undefined) {
        counts[a.category]++;
      }
    });
    el("catStatsList").innerHTML = Object.keys(CATEGORY_META).map((cat) => `
      <li data-cat-jump="${cat}">
        <span>${escapeHtml(CATEGORY_META[cat][state.lang])}</span>
        <strong>${fmtCount(counts[cat])}</strong>
      </li>
    `).join("");

    const topAuthors = state.authors.slice(0, 6);
    el("sidebarAuthorsList").innerHTML = topAuthors.map((au) => `
      <div class="sidebar-author-row" data-author="${escapeHtml(au.authorId)}" data-author-name="${escapeHtml(au.name)}">
        <span>${escapeHtml(au.name)}</span>
        <span class="count-meta">${fmtCount(au.articleCount)}</span>
      </div>
    `).join("");
  }

  function renderFeedView() {
    const filtered = getFilteredArticles();
    const isUnfilteredHome =
      !state.selectedCategory &&
      !state.selectedPublisherId &&
      !state.selectedContentLang &&
      !state.selectedAuthorId &&
      !state.searchQuery.trim() &&
      state.sortOrder === "NEWEST";

    // Author filter banner
    if (state.selectedAuthorId) {
      el("authorActiveFilter").classList.remove("hidden");
      el("authorActiveName").textContent = state.lang === "en"
        ? `Showing articles by: ${state.selectedAuthorName}`
        : `নির্বাচিত লেখক: ${state.selectedAuthorName}`;
    } else {
      el("authorActiveFilter").classList.add("hidden");
    }

    // Featured Hero
    const heroWrap = el("featuredContainer");
    if (isUnfilteredHome && filtered.length > 0) {
      const hero = filtered.find((a) => a.thumbnailUrl) || filtered[0];
      const contrib = contributorText(hero);
      heroWrap.innerHTML = `
        <article class="hero-article" data-open="${escapeHtml(hero.articleId)}">
          <div>
            <div class="badges-left">
              <span class="cat-badge ${escapeHtml(hero.category)}">${escapeHtml(catLabel(hero.category, hero.isOfficialEditorial))}</span>
              <span class="pub-name">${escapeHtml(pubName(hero))} • ${escapeHtml(fmtDhakaDate(hero.originalPublicationDate))}</span>
            </div>
            <h1>${escapeHtml(hero.title)}</h1>
            ${contrib ? `<div class="card-byline">${escapeHtml(contrib)}</div>` : ""}
            ${hero.description ? `<p class="hero-desc">${escapeHtml(hero.description)}</p>` : ""}
          </div>
          ${
            hero.thumbnailUrl
              ? `<img class="hero-img" src="${escapeHtml(hero.thumbnailUrl)}" alt="${escapeHtml(hero.title)}" loading="lazy" />`
              : `<div class="hero-placeholder"><span>${escapeHtml(pubName(hero))}</span><strong>${escapeHtml(catLabel(hero.category, hero.isOfficialEditorial))}</strong></div>`
          }
        </article>
      `;
      heroWrap.classList.remove("hidden");
    } else {
      heroWrap.innerHTML = "";
      heroWrap.classList.add("hidden");
    }

    const headingText = state.selectedCategory
      ? CATEGORY_META[state.selectedCategory][state.lang]
      : (state.lang === "en" ? "Recent Columns, Editorials & Interviews" : "সাম্প্রতিক কলাম, সম্পাদকীয় ও মতামত");
    el("streamHeading").textContent = headingText;
    el("streamCount").textContent = state.lang === "en"
      ? `${filtered.length} articles`
      : `${fmtCount(filtered.length)}টি লেখা`;

    const streamItems = isUnfilteredHome && filtered.length > 1 ? filtered.slice(1) : filtered;
    const listEl = el("articlesList");
    if (!streamItems.length) {
      listEl.innerHTML = `<div class="empty-state">${
        state.lang === "en"
          ? "No verified editorial articles match your current filter (cutoff: 2026-11-23)."
          : "নির্বাচিত ফিল্টারে ২৩ নভেম্বর ২০২৬ বা তার পরে প্রকাশিত কোনো লেখা পাওয়া যায়নি।"
      }</div>`;
    } else {
      listEl.innerHTML = streamItems.map(renderCardHtml).join("");
    }
  }

  function renderAuthorsView() {
    const q = state.authorQuery.trim().toLowerCase();
    const list = state.authors.filter((au) => {
      if (state.authorRole && au.role !== state.authorRole) return false;
      if (q) {
        const hay = `${au.name} ${au.affiliation || ""} ${au.publisherNameBn || ""} ${au.publisherNameEn || ""}`.toLowerCase();
        if (!hay.includes(q)) return false;
      }
      return true;
    });
    el("authorsCountMeta").textContent = state.lang === "en"
      ? `${list.length} contributors`
      : `${fmtCount(list.length)} জন`;

    const grid = el("authorsDirectoryGrid");
    if (!list.length) {
      grid.innerHTML = `<div class="empty-state">${state.lang === "en" ? "No matching contributors." : "কোনো লেখক পাওয়া যায়নি।"}</div>`;
      return;
    }
    grid.innerHTML = list.map((au) => `
      <div class="dir-card clickable" data-author="${escapeHtml(au.authorId)}" data-author-name="${escapeHtml(au.name)}">
        <strong>${escapeHtml(au.name)}</strong>
        <div class="count-meta">${escapeHtml(state.lang === "en" ? (au.publisherNameEn || "") : (au.publisherNameBn || ""))}${au.affiliation ? ` • ${escapeHtml(au.affiliation)}` : ""}</div>
        <div style="margin-top:8px;font-size:0.82rem;color:var(--teal);font-weight:700;">
          ${state.lang === "en" ? `${au.articleCount} published` : `${fmtCount(au.articleCount)}টি প্রকাশিত লেখা →`}
        </div>
      </div>
    `).join("");
  }

  function renderSavedView() {
    const savedArts = state.articles.filter(
      (a) => state.bookmarks.has(a.articleId) && a.originalPublicationDate >= CUTOFF_EPOCH_MS
    );
    el("savedCountMeta").textContent = fmtCount(savedArts.length);
    const listEl = el("savedList");
    if (!savedArts.length) {
      listEl.innerHTML = `<div class="empty-state">${
        state.lang === "en"
          ? "No bookmarked articles yet. Click ☆ on any article card to save it here."
          : "এখনও কোনো লেখা সংরক্ষিত নেই। যেকোনো কার্ডের ☆ চিহ্নে ক্লিক করে বুকমার্ক করুন।"
      }</div>`;
    } else {
      listEl.innerHTML = savedArts.map(renderCardHtml).join("");
    }
  }

  function renderPublishersView() {
    el("publishersCountMeta").textContent = fmtCount(state.publishers.length);
    el("publishersDirectoryGrid").innerHTML = state.publishers.map((p) => {
      const verified = p.verificationStatus === "VERIFIED" && p.enabled;
      const cats = (p.supportedCategories || []).map((c) => CATEGORY_META[c]?.[state.lang] || c).join(" • ");
      return `
        <div class="dir-card">
          <div class="badges-row">
            <strong>${escapeHtml(pubName(p))}</strong>
            <span class="${verified ? "pill-verified" : "pill-disabled"}">
              ${verified ? (state.lang === "en" ? "Verified" : "যাচাইকৃত") : (state.lang === "en" ? "Disabled" : "নিষ্ক্রিয়")}
            </span>
          </div>
          <div class="count-meta" style="margin-bottom:6px;">${escapeHtml(cats)}</div>
          <div class="count-meta">${state.lang === "en" ? `Collected: ${p.collectedCount || 0}` : `সংগৃহীত লেখা: ${fmtCount(p.collectedCount || 0)}`}</div>
          ${!verified && p.robotsNote ? `<div style="margin-top:6px;font-size:0.78rem;color:var(--crimson);">${escapeHtml(p.robotsNote)}</div>` : ""}
          <div style="margin-top:8px;">
            <a href="${escapeHtml(p.website)}" target="_blank" rel="noopener noreferrer" style="font-size:0.82rem;color:var(--teal);font-weight:600;text-decoration:none;">
              ${escapeHtml(p.website)} ↗
            </a>
          </div>
        </div>
      `;
    }).join("");
  }

  function switchView(viewKey) {
    if (["COLUMN", "EDITORIAL", "OPINION", "INTERVIEW"].includes(viewKey)) {
      state.activeView = "feed";
      state.selectedCategory = viewKey;
    } else {
      state.activeView = viewKey;
      if (viewKey === "feed") state.selectedCategory = "";
    }

    document.querySelectorAll(".nav-link").forEach((btn) => {
      const v = btn.getAttribute("data-view");
      const active =
        (state.activeView === "feed" && state.selectedCategory && v === state.selectedCategory) ||
        (state.activeView === "feed" && !state.selectedCategory && v === "feed") ||
        (state.activeView !== "feed" && v === state.activeView);
      btn.classList.toggle("active", active);
    });

    el("sectionFeed").classList.toggle("hidden", state.activeView !== "feed");
    el("sectionAuthors").classList.toggle("hidden", state.activeView !== "authors");
    el("sectionSaved").classList.toggle("hidden", state.activeView !== "saved");
    el("sectionPublishers").classList.toggle("hidden", state.activeView !== "publishers");

    renderAll();
  }

  function openReaderModal(articleId) {
    const art = state.articles.find((a) => a.articleId === articleId);
    if (!art) return;
    state.modalArticleId = articleId;

    el("modalBadges").innerHTML = `
      <div class="badges-left">
        <span class="cat-badge ${escapeHtml(art.category)}">${escapeHtml(catLabel(art.category, art.isOfficialEditorial))}</span>
        <span class="pub-name">${escapeHtml(pubName(art))}</span>
      </div>
    `;
    el("modalTitle").textContent = art.title;
    const contrib = contributorText(art);
    el("modalMeta").textContent = [contrib, fmtDhakaDate(art.originalPublicationDate)].filter(Boolean).join(" • ");
    el("modalDescription").textContent = art.description || (state.lang === "en" ? "Summary available on the original publisher page." : "বিস্তারিত মূল প্রকাশকের পাতায় উপলব্ধ।");
    el("modalOriginalBtn").href = art.originalArticleUrl;
    updateModalBookmarkLabel();

    const synEl = el("modalSyndicated");
    if (art.syndicatedEditions && art.syndicatedEditions.length > 0) {
      synEl.classList.remove("hidden");
      const label = state.lang === "en" ? "Also syndicated in: " : "অন্যান্য প্রকাশনায় সংস্করণ: ";
      synEl.innerHTML = label + art.syndicatedEditions.map((s) =>
        `<a href="${escapeHtml(s.url)}" target="_blank" rel="noopener noreferrer">${escapeHtml(state.lang === "en" ? s.publisherNameEn : s.publisherNameBn)}</a>`
      ).join(", ");
    } else {
      synEl.classList.add("hidden");
    }

    el("readerModal").classList.remove("hidden");
  }

  function updateModalBookmarkLabel() {
    if (!state.modalArticleId) return;
    const saved = state.bookmarks.has(state.modalArticleId);
    el("modalBookmarkBtn").textContent = saved
      ? (state.lang === "en" ? "★ Saved" : "★ সংরক্ষিত")
      : (state.lang === "en" ? "☆ Save" : "☆ সংরক্ষণ");
  }

  function renderAll() {
    applyLanguageStaticLabels();
    populatePublisherDropdown();
    renderTrendingTags();
    renderSidebar();

    if (state.meta?.lastSyncedIsoDhaka) {
      const syncDate = new Date(state.meta.lastSyncedIsoDhaka);
      el("lastSyncBadge").textContent = state.lang === "en"
        ? `Updated: ${fmtDhakaDate(syncDate.getTime())}`
        : `সর্বশেষ হালনাগাদ: ${fmtDhakaDate(syncDate.getTime())}`;
    }

    if (state.activeView === "feed") renderFeedView();
    else if (state.activeView === "authors") renderAuthorsView();
    else if (state.activeView === "saved") renderSavedView();
    else if (state.activeView === "publishers") renderPublishersView();
  }

  async function init() {
    applyLanguageStaticLabels();

    try {
      const [artRes, pubRes, authRes, metaRes] = await Promise.all([
        fetch("./data/articles.json"),
        fetch("./data/publishers.json"),
        fetch("./data/authors.json"),
        fetch("./data/meta.json")
      ]);
      if (artRes.ok) state.articles = await artRes.json();
      if (pubRes.ok) state.publishers = await pubRes.json();
      if (authRes.ok) state.authors = await authRes.json();
      if (metaRes.ok) state.meta = await metaRes.json();
    } catch (err) {
      console.error("Failed loading static JSON data", err);
    }

    renderAll();

    // Event Delegation
    document.querySelectorAll(".nav-link").forEach((btn) => {
      btn.addEventListener("click", () => switchView(btn.getAttribute("data-view")));
    });

    el("brandHomeLink").addEventListener("click", (e) => {
      e.preventDefault();
      state.selectedAuthorId = "";
      state.selectedCategory = "";
      switchView("feed");
    });

    el("langBtn").addEventListener("click", () => {
      state.lang = state.lang === "en" ? "bn" : "en";
      localStorage.setItem(LANG_KEY, state.lang);
      renderAll();
    });

    el("themeBtn").addEventListener("click", () => {
      const order = ["light", "sepia", "dark"];
      state.theme = order[(order.indexOf(state.theme) + 1) % order.length];
      applyTheme();
    });

    el("searchInput").addEventListener("input", (e) => {
      state.searchQuery = e.target.value;
      renderFeedView();
    });

    el("publisherFilter").addEventListener("change", (e) => {
      state.selectedPublisherId = e.target.value;
      renderFeedView();
    });

    el("langFilter").addEventListener("change", (e) => {
      state.selectedContentLang = e.target.value;
      renderFeedView();
    });

    el("sortFilter").addEventListener("change", (e) => {
      state.sortOrder = e.target.value;
      renderFeedView();
    });

    el("clearAuthorBtn").addEventListener("click", () => {
      state.selectedAuthorId = "";
      state.selectedAuthorName = "";
      renderFeedView();
    });

    el("authorSearchInput").addEventListener("input", (e) => {
      state.authorQuery = e.target.value;
      renderAuthorsView();
    });

    el("authorRoleFilter").addEventListener("change", (e) => {
      state.authorRole = e.target.value;
      renderAuthorsView();
    });

    document.body.addEventListener("click", (e) => {
      const bBtn = e.target.closest("[data-bookmark]");
      if (bBtn) {
        e.stopPropagation();
        const id = bBtn.getAttribute("data-bookmark");
        if (state.bookmarks.has(id)) state.bookmarks.delete(id);
        else state.bookmarks.add(id);
        saveBookmarks();
        renderAll();
        return;
      }

      const authEl = e.target.closest("[data-author]");
      if (authEl) {
        e.stopPropagation();
        state.selectedAuthorId = authEl.getAttribute("data-author");
        state.selectedAuthorName = authEl.getAttribute("data-author-name") || "";
        state.selectedCategory = "";
        switchView("feed");
        window.scrollTo({ top: 0, behavior: "smooth" });
        return;
      }

      const tagBtn = e.target.closest("[data-tag]");
      if (tagBtn) {
        state.searchQuery = tagBtn.getAttribute("data-tag");
        el("searchInput").value = state.searchQuery;
        renderFeedView();
        return;
      }

      const catJump = e.target.closest("[data-cat-jump]");
      if (catJump) {
        switchView(catJump.getAttribute("data-cat-jump"));
        return;
      }

      const openEl = e.target.closest("[data-open]");
      if (openEl) {
        openReaderModal(openEl.getAttribute("data-open"));
      }
    });

    el("modalCloseBtn").addEventListener("click", () => el("readerModal").classList.add("hidden"));
    el("readerModal").addEventListener("click", (e) => {
      if (e.target === el("readerModal")) el("readerModal").classList.add("hidden");
    });
    el("modalBookmarkBtn").addEventListener("click", () => {
      if (!state.modalArticleId) return;
      if (state.bookmarks.has(state.modalArticleId)) state.bookmarks.delete(state.modalArticleId);
      else state.bookmarks.add(state.modalArticleId);
      saveBookmarks();
      updateModalBookmarkLabel();
      renderAll();
    });
    el("fontSmallerBtn").addEventListener("click", () => {
      state.readerScale = Math.max(0.85, state.readerScale - 0.1);
      document.documentElement.style.setProperty("--reader-scale", String(state.readerScale));
    });
    el("fontLargerBtn").addEventListener("click", () => {
      state.readerScale = Math.min(1.45, state.readerScale + 0.1);
      document.documentElement.style.setProperty("--reader-scale", String(state.readerScale));
    });
  }

  document.addEventListener("DOMContentLoaded", init);
})();

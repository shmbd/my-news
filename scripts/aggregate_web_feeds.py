#!/usr/bin/env python3
"""
My News (আমার খবর) — Static Web Editorial Aggregator for GitHub Pages.

Reads ../app/src/main/assets/publisher_registry.json, enforces robots.txt,
applies the strict 2026-11-23 (Asia/Dhaka) publication cutoff, classifies items
exclusively into COLUMN, EDITORIAL, OPINION, or INTERVIEW, deduplicates same-publisher
entries while preserving cross-publisher syndication, and outputs JSON data to ../docs/data/.
"""

from __future__ import annotations

import datetime as dt
import email.utils
import hashlib
import html
import json
import os
import re
import ssl
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any
try:
    from zoneinfo import ZoneInfo
    DHAKA_TZ: dt.tzinfo = ZoneInfo("Asia/Dhaka")
except Exception:
    DHAKA_TZ = dt.timezone(dt.timedelta(hours=6), name="Asia/Dhaka")
DEFAULT_CUTOFF_ISO = "2026-11-23"
DEFAULT_CUTOFF_DT = dt.datetime(2026, 11, 23, 0, 0, 0, tzinfo=DHAKA_TZ)
DEFAULT_CUTOFF_EPOCH_MS = int(DEFAULT_CUTOFF_DT.timestamp() * 1000)

USER_AGENT = "MyNewsBangladeshEditorialWebAggregator/1.0 (+https://github.com/shmbd/my-news)"
SSL_CTX = ssl.create_default_context()

BANGLA_MONTHS = {
    "জানুয়ারি": "January",
    "জানুয়ারি": "January",
    "ফেব্রুয়ারি": "February",
    "ফেব্রুয়ারি": "February",
    "মার্চ": "March",
    "এপ্রিল": "April",
    "মে": "May",
    "জুন": "June",
    "জুলাই": "July",
    "আগস্ট": "August",
    "সেপ্টেম্বর": "September",
    "অক্টোবর": "October",
    "নভেম্বর": "November",
    "ডিসেম্বর": "December",
}

BANGLA_DIGITS = str.maketrans("০১২৩৪৫৬৭৮৯", "0123456789")

TRACKING_PARAMS = {
    "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
    "fbclid", "gclid", "mc_cid", "mc_eid", "ref", "ref_src", "source",
    "share", "s", "si", "igshid", "yclid", "_ga", "amp", "_rsc",
}

HARD_EXCLUSION_SIGNALS = [
    "ব্রেকিং", "ব্রেকিং নিউজ", "সদ্যপ্রাপ্ত", "লাইভ আপডেট", "সরাসরি আপডেট",
    "প্রেস বিজ্ঞপ্তি", "সংবাদ বিজ্ঞপ্তি", "নিয়োগ বিজ্ঞপ্তি", "নিয়োগ বিজ্ঞপ্তি",
    "দরপত্র বিজ্ঞপ্তি", "বিজ্ঞাপন", "স্পন্সরড", "প্রচারণা মূলক",
    "breaking news", "live update", "press release", "sponsored content",
    "advertorial", "branded content", "tender notice", "job circular",
]

REPORTER_BYLINE_SIGNALS = [
    "নিজস্ব প্রতিবেদক", "স্টাফ রিপোর্টার", "ডিজিটাল রিপোর্ট", "অনলাইন ডেস্ক",
    "নিউজ ডেস্ক", "খেলা ডেস্ক", "বিনোদন ডেস্ক", "আন্তর্জাতিক ডেস্ক",
    "প্রতিনিধি", "সংবাদদাতা", "বাসস", "ইউএনবি", "রয়টার্স", "এএফপি",
    "staff correspondent", "desk report", "online desk", "news desk",
    "tribune desk", "star digital report", "tbs report", "unb", "bss",
]

HARD_NEWS_URL_SEGMENTS = [
    "/crime/", "/accident/", "/sports/", "/cricket/", "/football/",
    "/entertainment/", "/showbiz/", "/lifestyle/", "/jobs/", "/chakri/",
    "/classified", "/video/", "/photo/", "/gallery/", "/weather/",
]

CRIME_HEADLINE_SIGNALS = [
    "নিহত", "আহত", "লাশ উদ্ধার", "মরদেহ উদ্ধার", "গ্রেপ্তার", "গ্রেফতার",
    "আটক", "সড়ক দুর্ঘটনা", "সড়ক দুর্ঘটনা", "মুখোমুখি সংঘর্ষে", "বন্দুকযুদ্ধে",
    "মামলা দায়ের", "মামলা দায়ের", "কারাগারে প্রেরণ", "রিমান্ডে",
    "killed in crash", "arrested in", "body recovered", "sent to jail",
]

SPORTS_SIGNALS = [
    "উইকেটে জয়", "উইকেটে জয়", "রানে জয়", "রানে জয়", "গোলে জয়", "গোলে জয়",
    "টসে জিতে", "সেঞ্চুরি করলেন", "পয়েন্ট টেবিল", "পয়েন্ট টেবিল",
    "won by", "wickets", "match highlights", "live score",
]

ROUTINE_POLITICAL_SIGNALS = [
    "সংবাদ সম্মেলনে বললেন", "মানববন্ধনে বক্তারা", "বিক্ষোভ মিছিল", "স্মারকলিপি প্রদান",
    "কর্মসূচি ঘোষণা", "ফেসবুক পোস্টে", "ভেরিফায়েড ফেসবুক", "শোক প্রকাশ",
]

OFFICIAL_EDITORIAL_AUTHORS = {
    "সম্পাদকীয়", "সম্পাদকীয়", "সম্পাদকীয় পর্ষদ", "সম্পাদকীয় পর্ষদ",
    "সম্পাদকমণ্ডলী", "editorial", "editorial board", "editorial desk",
}

COLUMNIST_CREDENTIALS = [
    "অধ্যাপক", "ড.", "ডক্টর", "গবেষক", "অর্থনীতিবিদ", "কলামিস্ট", "বিশ্লেষক",
    "সাবেক রাষ্ট্রদূত", "আইনজীবী", "প্রাবন্ধিক", "শিক্ষাবিদ", "লেখক",
    "professor", "dr.", "economist", "columnist", "researcher", "analyst", "fellow",
]

STOP_WORDS = {
    "ও", "এবং", "যে", "থেকে", "করে", "হয়েছে", "হবে", "জন্য", "নিয়ে",
    "সঙ্গে", "সাথে", "একটি", "এই", "তার", "তাঁর", "করা", "বলে", "বলেন",
    "দ্য", "এ", "এর", "তে", "কে", "কি", "কী", "না",
    "the", "a", "an", "in", "on", "of", "to", "for", "and", "is", "are",
}


def strip_html(raw: str | None) -> str:
    if not raw:
        return ""
    no_tags = re.sub(r"<[^>]+>", " ", raw)
    unescaped = html.unescape(no_tags).replace("\xa0", " ")
    return re.sub(r"\s+", " ", unescaped).strip()


def normalize_bangla_text(text: str | None) -> str:
    if not text:
        return ""
    nfc = unicodedata.normalize("NFC", text)
    nfc = re.sub(r"[\u200B-\u200F\uFEFF]", "", nfc).lower()
    nfc = nfc.replace("য়", "য়").replace("ড়", "ড়").replace("ঢ়", "ঢ়")
    nfc = re.sub(r"[^\w\u0980-\u09FF\s]", " ", nfc)
    return re.sub(r"\s+", " ", nfc).strip()


def title_similarity(norm_a: str, norm_b: str) -> float:
    if not norm_a or not norm_b:
        return 0.0
    if norm_a == norm_b:
        return 1.0
    toks_a = {t for t in norm_a.split() if len(t) >= 2 and t not in STOP_WORDS}
    toks_b = {t for t in norm_b.split() if len(t) >= 2 and t not in STOP_WORDS}
    if not toks_a or not toks_b:
        return 0.0
    return len(toks_a & toks_b) / len(toks_a | toks_b)


def canonicalize_url(raw_url: str | None) -> str:
    if not raw_url or not raw_url.strip():
        return ""
    u = raw_url.strip()
    if u.startswith("//"):
        u = "https:" + u
    elif "://" not in u:
        u = "https://" + u
    parsed = urllib.parse.urlsplit(u)
    host = (parsed.hostname or "").lower()
    if host.startswith("www."):
        host = host[4:]
    if host.startswith("m."):
        host = host[2:]
    if not host:
        return u.lower().rstrip("/")
    scheme = parsed.scheme.lower() if host in ("localhost", "127.0.0.1") else "https"
    path = re.sub(r"/+", "/", parsed.path or "/")
    if len(path) > 1:
        path = path.rstrip("/")
    kept_q = []
    for k, v in urllib.parse.parse_qsl(parsed.query, keep_blank_values=True):
        kl = k.lower()
        if kl in TRACKING_PARAMS or kl.startswith("utm_"):
            continue
        kept_q.append((k, v))
    kept_q.sort()
    query_str = urllib.parse.urlencode(kept_q)
    port_part = f":{parsed.port}" if parsed.port and parsed.port not in (80, 443) else ""
    return f"{scheme}://{host}{port_part}{path}" + (f"?{query_str}" if query_str else "")


def parse_timestamp_dhaka_ms(raw_date: str | None) -> int | None:
    if not raw_date or not raw_date.strip():
        return None
    s = raw_date.strip().translate(BANGLA_DIGITS)
    for bn, en in BANGLA_MONTHS.items():
        if bn in s:
            s = s.replace(bn, en)
    for prefix in ("প্রকাশ:", "প্রকাশ :", "প্রকাশিত:", "Published:", "Published :"):
        if s.startswith(prefix):
            s = s[len(prefix):].strip()
    s = (
        s.replace("GMT+06:00", "+0600")
        .replace("GMT+6", "+0600")
        .replace("UTC+6", "+0600")
    )
    s = re.sub(r"\bBST\b", "+0600", s)
    s = re.sub(r"\s+", " ", s).strip()

    # 1. RFC 2822 / 1123
    try:
        parsed_rfc = email.utils.parsedate_to_datetime(s)
        if parsed_rfc is not None:
            if parsed_rfc.tzinfo is None:
                parsed_rfc = parsed_rfc.replace(tzinfo=DHAKA_TZ)
            if 2000 <= parsed_rfc.astimezone(DHAKA_TZ).year <= 2100:
                return int(parsed_rfc.timestamp() * 1000)
    except Exception:
        pass

    # 2. ISO 8601
    iso_candidate = s.replace("Z", "+00:00")
    try:
        parsed_iso = dt.datetime.fromisoformat(iso_candidate)
        if parsed_iso.tzinfo is None:
            parsed_iso = parsed_iso.replace(tzinfo=DHAKA_TZ)
        if 2000 <= parsed_iso.astimezone(DHAKA_TZ).year <= 2100:
            return int(parsed_iso.timestamp() * 1000)
    except Exception:
        pass

    # 3. Common date / datetime formats
    patterns = [
        "%Y-%m-%d %H:%M:%S %z",
        "%d %b %Y %H:%M:%S %z",
        "%Y-%m-%d %H:%M:%S",
        "%Y-%m-%d %H:%M",
        "%Y-%m-%d",
        "%d %B %Y",
        "%d %b %Y",
        "%B %d, %Y",
    ]
    for pat in patterns:
        try:
            d = dt.datetime.strptime(s, pat)
            if d.tzinfo is None:
                d = d.replace(tzinfo=DHAKA_TZ)
            if 2000 <= d.astimezone(DHAKA_TZ).year <= 2100:
                return int(d.timestamp() * 1000)
        except Exception:
            continue
    return None


def validate_publication_date(
    original_raw: str | None,
    updated_raw: str | None = None,
    cutoff_iso: str = DEFAULT_CUTOFF_ISO,
    explicitly_newly_published: bool = False,
) -> tuple[str, int | None, int | None]:
    """
    Returns (status, original_epoch_ms, updated_epoch_ms) where status is one of:
    'ELIGIBLE', 'PENDING_DATE', 'REJECTED_BEFORE_CUTOFF', 'REJECTED_INVALID'
    """
    cutoff_dt = dt.date.fromisoformat(cutoff_iso)
    cutoff_ms = int(dt.datetime(cutoff_dt.year, cutoff_dt.month, cutoff_dt.day, 0, 0, 0, tzinfo=DHAKA_TZ).timestamp() * 1000)
    updated_ms = parse_timestamp_dhaka_ms(updated_raw) if updated_raw else None

    if not original_raw or not original_raw.strip():
        if explicitly_newly_published and updated_ms is not None:
            if updated_ms >= cutoff_ms:
                return ("ELIGIBLE", updated_ms, updated_ms)
            return ("REJECTED_BEFORE_CUTOFF", updated_ms, updated_ms)
        return ("PENDING_DATE", None, updated_ms)

    orig_ms = parse_timestamp_dhaka_ms(original_raw)
    if orig_ms is None:
        return ("REJECTED_INVALID", None, updated_ms)

    if orig_ms < cutoff_ms:
        if explicitly_newly_published and updated_ms is not None and updated_ms >= cutoff_ms:
            return ("ELIGIBLE", updated_ms, updated_ms)
        return ("REJECTED_BEFORE_CUTOFF", orig_ms, updated_ms)

    return ("ELIGIBLE", orig_ms, updated_ms)


def classify_editorial_item(
    title: str,
    description: str,
    url: str,
    publisher_bn: str,
    publisher_en: str,
    author_name: str | None = None,
    author_affiliation: str | None = None,
    tags: list[str] | None = None,
    has_qa_structure: bool = False,
    source_default_category: str | None = None,
    is_dedicated_editorial: bool = False,
    force_official_editorial: bool = False,
) -> dict[str, Any]:
    norm_title = normalize_bangla_text(title)
    norm_desc = normalize_bangla_text(description)
    lower_url = url.lower()
    lower_author = (author_name or "").strip().lower()
    lower_aff = (author_affiliation or "").strip().lower()
    norm_tags = [normalize_bangla_text(t) for t in (tags or []) if t and t.strip()]
    joined_tags = " | ".join(norm_tags)

    for sig in HARD_EXCLUSION_SIGNALS:
        ns = normalize_bangla_text(sig)
        if ns in norm_title or ns in joined_tags:
            return {"category": None, "isOfficialEditorial": False, "confidence": 0.05, "tier": "LOW", "reason": f"Excluded:{sig}"}

    if any(seg in lower_url for seg in HARD_NEWS_URL_SEGMENTS):
        return {"category": None, "isOfficialEditorial": False, "confidence": 0.05, "tier": "LOW", "reason": "ExcludedURL"}

    has_interview_sig = (
        "/interview" in lower_url
        or "সাক্ষাৎকার" in joined_tags
        or "interview" in joined_tags
        or "সাক্ষাৎকার" in norm_title
        or "মুখোমুখি" in norm_title
        or "interview with" in norm_title
        or (has_qa_structure and "প্রশ্ন" in norm_desc)
    )

    if not has_interview_sig and lower_author:
        for rep in REPORTER_BYLINE_SIGNALS:
            if rep.lower() in lower_author:
                return {"category": None, "isOfficialEditorial": False, "confidence": 0.05, "tier": "LOW", "reason": f"ExcludedReporter:{rep}"}

    if not is_dedicated_editorial:
        if any(normalize_bangla_text(c) in norm_title for c in CRIME_HEADLINE_SIGNALS):
            return {"category": None, "isOfficialEditorial": False, "confidence": 0.05, "tier": "LOW", "reason": "ExcludedCrime"}
        if any(normalize_bangla_text(s) in norm_title for s in SPORTS_SIGNALS):
            return {"category": None, "isOfficialEditorial": False, "confidence": 0.05, "tier": "LOW", "reason": "ExcludedSports"}
        if not has_interview_sig and any(
            normalize_bangla_text(p) in norm_title or normalize_bangla_text(p) in norm_desc
            for p in ROUTINE_POLITICAL_SIGNALS
        ):
            return {"category": None, "isOfficialEditorial": False, "confidence": 0.05, "tier": "LOW", "reason": "ExcludedRoutinePolitics"}

    interview_score = 0.0
    editorial_score = 0.0
    column_score = 0.0
    opinion_score = 0.0
    reasons: list[str] = []

    if any(x in lower_url for x in ("/interview", "/interviews", "/sakkhatkar")):
        interview_score += 0.65
        reasons.append("URL:Interview")
    if any(x in joined_tags for x in ("সাক্ষাৎকার", "মুখোমুখি", "আলাপচারিতা", "কথোপকথন", "interview", "interviews")):
        interview_score += 0.60
        reasons.append("Tag:Interview")
    if any(x in norm_title for x in ("সাক্ষাৎকার", "সাক্ষাৎকারে", "মুখোমুখি", "আলাপচারিতায়", "interview with", "in conversation with")):
        interview_score += 0.45
        reasons.append("Title:Interview")
    if has_qa_structure:
        interview_score += 0.40
        reasons.append("Body:Q&A")
    if source_default_category == "INTERVIEW" and is_dedicated_editorial:
        interview_score += 0.50

    is_editorial_author = (
        not lower_author
        or any(lower_author == ea or ea in lower_author for ea in OFFICIAL_EDITORIAL_AUTHORS)
        or lower_author == publisher_bn.strip().lower()
        or lower_author == publisher_en.strip().lower()
    )
    has_person_author = bool(lower_author) and not is_editorial_author

    if force_official_editorial:
        editorial_score += 0.85
        reasons.append("Source:OfficialEditorial")
    if "/editorial/" in lower_url or lower_url.endswith("/editorial") or "/sampadakiyo" in lower_url:
        if not has_person_author:
            editorial_score += 0.65
            reasons.append("URL:Editorial")
        else:
            column_score += 0.45
            opinion_score += 0.35
    if any(t in ("সম্পাদকীয়", "editorial") for t in norm_tags):
        if not has_person_author:
            editorial_score += 0.65
            reasons.append("Tag:Editorial")
        else:
            column_score += 0.40
    if lower_author in OFFICIAL_EDITORIAL_AUTHORS:
        editorial_score += 0.55

    if any(x in lower_url for x in ("/column", "/columns", "/sub-editorial", "/post-editorial", "/uposampadakiyo")):
        column_score += 0.65
        reasons.append("URL:Column")
    if any(x in joined_tags for x in ("কলাম", "উপসম্পাদকীয়", "column", "columns", "sub editorial")):
        column_score += 0.65
        reasons.append("Tag:Column")
    if has_person_author and (any(c.lower() in lower_author or c.lower() in lower_aff for c in COLUMNIST_CREDENTIALS) or len(lower_aff) >= 6):
        column_score += 0.25
        reasons.append("Author:Credentials")
    if source_default_category == "COLUMN" and is_dedicated_editorial:
        column_score += 0.50

    if any(x in lower_url for x in ("/opinion", "/op-ed", "/oped", "/thoughts", "/views", "/perspectives", "/muktadhara", "/muktomot", "/commentary", "/longform")):
        opinion_score += 0.58
        reasons.append("URL:Opinion")
    if any(x in joined_tags for x in ("মতামত", "মুক্তমত", "মুক্তধারা", "চিন্তা", "দৃষ্টিকোণ", "মত মতান্তর", "অভিমত", "opinion", "op ed", "thoughts", "views", "commentary", "longform")):
        opinion_score += 0.58
        reasons.append("Tag:Opinion")

    if is_dedicated_editorial:
        if source_default_category == "OPINION":
            opinion_score += 0.42
            if has_person_author and lower_aff:
                column_score += 0.20
            reasons.append("Source:DedicatedOpinion")
        elif source_default_category == "EDITORIAL":
            if not has_person_author:
                editorial_score += 0.45
            else:
                column_score += 0.42

    max_ctx = max(interview_score, editorial_score, column_score, opinion_score)
    if max_ctx >= 0.40 and has_person_author:
        if column_score >= opinion_score or lower_aff or any(c.lower() in lower_author for c in COLUMNIST_CREDENTIALS):
            column_score += 0.20
        else:
            opinion_score += 0.20

    ranked = sorted(
        [
            ("INTERVIEW", interview_score),
            ("EDITORIAL", editorial_score),
            ("COLUMN", column_score),
            ("OPINION", opinion_score),
        ],
        key=lambda kv: kv[1],
        reverse=True,
    )
    best_cat, best_score = ranked[0]
    conf = round(min(max(best_score, 0.0), 0.99), 2)
    tier = "HIGH" if conf >= 0.75 else ("MEDIUM" if conf >= 0.45 else "LOW")
    if tier == "LOW":
        return {"category": None, "isOfficialEditorial": False, "confidence": conf, "tier": "LOW", "reason": "LowScore"}

    return {
        "category": best_cat,
        "isOfficialEditorial": best_cat == "EDITORIAL" and not has_person_author,
        "confidence": conf,
        "tier": tier,
        "reason": ", ".join(dict.fromkeys(reasons)) or best_cat,
    }


def resolve_person_id(name: str, affiliation: str | None, publisher_id: str) -> str:
    norm_name = normalize_bangla_text(name)
    norm_aff = normalize_bangla_text(affiliation) if affiliation and len(normalize_bangla_text(affiliation)) >= 5 else None
    key = f"person|{norm_name}|aff:{norm_aff}" if norm_aff else f"person|{norm_name}|pub:{publisher_id}"
    return hashlib.sha256(key.encode("utf-8")).hexdigest()[:20]


def deduplicate_and_syndicate(articles: list[dict[str, Any]]) -> list[dict[str, Any]]:
    by_canonical: dict[str, dict[str, Any]] = {}
    same_pub_window_ms = 36 * 3600 * 1000
    syn_window_ms = 48 * 3600 * 1000

    for cand in articles:
        can_url = cand["canonicalUrl"]
        if can_url in by_canonical:
            existing = by_canonical[can_url]
            if len(cand.get("description", "")) > len(existing.get("description", "")):
                existing["description"] = cand["description"]
            existing["thumbnailUrl"] = existing.get("thumbnailUrl") or cand.get("thumbnailUrl")
            continue

        dup_key = None
        for k, cur in by_canonical.items():
            if cur["publisherId"] == cand["publisherId"]:
                if abs(cur["originalPublicationDate"] - cand["originalPublicationDate"]) <= same_pub_window_ms:
                    if cur["normalizedTitle"] == cand["normalizedTitle"] or title_similarity(cur["normalizedTitle"], cand["normalizedTitle"]) >= 0.85:
                        dup_key = k
                        break
        if dup_key is not None:
            primary = by_canonical[dup_key]
            if len(cand.get("description", "")) > len(primary.get("description", "")):
                primary["description"] = cand["description"]
            primary["thumbnailUrl"] = primary.get("thumbnailUrl") or cand.get("thumbnailUrl")
        else:
            cand.setdefault("syndicatedEditions", [])
            by_canonical[can_url] = cand

    items = list(by_canonical.values())
    for i in range(len(items)):
        for j in range(i + 1, len(items)):
            a, b = items[i], items[j]
            if a["publisherId"] != b["publisherId"]:
                if abs(a["originalPublicationDate"] - b["originalPublicationDate"]) <= syn_window_ms:
                    if a["normalizedTitle"] == b["normalizedTitle"] or title_similarity(a["normalizedTitle"], b["normalizedTitle"]) >= 0.88:
                        if not any(s["publisherId"] == b["publisherId"] for s in a["syndicatedEditions"]):
                            a["syndicatedEditions"].append({
                                "publisherId": b["publisherId"],
                                "publisherNameBn": b["publisherNameBn"],
                                "publisherNameEn": b["publisherNameEn"],
                                "url": b["originalArticleUrl"],
                            })
                        if not any(s["publisherId"] == a["publisherId"] for s in b["syndicatedEditions"]):
                            b["syndicatedEditions"].append({
                                "publisherId": a["publisherId"],
                                "publisherNameBn": a["publisherNameBn"],
                                "publisherNameEn": a["publisherNameEn"],
                                "url": a["originalArticleUrl"],
                            })

    items.sort(key=lambda x: x["originalPublicationDate"], reverse=True)
    return items


class RobotsCache:
    def __init__(self) -> None:
        self._rules: dict[str, tuple[list[str], list[str]]] = {}

    def is_allowed(self, url: str) -> bool:
        parsed = urllib.parse.urlsplit(url)
        if not parsed.hostname:
            return False
        origin = f"{parsed.scheme or 'https'}://{parsed.hostname}"
        if origin not in self._rules:
            self._rules[origin] = self._fetch_robots(origin)
        allowed, disallowed = self._rules[origin]
        path = parsed.path or "/"
        if parsed.query:
            path += "?" + parsed.query
        longest_allow = max((len(p) for p in allowed if path.startswith(p.rstrip("*"))), default=-1)
        longest_disallow = max((len(p) for p in disallowed if path.startswith(p.rstrip("*"))), default=-1)
        if longest_disallow < 0:
            return True
        return longest_allow >= longest_disallow

    def _fetch_robots(self, origin: str) -> tuple[list[str], list[str]]:
        status, body = http_get(f"{origin}/robots.txt", accept="text/plain,*/*;q=0.5", timeout=7)
        if status in (401, 403):
            return ([], ["/"])
        if status != 200 or not body:
            return (["/"], [])
        allowed: list[str] = []
        disallowed: list[str] = []
        wildcard = False
        seen_rule = False
        for raw_line in body.splitlines():
            line = raw_line.split("#", 1)[0].strip()
            if ":" not in line:
                continue
            k, v = [x.strip() for x in line.split(":", 1)]
            kl = k.lower()
            if kl == "user-agent":
                if seen_rule:
                    wildcard = False
                    seen_rule = False
                if v == "*":
                    wildcard = True
            elif kl == "allow":
                seen_rule = True
                if wildcard and v:
                    allowed.append(v.rstrip("$"))
            elif kl == "disallow":
                seen_rule = True
                if wildcard and v:
                    disallowed.append(v.rstrip("$"))
        return (allowed, disallowed)


def http_get(url: str, accept: str = "*/*", timeout: int = 10) -> tuple[int, str]:
    if url.startswith("http://") and not url.startswith(("http://localhost", "http://127.0.0.1")):
        url = "https://" + url[7:]
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": USER_AGENT,
            "Accept": accept,
            "Accept-Language": "bn-BD,bn;q=0.9,en;q=0.8",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=SSL_CTX) as resp:
            raw = resp.read(262144)
            text = raw.decode("utf-8", errors="replace")
            return (resp.status, text)
    except urllib.error.HTTPError as e:
        return (e.code, "")
    except Exception:
        return (-1, "")


def local_tag(tag: str) -> str:
    return tag.split("}", 1)[-1].split(":", 1)[-1].lower()


def parse_rss_or_atom_xml(raw_xml: str) -> list[dict[str, Any]]:
    cleaned = raw_xml.lstrip("\ufeff").strip()
    idx = cleaned.find("<")
    if idx < 0:
        return []
    cleaned = re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f]", "", cleaned[idx:])
    try:
        root = ET.fromstring(cleaned)
    except ET.ParseError:
        return []

    out: list[dict[str, Any]] = []
    for elem in root.iter():
        lt = local_tag(elem.tag)
        if lt == "item":
            title = ""
            link = ""
            guid = ""
            desc = ""
            content_enc = ""
            author = None
            pub_raw = None
            upd_raw = None
            thumb = None
            cats: list[str] = []
            for ch in elem:
                ct = local_tag(ch.tag)
                txt = (ch.text or "").strip()
                if ct == "title" and not title:
                    title = strip_html(txt)
                elif ct == "link" and not link:
                    link = ch.attrib.get("href", "").strip() or txt
                elif ct == "guid" and not guid:
                    guid = txt
                elif ct in ("description", "summary") and not desc:
                    desc = txt
                elif ct == "encoded" and not content_enc:
                    content_enc = txt
                elif ct in ("author", "creator") and not author:
                    author = strip_html(txt) or None
                elif ct in ("pubdate", "date", "published") and not pub_raw:
                    pub_raw = txt or None
                elif ct in ("updated", "modified") and not upd_raw:
                    upd_raw = txt or None
                elif ct == "category":
                    c_val = strip_html(txt)
                    if c_val:
                        cats.append(c_val)
                elif ct in ("enclosure", "content", "thumbnail"):
                    u = ch.attrib.get("url", "").strip()
                    if u.startswith("http") and not thumb:
                        thumb = u
            resolved_link = link or (guid if guid.startswith("http") else "")
            if not title or not resolved_link:
                continue
            if not thumb:
                m_img = re.search(r'<img[^>]+src=["\']([^"\']+)["\']', content_enc or desc, re.I)
                if m_img:
                    thumb = m_img.group(1)
            summary = strip_html(desc or content_enc)
            for suf in ("বিস্তারিত", "Details"):
                if summary.endswith(suf):
                    summary = summary[: -len(suf)].strip()
            out.append({
                "title": title,
                "link": resolved_link,
                "description": summary[:380],
                "author": author,
                "thumbnailUrl": _https_img(thumb),
                "originalPublishedRaw": pub_raw,
                "updatedRaw": upd_raw,
                "tags": cats,
                "needsDetail": not bool(pub_raw),
            })
        elif lt == "entry":
            title = ""
            link = ""
            desc = ""
            author = None
            pub_raw = None
            upd_raw = None
            thumb = None
            cats = []
            for ch in elem:
                ct = local_tag(ch.tag)
                txt = (ch.text or "").strip()
                if ct == "title" and not title:
                    title = strip_html(txt)
                elif ct == "link":
                    rel = ch.attrib.get("rel", "alternate")
                    href = ch.attrib.get("href", "").strip()
                    if rel in ("", "alternate") and href and not link:
                        link = href
                elif ct in ("summary", "content") and not desc:
                    desc = txt
                elif ct == "author":
                    for sub in ch:
                        if local_tag(sub.tag) == "name" and sub.text:
                            author = strip_html(sub.text)
                elif ct == "published" and not pub_raw:
                    pub_raw = txt or None
                elif ct == "updated" and not upd_raw:
                    upd_raw = txt or None
                elif ct == "category":
                    term = ch.attrib.get("label") or ch.attrib.get("term") or txt
                    if term:
                        cats.append(strip_html(term))
            if title and link:
                out.append({
                    "title": title,
                    "link": link,
                    "description": strip_html(desc)[:380],
                    "author": author,
                    "thumbnailUrl": _https_img(thumb),
                    "originalPublishedRaw": pub_raw,
                    "updatedRaw": upd_raw,
                    "tags": cats,
                    "needsDetail": not bool(pub_raw),
                })
    return out


def extract_jsonld_objects(raw_html: str) -> list[dict[str, Any]]:
    objs: list[dict[str, Any]] = []
    for m in re.finditer(r'<script[^>]*type=["\']application/ld\+json["\'][^>]*>(.*?)</script>', raw_html, re.I | re.S):
        payload = m.group(1).strip()
        if not payload:
            continue
        try:
            parsed = json.loads(payload)
            _collect_dicts(parsed, objs)
        except Exception:
            continue
    return objs


def _collect_dicts(node: Any, out: list[dict[str, Any]]) -> None:
    if isinstance(node, dict):
        out.append(node)
        if "@graph" in node:
            _collect_dicts(node["@graph"], out)
    elif isinstance(node, list):
        for item in node:
            _collect_dicts(item, out)


def discover_section_items(raw_html: str, section_url: str, rules: dict[str, Any], max_items: int = 10) -> list[dict[str, Any]]:
    inc_pat = rules.get("articleLinkPattern")
    exc_pat = rules.get("excludeLinkPattern")
    inc_re = re.compile(inc_pat, re.I) if inc_pat else None
    exc_re = re.compile(exc_pat, re.I) if exc_pat else None

    found: dict[str, dict[str, Any]] = {}
    for obj in extract_jsonld_objects(raw_html):
        if "itemListElement" in obj and isinstance(obj["itemListElement"], list):
            for el in obj["itemListElement"]:
                item = el.get("item", el) if isinstance(el, dict) else None
                if not isinstance(item, dict):
                    continue
                u = str(item.get("url") or item.get("@id") or "").strip()
                t = strip_html(str(item.get("headline") or item.get("name") or ""))
                if u.startswith("http") and len(t) >= 8 and _link_ok(u, section_url, inc_re, exc_re):
                    found[u] = {
                        "title": t,
                        "link": u,
                        "description": strip_html(str(item.get("description") or ""))[:360],
                        "author": _json_author_name(item.get("author")),
                        "thumbnailUrl": _https_img(_json_image(item.get("image"))),
                        "originalPublishedRaw": item.get("datePublished"),
                        "updatedRaw": item.get("dateModified"),
                        "tags": [],
                        "needsDetail": not bool(item.get("datePublished")),
                    }

    for m in re.finditer(r'<a\s+[^>]*href=["\']([^"\']+)["\'][^>]*>(.*?)</a>', raw_html, re.I | re.S):
        if len(found) >= max_items:
            break
        href = urllib.parse.urljoin(section_url, html.unescape(m.group(1).strip()))
        if not href.startswith("http") or href in found:
            continue
        if not _link_ok(href, section_url, inc_re, exc_re):
            continue
        anchor_text = strip_html(m.group(2))
        if len(anchor_text) < 12:
            continue
        found[href] = {
            "title": anchor_text[:220],
            "link": href,
            "description": "",
            "author": None,
            "thumbnailUrl": None,
            "originalPublishedRaw": None,
            "updatedRaw": None,
            "tags": [],
            "needsDetail": True,
        }

    return list(found.values())[:max_items]


def extract_page_metadata(raw_html: str) -> dict[str, Any]:
    objs = extract_jsonld_objects(raw_html)
    art_obj = next(
        (
            o for o in objs
            if any(k in str(o.get("@type", "")).lower() for k in ("article", "news", "opinion"))
        ),
        None,
    )
    headline = strip_html(str(art_obj.get("headline") or art_obj.get("name") or "")) if art_obj else ""
    pub_raw = str(art_obj.get("datePublished") or art_obj.get("dateCreated") or "").strip() if art_obj else ""
    upd_raw = str(art_obj.get("dateModified") or "").strip() if art_obj else ""
    desc = strip_html(str(art_obj.get("description") or "")) if art_obj else ""
    author = _json_author_name(art_obj.get("author")) if art_obj else None
    aff = _json_author_job(art_obj.get("author")) if art_obj else None
    img = _json_image(art_obj.get("image")) if art_obj else None
    sec = strip_html(str(art_obj.get("articleSection") or "")) if art_obj else ""

    if not pub_raw:
        m_pub = re.search(
            r'<meta[^>]+(?:property|name|itemprop)=["\'](?:article:published_time|pubdate|publish-date|datePublished)["\'][^>]+content=["\']([^"\']+)["\']',
            raw_html,
            re.I,
        )
        if m_pub:
            pub_raw = m_pub.group(1).strip()
    if not pub_raw:
        m_time = re.search(r'<time[^>]+datetime=["\']([^"\']+)["\']', raw_html, re.I)
        if m_time:
            pub_raw = m_time.group(1).strip()
    if not upd_raw:
        m_upd = re.search(
            r'<meta[^>]+(?:property|name|itemprop)=["\'](?:article:modified_time|og:updated_time|dateModified)["\'][^>]+content=["\']([^"\']+)["\']',
            raw_html,
            re.I,
        )
        if m_upd:
            upd_raw = m_upd.group(1).strip()
    if not desc:
        m_desc = re.search(r'<meta[^>]+(?:property|name)=["\'](?:og:description|description)["\'][^>]+content=["\']([^"\']+)["\']', raw_html, re.I)
        if m_desc:
            desc = strip_html(m_desc.group(1))
    if not img:
        m_img = re.search(r'<meta[^>]+(?:property|name)=["\'](?:og:image|twitter:image)["\'][^>]+content=["\']([^"\']+)["\']', raw_html, re.I)
        if m_img:
            img = m_img.group(1).strip()
    if not headline:
        m_h1 = re.search(r"<h1[^>]*>(.*?)</h1>", raw_html, re.I | re.S)
        if m_h1:
            headline = strip_html(m_h1.group(1))

    has_qa = bool(re.search(r"(?:প্রশ্ন\s*:|উত্তর\s*:)", raw_html))
    interviewee = None
    m_int = re.search(r"(?:সাক্ষাৎকারে|মুখোমুখি|আলাপচারিতায়)\s+([^:|—\-।\n]{4,50})", headline)
    if m_int:
        interviewee = strip_html(m_int.group(1))

    return {
        "headline": headline or None,
        "originalPublishedRaw": pub_raw or None,
        "updatedRaw": upd_raw or None,
        "description": desc[:380] if desc else None,
        "author": author,
        "affiliation": aff,
        "thumbnailUrl": _https_img(img),
        "section": sec or None,
        "hasQa": has_qa,
        "interviewee": interviewee,
    }


def _json_author_name(a: Any) -> str | None:
    if isinstance(a, str):
        return strip_html(a) or None
    if isinstance(a, dict):
        return strip_html(str(a.get("name") or "")) or None
    if isinstance(a, list) and a:
        return _json_author_name(a[0])
    return None


def _json_author_job(a: Any) -> str | None:
    if isinstance(a, dict):
        return strip_html(str(a.get("jobTitle") or a.get("description") or ""))[:120] or None
    if isinstance(a, list) and a:
        return _json_author_job(a[0])
    return None


def _json_image(img: Any) -> str | None:
    if isinstance(img, str):
        return img.strip() or None
    if isinstance(img, dict):
        return str(img.get("url") or img.get("contentUrl") or "").strip() or None
    if isinstance(img, list) and img:
        return _json_image(img[0])
    return None


def _https_img(u: str | None) -> str | None:
    if not u:
        return None
    u = u.strip()
    if u.startswith("//"):
        return "https:" + u
    if u.startswith("http://"):
        return "https://" + u[7:]
    return u if u.startswith("https://") else None


def _link_ok(url: str, section_url: str, inc_re: re.Pattern[str] | None, exc_re: re.Pattern[str] | None) -> bool:
    clean = url.split("#", 1)[0].rstrip("/")
    if clean.lower() == section_url.rstrip("/").lower():
        return False
    if exc_re and exc_re.search(url):
        return False
    if inc_re:
        return bool(inc_re.search(url))
    return any(x in clean.lower() for x in ("/opinion", "/editorial", "/sub-editorial", "/op-ed", "/column", "/interview"))


def sync_all_sources(registry_path: Path, output_dir: Path) -> dict[str, Any]:
    manifest = json.loads(registry_path.read_text(encoding="utf-8"))
    cutoff_iso = manifest.get("defaultCutoffIsoDate", DEFAULT_CUTOFF_ISO)
    robots = RobotsCache()

    publishers_summary: list[dict[str, Any]] = []
    source_jobs: list[tuple[dict[str, Any], dict[str, Any]]] = []

    for pub in manifest.get("publishers", []):
        is_verified = pub.get("verificationStatus") == "VERIFIED" and pub.get("enabled", False)
        publishers_summary.append({
            "publisherId": pub["publisherId"],
            "nameBn": pub["nameBn"],
            "nameEn": pub["nameEn"],
            "website": pub["website"],
            "logoUrl": pub.get("logoUrl"),
            "language": pub.get("language", "BANGLA"),
            "enabled": is_verified,
            "verificationStatus": pub.get("verificationStatus", "REQUIRES_VERIFICATION"),
            "robotsNote": pub.get("robotsNote", ""),
            "supportedCategories": sorted({
                c for src in pub.get("sources", []) for c in src.get("supportedCategories", [])
            }),
            "collectionMethods": sorted({
                src.get("collectionMethod", "RSS_ATOM") for src in pub.get("sources", [])
            }),
            "collectedCount": 0,
        })
        if is_verified:
            for src in pub.get("sources", []):
                if src.get("enabled", False) and src.get("verificationStatus") == "VERIFIED":
                    source_jobs.append((pub, src))

    collected_published: list[dict[str, Any]] = []
    pending_queue: list[dict[str, Any]] = []
    stats_counters = {
        "sourcesAttempted": len(source_jobs),
        "sourcesSucceeded": 0,
        "rejectedPreCutoff": 0,
        "rejectedNonEditorial": 0,
    }

    def process_one_source(job: tuple[dict[str, Any], dict[str, Any]]) -> tuple[str, bool, list[dict[str, Any]], list[dict[str, Any]], int, int]:
        pub, src = job
        method = src.get("collectionMethod", "RSS_ATOM")
        rules = src.get("parsingRules", {})
        endpoint = src.get("feedUrl") if method != "SECTION_HTML_JSONLD" else src.get("sectionUrl")
        if not endpoint:
            return (pub["publisherId"], False, [], [], 0, 0)

        if method == "SECTION_HTML_JSONLD" and not robots.is_allowed(endpoint):
            return (pub["publisherId"], False, [], [], 0, 0)

        accept = "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8" if method == "SECTION_HTML_JSONLD" else "application/rss+xml,application/atom+xml,application/xml,text/xml;q=0.9,*/*;q=0.8"
        st, body = http_get(endpoint, accept=accept, timeout=10)
        if st != 200 or not body:
            return (pub["publisherId"], False, [], [], 0, 0)

        raw_items = (
            discover_section_items(body, endpoint, rules, max_items=8)
            if method == "SECTION_HTML_JSONLD"
            else parse_rss_or_atom_xml(body)
        )

        pub_out: list[dict[str, Any]] = []
        pend_out: list[dict[str, Any]] = []
        rej_old = 0
        rej_non_ed = 0
        detail_fetches = 0

        for raw in raw_items:
            link = raw["link"]
            meta: dict[str, Any] = {}
            if raw.get("needsDetail") and detail_fetches < 4 and robots.is_allowed(link):
                time.sleep(0.25)
                detail_fetches += 1
                p_st, p_html = http_get(link, accept="text/html", timeout=8)
                if p_st == 200 and p_html:
                    meta = extract_page_metadata(p_html)

            title = meta.get("headline") or raw["title"]
            desc = meta.get("description") or raw.get("description") or ""
            author = meta.get("author") or raw.get("author")
            aff = meta.get("affiliation")
            tags = list(dict.fromkeys((raw.get("tags") or []) + ([meta["section"]] if meta.get("section") else [])))
            orig_pub_raw = raw.get("originalPublishedRaw") or meta.get("originalPublishedRaw")
            upd_raw = raw.get("updatedRaw") or meta.get("updatedRaw")

            cls = classify_editorial_item(
                title=title,
                description=desc,
                url=link,
                publisher_bn=pub["nameBn"],
                publisher_en=pub["nameEn"],
                author_name=author,
                author_affiliation=aff,
                tags=tags,
                has_qa_structure=bool(meta.get("hasQa")),
                source_default_category=src.get("defaultCategory"),
                is_dedicated_editorial=bool(rules.get("isDedicatedEditorialFeed")),
                force_official_editorial=bool(rules.get("forceOfficialEditorial")),
            )
            if cls["tier"] == "LOW" or not cls["category"]:
                rej_non_ed += 1
                continue

            d_status, orig_ms, upd_ms = validate_publication_date(orig_pub_raw, upd_raw, cutoff_iso=cutoff_iso)
            if d_status in ("REJECTED_BEFORE_CUTOFF", "REJECTED_INVALID"):
                rej_old += 1
                continue

            can_url = canonicalize_url(link)
            if not can_url:
                continue
            art_id = hashlib.sha256(can_url.encode("utf-8")).hexdigest()[:24]

            clean_author = None
            if author and author.strip().lower() not in OFFICIAL_EDITORIAL_AUTHORS and author.strip().lower() not in (pub["nameBn"].lower(), pub["nameEn"].lower()):
                clean_author = author.strip()

            author_id = resolve_person_id(clean_author, aff, pub["publisherId"]) if clean_author else None
            interviewee = meta.get("interviewee")
            interviewee_id = resolve_person_id(interviewee, None, pub["publisherId"]) if interviewee else None

            record = {
                "articleId": art_id,
                "canonicalUrl": can_url,
                "originalArticleUrl": link,
                "title": title,
                "normalizedTitle": normalize_bangla_text(title),
                "description": desc,
                "authorId": author_id,
                "authorName": clean_author,
                "authorAffiliation": aff,
                "intervieweeId": interviewee_id,
                "intervieweeName": interviewee,
                "publisherId": pub["publisherId"],
                "publisherNameBn": pub["nameBn"],
                "publisherNameEn": pub["nameEn"],
                "publisherLogoUrl": pub.get("logoUrl"),
                "publisherWebsite": pub["website"],
                "category": cls["category"],
                "isOfficialEditorial": cls["isOfficialEditorial"],
                "originalPublicationDate": orig_ms or 0,
                "originalPublicationIsoDhaka": (
                    dt.datetime.fromtimestamp(orig_ms / 1000, tz=DHAKA_TZ).isoformat()
                    if orig_ms else None
                ),
                "updatedDate": upd_ms,
                "thumbnailUrl": meta.get("thumbnailUrl") or raw.get("thumbnailUrl"),
                "language": pub.get("language", "BANGLA"),
                "tags": tags[:8],
                "classificationConfidence": cls["confidence"],
                "classificationReason": cls["reason"],
            }

            if d_status == "PENDING_DATE" or cls["tier"] == "MEDIUM":
                record["verificationStatus"] = "PENDING_DATE_VERIFICATION" if d_status == "PENDING_DATE" else "PENDING_VERIFICATION"
                pend_out.append(record)
            else:
                record["verificationStatus"] = "PUBLISHED"
                pub_out.append(record)

        return (pub["publisherId"], True, pub_out, pend_out, rej_old, rej_non_ed)

    with ThreadPoolExecutor(max_workers=6) as pool:
        futures = [pool.submit(process_one_source, j) for j in source_jobs]
        for fut in as_completed(futures):
            pub_id, ok, p_items, pend_items, r_old, r_non = fut.result()
            if ok:
                stats_counters["sourcesSucceeded"] += 1
            stats_counters["rejectedPreCutoff"] += r_old
            stats_counters["rejectedNonEditorial"] += r_non
            collected_published.extend(p_items)
            pending_queue.extend(pend_items)

    # Merge with existing docs/data/articles.json so previously collected >= 2026-11-23 articles remain available
    output_dir.mkdir(parents=True, exist_ok=True)
    articles_file = output_dir / "articles.json"
    existing_articles: list[dict[str, Any]] = []
    if articles_file.exists():
        try:
            loaded = json.loads(articles_file.read_text(encoding="utf-8"))
            if isinstance(loaded, list):
                existing_articles = [
                    a for a in loaded
                    if a.get("originalPublicationDate", 0) >= DEFAULT_CUTOFF_EPOCH_MS
                    and a.get("verificationStatus") == "PUBLISHED"
                ]
        except Exception:
            existing_articles = []

    reconciled = deduplicate_and_syndicate(existing_articles + collected_published)
    reconciled = [
        a for a in reconciled
        if a.get("originalPublicationDate", 0) >= DEFAULT_CUTOFF_EPOCH_MS
    ][:500]

    counts_by_pub: dict[str, int] = {}
    counts_by_cat = {"COLUMN": 0, "EDITORIAL": 0, "OPINION": 0, "INTERVIEW": 0}
    authors_index: dict[str, dict[str, Any]] = {}

    for art in reconciled:
        pid = art["publisherId"]
        counts_by_pub[pid] = counts_by_pub.get(pid, 0) + 1
        cat = art["category"]
        if cat in counts_by_cat:
            counts_by_cat[cat] += 1
        if art.get("authorId") and art.get("authorName"):
            aid = art["authorId"]
            entry = authors_index.setdefault(aid, {
                "authorId": aid,
                "name": art["authorName"],
                "affiliation": art.get("authorAffiliation"),
                "publisherNameBn": art["publisherNameBn"],
                "publisherNameEn": art["publisherNameEn"],
                "role": "AUTHOR",
                "articleCount": 0,
            })
            entry["articleCount"] += 1
        if art.get("intervieweeId") and art.get("intervieweeName"):
            iid = art["intervieweeId"]
            entry = authors_index.setdefault(iid, {
                "authorId": iid,
                "name": art["intervieweeName"],
                "affiliation": None,
                "publisherNameBn": art["publisherNameBn"],
                "publisherNameEn": art["publisherNameEn"],
                "role": "INTERVIEWEE",
                "articleCount": 0,
            })
            entry["articleCount"] += 1

    for p in publishers_summary:
        p["collectedCount"] = counts_by_pub.get(p["publisherId"], 0)

    now_dhaka = dt.datetime.now(tz=DHAKA_TZ)
    meta_summary = {
        "appName": "My News (আমার খবর)",
        "packageId": "bd.nib.amarkhobor",
        "cutoffIsoDate": cutoff_iso,
        "timezone": "Asia/Dhaka",
        "lastSyncedIsoDhaka": now_dhaka.isoformat(timespec="seconds"),
        "totalPublishedArticles": len(reconciled),
        "categoryCounts": counts_by_cat,
        "authorCount": len(authors_index),
        "syncStats": stats_counters,
    }

    (output_dir / "articles.json").write_text(json.dumps(reconciled, ensure_ascii=False, indent=2), encoding="utf-8")
    (output_dir / "publishers.json").write_text(json.dumps(publishers_summary, ensure_ascii=False, indent=2), encoding="utf-8")
    (output_dir / "authors.json").write_text(
        json.dumps(sorted(authors_index.values(), key=lambda x: (-x["articleCount"], x["name"])), ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    (output_dir / "meta.json").write_text(json.dumps(meta_summary, ensure_ascii=False, indent=2), encoding="utf-8")
    return meta_summary


if __name__ == "__main__":
    repo_root = Path(__file__).resolve().parent.parent
    reg_file = repo_root / "app" / "src" / "main" / "assets" / "publisher_registry.json"
    out_dir = repo_root / "docs" / "data"
    summary = sync_all_sources(reg_file, out_dir)
    print(json.dumps(summary, ensure_ascii=False, indent=2))

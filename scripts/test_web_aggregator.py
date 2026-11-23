import unittest
from aggregate_web_feeds import (
    DEFAULT_CUTOFF_EPOCH_MS,
    classify_editorial_item,
    deduplicate_and_syndicate,
    parse_rss_or_atom_xml,
    resolve_person_id,
    validate_publication_date,
)


class WebAggregatorTests(unittest.TestCase):

    def test_cutoff_boundary_asia_dhaka(self) -> None:
        st_ok, ms_ok, _ = validate_publication_date("2026-11-23T00:00:00+06:00")
        self.assertEqual(st_ok, "ELIGIBLE")
        self.assertEqual(ms_ok, DEFAULT_CUTOFF_EPOCH_MS)

        st_old, _, _ = validate_publication_date("2026-11-22T23:59:59+06:00")
        self.assertEqual(st_old, "REJECTED_BEFORE_CUTOFF")

    def test_updated_old_article_is_rejected(self) -> None:
        st, _, _ = validate_publication_date(
            original_raw="2026-10-01T10:00:00+06:00",
            updated_raw="2026-11-24T12:00:00+06:00",
            explicitly_newly_published=False,
        )
        self.assertEqual(st, "REJECTED_BEFORE_CUTOFF")

    def test_missing_original_date_routes_to_pending(self) -> None:
        st, orig_ms, _ = validate_publication_date(None, "2026-11-24T09:00:00+06:00")
        self.assertEqual(st, "PENDING_DATE")
        self.assertIsNone(orig_ms)

    def test_four_category_classification_and_exclusions(self) -> None:
        ed = classify_editorial_item(
            title="সম্ভাবনা আছে; কিন্তু কর্মসংস্থান কোথায়?",
            description="সম্পাদকীয় বিশ্লেষণ",
            url="https://www.ittefaq.com.bd/editorial/822510",
            publisher_bn="দৈনিক ইত্তেফাক",
            publisher_en="The Daily Ittefaq",
            author_name="সম্পাদকীয়",
            tags=["সম্পাদকীয়"],
            is_dedicated_editorial=True,
            force_official_editorial=True,
        )
        self.assertEqual(ed["category"], "EDITORIAL")
        self.assertTrue(ed["isOfficialEditorial"])
        self.assertEqual(ed["tier"], "HIGH")

        brk = classify_editorial_item(
            title="ব্রেকিং: সড়ক দুর্ঘটনায় নিহত ৩",
            description="পুলিশ ঘটনাস্থলে",
            url="https://example.bd/country/123",
            publisher_bn="প্রথম আলো",
            publisher_en="Prothom Alo",
        )
        self.assertIsNone(brk["category"])
        self.assertEqual(brk["tier"], "LOW")

    def test_same_publisher_dedup_and_cross_publisher_syndication(self) -> None:
        base = {
            "description": "বিশ্লেষণ",
            "authorId": "a1",
            "authorName": "ড. কামাল",
            "authorAffiliation": None,
            "intervieweeId": None,
            "intervieweeName": None,
            "publisherLogoUrl": None,
            "publisherWebsite": "https://ex.bd",
            "category": "OPINION",
            "isOfficialEditorial": False,
            "originalPublicationDate": DEFAULT_CUTOFF_EPOCH_MS + 3600_000,
            "originalPublicationIsoDhaka": "2026-11-23T01:00:00+06:00",
            "updatedDate": None,
            "thumbnailUrl": None,
            "language": "BANGLA",
            "tags": [],
            "classificationConfidence": 0.9,
            "classificationReason": "Test",
            "verificationStatus": "PUBLISHED",
        }
        a1 = {**base, "articleId": "1", "canonicalUrl": "https://star.bd/op/1", "originalArticleUrl": "https://star.bd/op/1", "title": "Reform Plan", "normalizedTitle": "reform plan", "publisherId": "star", "publisherNameBn": "স্টার", "publisherNameEn": "Star"}
        a2_dup = {**base, "articleId": "2", "canonicalUrl": "https://star.bd/rss/1", "originalArticleUrl": "https://star.bd/rss/1", "title": "Reform Plan", "normalizedTitle": "reform plan", "publisherId": "star", "publisherNameBn": "স্টার", "publisherNameEn": "Star"}
        a3_syn = {**base, "articleId": "3", "canonicalUrl": "https://tribune.bd/op/9", "originalArticleUrl": "https://tribune.bd/op/9", "title": "Reform Plan", "normalizedTitle": "reform plan", "publisherId": "tribune", "publisherNameBn": "ট্রিবিউন", "publisherNameEn": "Tribune"}

        out = deduplicate_and_syndicate([a1, a2_dup, a3_syn])
        self.assertEqual(len(out), 2)
        self.assertEqual(len(out[0]["syndicatedEditions"]), 1)

    def test_author_disambiguation(self) -> None:
        p1 = resolve_person_id("মোহাম্মদ আলী", None, "prothom_alo")
        p2 = resolve_person_id("মোহাম্মদ আলী", None, "samakal")
        self.assertNotEqual(p1, p2)

    def test_rss_parser_separates_pubdate_from_updated(self) -> None:
        xml = """<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom"><channel>
        <item><title>কলাম শিরোনাম</title><link>https://ex.bd/1</link>
        <pubDate>Mon, 23 Nov 2026 06:00:00 +0600</pubDate>
        <atom:updated>Tue, 24 Nov 2026 08:00:00 +0600</atom:updated></item></channel></rss>"""
        items = parse_rss_or_atom_xml(xml)
        self.assertEqual(len(items), 1)
        self.assertEqual(items[0]["originalPublishedRaw"], "Mon, 23 Nov 2026 06:00:00 +0600")
        self.assertEqual(items[0]["updatedRaw"], "Tue, 24 Nov 2026 08:00:00 +0600")


if __name__ == "__main__":
    unittest.main()

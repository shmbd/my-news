package bd.nib.songbadpatro

import bd.nib.songbadpatro.data.remote.RobotsTxtPolicyChecker
import bd.nib.songbadpatro.data.remote.RssAtomParser
import bd.nib.songbadpatro.data.remote.SectionAndMetadataExtractor
import bd.nib.songbadpatro.domain.model.ReaderAccessStatus
import bd.nib.songbadpatro.domain.model.SourceParsingRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParserFactory

class RssAndMetadataExtractionTest {

    private val parser = RssAtomParser {
        XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
    }
    private val extractor = SectionAndMetadataExtractor()

    @Test
    fun `parses RSS 2_0 editorial and column items keeping pubDate distinct from updated`() {
        val rss = """
            ﻿<?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:atom="http://www.w3.org/2005/Atom">
              <channel>
                <title>মতামত - দৈনিক ইত্তেফাক</title>
                <item>
                  <title><![CDATA[সম্ভাবনা আছে; কিন্তু কর্মসংস্থান কোথায়?]]></title>
                  <link>https://www.ittefaq.com.bd/822510/editorial-piece</link>
                  <pubDate>Mon, 23 Nov 2026 06:00:00 +0600</pubDate>
                  <atom:updated>Tue, 24 Nov 2026 09:00:00 +0600</atom:updated>
                  <dc:creator>সম্পাদকীয়</dc:creator>
                  <category><![CDATA[মতামত]]></category>
                  <category><![CDATA[সম্পাদকীয়]]></category>
                  <description><![CDATA[<p><img src="http://cdn.ittefaqbd.com/thumb.jpg"/>তরুণদের কর্মসংস্থান নিয়ে সম্পাদকীয় বিশ্লেষণ। বিস্তারিত</p>]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val feed = parser.parse(rss)
        assertEquals("মতামত - দৈনিক ইত্তেফাক", feed.feedTitle)
        assertEquals(1, feed.items.size)
        val item = feed.items.first()
        assertEquals("সম্ভাবনা আছে; কিন্তু কর্মসংস্থান কোথায়?", item.title)
        assertEquals("Mon, 23 Nov 2026 06:00:00 +0600", item.originalPublishedRaw)
        assertEquals("Tue, 24 Nov 2026 09:00:00 +0600", item.updatedRaw)
        assertEquals("সম্পাদকীয়", item.author)
        assertEquals("https://cdn.ittefaqbd.com/thumb.jpg", item.thumbnailUrl)
        assertTrue(item.categoriesAndTags.contains("সম্পাদকীয়"))
        assertFalse(item.requiresDetailFetchForDateOrMeta)
    }

    @Test
    fun `missing pubDate in Atom entry never fabricates current timestamp`() {
        val atom = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Bangla Opinion Atom</title>
              <entry>
                <title>সংবিধান সংস্কার ও নাগরিক প্রত্যাশা</title>
                <link rel="alternate" href="https://example.bd/opinion/101"/>
                <updated>2026-11-24T04:00:00+06:00</updated>
                <author><name>ড. রাশেদ আল মাহমুদ</name></author>
                <summary>একটি মতামত নিবন্ধ।</summary>
              </entry>
            </feed>
        """.trimIndent()

        val parsed = parser.parse(atom)
        assertEquals(1, parsed.items.size)
        val entry = parsed.items.first()
        assertNull(entry.originalPublishedRaw)
        assertEquals("2026-11-24T04:00:00+06:00", entry.updatedRaw)
        assertTrue(entry.requiresDetailFetchForDateOrMeta)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `completely malformed non-XML throws IllegalArgumentException`() {
        parser.parse("<html>not an rss feed and broken")
    }

    @Test
    fun `empty channel returns zero items cleanly`() {
        val emptyRss = "<rss version='2.0'><channel><title>Empty</title></channel></rss>"
        val parsed = parser.parse(emptyRss)
        assertTrue(parsed.items.isEmpty())
    }

    @Test
    fun `extracts JSON-LD metadata Q-and-A interview structure and interviewee details`() {
        val html = """
            <!DOCTYPE html>
            <html lang="bn">
            <head>
              <link rel="canonical" href="https://www.prothomalo.com/opinion/interview/abc123"/>
              <script type="application/ld+json">
              {
                "@context": "https://schema.org",
                "@type": "OpinionNewsArticle",
                "headline": "সাক্ষাৎকারে ড. দেবপ্রিয় ভট্টাচার্য: অর্থনীতিতে কাঠামোগত সংস্কার জরুরি",
                "datePublished": "2026-11-23T14:30:00+06:00",
                "dateModified": "2026-11-23T16:00:00+06:00",
                "articleSection": "সাক্ষাৎকার",
                "author": {
                  "@type": "Person",
                  "name": "শওকত হোসেন",
                  "jobTitle": "বিশেষ প্রতিনিধি"
                },
                "keywords": "অর্থনীতি, সংস্কার, সাক্ষাৎকার"
              }
              </script>
            </head>
            <body>
              <article>
                <h1>সাক্ষাৎকারে ড. দেবপ্রিয় ভট্টাচার্য: অর্থনীতিতে কাঠামোগত সংস্কার জরুরি</h1>
                <p>ড. দেবপ্রিয় ভট্টাচার্য, বিশিষ্ট অর্থনীতিবিদ ও সিপিডির সম্মাননীয় ফেলো। সাক্ষাৎকার নিয়েছেন শওকত হোসেন।</p>
                <p>প্রশ্ন: বর্তমান মুদ্রাস্ফীতি ও কর্মসংস্থান পরিস্থিতি আপনি কীভাবে মূল্যায়ন করছেন?</p>
                <p>উত্তর: বিনিয়োগ ও প্রাতিষ্ঠানিক সংস্কার ছাড়া টেকসই কর্মসংস্থান তৈরি সম্ভব নয় বলে আমি মনে করি।</p>
              </article>
            </body>
            </html>
        """.trimIndent()

        val meta = extractor.extractArticleMetadata(html, "https://www.prothomalo.com/opinion/interview/abc123")
        assertEquals("2026-11-23T14:30:00+06:00", meta.originalPublishedRaw)
        assertEquals("2026-11-23T16:00:00+06:00", meta.updatedRaw)
        assertTrue(meta.hasQuestionAnswerStructure)
        assertEquals("ড. দেবপ্রিয় ভট্টাচার্য", meta.intervieweeName)
        assertEquals("শওকত হোসেন", meta.interviewerName)
        assertEquals(ReaderAccessStatus.LIVE_PERMITTED_EXTRACT, meta.readerAccessStatus)
    }

    @Test
    fun `respects paywall and robots noarchive directives without extracting body`() {
        val paywalledHtml = """
            <html>
            <head>
              <meta name="robots" content="index, noarchive"/>
              <meta property="article:published_time" content="2026-11-23T10:00:00+06:00"/>
            </head>
            <body>
              <article class="subscriber-only">
                <p>এই অংশটি শুধুমাত্র গ্রাহকদের জন্য সংরক্ষিত নিবন্ধের দীর্ঘ অনুচ্ছেদ।</p>
                <p>দ্বিতীয় সংরক্ষিত অনুচ্ছেদ যা কপিরাইট নীতিমালায় উদ্ধার করা হবে না।</p>
              </article>
            </body>
            </html>
        """.trimIndent()

        val meta = extractor.extractArticleMetadata(paywalledHtml, "https://example.bd/opinion/locked")
        assertEquals(ReaderAccessStatus.PAYWALLED_OR_RESTRICTED, meta.readerAccessStatus)
        assertFalse(meta.isFullExtractPermitted)
        assertTrue(meta.permittedExcerptParagraphs.isEmpty())
    }

    @Test
    fun `discovers section articles from JSON-LD ItemList and honours excludeLinkPattern`() {
        val sectionHtml = """
            <html>
            <head>
              <script type="application/ld+json">
              {
                "@type": "ItemList",
                "itemListElement": [
                  {
                    "@type": "ListItem",
                    "item": {
                      "url": "https://www.newagebd.net/post/editorial/251001/clean-air-policy",
                      "headline": "Clean air policy requires enforcement",
                      "datePublished": "2026-11-23T01:00:00+06:00"
                    }
                  },
                  {
                    "@type": "ListItem",
                    "item": {
                      "url": "https://www.newagebd.net/print/post/editorial/251001",
                      "headline": "Print duplicate link"
                    }
                  }
                ]
              }
              </script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val rules = SourceParsingRules(
            articleLinkPattern = "newagebd\\.net/post/(editorial|opinion)/",
            excludeLinkPattern = "/print/"
        )
        val items = extractor.discoverFromSectionHtml(sectionHtml, "https://www.newagebd.net/articlelist/25/editorial", rules)
        assertEquals(1, items.size)
        assertEquals("Clean air policy requires enforcement", items.first().title)
    }

    @Test
    fun `robots_txt parser respects Allow and Disallow wildcard directives`() {
        val robots = """
            User-agent: *
            Allow: /
            Disallow: /print/
            Disallow: /api/
        """.trimIndent()

        val (allowed, disallowed) = RobotsTxtPolicyChecker.parseRobotsTxt(robots)
        assertTrue(RobotsTxtPolicyChecker.evaluatePath("/opinion/column-1", allowed, disallowed))
        assertFalse(RobotsTxtPolicyChecker.evaluatePath("/print/article/123", allowed, disallowed))
        assertFalse(RobotsTxtPolicyChecker.evaluatePath("/api/private", allowed, disallowed))
    }
}

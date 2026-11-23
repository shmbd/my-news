package bd.nib.songbadpatro

import bd.nib.songbadpatro.domain.config.PublicationCutoffConfig
import bd.nib.songbadpatro.domain.usecase.DateValidationOutcome
import bd.nib.songbadpatro.domain.usecase.DateValidationUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DateValidationAndCutoffTest {

    private val validator = DateValidationUseCase()

    @Test
    fun `exact cutoff moment 2026-11-23 00-00-00 Asia-Dhaka is eligible`() {
        val outcome = validator.validate("2026-11-23T00:00:00+06:00")
        assertTrue(outcome is DateValidationOutcome.Eligible)
        val eligible = outcome as DateValidationOutcome.Eligible
        assertEquals(PublicationCutoffConfig.DEFAULT_CUTOFF_EPOCH_MILLIS, eligible.originalPublicationEpochMs)
    }

    @Test
    fun `one second before cutoff in Asia-Dhaka is strictly rejected`() {
        val outcome = validator.validate("2026-11-22T23:59:59+06:00")
        assertTrue(outcome is DateValidationOutcome.RejectedBeforeCutoff)
    }

    @Test
    fun `UTC timestamp converting to 2026-11-23 00-00-00 Dhaka is eligible while 17-59Z is rejected`() {
        val eligibleUtc = validator.validate("2026-11-22T18:00:00Z")
        assertTrue(eligibleUtc is DateValidationOutcome.Eligible)

        val rejectedUtc = validator.validate("2026-11-22T17:59:59Z")
        assertTrue(rejectedUtc is DateValidationOutcome.RejectedBeforeCutoff)
    }

    @Test
    fun `RFC 1123 publisher date on 23 Nov 2026 is eligible and 22 Nov 2026 is rejected`() {
        val ittefaqEligible = validator.validate("Mon, 23 Nov 2026 06:00:00 +0600")
        assertTrue(ittefaqEligible is DateValidationOutcome.Eligible)

        val deshRupantorOld = validator.validate("Sun, 22 Nov 2026 16:35:55 +0600")
        assertTrue(deshRupantorOld is DateValidationOutcome.RejectedBeforeCutoff)
    }

    @Test
    fun `older article updated after 23 November 2026 is NOT automatically included`() {
        val outcome = validator.validate(
            originalPublishedRaw = "2026-10-15T09:30:00+06:00",
            updatedRaw = "2026-11-24T10:00:00+06:00",
            isExplicitlyNewlyPublished = false
        )
        assertTrue(outcome is DateValidationOutcome.RejectedBeforeCutoff)
    }

    @Test
    fun `article explicitly marked as newly published edition after cutoff is accepted`() {
        val outcome = validator.validate(
            originalPublishedRaw = "2025-05-01T08:00:00+06:00",
            updatedRaw = "2026-11-24T08:00:00+06:00",
            isExplicitlyNewlyPublished = true
        )
        assertTrue(outcome is DateValidationOutcome.Eligible)
    }

    @Test
    fun `missing original date with updated date routes to pending verification instead of publishing`() {
        val outcome = validator.validate(
            originalPublishedRaw = null,
            updatedRaw = "2026-11-24T09:00:00+06:00",
            allowPendingQueueWhenMissing = true
        )
        assertTrue(outcome is DateValidationOutcome.PendingDateVerification)
    }

    @Test
    fun `malformed unparseable date string is rejected`() {
        val outcome = validator.validate(originalPublishedRaw = "not-a-valid-date-xyz")
        assertTrue(outcome is DateValidationOutcome.RejectedInvalidOrMissing)
    }

    @Test
    fun `Bangla digits and Bangla month names parse accurately in Asia-Dhaka`() {
        val eligibleBn = validator.validate("২৩ নভেম্বর ২০২৬")
        assertTrue(eligibleBn is DateValidationOutcome.Eligible)

        val rejectedBn = validator.validate("২২ নভেম্বর ২০২৬")
        assertTrue(rejectedBn is DateValidationOutcome.RejectedBeforeCutoff)
    }

    @Test
    fun `configurable administrative cutoff enforces updated future date`() {
        val nov25 = validator.validate(
            originalPublishedRaw = "2026-11-25T12:00:00+06:00",
            cutoffIsoDate = "2026-12-01"
        )
        assertTrue(nov25 is DateValidationOutcome.RejectedBeforeCutoff)
        assertFalse(PublicationCutoffConfig.isOnOrAfterCutoff(null))
    }
}

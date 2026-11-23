package bd.nib.songbadpatro

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import bd.nib.songbadpatro.domain.config.PublicationCutoffConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MyNewsInstrumentedTest {

    @Test
    fun verifyPackageAndCutoffConfiguration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.startsWith("bd.nib.songbadpatro"))
        assertEquals("2026-11-23", PublicationCutoffConfig.DEFAULT_CUTOFF_ISO_DATE)
    }
}

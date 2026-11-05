package ai.freeapp.bricktown.core.flags

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppFlagsTest {
    private val cache = object : FlagCache {
        var stored: Map<String, Boolean> = emptyMap()
        override fun read() = stored
        override fun write(flags: Map<String, Boolean>) {
            stored = flags
        }
    }

    private fun flags(source: FlagSource) = AppFlags(source, cache, StandardTestDispatcher())

    @Test
    fun `a flag the server never named is on`() {
        assertTrue(flags { emptyMap() }.isOn(Flag.WIDGETS))
    }

    @Test
    fun `a flag the server switched off is off after a refresh`() = runTest {
        val flags = flags { mapOf(Flag.WIDGETS to false, Flag.PENNY_RECAP to true) }
        flags.refresh()
        assertFalse(flags.isOn(Flag.WIDGETS))
        assertTrue(flags.isOn(Flag.PENNY_RECAP))
    }

    @Test
    fun `a failed fetch keeps the last answer`() = runTest {
        flags { mapOf(Flag.IMPORT_PDF to false) }.refresh()
        val offline = flags { error("no network") }
        offline.refresh()
        assertFalse(offline.isOn(Flag.IMPORT_PDF))
    }

    @Test
    fun `the last answer survives a restart through the cache`() = runTest {
        flags { mapOf(Flag.PENNY_NUDGE to false) }.refresh()
        assertEquals(mapOf(Flag.PENNY_NUDGE to false), flags { emptyMap() }.all.value)
    }
}

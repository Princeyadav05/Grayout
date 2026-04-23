package com.princeyadav.grayout.service

import com.princeyadav.grayout.fakes.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExclusionPrefsTest {

    private lateinit var prefs: ExclusionPrefs

    @Before
    fun setUp() {
        prefs = ExclusionPrefs(FakeSharedPreferences())
    }

    @Test
    fun `default excluded packages is empty`() {
        assertEquals(emptySet<String>(), prefs.getExcludedPackages())
    }

    @Test
    fun `default excluded count is 0`() {
        assertEquals(0, prefs.getExcludedCount())
    }

    @Test
    fun `default excluded app active is false`() {
        assertFalse(prefs.isExcludedAppActive())
    }

    @Test
    fun `default wasGrayscaleOnBeforeExclusion is false`() {
        assertFalse(prefs.wasGrayscaleOnBeforeExclusion())
    }

    @Test
    fun `addExcludedPackage adds to set`() {
        prefs.addExcludedPackage("com.example.app")
        assertTrue(prefs.isExcluded("com.example.app"))
        assertEquals(1, prefs.getExcludedCount())
    }

    @Test
    fun `addExcludedPackage multiple packages`() {
        prefs.addExcludedPackage("com.example.a")
        prefs.addExcludedPackage("com.example.b")
        prefs.addExcludedPackage("com.example.c")

        assertEquals(3, prefs.getExcludedCount())
        assertTrue(prefs.isExcluded("com.example.a"))
        assertTrue(prefs.isExcluded("com.example.b"))
        assertTrue(prefs.isExcluded("com.example.c"))
    }

    @Test
    fun `addExcludedPackage duplicate is idempotent`() {
        prefs.addExcludedPackage("com.example.app")
        prefs.addExcludedPackage("com.example.app")
        assertEquals(1, prefs.getExcludedCount())
    }

    @Test
    fun `removeExcludedPackage removes from set`() {
        prefs.addExcludedPackage("com.example.a")
        prefs.addExcludedPackage("com.example.b")

        prefs.removeExcludedPackage("com.example.a")

        assertFalse(prefs.isExcluded("com.example.a"))
        assertTrue(prefs.isExcluded("com.example.b"))
        assertEquals(1, prefs.getExcludedCount())
    }

    @Test
    fun `removeExcludedPackage non-existent package is no-op`() {
        prefs.addExcludedPackage("com.example.a")
        prefs.removeExcludedPackage("com.example.nonexistent")
        assertEquals(1, prefs.getExcludedCount())
    }

    @Test
    fun `isExcluded returns false for unknown package`() {
        assertFalse(prefs.isExcluded("com.example.unknown"))
    }

    @Test
    fun `setExcludedAppActive round-trip`() {
        prefs.setExcludedAppActive(true)
        assertTrue(prefs.isExcludedAppActive())

        prefs.setExcludedAppActive(false)
        assertFalse(prefs.isExcludedAppActive())
    }

    @Test
    fun `wasGrayscaleOnBeforeExclusion round-trip`() {
        prefs.setWasGrayscaleOnBeforeExclusion(true)
        assertTrue(prefs.wasGrayscaleOnBeforeExclusion())

        prefs.setWasGrayscaleOnBeforeExclusion(false)
        assertFalse(prefs.wasGrayscaleOnBeforeExclusion())
    }

    @Test
    fun `clearExclusionState resets active and wasOn flags`() {
        prefs.setExcludedAppActive(true)
        prefs.setWasGrayscaleOnBeforeExclusion(true)

        prefs.clearExclusionState()

        assertFalse(prefs.isExcludedAppActive())
        assertFalse(prefs.wasGrayscaleOnBeforeExclusion())
    }

    @Test
    fun `clearExclusionState does not remove excluded packages`() {
        prefs.addExcludedPackage("com.example.app")
        prefs.setExcludedAppActive(true)
        prefs.setWasGrayscaleOnBeforeExclusion(true)

        prefs.clearExclusionState()

        assertTrue(prefs.isExcluded("com.example.app"))
        assertEquals(1, prefs.getExcludedCount())
    }

    @Test
    fun `setExcludedPackages replaces entire set`() {
        prefs.addExcludedPackage("com.example.old")

        prefs.setExcludedPackages(setOf("com.example.new1", "com.example.new2"))

        assertFalse(prefs.isExcluded("com.example.old"))
        assertTrue(prefs.isExcluded("com.example.new1"))
        assertTrue(prefs.isExcluded("com.example.new2"))
        assertEquals(2, prefs.getExcludedCount())
    }

    @Test
    fun `setExcludedPackages with empty set clears all`() {
        prefs.addExcludedPackage("com.example.a")
        prefs.addExcludedPackage("com.example.b")

        prefs.setExcludedPackages(emptySet())

        assertEquals(0, prefs.getExcludedCount())
        assertEquals(emptySet<String>(), prefs.getExcludedPackages())
    }

    @Test
    fun `full enter-exclusion state cycle`() {
        prefs.addExcludedPackage("com.example.game")

        // User enters the excluded app while grayscale was on
        prefs.setWasGrayscaleOnBeforeExclusion(true)
        prefs.setExcludedAppActive(true)

        assertTrue(prefs.isExcludedAppActive())
        assertTrue(prefs.wasGrayscaleOnBeforeExclusion())

        // User exits the excluded app -- service clears exclusion state
        prefs.clearExclusionState()

        assertFalse(prefs.isExcludedAppActive())
        assertFalse(prefs.wasGrayscaleOnBeforeExclusion())
        // Package list survives the cycle
        assertTrue(prefs.isExcluded("com.example.game"))
    }

    @Test
    fun `enter-exclusion when grayscale was off`() {
        prefs.addExcludedPackage("com.example.camera")

        prefs.setWasGrayscaleOnBeforeExclusion(false)
        prefs.setExcludedAppActive(true)

        assertTrue(prefs.isExcludedAppActive())
        assertFalse(prefs.wasGrayscaleOnBeforeExclusion())

        prefs.clearExclusionState()

        assertFalse(prefs.isExcludedAppActive())
        assertFalse(prefs.wasGrayscaleOnBeforeExclusion())
    }

    @Test
    fun `getExcludedPackages returns defensive copy`() {
        prefs.addExcludedPackage("com.example.a")
        val snapshot = prefs.getExcludedPackages()

        prefs.addExcludedPackage("com.example.b")

        assertFalse(snapshot.contains("com.example.b"))
        assertEquals(1, snapshot.size)
    }
}

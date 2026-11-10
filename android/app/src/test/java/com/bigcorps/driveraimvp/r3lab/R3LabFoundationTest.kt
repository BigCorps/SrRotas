package com.srrotas.app.r3lab

import org.junit.Assert.*
import org.junit.Test

/** Fundação R3 (revisada da Fase 0/1): JVM pura, sem Android, OCR, backend ou LocalStore oficial. */
class R3LabFoundationTest {
    private val env = R3LabEnvironment(36, true, true, true, false, false, false, R3AllowedPackages.UBER)
    private val consent = R3LabConsent(labEnabled = true, accessibilityDisclosureAccepted = true)
    private val fare = listOf(R3ExtractionRule("fare", "R\\$ (\\d+)", listOf("fare")))

    @Test fun offByDefaultAndOnlyInLabBuild() {
        assertEquals(R3LabBlock.NOT_LAB_BUILD, R3LabGate.canCapture(consent, env.copy(labBuild = false)))
        assertEquals(R3LabBlock.LAB_DISABLED, R3LabGate.canCapture(R3LabConsent(), env))
        assertEquals(R3LabBlock.DISCLOSURE_REQUIRED, R3LabGate.canCapture(R3LabConsent(labEnabled = true), env))
        assertNull(R3LabGate.canCapture(consent, env))
    }

    @Test fun separateImageConsentAndOnlyUber99() {
        assertFalse(R3LabGate.mayRetainImage(consent))
        assertTrue(R3LabGate.mayRetainImage(consent.copy(imageRetentionConsent = true)))
        assertEquals(R3LabBlock.TARGET_UNVERIFIED, R3LabGate.canCapture(consent, env.copy(targetPackage = "com.example.bank")))
        assertEquals(R3LabBlock.TARGET_UNVERIFIED, R3LabGate.canCapture(consent, env.copy(targetPackage = null)))
        assertNull(R3LabGate.canCapture(consent, env.copy(targetPackage = R3AllowedPackages.NINETY_NINE)))
    }

    @Test fun neverTwoHeavyReadersScreenOffOrOldAndroid() {
        assertEquals(R3LabBlock.OFFICIAL_READER_ACTIVE, R3LabGate.canCapture(consent, env.copy(officialM1CaptureActive = true)))
        assertEquals(R3LabBlock.OPERATIONAL_APP_ACTIVE, R3LabGate.canCapture(consent, env.copy(operationalAppVisible = true)))
        assertEquals(R3LabBlock.OCR_BUSY, R3LabGate.canCapture(consent, env.copy(anotherOcrRunning = true)))
        assertEquals(R3LabBlock.API_UNSUPPORTED, R3LabGate.canCapture(consent, env.copy(apiLevel = 29)))
        assertEquals(R3LabBlock.SCREEN_OFF, R3LabGate.canCapture(consent, env.copy(screenOn = false)))
    }

    @Test fun invalidProfilesNeverReplaceTheLastGoodOne() {
        val m = R3ProfileManager()
        val first = m.install("v1", R3AllowedPackages.UBER, setOf("fare"), fare)
        val bad = listOf(
            listOf(R3ExtractionRule("fare", "[", listOf("fare"))),                                // regex inválida
            listOf(R3ExtractionRule("fare", "R\\$ (\\d+) (\\d+)", listOf("fare"))),               // nº de grupos ≠ campos
            listOf(R3ExtractionRule("fare", "R\\$ \\d+", listOf("fare"))),                         // sem grupo
            listOf(R3ExtractionRule("fare", "R\\$ (\\d+)", listOf("password"))),                   // campo ilegal
        )
        bad.forEach { rules -> assertThrows(IllegalArgumentException::class.java) { m.install("bad", R3AllowedPackages.UBER, setOf("fare"), rules) } }
        assertThrows(IllegalArgumentException::class.java) { m.install("x", "com.example.bank", setOf("fare"), fare) }
        assertSame(first, m.activeFor(R3AllowedPackages.UBER))
    }

    @Test fun rollbackKeepsSnapshotAndGenerationsAreNeverReused() {
        val m = R3ProfileManager()
        val v1 = m.install("v1", R3AllowedPackages.UBER, setOf("fare"), fare)
        val v2 = m.install("v2", R3AllowedPackages.UBER, setOf("fare"), listOf(R3ExtractionRule("fare", "R\\$ ([0-9]+)", listOf("fare"))))
        assertNotEquals(v1.sha256, v2.sha256)
        assertTrue(m.rollback(R3AllowedPackages.UBER)); assertSame(v1, m.activeFor(R3AllowedPackages.UBER))
        assertFalse(m.rollback(R3AllowedPackages.UBER))
        assertEquals(3L, m.install("v3", R3AllowedPackages.UBER, setOf("fare"), fare).generation)
        // Um envelope em voo mantém o bundle antigo mesmo após a troca.
        val env = R3CaptureEnvelope("f", 1, 1, v1, R3AllowedPackages.UBER, 7, 0, 0, R3SourceKind.WINDOW, null)
        assertEquals(1L, env.profile.generation)
    }

    @Test fun missingCaptureGroupIsNoMatchNeverTheWholeLine() {
        val b = R3ProfileBundle.create("p", 1, R3AllowedPackages.UBER, setOf("fare"),
            listOf(R3ExtractionRule("fare", "R\\$ (\\d+)(?:,(\\d{2}))?", listOf("fare", "rating"))))
        assertNull("optional empty group must not fabricate a value", b.match("fare", "R$ 30"))
        assertEquals("30", b.match("fare", "R$ 30,50")!!["fare"])
        assertNull(b.match("fare", "x".repeat(300) + "R$ 30,50")) // entrada limitada
    }

    @Test fun staleCallbackNeverReleasesNewOperation() {
        var clock = 0L
        val lease = R3CaptureLease({ clock }, 10L)
        val old = lease.start(1, 1)!!
        assertNull(lease.start(1, 1))
        clock = 11
        assertFalse(lease.matches(old))
        assertEquals(old, lease.expire())
        val recent = lease.start(1, 2)!!
        assertFalse(lease.finish(old))
        assertTrue(lease.matches(recent))
        lease.cancelService(1)
        assertFalse(lease.isBusy())
    }

    @Test fun exactLinksOnlyAfterLabCommitAndNeverAcrossPackagesOrToPurged() {
        val l = R3LabEvidenceLedger()
        l.stage("fa", R3AllowedPackages.UBER); l.recordImage("fa", "a".repeat(64), R3EvidenceStatus.READY)
        l.observe(R3Observation("oa", "fa", 1))
        val offer = R3LabOffer("lab-1", "c1", true)
        assertFalse(l.commitPersistedLabOffer(offer, listOf("oa" to R3EvidenceRole.PRIMARY), false))
        assertNull(l.resolvedImage("lab-1", "oa"))
        assertTrue(l.commitPersistedLabOffer(offer, listOf("oa" to R3EvidenceRole.PRIMARY), true))
        assertEquals("fa", l.resolvedImage("lab-1", "oa")?.frameId)
        assertNull(l.resolvedImage("lab-1", "other"))
        l.stage("f99", R3AllowedPackages.NINETY_NINE); l.observe(R3Observation("o99", "f99", 1))
        assertThrows(IllegalArgumentException::class.java) {
            l.commitPersistedLabOffer(R3LabOffer("lab-2", "c2", true), listOf("oa" to R3EvidenceRole.PRIMARY, "o99" to R3EvidenceRole.SUPPLEMENTAL), true)
        }
        l.recordImage("f99", null, R3EvidenceStatus.PURGED)
        assertThrows(IllegalArgumentException::class.java) {
            l.commitPersistedLabOffer(R3LabOffer("lab-3", "c3", true), listOf("o99" to R3EvidenceRole.PRIMARY), true)
        }
        assertThrows(IllegalArgumentException::class.java) { l.recordImage("fa", "zz", R3EvidenceStatus.READY) }
    }

    @Test fun oneFrameManyOffersAndPurgedImageNeverMisassigned() {
        val l = R3LabEvidenceLedger()
        l.stage("shared", R3AllowedPackages.UBER); l.recordImage("shared", "f".repeat(64), R3EvidenceStatus.READY)
        l.observe(R3Observation("so", "shared", 1))
        listOf("lab-1", "lab-2").forEach { assertTrue(l.commitPersistedLabOffer(R3LabOffer(it, it, true), listOf("so" to R3EvidenceRole.PRIMARY), true)) }
        l.recordImage("shared", null, R3EvidenceStatus.PURGED)
        assertNull(l.resolvedImage("lab-1", "so")); assertNull(l.resolvedImage("lab-2", "so"))
    }
}

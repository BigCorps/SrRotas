package com.srrotas.app
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class Field102ReportContractTest {
    private val root by lazy {
        generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .first { File(it,"app/build.gradle.kts").exists() }
    }
    private fun src(name:String)=File(root,"app/src/main/java/com/bigcorps/driveraimvp/$name.kt").readText()
    private val settings=DriverSettings(costPerKm=0.85)
    private fun parse(text:String)=OfferParser.parse(text,"com.ubercab.driver","fixture",settings)
    @Test fun sameFareCannotPromoteAmbiguousFifteenMinutes() {
        val bad="""Electric
Exclusivo
R$ 11,77
R$ 4,36/km aprox.
4,83 (120)
13 min (2,7 km)
2 minutos (0,3 km)
Aceitar"""
        val good="""Electric
Exclusivo
R$ 11,77
R$ 4,36/km aprox.
4,83 (120)
2 min (0,3 km)
11 minutos (2,4 km)
Aceitar"""
        assertNull(parse(bad))
        val valid=parse(good);assertNotNull(valid)
        assertEquals(2,valid!!.pickupMinutes);assertEquals(11,valid.tripMinutes)
        assertEquals(13,valid.totalMinutes);assertEquals(54.32,valid.perHour!!,0.01)
    }
    @Test fun threeGeometryPairsAreNotSilentlyTruncated() {
        assertNull(parse("""UberX
Exclusivo
R$ 30,98
R$ 1,72/km aprox.
2 min (0,7 km)
34 minutos (17,3 km)
8 minutos (6,1 km)
Aceitar"""))
    }
    @Test fun galleryOcrRunsInDedicatedProcessAndCalculatesPreview() {
        val man=File(root,"app/src/main/AndroidManifest.xml").readText()
        val hud=src("FloatingWindowChrome023")
        val review=src("ScreenshotRescanComparisonV1")
        assertTrue(man.contains("android:process=\":rescan\""))
        assertTrue(src("SrRotasApplication").contains("Application.getProcessName().endsWith(\":rescan\")"))
        assertTrue(hud.contains("R.drawable.sr23_ic_photos"))
        assertFalse(hud.contains("if(BuildConfig.VERSION_NAME.contains(\"field\"))"))
        assertTrue(hud.contains("menuButton(context, \"Jornada\", true)"))
        assertTrue(hud.contains("menuButton(context, \"Histórico\", false)"))
        assertTrue(src("OfferRescanActivityV1").contains("Intent.ACTION_OPEN_DOCUMENT"))
        assertTrue(review.contains("Tempo total (min)"));assertTrue(review.contains("R$/h recalculado"))
        assertTrue(review.contains("Resultado estimado"))
        assertTrue(src("JourneyBubbleController").contains("GreenHaloDrawableV1"))
    }
}

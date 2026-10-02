package com.srrotas.app
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
class AccessResolver10BContractTest {
 private val root:File by lazy{val cwd=File(System.getProperty("user.dir")?:".").absoluteFile;generateSequence(cwd){it.parentFile}.flatMap{base->sequenceOf(File(base,"app/src/main/java/com/bigcorps/driveraimvp"),File(base,"android/app/src/main/java/com/bigcorps/driveraimvp"))}.firstOrNull{File(it,"SrRotasApplication.kt").isFile}?:error("Fontes Android não encontrados.")}
 private fun source(name:String)=File(root,name).readText()
 @Test fun deviceIdentityUsesNonInvasiveAndroidId(){val s=source("DeviceIdentity10B.kt");assertTrue(s.contains("Settings.Secure.ANDROID_ID"));assertFalse(s.contains("TelephonyManager"));assertFalse(s.contains("getImei"));assertFalse(s.contains("Build.getSerial"));assertFalse(s.contains("WifiInfo"))}
 @Test fun accessResolverDoesNotExportIdentity(){val s=source("AccessResolver10B.kt");assertTrue(s.contains("exports_raw_device_identity"));assertTrue(s.contains("exports_device_identity_hash"));assertTrue(s.contains("uses_imei"));assertTrue(s.contains("uses_serial"));assertTrue(s.contains("uses_mac"))}
 @Test fun applicationAdoptsIdentityAndDiagnosticExportsResolver(){val a=source("SrRotasApplication.kt");val d=source("ReaderLabCombinedDiagnostic0270361.kt");assertTrue(a.contains("AccessResolver10B.sync(this)"));assertTrue(d.contains("access_resolver_10b"));assertTrue(d.contains("AccessResolver10B.toJson(context)"))}
 @Test fun commercialCapabilitiesAreExported(){val s=source("AccessResolver10B.kt");assertTrue(s.contains("commercial_tier"));assertTrue(s.contains("policy_can_analytics"));assertTrue(s.contains("effective_can_analytics"));assertTrue(s.contains("fun canUseAnalytics"));assertTrue(s.contains("fun canUseAi"));assertTrue(s.contains("fun canUseMcp"))}
}

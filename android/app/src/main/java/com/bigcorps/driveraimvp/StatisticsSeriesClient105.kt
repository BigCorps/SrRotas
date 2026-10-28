package com.srrotas.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/** Isolated authenticated GET of the daily series, mirroring PerformanceSnapshotClient. */
object StatisticsSeriesClient105 {
    private val executor = Executors.newSingleThreadExecutor()

    fun fetch(context: Context, from: String, to: String, callback: (Result<StatisticsSeries105>) -> Unit) {
        val app = context.applicationContext
        val settings = SettingsRepository(app).load()
        executor.execute {
            val result = runCatching {
                if (settings.deviceToken.isBlank()) throw PerformanceFailure(PerformanceProblem.NO_SESSION)
                if (settings.backendUrl.isBlank()) throw PerformanceFailure(PerformanceProblem.NO_CONFIGURATION)
                val query = "from=${URLEncoder.encode(from, "UTF-8")}&to=${URLEncoder.encode(to, "UTF-8")}"
                val connection = (URL("${settings.backendUrl.trimEnd('/')}/api/v1/statistics/series?$query").openConnection() as HttpURLConnection)
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 8000
                    connection.readTimeout = 65000
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("Authorization", "Bearer ${settings.deviceToken}")
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("X-SrRotas-App-Version", BuildConfig.VERSION_NAME)
                    PerformanceFailure.forStatus(connection.responseCode)?.let { throw PerformanceFailure(it) }
                    val raw = connection.inputStream.bufferedReader().use { reader ->
                        val buffer = CharArray(4096)
                        val body = StringBuilder()
                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            body.append(buffer, 0, count)
                            if (body.length > 1_000_000) throw PerformanceFailure(PerformanceProblem.INVALID_PAYLOAD)
                        }
                        body.toString()
                    }
                    val series = StatisticsSeries105.parse(JSONObject(raw))
                    if (!series.matches(from, to)) throw PerformanceFailure(PerformanceProblem.PERIOD_MISMATCH)
                    series
                } finally { connection.disconnect() }
            }.recoverCatching { throw PerformanceFailure.classify(it) }
            Handler(Looper.getMainLooper()).post {
                val current = SettingsRepository(app).load()
                if (current.deviceToken != settings.deviceToken || current.backendUrl != settings.backendUrl) {
                    callback(Result.failure(PerformanceFailure(PerformanceProblem.NO_SESSION)))
                } else callback(result)
            }
        }
    }
}

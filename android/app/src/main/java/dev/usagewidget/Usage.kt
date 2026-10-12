package dev.usagewidget

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ── 서버와 약속한 데이터 형식 ──────────────────────────────
@Serializable
data class Limit(val percent: Double, val resetsAt: String)

@Serializable
data class Usage(val session: Limit, val weekly: Limit, val updatedAt: String)

val UsageJson = Json { ignoreUnknownKeys = true }

// ── 데이터 가져오기 ────────────────────────────────────────
object UsageRepository {
    /** true면 서버 없이 예시 데이터로 동작 (새로고침할 때마다 값이 바뀜) */
    const val USE_MOCK = true
    const val SERVER_URL = "https://YOUR-SERVER.example.com/usage"

    /** 사용량 JSON 문자열을 돌려준다. 실패하면 예외. */
    suspend fun loadJson(context: Context): String = withContext(Dispatchers.IO) {
        if (USE_MOCK) mockJson(context) else fetchJson()
    }

    private fun fetchJson(): String {
        val conn = URL(SERVER_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        return try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            val body = conn.inputStream.bufferedReader().readText()
            UsageJson.decodeFromString<Usage>(body) // 형식 검증
            body
        } finally {
            conn.disconnect()
        }
    }

    private val PRESETS = listOf(34.0 to 51.0, 62.0 to 74.0, 86.0 to 93.0, 97.0 to 99.0)

    private fun mockJson(context: Context): String {
        val prefs = context.getSharedPreferences("mock", Context.MODE_PRIVATE)
        val i = prefs.getInt("i", 0)
        prefs.edit().putInt("i", (i + 1) % PRESETS.size).apply()
        val (s, w) = PRESETS[i]
        val now = Instant.now()
        val usage = Usage(
            session = Limit(s, now.plus(Duration.ofMinutes(102)).toString()),
            weekly = Limit(w, now.plus(Duration.ofHours(78)).toString()),
            updatedAt = now.toString(),
        )
        return UsageJson.encodeToString(usage)
    }
}

// ── 표시용 도우미 ──────────────────────────────────────────
/** 흑백 막대 색: 기준점까지 천천히 밝아지다가, 넘으면 빠르게 흰색에 가까워짐 */
fun barTone(percent: Double, threshold: Double = 80.0): Color {
    val v = percent.coerceIn(0.0, 100.0)
    val l = if (v <= threshold) {
        0.36 + 0.30 * (v / threshold)
    } else {
        0.66 + 0.30 * ((v - threshold) / (100.0 - threshold))
    }
    val f = l.toFloat()
    return Color(f, f, f)
}

/** "1시간 42분 후", "3일 6시간 후" */
fun remaining(resetsAt: String): String {
    val d = runCatching { Duration.between(Instant.now(), Instant.parse(resetsAt)) }
        .getOrNull() ?: return "--"
    if (d.isNegative || d.isZero) return "곧"
    val days = d.toDays()
    val hours = d.toHours() % 24
    val mins = d.toMinutes() % 60
    return when {
        days > 0 -> "${days}일 ${hours}시간 후"
        hours > 0 -> "${hours}시간 ${mins}분 후"
        else -> "${mins}분 후"
    }
}

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

fun clock(iso: String): String =
    runCatching { CLOCK.format(Instant.parse(iso)) }.getOrDefault("--:--")

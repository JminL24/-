package dev.usagewidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.serialization.decodeFromString
import kotlin.math.roundToInt

// ── 색상 (시안과 동일) ────────────────────────────────────
private val BG = Color(0xFF17181B)
private val TEXT = Color(0xFFEDEEF0)
private val LABEL = Color(0xFFD5D8DE)
private val CAPTION = Color(0xFFA6ABB5)
private val TRACK = Color(0x17FFFFFF)

private enum class Layout { SMALL, MEDIUM, LARGE }

class UsageWidget : GlanceAppWidget() {

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 250.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent() }
    }
}

@Composable
private fun WidgetContent() {
    val prefs = currentState<Preferences>()
    val usage = prefs[Keys.JSON]?.let {
        runCatching { UsageJson.decodeFromString<Usage>(it) }.getOrNull()
    }
    val loading = prefs[Keys.LOADING] ?: false
    val failed = prefs[Keys.FAILED] ?: false

    val size = LocalSize.current
    val layout = when {
        size.height >= UsageWidget.LARGE.height -> Layout.LARGE
        size.width >= UsageWidget.MEDIUM.width -> Layout.MEDIUM
        else -> Layout.SMALL
    }

    val status = when {
        loading -> "갱신 중…"
        failed && usage != null -> "갱신 실패 · ${clock(usage.updatedAt)}"
        usage != null -> "${clock(usage.updatedAt)} 갱신"
        else -> "불러오는 중…"
    }

    var root = GlanceModifier
        .fillMaxSize()
        .appWidgetBackground()
        .background(BG)
        .cornerRadius(22.dp)
        .padding(if (layout == Layout.LARGE) 18.dp else 14.dp)
    // 소형은 위젯 전체를 탭하면 새로고침
    if (layout == Layout.SMALL) root = root.clickable(actionRunCallback<RefreshAction>())

    Column(modifier = root) {
        Header(layout, status)
        if (usage == null) return@Column

        // 헤더 아래는 고정 간격, 남는 공간은 막대 사이와 아래 여백에 나눔 (iOS와 동일)
        val gap = if (layout == Layout.LARGE) 14.dp else 8.dp
        Spacer(GlanceModifier.height(if (layout == Layout.LARGE) 18.dp else 10.dp))
        LimitBlock("세션", usage.session, layout)
        Spacer(GlanceModifier.height(gap))
        Spacer(GlanceModifier.defaultWeight())
        LimitBlock("주간", usage.weekly, layout)

        if (layout == Layout.LARGE) {
            Spacer(GlanceModifier.height(gap))
            Spacer(GlanceModifier.defaultWeight())
            Legend()
        }
        Spacer(GlanceModifier.defaultWeight())
    }
}

@Composable
private fun Header(layout: Layout, status: String) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = if (layout == Layout.SMALL) "Claude" else "Claude 사용량",
                style = style(TEXT, if (layout == Layout.LARGE) 16.sp else 14.sp, FontWeight.Medium),
            )
            Text(text = status, style = style(CAPTION, 11.sp))
        }
        if (layout != Layout.SMALL) {
            // 보이는 아이콘은 작게, 터치 영역은 넉넉하게
            Box(
                modifier = GlanceModifier
                    .size(40.dp)
                    .clickable(actionRunCallback<RefreshAction>()),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(32.dp)
                        .background(Color(0x0FFFFFFF))
                        .cornerRadius(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_refresh),
                        contentDescription = "사용량 새로고침",
                        modifier = GlanceModifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LimitBlock(label: String, limit: Limit, layout: Layout) {
    val pct = "${limit.percent.roundToInt()}%"
    val left = remaining(limit.resetsAt)

    Column(modifier = GlanceModifier.fillMaxWidth()) {
        when (layout) {
            Layout.LARGE -> Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text("$label 한도", style = style(LABEL, 13.sp, FontWeight.Medium))
                    Text("$left 초기화", style = style(CAPTION, 12.sp))
                }
                Text(pct, style = style(TEXT, 30.sp))
            }

            Layout.MEDIUM -> Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = style(LABEL, 12.sp, FontWeight.Medium))
                Spacer(GlanceModifier.width(8.dp))
                Text("$left 초기화", style = style(CAPTION, 11.sp), modifier = GlanceModifier.defaultWeight())
                Text(pct, style = style(TEXT, 13.sp, FontWeight.Medium))
            }

            Layout.SMALL -> Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = style(LABEL, 12.sp, FontWeight.Medium), modifier = GlanceModifier.defaultWeight())
                Text(pct, style = style(TEXT, 16.sp, FontWeight.Medium))
            }
        }

        Spacer(GlanceModifier.height(if (layout == Layout.LARGE) 8.dp else 5.dp))
        Bar(limit.percent, if (layout == Layout.SMALL) 4.dp else 6.dp)

        if (layout == Layout.SMALL) {
            Spacer(GlanceModifier.height(3.dp))
            Text(left, style = style(CAPTION, 11.sp))
        }
    }
}

@Composable
private fun Bar(percent: Double, thickness: androidx.compose.ui.unit.Dp) {
    LinearProgressIndicator(
        progress = (percent / 100.0).toFloat().coerceIn(0f, 1f),
        modifier = GlanceModifier.fillMaxWidth().height(thickness).cornerRadius(thickness / 2),
        color = ColorProvider(barTone(percent)),
        backgroundColor = ColorProvider(TRACK),
    )
}

/** 대형 위젯 하단: 밝기 기준표 */
@Composable
private fun Legend() {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Text("밝기 기준", style = style(CAPTION, 11.sp))
        Spacer(GlanceModifier.height(6.dp))
        Row(modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp)) {
            repeat(20) { i ->
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .background(barTone(i * 5.0 + 2.5)),
                ) {}
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text("0%", style = style(CAPTION, 11.sp))
            Text(
                "80%부터 급격히 변화",
                style = style(CAPTION, 11.sp),
                modifier = GlanceModifier.defaultWeight().padding(horizontal = 8.dp),
            )
            Text("100%", style = style(CAPTION, 11.sp))
        }
    }
}

private fun style(color: Color, size: TextUnit, weight: FontWeight = FontWeight.Normal) =
    TextStyle(color = ColorProvider(color), fontSize = size, fontWeight = weight)

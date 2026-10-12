package dev.usagewidget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 위젯 상태 저장 키 */
object Keys {
    val JSON = stringPreferencesKey("usage_json")
    val LOADING = booleanPreferencesKey("loading")
    val FAILED = booleanPreferencesKey("failed")
}

object UsageUpdater {
    /** 데이터를 새로 받아 모든 위젯에 반영 */
    suspend fun refreshAll(context: Context) {
        val ids = GlanceAppWidgetManager(context).getGlanceIds(UsageWidget::class.java)
        if (ids.isEmpty()) return

        ids.forEach { id ->
            updateAppWidgetState(context, id) { it[Keys.LOADING] = true }
            UsageWidget().update(context, id)
        }

        val json = runCatching { UsageRepository.loadJson(context) }.getOrNull()

        ids.forEach { id ->
            updateAppWidgetState(context, id) { prefs ->
                if (json != null) prefs[Keys.JSON] = json // 실패하면 이전 값 유지
                prefs[Keys.FAILED] = json == null
                prefs[Keys.LOADING] = false
            }
            UsageWidget().update(context, id)
        }
    }
}

/** 새로고침 버튼 */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        UsageUpdater.refreshAll(context)
    }
}

/** 위젯 추가 시 + 30분 자동 갱신 시 호출됨 */
class UsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UsageWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // super 대신 직접 데이터를 받아와 갱신한다
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                UsageUpdater.refreshAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}

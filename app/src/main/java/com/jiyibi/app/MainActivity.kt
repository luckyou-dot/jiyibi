package com.jiyibi.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiyibi.app.core.designsystem.theme.JiYiBiTheme
import com.jiyibi.app.core.notify.AutoRecordNotifier
import com.jiyibi.app.nav.JiYiBiApp
import com.jiyibi.app.ui.settings.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * 唯一 Activity，承载 Compose 导航与所有页面。
 *
 * 在根部订阅 [ThemeViewModel.theme]，将用户在「我的 → 主题风格」
 * 选择的主题应用到整个 Compose 树。
 *
 * 另外承接「自动记账提醒通知」的点击跳转：通知携带
 * [AutoRecordNotifier.EXTRA_EDIT_TRANSACTION_ID]，这里读出后交给
 * [JiYiBiApp] 导航到对应交易的编辑页（一次性消费，避免旋转屏幕重复跳转）。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** 待跳转编辑页的交易 id；-1 表示没有待处理的跳转 */
    private val pendingEditId = mutableLongStateOf(-1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingEditId.longValue = intent
            ?.getLongExtra(AutoRecordNotifier.EXTRA_EDIT_TRANSACTION_ID, -1L)
            ?: -1L
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val appTheme by themeViewModel.theme.collectAsStateWithLifecycle()
            JiYiBiTheme(appTheme = appTheme) {
                JiYiBiApp(
                    pendingEditTransactionId = pendingEditId.longValue,
                    onPendingEditConsumed = { pendingEditId.longValue = -1L },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 通知用 FLAG_ACTIVITY_SINGLE_TOP 打开：App 已在前台时走这里而不是 onCreate
        pendingEditId.longValue =
            intent.getLongExtra(AutoRecordNotifier.EXTRA_EDIT_TRANSACTION_ID, -1L)
    }
}

package com.jiyibi.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
 * 另外承接两类通知点击：
 * - 「已自动记账」提醒携带 [AutoRecordNotifier.EXTRA_EDIT_TRANSACTION_ID]，
 *   读出后交给 [JiYiBiApp] 导航到该笔交易的编辑页；
 * - 「自动记账已失效」提醒携带 [AutoRecordNotifier.EXTRA_OPEN_AUTO_RECORD]，
 *   读出后直达「自动记账」页——权限掉线时用户要做的事都在那一页。
 * 两者都是一次性消费，避免旋转屏幕重复跳转。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** 待跳转编辑页的交易 id；-1 表示没有待处理的跳转 */
    private val pendingEditId = mutableLongStateOf(-1L)

    /** 是否要求直达「自动记账」页（服务掉线提醒的点击入口） */
    private val pendingOpenAutoRecord = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeIntent(intent)
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val appTheme by themeViewModel.theme.collectAsStateWithLifecycle()
            JiYiBiTheme(appTheme = appTheme) {
                JiYiBiApp(
                    pendingEditTransactionId = pendingEditId.longValue,
                    onPendingEditConsumed = { pendingEditId.longValue = -1L },
                    pendingOpenAutoRecord = pendingOpenAutoRecord.value,
                    onPendingOpenAutoRecordConsumed = { pendingOpenAutoRecord.value = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 通知用 FLAG_ACTIVITY_SINGLE_TOP 打开：App 已在前台时走这里而不是 onCreate
        consumeIntent(intent)
    }

    /** 从通知点击的 Intent 里取出跳转目标（一次性） */
    private fun consumeIntent(intent: Intent?) {
        pendingEditId.longValue =
            intent?.getLongExtra(AutoRecordNotifier.EXTRA_EDIT_TRANSACTION_ID, -1L) ?: -1L
        pendingOpenAutoRecord.value =
            intent?.getBooleanExtra(AutoRecordNotifier.EXTRA_OPEN_AUTO_RECORD, false) ?: false
    }
}

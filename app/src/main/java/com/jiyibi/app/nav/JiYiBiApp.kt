package com.jiyibi.app.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jiyibi.app.R
import com.jiyibi.app.ui.account.AccountManageScreen
import com.jiyibi.app.ui.autorecord.AutoRecordScreen
import com.jiyibi.app.ui.backup.BackupExportScreen
import com.jiyibi.app.ui.budget.BudgetEditScreen
import com.jiyibi.app.ui.budget.BudgetScreen
import com.jiyibi.app.ui.category.CategoryManageScreen
import com.jiyibi.app.ui.debt.DebtListScreen
import com.jiyibi.app.ui.home.HomeScreen
import com.jiyibi.app.ui.recurring.RecurringListScreen
import com.jiyibi.app.ui.search.SearchScreen
import com.jiyibi.app.ui.settings.AboutScreen
import com.jiyibi.app.ui.settings.FeedbackScreen
import com.jiyibi.app.ui.settings.SettingsScreen
import com.jiyibi.app.ui.statistics.StatisticsScreen
import com.jiyibi.app.ui.tag.TagManageScreen
import com.jiyibi.app.ui.transaction.TransactionEditScreen

private data class BottomItem(
    val route: String,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val bottomItems = listOf(
    BottomItem(Routes.HOME, R.string.nav_home, Icons.Filled.Dashboard),
    BottomItem(Routes.STATISTICS, R.string.nav_statistics, Icons.Filled.BarChart),
    BottomItem(Routes.BUDGET, R.string.nav_budget, Icons.Filled.Wallet),
    BottomItem(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

// 主 Tab 页淡入淡出转场时长
private const val TAB_TRANSITION_MS = 300

/**
 * 应用根 Composable：Scaffold + 底部导航 + NavHost。
 */
@Composable
fun JiYiBiApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            // 二级页/全屏页面隐藏底部栏：
            // - transaction/* 新增/编辑交易
            // - settings/* 分类/账户/周期/债务/备份等管理页
            // - search 搜索页
            // - budget/edit 预算编辑页
            // - tag_manage 标签管理
            if (currentRoute?.startsWith("transaction") != true &&
                currentRoute?.startsWith("settings/") != true &&
                currentRoute?.startsWith("search") != true &&
                currentRoute?.startsWith("budget/edit") != true &&
                currentRoute?.startsWith("tag_manage") != true
            ) {
                NavigationBar(
                    // 半透明背景营造毛玻璃观感，tonalElevation=0 避免系统色调叠加让背景内容透出
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    tonalElevation = 0.dp,
                ) {
                    bottomItems.forEach { item ->
                        val selected = backStackEntry?.destination?.hierarchy
                            ?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.labelRes)) },
                            // 选中项图标/文字用 primary，未选用 onSurfaceVariant；指示器用 primary 的低透明度
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            ),
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
            // 二级页默认转场：从右侧滑入/向左侧滑出 + 淡入淡出（标准 push/pop 体感）
            enterTransition = {
                slideInHorizontally(
                    animationSpec = tween(TAB_TRANSITION_MS),
                    initialOffsetX = { fullWidth -> fullWidth },
                ) + fadeIn(animationSpec = tween(TAB_TRANSITION_MS))
            },
            exitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(TAB_TRANSITION_MS),
                    targetOffsetX = { fullWidth -> -fullWidth },
                ) + fadeOut(animationSpec = tween(TAB_TRANSITION_MS))
            },
            popEnterTransition = {
                slideInHorizontally(
                    animationSpec = tween(TAB_TRANSITION_MS),
                    initialOffsetX = { fullWidth -> -fullWidth },
                ) + fadeIn(animationSpec = tween(TAB_TRANSITION_MS))
            },
            popExitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(TAB_TRANSITION_MS),
                    targetOffsetX = { fullWidth -> fullWidth },
                ) + fadeOut(animationSpec = tween(TAB_TRANSITION_MS))
            },
        ) {
            // 主 Tab 页：纯淡入淡出转场（覆盖 NavHost 默认的滑入滑出）
            composable(
                Routes.HOME,
                enterTransition = { fadeIn(animationSpec = tween(TAB_TRANSITION_MS)) },
                exitTransition = { fadeOut(animationSpec = tween(TAB_TRANSITION_MS)) },
                popEnterTransition = { fadeIn(animationSpec = tween(TAB_TRANSITION_MS)) },
                popExitTransition = { fadeOut(animationSpec = tween(TAB_TRANSITION_MS)) },
            ) {
                HomeScreen(
                    onAddTransaction = {
                        navController.navigate(Routes.transactionEdit())
                    },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onEditTransaction = { id -> navController.navigate(Routes.transactionEdit(id)) },
                )
            }
            composable(
                Routes.STATISTICS,
                enterTransition = { fadeIn(animationSpec = tween(TAB_TRANSITION_MS)) },
                exitTransition = { fadeOut(animationSpec = tween(TAB_TRANSITION_MS)) },
                popEnterTransition = { fadeIn(animationSpec = tween(TAB_TRANSITION_MS)) },
                popExitTransition = { fadeOut(animationSpec = tween(TAB_TRANSITION_MS)) },
            ) { StatisticsScreen() }
            composable(
                Routes.BUDGET,
                enterTransition = { fadeIn(animationSpec = tween(TAB_TRANSITION_MS)) },
                exitTransition = { fadeOut(animationSpec = tween(TAB_TRANSITION_MS)) },
                popEnterTransition = { fadeIn(animationSpec = tween(TAB_TRANSITION_MS)) },
                popExitTransition = { fadeOut(animationSpec = tween(TAB_TRANSITION_MS)) },
            ) {
                BudgetScreen(
                    onAddBudget = { navController.navigate(Routes.budgetEdit()) },
                    onAddCategoryBudget = { navController.navigate(Routes.budgetEdit(forceCategory = true)) },
                    onEditBudget = { id -> navController.navigate(Routes.budgetEdit(id)) },
                )
            }
            composable(
                Routes.SETTINGS,
                enterTransition = { fadeIn(animationSpec = tween(TAB_TRANSITION_MS)) },
                exitTransition = { fadeOut(animationSpec = tween(TAB_TRANSITION_MS)) },
                popEnterTransition = { fadeIn(animationSpec = tween(TAB_TRANSITION_MS)) },
                popExitTransition = { fadeOut(animationSpec = tween(TAB_TRANSITION_MS)) },
            ) {
                SettingsScreen(
                    onOpenCategory = { navController.navigate(Routes.CATEGORY_MANAGE) },
                    onOpenAccount = { navController.navigate(Routes.ACCOUNT_MANAGE) },
                    onOpenRecurring = { navController.navigate(Routes.RECURRING) },
                    onOpenDebt = { navController.navigate(Routes.DEBT) },
                    onNavigateTagManage = { navController.navigate(Routes.TAG_MANAGE) },
                    onOpenBackup = { navController.navigate(Routes.BACKUP) },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    onOpenFeedback = { navController.navigate(Routes.FEEDBACK) },
                    onOpenAutoRecord = { navController.navigate(Routes.AUTO_RECORD) },
                )
            }

            composable(
                route = Routes.TRANSACTION_EDIT,
                arguments = listOf(
                    navArgument("transactionId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("prefill") { type = NavType.StringType; defaultValue = "" },
                ),
            ) {
                TransactionEditScreen(
                    onSaved = { navController.popBackStack() },
                    // TODO: 由另一子代理为 TransactionEditScreen 添加 prefill 参数后启用下方调用：
                    //   prefill = backStackEntry?.arguments?.getString("prefill") ?: "",
                    onAddCategory = { navController.navigate(Routes.CATEGORY_MANAGE) },
                )
            }

            // 搜索页
            composable(Routes.SEARCH) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onEditTransaction = { id -> navController.navigate(Routes.transactionEdit(id)) },
                )
            }
            // 分类管理
            composable(Routes.CATEGORY_MANAGE) {
                CategoryManageScreen(onBack = { navController.popBackStack() })
            }
            // 账户管理
            composable(Routes.ACCOUNT_MANAGE) {
                AccountManageScreen(onBack = { navController.popBackStack() })
            }
            // 周期性记账
            composable(Routes.RECURRING) {
                RecurringListScreen(onBack = { navController.popBackStack() })
            }
            // 债务
            composable(Routes.DEBT) {
                DebtListScreen(onBack = { navController.popBackStack() })
            }
            // 标签管理
            composable(Routes.TAG_MANAGE) {
                TagManageScreen(onBack = { navController.popBackStack() })
            }
            // 自动记账（通知监听）
            composable(Routes.AUTO_RECORD) {
                AutoRecordScreen(
                    onBack = { navController.popBackStack() },
                    onEditTransaction = { id -> navController.navigate(Routes.transactionEdit(id)) },
                )
            }
            // 备份导出
            composable(Routes.BACKUP) {
                BackupExportScreen(onBack = { navController.popBackStack() })
            }
            // 关于应用
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
            // 意见反馈
            composable(Routes.FEEDBACK) {
                FeedbackScreen(onBack = { navController.popBackStack() })
            }
            // 编辑预算
            composable(
                route = Routes.BUDGET_EDIT,
                arguments = listOf(
                    navArgument("budgetId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("forceCategory") { type = NavType.BoolType; defaultValue = false },
                ),
            ) {
                BudgetEditScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

package com.jiyibi.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiyibi.app.core.designsystem.component.Spacing
import com.jiyibi.app.core.designsystem.component.UnifiedCard
import com.jiyibi.app.core.designsystem.component.UnifiedCardVariant
import com.jiyibi.app.core.designsystem.component.listItemEnterAnimation
import com.jiyibi.app.core.designsystem.theme.gradientBrush
import com.jiyibi.app.core.domain.model.yuanToCents

/**
 * 「我的」聚合页：聚合各业务入口（分类 / 账户 / 周期 / 借贷 / 标签 / 备份 / 迁移），
 * 并提供主题切换、关于等入口。
 * 资产看板由 [SettingsViewModel] 提供数据。
 */
private val HeroHeight = 390.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenCategory: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenDebt: () -> Unit,
    onNavigateTagManage: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenAutoRecord: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val board by viewModel.assetBoard.collectAsStateWithLifecycle()
    val hasTransactions by viewModel.hasTransactions.collectAsStateWithLifecycle()
    val hasAccounts by viewModel.hasAccounts.collectAsStateWithLifecycle()
    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val defaultExpenseAccountId by viewModel.defaultExpenseAccountId.collectAsStateWithLifecycle()
    val defaultIncomeAccountId by viewModel.defaultIncomeAccountId.collectAsStateWithLifecycle()

    // 总资产编辑弹窗状态：先警告（有交易数据时），再输入新值
    var showAssetsWarning by remember { mutableStateOf(false) }
    var showAssetsEdit by remember { mutableStateOf(false) }
    // 无账户时提示用户先创建账户
    var showNoAccountsTip by remember { mutableStateOf(false) }
    // 主题风格选择弹窗
    var showThemePicker by remember { mutableStateOf(false) }
    // 「分类与账户」二级选择弹窗
    var showCategoryAccountPicker by remember { mutableStateOf(false) }
    // 「默认账户」选择弹窗（同时设置支出/收入）
    var showDefaultAccountPicker by remember { mutableStateOf(false) }
    // 「周期与借贷」二级选择弹窗
    var showRecurringDebtPicker by remember { mutableStateOf(false) }

    // 当前默认账户显示名：账户被删除或未设置时显示「未设置」
    val expenseAccountName = accounts.firstOrNull { it.id == defaultExpenseAccountId }?.name
    val incomeAccountName = accounts.firstOrNull { it.id == defaultIncomeAccountId }?.name

    // 根 Box：底层渐变 Hero 背景 + 透明 Scaffold 叠加，使 TopAppBar 透明地浮于渐变之上
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 顶部渐变 Hero 背景层
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HeroHeight)
                .background(gradientBrush()),
        )
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("我的", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = Spacing.xxl),
            ) {
                // 顶部 Hero 区：应用图标 + 名称 + 版本 + 毛玻璃资产看板
                item {
                    HeroContent(
                        board = board,
                        onEditAssets = {
                            if (!hasAccounts) {
                                showNoAccountsTip = true
                            } else if (hasTransactions) {
                                showAssetsWarning = true
                            } else {
                                showAssetsEdit = true
                            }
                        },
                    )
                }

                // 分组 1：外观
                item { SectionHeader("外观") }
                item {
                    UnifiedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.l),
                        variant = UnifiedCardVariant.ELEVATED,
                        contentPadding = PaddingValues(vertical = Spacing.xs),
                    ) {
                        ClickableItem(
                            icon = Icons.Filled.Palette,
                            title = "主题风格",
                            subtitle = currentTheme.displayName,
                            onClick = { showThemePicker = true },
                            modifier = Modifier.listItemEnterAnimation(0),
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // 当前主题色板预览
                                    ThemeSwatch(theme = currentTheme, size = 20.dp)
                                    Spacer(Modifier.size(Spacing.s))
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                }

                // 分组 2：记账管理
                item { SectionHeader("记账管理") }
                item {
                    UnifiedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.l),
                        variant = UnifiedCardVariant.ELEVATED,
                        contentPadding = PaddingValues(vertical = Spacing.xs),
                    ) {
                        ClickableItem(
                            icon = Icons.Filled.Category,
                            title = "分类与账户",
                            subtitle = "管理收支分类与账户",
                            onClick = { showCategoryAccountPicker = true },
                            modifier = Modifier.listItemEnterAnimation(0),
                        )
                        ClickableItem(
                            icon = Icons.Filled.NotificationsActive,
                            title = "自动记账",
                            subtitle = "监听微信 / 支付宝支付通知自动记录",
                            onClick = onOpenAutoRecord,
                            modifier = Modifier.listItemEnterAnimation(1),
                        )
                        ClickableItem(
                            icon = Icons.Filled.Payment,
                            title = "默认账户",
                            subtitle = defaultAccountSubtitle(expenseAccountName, incomeAccountName),
                            onClick = { showDefaultAccountPicker = true },
                            modifier = Modifier.listItemEnterAnimation(2),
                        )
                        ClickableItem(
                            icon = Icons.Filled.Repeat,
                            title = "周期与借贷",
                            subtitle = "周期性记账与借贷记录",
                            onClick = { showRecurringDebtPicker = true },
                            modifier = Modifier.listItemEnterAnimation(3),
                        )
                        ClickableItem(
                            icon = Icons.AutoMirrored.Filled.Label,
                            title = "标签管理",
                            subtitle = "管理自定义标签",
                            onClick = onNavigateTagManage,
                            modifier = Modifier.listItemEnterAnimation(4),
                        )
                    }
                }

                // 分组 3：数据
                item { SectionHeader("数据") }
                item {
                    UnifiedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.l),
                        variant = UnifiedCardVariant.ELEVATED,
                        contentPadding = PaddingValues(vertical = Spacing.xs),
                    ) {
                        ClickableItem(
                            icon = Icons.Filled.CloudUpload,
                            title = "备份与恢复",
                            subtitle = "导出 CSV / JSON 备份 / 恢复",
                            onClick = onOpenBackup,
                            modifier = Modifier.listItemEnterAnimation(0),
                        )
                    }
                }

                // 分组 4：关于
                item { SectionHeader("关于") }
                item {
                    UnifiedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.l),
                        variant = UnifiedCardVariant.ELEVATED,
                        contentPadding = PaddingValues(vertical = Spacing.xs),
                    ) {
                        ClickableItem(
                            icon = Icons.Filled.Info,
                            title = "关于应用",
                            onClick = onOpenAbout,
                            modifier = Modifier.listItemEnterAnimation(0),
                        )
                        ClickableItem(
                            icon = Icons.Filled.Feedback,
                            title = "意见反馈",
                            onClick = onOpenFeedback,
                            modifier = Modifier.listItemEnterAnimation(1),
                        )
                    }
                }
            }
        }
    }

    // 无账户提示弹窗：引导用户先创建账户
    if (showNoAccountsTip) {
        AlertDialog(
            onDismissRequest = { showNoAccountsTip = false },
            title = { Text("暂无账户") },
            text = { Text("请先在「账户管理」中创建一个账户，再设置总资产。") },
            confirmButton = {
                TextButton(onClick = { showNoAccountsTip = false }) { Text("知道了") }
            },
        )
    }

    // 警告弹窗：已有交易数据时，修改总资产前提醒用户
    if (showAssetsWarning) {
        AlertDialog(
            onDismissRequest = { showAssetsWarning = false },
            title = { Text("修改总资产") },
            text = {
                Text("已存在交易数据，修改总资产将调整首个账户余额以匹配新值，可能与实际账目不符。是否继续？")
            },
            confirmButton = {
                TextButton(onClick = {
                    showAssetsWarning = false
                    showAssetsEdit = true
                }) { Text("继续") }
            },
            dismissButton = {
                TextButton(onClick = { showAssetsWarning = false }) { Text("取消") }
            },
        )
    }

    // 输入弹窗：输入新的总资产金额（元）
    if (showAssetsEdit) {
        var assetsText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAssetsEdit = false },
            title = { Text("设置总资产") },
            text = {
                OutlinedTextField(
                    value = assetsText,
                    onValueChange = { assetsText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("总资产（元）") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val yuan = assetsText.toDoubleOrNull()
                    if (yuan != null && yuan >= 0) {
                        viewModel.setTotalAssets(yuan.yuanToCents())
                        showAssetsEdit = false
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showAssetsEdit = false }) { Text("取消") }
            },
        )
    }

    // 主题风格选择弹窗
    if (showThemePicker) {
        ThemePickerDialog(
            currentTheme = currentTheme,
            onSelect = { viewModel.setTheme(it) },
            onDismiss = { showThemePicker = false },
        )
    }

    // 「分类与账户」二级选择弹窗
    if (showCategoryAccountPicker) {
        CategoryAccountPickerDialog(
            onOpenCategory = {
                showCategoryAccountPicker = false
                onOpenCategory()
            },
            onOpenAccount = {
                showCategoryAccountPicker = false
                onOpenAccount()
            },
            onDismiss = { showCategoryAccountPicker = false },
        )
    }

    // 「默认账户」选择弹窗（同时设置支出/收入账户）
    if (showDefaultAccountPicker) {
        DefaultAccountPickerDialog(
            accounts = accounts,
            expenseAccountId = defaultExpenseAccountId,
            incomeAccountId = defaultIncomeAccountId,
            onSelectExpense = { viewModel.setDefaultExpenseAccount(it) },
            onSelectIncome = { viewModel.setDefaultIncomeAccount(it) },
            onDismiss = { showDefaultAccountPicker = false },
        )
    }

    // 「周期与借贷」二级选择弹窗
    if (showRecurringDebtPicker) {
        RecurringDebtPickerDialog(
            onOpenRecurring = {
                showRecurringDebtPicker = false
                onOpenRecurring()
            },
            onOpenDebt = {
                showRecurringDebtPicker = false
                onOpenDebt()
            },
            onDismiss = { showRecurringDebtPicker = false },
        )
    }
}

/**
 * 分组标题：小字 + 大写 + 灰色 + 上下 padding。
 */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.l, end = Spacing.l, top = Spacing.l, bottom = Spacing.s),
    )
}

/**
 * 可点击的设置项：圆形主题色底图标 + 标题 + 右侧灰色右箭头。
 *
 * @param modifier 外部修饰符，可用于注入入场动画
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClickableItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = {
            // 圆形主题色底 + 主题色图标
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = trailing ?: {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

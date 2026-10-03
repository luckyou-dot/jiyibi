package com.jiyibi.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiyibi.app.core.designsystem.component.AnimatedNumber
import com.jiyibi.app.core.designsystem.component.GlassCard
import com.jiyibi.app.core.designsystem.component.Spacing
import com.jiyibi.app.core.designsystem.component.UnifiedCard
import com.jiyibi.app.core.designsystem.component.UnifiedCardVariant
import com.jiyibi.app.core.designsystem.component.listItemEnterAnimation
import com.jiyibi.app.core.designsystem.theme.AppTheme
import com.jiyibi.app.core.designsystem.theme.BudgetAmber
import com.jiyibi.app.core.designsystem.theme.ExpenseRed
import com.jiyibi.app.core.designsystem.theme.IncomeGreen
import com.jiyibi.app.core.designsystem.theme.gradientBrush
import com.jiyibi.app.core.designsystem.theme.paletteOf
import com.jiyibi.app.core.domain.model.Account
import com.jiyibi.app.core.domain.model.centsToYuan
import com.jiyibi.app.core.domain.model.yuanToCents
import androidx.compose.ui.platform.LocalContext
import com.jiyibi.app.core.common.AppVersion

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
 * 顶部渐变 Hero 区：使用主题渐变作为背景，自顶部状态栏延伸而下。
 *
 * 内部包含：
 * 1. 透明 TopAppBar 占位（64dp，与叠加的 TopAppBar 高度对齐）
 * 2. [HeaderItem]：应用图标（圆形毛玻璃底）+ 名称 + 版本号
 * 3. [AssetBoardCard]：毛玻璃资产看板，三栏金额展示
 */
/**
 * 顶部 Hero 内容区：应用图标 + 名称 + 版本 + 毛玻璃资产看板。
 */
@Composable
private fun HeroContent(
    board: AssetBoard,
    onEditAssets: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        // 应用图标 + 名称 + 版本号（白色文字保证渐变上可读）
        HeaderItem()

        Spacer(Modifier.height(Spacing.m))

        // 毛玻璃资产看板：叠加在 Hero 底部
        AssetBoardCard(
            board = board,
            onEditAssets = onEditAssets,
        )

        Spacer(Modifier.height(Spacing.l))
    }
}

/**
 * 顶部 Header：居中显示应用 Logo（72dp 圆形毛玻璃底 + 钱包图标）、应用名、版本号。
 * 文字使用白色，确保在渐变背景上可读。
 */
@Composable
private fun HeaderItem() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        // 72dp 圆形毛玻璃底包裹钱包图标
        GlassCard(
            modifier = Modifier.size(72.dp),
            // 72dp 尺寸下 36dp 圆角即为正圆
            cornerRadius = 36.dp,
            contentPadding = PaddingValues(0.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountBalanceWallet,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = Color.White,
                )
            }
        }
        Text(
            text = "记一笔",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        // 版本号运行时读取：BuildConfig 常量会被内联进字节码，增量编译下
        // 未改动的本文件可能一直带着旧值（曾显示 v1.05 而 manifest 是 1.16）
        val context = LocalContext.current
        val versionName = remember(context) { AppVersion.name(context) }
        Text(
            text = "v$versionName",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

/**
 * 资产看板卡片：总资产 / 本月结余 / 待收 三栏展示，使用毛玻璃效果叠加在 Hero 上。
 *
 * 数字使用 [AnimatedNumber]（等宽字体 + 白色），列间用 [VerticalDivider] 分隔。
 * 点击「总资产」列触发 [onEditAssets]，允许用户手动修改总资产。
 */
@Composable
private fun AssetBoardCard(
    board: AssetBoard,
    onEditAssets: () -> Unit,
) {
    // 等宽字体样式，保证数字滚动时宽度稳定
    val numberStyle = MaterialTheme.typography.titleMedium.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
    )
    // 本月结余：正数绿色（结余），负数红色（超支）
    val balanceColor = if (board.monthBalance >= 0) IncomeGreen else ExpenseRed
    val balancePrefix = if (board.monthBalance >= 0) "¥" else "-¥"
    val balanceValue = kotlin.math.abs(board.monthBalance.centsToYuan().toDouble())

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l),
        contentPadding = PaddingValues(Spacing.m),
    ) {
        Column {
            // 顶部行：标题 + 「本月」半透明 AssistChip
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "💰 资产看板",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = {},
                    label = { Text("本月", color = Color.White) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color.White.copy(alpha = 0.25f),
                        labelColor = Color.White,
                    ),
                )
            }
            Spacer(Modifier.height(Spacing.s))
            // 三栏金额展示：总资产白、本月结余绿/红、待收黄
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 总资产：可点击编辑
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onEditAssets),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "总资产",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Box {
                        AnimatedNumber(
                            targetValue = board.totalAssets.centsToYuan().toDouble(),
                            prefix = "¥",
                            style = numberStyle,
                            color = Color.White,
                        )
                    }
                }
                VerticalDivider(
                    modifier = Modifier.height(24.dp),
                    color = Color.White.copy(alpha = 0.3f),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "本月结余",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    AnimatedNumber(
                        targetValue = balanceValue,
                        prefix = balancePrefix,
                        style = numberStyle,
                        color = balanceColor,
                    )
                }
                VerticalDivider(
                    modifier = Modifier.height(24.dp),
                    color = Color.White.copy(alpha = 0.3f),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "待收",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    AnimatedNumber(
                        targetValue = board.pendingReceivable.centsToYuan().toDouble(),
                        prefix = "¥",
                        style = numberStyle,
                        color = BudgetAmber,
                    )
                }
            }
        }
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

/** 主题色板预览：圆形渐变，由主色 → 辅色构成。
 */
@Composable
private fun ThemeSwatch(theme: AppTheme, size: androidx.compose.ui.unit.Dp) {
    val palette = paletteOf(theme)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(palette.lightPrimary, palette.lightSecondary),
                ),
            ),
    )
}

/**
 * 主题风格选择对话框：列出 4 种主题，每个选项左侧为色板预览，右侧为当前选中标记。
 */
@Composable
private fun ThemePickerDialog(
    currentTheme: AppTheme,
    onSelect: (AppTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择主题风格") },
        text = {
            Column {
                AppTheme.entries.forEach { theme ->
                    val selected = theme == currentTheme
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(theme)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ThemeSwatch(theme = theme, size = 28.dp)
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = theme.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Text(
                                text = themeDescription(theme),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "已选择",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/** 各主题的简短描述 */
private fun themeDescription(theme: AppTheme): String = when (theme) {
    AppTheme.MINT -> "青绿渐变 · 清爽自然"
    AppTheme.VIBRANT -> "紫粉渐变 · 年轻时尚"
    AppTheme.SUNSET -> "橙红渐变 · 温暖治愈"
    AppTheme.MORANDI -> "低饱和灰调 · 高级优雅"
}

/**
 * 默认账户副标题：两个账户都未设置时显示「未设置」，
 * 否则显示「支出 {name} · 收入 {name}」（未设置的项显示「未设置」）。
 */
private fun defaultAccountSubtitle(expenseName: String?, incomeName: String?): String {
    if (expenseName == null && incomeName == null) return "未设置"
    return "支出 ${expenseName ?: "未设置"} · 收入 ${incomeName ?: "未设置"}"
}

/**
 * 「分类与账户」二级选择弹窗：点选后进入对应管理页面。
 */
@Composable
private fun CategoryAccountPickerDialog(
    onOpenCategory: () -> Unit,
    onOpenAccount: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分类与账户") },
        text = {
            Column {
                PickerEntryRow(
                    icon = Icons.Filled.Category,
                    title = "分类管理",
                    subtitle = "管理收支分类与图标",
                    onClick = onOpenCategory,
                )
                PickerEntryRow(
                    icon = Icons.Filled.AccountBalance,
                    title = "账户管理",
                    subtitle = "管理账户与余额",
                    onClick = onOpenAccount,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/** 「分类与账户」弹窗内的单行入口 */
@Composable
private fun PickerEntryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 「周期与借贷」二级选择弹窗：点选后进入对应管理页面。
 */
@Composable
private fun RecurringDebtPickerDialog(
    onOpenRecurring: () -> Unit,
    onOpenDebt: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("周期与借贷") },
        text = {
            Column {
                PickerEntryRow(
                    icon = Icons.Filled.Repeat,
                    title = "周期性记账",
                    subtitle = "自动记录固定收支",
                    onClick = onOpenRecurring,
                )
                PickerEntryRow(
                    icon = Icons.Filled.Handshake,
                    title = "借贷记录",
                    subtitle = "跟踪应收应付",
                    onClick = onOpenDebt,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/**
 * 「默认账户」选择对话框：同时设置默认支出账户与默认收入账户。
 *
 * 列出全部账户，分「支出账户」「收入账户」两个区块，选中项右侧带 Check 标记。
 * 选择后不自动关闭，便于用户连续设置两项后再手动关闭。
 * 当无可用账户时提示用户先创建账户。
 *
 * @param accounts           全部可用账户
 * @param expenseAccountId   当前默认支出账户 id（null 表示未设置）
 * @param incomeAccountId    当前默认收入账户 id（null 表示未设置）
 * @param onSelectExpense    选择支出账户回调
 * @param onSelectIncome     选择收入账户回调
 * @param onDismiss          关闭弹窗回调
 */
@Composable
private fun DefaultAccountPickerDialog(
    accounts: List<Account>,
    expenseAccountId: Long?,
    incomeAccountId: Long?,
    onSelectExpense: (Long) -> Unit,
    onSelectIncome: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("默认账户") },
        text = {
            if (accounts.isEmpty()) {
                Text("暂无可用账户，请先在「分类与账户」中创建账户。")
            } else {
                Column {
                    // 支出账户区块
                    Text(
                        "支出账户",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    accounts.forEach { account ->
                        AccountPickerRow(
                            account = account,
                            selected = account.id == expenseAccountId,
                            onClick = { onSelectExpense(account.id) },
                        )
                    }
                    Spacer(Modifier.size(Spacing.s))
                    // 收入账户区块
                    Text(
                        "收入账户",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    accounts.forEach { account ->
                        AccountPickerRow(
                            account = account,
                            selected = account.id == incomeAccountId,
                            onClick = { onSelectIncome(account.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/** 默认账户弹窗内的单行账户项：色块 + 名称，选中项右侧带 Check */
@Composable
private fun AccountPickerRow(
    account: Account,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(account.color)),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = account.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "已选择",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

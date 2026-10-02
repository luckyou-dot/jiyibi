package com.jiyibi.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.jiyibi.app.core.common.AppVersion
import com.jiyibi.app.core.designsystem.component.Corner
import com.jiyibi.app.core.designsystem.component.GlassCard
import com.jiyibi.app.core.designsystem.component.Spacing
import com.jiyibi.app.core.designsystem.component.UnifiedCard
import com.jiyibi.app.core.designsystem.component.UnifiedCardVariant
import com.jiyibi.app.core.designsystem.component.listItemEnterAnimation
import com.jiyibi.app.core.designsystem.theme.gradientBrush

/**
 * 关于应用页：展示应用信息、四大页面介绍（点击查看详情）、署名与联系方式。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    // 当前展开详情的页面索引，null 表示未展开
    var detailPage by remember { mutableStateOf<PageIntro?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于应用") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = Spacing.s),
        ) {
            // 顶部渐变 Hero 区
            item { HeroSection() }

            // 页面介绍
            item { SectionLabel("页面介绍") }
            item {
                UnifiedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.l),
                    variant = UnifiedCardVariant.ELEVATED,
                    contentPadding = PaddingValues(vertical = Spacing.s),
                ) {
                    PageIntro.entries.forEachIndexed { index, page ->
                        PageIntroItem(
                            page = page,
                            index = index,
                            onClick = { detailPage = page },
                        )
                    }
                }
            }

            // 署名
            item { SectionLabel("署名") }
            item { CreditCard() }
        }
    }

    // 页面详情弹窗
    detailPage?.let { page ->
        PageDetailDialog(page = page, onDismiss = { detailPage = null })
    }
}

/** 顶部渐变 Hero：圆角渐变底 + 毛玻璃圆形图标 + 应用名 + 版本号 + 简介，文字白色保证可读 */
@Composable
private fun HeroSection() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l, vertical = Spacing.m)
            .clip(RoundedCornerShape(Corner.large))
            .background(gradientBrush())
            .padding(vertical = Spacing.xxl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            // 毛玻璃圆形底包裹的钱包图标（36dp 圆角 = 72dp 直径的一半，形成正圆）
            GlassCard(
                modifier = Modifier.size(72.dp),
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
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            // 应用名（白色加粗）
            Text(
                text = "记一笔",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            // 版本号（白色）。运行时读取原因同 SettingsScreen.HeaderItem：
            // BuildConfig 常量内联 + 增量编译会让未改动文件永远显示旧版本
            val context = LocalContext.current
            val versionName = remember(context) { AppVersion.name(context) }
            Text(
                text = "v$versionName",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            // 一句话简介（白色半透明）
            Text(
                text = "一款简洁实用的个人记账应用",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}

/** 分组标题 */
@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Spacing.l,
                end = Spacing.l,
                top = Spacing.l,
                bottom = Spacing.s,
            ),
    )
}

/** 页面介绍项：圆形主题色底图标 + 标题 + 简短描述 + 右箭头，附入场动画 */
@Composable
private fun PageIntroItem(page: PageIntro, index: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .listItemEnterAnimation(index)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 圆形主题色底图标
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.size(Spacing.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = page.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = page.brief,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 页面详情弹窗：展示该页面的具体功能要点 */
@Composable
private fun PageDetailDialog(page: PageIntro, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = page.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.size(Spacing.s))
                Text(page.title)
            }
        },
        text = {
            Column {
                Text(
                    text = page.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.s),
                )
                page.features.forEach { feat ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                    ) {
                        Text(
                            text = "•",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        Text(
                            text = feat,
                            style = MaterialTheme.typography.bodyMedium,
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

/**
 * 署名卡片：白色描边卡片，两位人物并列居中。
 * 每人：圆形首字母徽章（浅色底 + 主题色边）+ 姓名 + 角色，中间细竖线分隔。
 */
@Composable
private fun CreditCard() {
    UnifiedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l),
        variant = UnifiedCardVariant.OUTLINED,
        contentPadding = PaddingValues(vertical = Spacing.xl),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CreditPerson(initial = "可", name = "可燃火鸡面", role = "创作者")
            VerticalDivider(
                modifier = Modifier.height(40.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            CreditPerson(initial = "月", name = "月亮旅店", role = "特邀嘉宾")
        }
    }
}

/** 单个署名人：首字母徽章 + 姓名 + 角色 */
@Composable
private fun CreditPerson(initial: String, name: String, role: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 圆形首字母徽章
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = role,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 四大页面介绍数据：图标、标题、简短描述、副标题、功能要点列表。
 */
private enum class PageIntro(
    val icon: ImageVector,
    val title: String,
    val brief: String,
    val subtitle: String,
    val features: List<String>,
) {
    HOME(
        icon = Icons.Filled.Dashboard,
        title = "首页",
        brief = "今日支出 · 快速记账 · 最近交易",
        subtitle = "应用主入口，快速记录每一笔",
        features = listOf(
            "今日支出概览，一眼掌握当日花费",
            "本月预算进度条，实时查看预算使用情况",
            "快速记账入口，支持手动输入与 OCR 识别",
            "最近交易列表，支持点击编辑",
            "多维搜索，按账户/标签/类型筛选历史记录",
        ),
    ),
    STATISTICS(
        icon = Icons.Filled.BarChart,
        title = "统计",
        brief = "支出趋势 · 分类占比 · 月度对比",
        subtitle = "多维度数据分析你的收支结构",
        features = listOf(
            "支出趋势折线图，按日期展示整月花费",
            "分类占比饼图，了解钱花在了哪里",
            "月度收支对比，掌握结余情况",
            "支持按时间范围筛选（本周/本月/本年）",
        ),
    ),
    BUDGET(
        icon = Icons.Filled.AccountBalanceWallet,
        title = "预算",
        brief = "总预算 · 分类预算 · 进度追踪",
        subtitle = "设定预算目标，控制消费节奏",
        features = listOf(
            "总预算管理，设定本月总支出上限",
            "分类预算，为不同类目分别设定额度",
            "进度条可视化展示预算使用情况",
            "超支预警，避免冲动消费",
        ),
    ),
    SETTINGS(
        icon = Icons.Filled.Settings,
        title = "我的",
        brief = "资产看板 · 分类账户 · 备份恢复",
        subtitle = "个性化设置与数据管理中心",
        features = listOf(
            "资产看板，总资产 / 本月结余 / 待收一目了然",
            "分类与账户，管理收支分类与多账户余额",
            "默认账户，记一笔自动套用支出/收入账户",
            "周期与借贷，自动记录固定收支与跟踪应收应付",
            "标签管理，给交易打上自定义标签",
            "备份与恢复，导出 CSV / JSON 数据",
            "主题切换，多种配色风格",
        ),
    ),
}

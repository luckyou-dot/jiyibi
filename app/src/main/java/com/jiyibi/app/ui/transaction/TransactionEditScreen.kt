package com.jiyibi.app.ui.transaction

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiyibi.app.core.designsystem.component.Corner
import com.jiyibi.app.core.designsystem.component.GlassCard
import com.jiyibi.app.core.designsystem.component.LoadingState
import com.jiyibi.app.core.designsystem.component.Spacing
import com.jiyibi.app.core.designsystem.component.UnifiedCard
import com.jiyibi.app.core.designsystem.component.UnifiedCardVariant
import com.jiyibi.app.core.designsystem.component.categoryIconByKey
import com.jiyibi.app.core.designsystem.theme.ExpenseRed
import com.jiyibi.app.core.designsystem.theme.IncomeGreen
import com.jiyibi.app.core.designsystem.theme.gradientBrush
import com.jiyibi.app.core.domain.model.Account
import com.jiyibi.app.core.domain.model.Category
import com.jiyibi.app.core.domain.model.RecurringFrequency
import com.jiyibi.app.core.domain.model.Transaction
import com.jiyibi.app.core.domain.model.TransactionType
import com.jiyibi.app.core.domain.model.centsToYuan
import com.jiyibi.app.core.domain.model.yuanToCents
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.json.JSONObject

/** 交易类型对应的中文标签 */
private fun TransactionType.label(): String = when (this) {
    TransactionType.EXPENSE -> "支出"
    TransactionType.INCOME -> "收入"
    TransactionType.TRANSFER -> "转账"
}

/** Hero 渐变区高度:容纳透明 TopAppBar + 毛玻璃分段按钮 + 金额输入 + 分类卡片顶部 */
private val HeroHeight = 280.dp

/**
 * 新增/编辑交易页。
 *
 * 3-5 秒快速记账目标:金额输入框获得焦点、默认支出、分类快捷网格。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionEditScreen(
    onSaved: () -> Unit,
    onAddCategory: () -> Unit = {},
    viewModel: TransactionEditViewModel = hiltViewModel(),
) {
    val isEditMode = viewModel.isEditMode
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val expenseCategories by viewModel.expenseCategories.collectAsStateWithLifecycle()
    val incomeCategories by viewModel.incomeCategories.collectAsStateWithLifecycle()
    val editingTransaction by viewModel.editingTransaction.collectAsStateWithLifecycle()
    val defaultExpenseAccountId by viewModel.defaultExpenseAccountId.collectAsStateWithLifecycle()
    val defaultIncomeAccountId by viewModel.defaultIncomeAccountId.collectAsStateWithLifecycle()

    // 一键补记预填:从 navArg 读取并应用(仅首次进入生效)
    val prefill = viewModel.prefill
    val prefillAmount by viewModel.prefillAmount.collectAsStateWithLifecycle()
    val prefillNote by viewModel.prefillNote.collectAsStateWithLifecycle()
    var prefillApplied by remember { mutableStateOf(false) }

    // 表单状态
    var currentType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var selectedToAccountId by remember { mutableStateOf<Long?>(null) }
    var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var note by remember { mutableStateOf("") }

    // 用户是否手动修改过账户：为 false 时切换收支类型会自动切换到对应默认账户
    var userModifiedAccount by remember { mutableStateOf(false) }

    // 周期性记账开关与频率
    var recurringEnabled by remember { mutableStateOf(false) }
    var recurringFrequency by remember { mutableStateOf(RecurringFrequency.MONTHLY) }

    // 标签相关状态
    val selectedTags = remember { mutableStateListOf<String>() }
    var showTagDialog by remember { mutableStateOf(false) }
    var newTagText by remember { mutableStateOf("") }
    val existingTags by viewModel.existingTags.collectAsStateWithLifecycle()

    // 编辑模式:交易加载后填充表单
    LaunchedEffect(editingTransaction) {
        editingTransaction?.let { tx ->
            currentType = tx.type
            amountText = tx.amount.centsToYuan().toPlainString()
            selectedCategoryId = tx.categoryId
            selectedAccountId = tx.accountId
            selectedToAccountId = tx.toAccountId
            selectedDate = tx.date
            note = tx.note
            selectedTags.clear()
            selectedTags.addAll(tx.tags)
            // 编辑模式账户来自交易数据,视为已确定,不被默认账户逻辑覆盖
            userModifiedAccount = true
        }
    }

    // 一键补记预填:首次进入且 prefill 非空时,URL 解码 + JSON 解析后调用 applyPrefill
    LaunchedEffect(prefill) {
        if (prefill.isEmpty() || prefillApplied) return@LaunchedEffect
        prefillApplied = true
        try {
            val decoded = URLDecoder.decode(prefill, "UTF-8")
            val json = JSONObject(decoded)
            val amount = json.optLong("amount", 0L)
            val noteStr = json.optString("note", "")
            viewModel.applyPrefill(amount, noteStr)
        } catch (e: Exception) {
            // 解析失败忽略,不影响正常录入
        }
    }

    // 预填金额(单位:分)→ 表单 amountText(元)
    LaunchedEffect(prefillAmount) {
        prefillAmount?.let { amountText = it.centsToYuan().toPlainString() }
    }

    // 预填备注 → 表单 note
    LaunchedEffect(prefillNote) {
        prefillNote?.let { note = it }
    }

    // 账户默认选中:根据收支类型选择对应默认账户,失效(未设置/被删除)时回退到列表第一个
    LaunchedEffect(accounts, defaultExpenseAccountId, defaultIncomeAccountId) {
        if (accounts.isEmpty()) return@LaunchedEffect
        // 用户已手动修改账户或编辑模式已加载交易,不覆盖账户选择
        if (userModifiedAccount) return@LaunchedEffect
        // 根据当前收支类型取默认账户 id
        val defaultId = if (currentType == TransactionType.EXPENSE) defaultExpenseAccountId else defaultIncomeAccountId
        // 默认账户失效(被删除/未设置)时回退到列表第一个
        selectedAccountId = if (defaultId != null && accounts.any { it.id == defaultId }) {
            defaultId
        } else {
            accounts.first().id
        }
        // 转账目标账户默认取第二个账户(与原逻辑一致)
        if (selectedToAccountId == null && accounts.size > 1) {
            selectedToAccountId = accounts.getOrNull(1)?.id
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val noteFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // 新建模式下,进入页面让金额输入框获得焦点
    SideEffect {
        if (!isEditMode && amountText.isEmpty()) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    // 日期选择器
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate)

    // 校验结果与保存逻辑:底部保存条按钮和键盘「完成」共用同一段,避免两处漂移
    val amountValid = amountText.toDoubleOrNull()?.let { it > 0 } ?: false
    val accountValid = selectedAccountId != null
    val performSave: () -> Unit = {
        if (!amountValid || !accountValid) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (!amountValid) "请输入大于 0 的金额" else "请选择账户",
                )
            }
        } else {
            // 保存前先收起键盘,避免导航返回时输入法残留闪烁
            keyboardController?.hide()
            focusManager.clearFocus()
            viewModel.save(
                buildTransaction(
                    viewModel, currentType, amountText, selectedAccountId,
                    selectedToAccountId, selectedCategoryId, selectedDate, note, selectedTags,
                ),
                onSaved,
            )
            // 开启周期记账时同步创建规则
            if (recurringEnabled) {
                viewModel.saveRecurringRule(
                    amount = amountText.toDoubleOrNull()?.yuanToCents() ?: 0,
                    type = currentType,
                    accountId = selectedAccountId ?: 0L,
                    categoryId = selectedCategoryId,
                    frequency = recurringFrequency,
                    nextRunAt = calculateNextRun(recurringFrequency),
                )
            }
        }
    }

    // 根 Box:底层固定渐变 Hero 背景 + 透明 Scaffold 叠加,使 TopAppBar 透明地浮于渐变之上
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 顶部渐变 Hero 背景层:固定高度,位于屏幕顶部
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
                    title = {
                        Text(
                            if (isEditMode) "编辑交易" else "记一笔",
                            color = Color.White,
                        )
                    },
                    actions = {
                        // 编辑模式:删除用半透明白底圆钮,与右侧保存钮同尺寸
                        if (isEditMode) {
                            Box(
                                modifier = Modifier
                                    .padding(end = Spacing.s)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.18f))
                                    .clickable {
                                        viewModel.delete(viewModel.transactionId, onSaved)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除",
                                    tint = Color.White,
                                )
                            }
                        }
                        // 保存:右上角主色圆钮,固定在顶栏,任何键盘/滚动状态都直接可点
                        Box(
                            modifier = Modifier
                                .padding(end = Spacing.m)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { performSave() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "保存",
                                tint = Color.White,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            // 键盘弹出时把 Snackbar 顶到键盘上方,校验提示不被遮挡
            snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.imePadding()) },
        ) { padding ->
            // 编辑模式交易尚未加载完成:展示加载态
            if (isEditMode && editingTransaction == null) {
                LoadingState(modifier = Modifier.padding(padding))
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxWidth()
                    // 键盘弹出时收窄可视区,保证正在输入的控件不被键盘盖住
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.l, vertical = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                // === Hero 区:毛玻璃卡包裹分段按钮 + 金额输入 ===
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = Corner.large,
                    contentPadding = PaddingValues(Spacing.m),
                ) {
                    // 1. 分段按钮:支出/收入(去掉转账)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf(TransactionType.EXPENSE, TransactionType.INCOME).forEachIndexed { idx, type ->
                            SegmentedButton(
                                selected = currentType == type,
                                onClick = {
                                    currentType = type
                                    // 切换类型时清空分类
                                    selectedCategoryId = null
                                    // 用户未手动修改账户时,自动切换到对应默认账户
                                    if (!userModifiedAccount && accounts.isNotEmpty()) {
                                        val defaultId = if (type == TransactionType.EXPENSE) {
                                            defaultExpenseAccountId
                                        } else {
                                            defaultIncomeAccountId
                                        }
                                        selectedAccountId = if (defaultId != null && accounts.any { it.id == defaultId }) {
                                            defaultId
                                        } else {
                                            accounts.first().id
                                        }
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(idx, 2),
                            ) {
                                Text(type.label())
                            }
                        }
                    }

                    Spacer(Modifier.height(Spacing.s))

                    // 2. 金额输入:大字等宽,¥ 前缀主题色,文字按收支语义着色
                    val amountColor = if (currentType == TransactionType.EXPENSE) ExpenseRed else IncomeGreen
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("金额") },
                        prefix = { Text("¥", color = MaterialTheme.colorScheme.primary) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = amountColor,
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            // 金额回车直接跳到备注,不用手动点过去
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { noteFocusRequester.requestFocus() },
                        ),
                        shape = RoundedCornerShape(Corner.medium),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                }

                // === 3. 分类网格 ===
                UnifiedCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = UnifiedCardVariant.ELEVATED,
                    contentPadding = PaddingValues(Spacing.m),
                ) {
                    Text("选择分类", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(Spacing.s))
                    val cats = if (currentType == TransactionType.EXPENSE) expenseCategories else incomeCategories
                    CategoryGrid(
                        categories = cats,
                        selectedId = selectedCategoryId,
                        onSelect = { selectedCategoryId = it },
                        onAddCategory = onAddCategory,
                    )
                }

                // === 4-6. 详情分组:备注 + 账户 + 日期 ===
                UnifiedCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = UnifiedCardVariant.ELEVATED,
                    contentPadding = PaddingValues(Spacing.m),
                ) {
                    Text("详情", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(Spacing.s))
                    // 4. 备注
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("备注") },
                        singleLine = false,
                        maxLines = 3,
                        shape = RoundedCornerShape(Corner.medium),
                        // 备注键盘「完成」直接保存,少一步收键盘/滚动
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { performSave() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(noteFocusRequester),
                    )
                    Spacer(Modifier.height(Spacing.s))
                    // 5. 账户选择
                    AccountSelector(
                        accounts = accounts,
                        selectedId = selectedAccountId,
                        onSelect = {
                            selectedAccountId = it
                            // 标记用户已手动修改账户,后续切换收支类型不再自动切换
                            userModifiedAccount = true
                        },
                    )
                    Spacer(Modifier.height(Spacing.s))
                    // 6. 日期选择
                    OutlinedTextField(
                        value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(Date(selectedDate)),
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("日期") },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Filled.CalendarMonth, contentDescription = "选择日期")
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(Corner.medium),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // === 7. 周期性记账(ELEVATED 卡) ===
                UnifiedCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = UnifiedCardVariant.ELEVATED,
                    contentPadding = PaddingValues(Spacing.m),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Repeat, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(Spacing.s))
                        Text("周期性记账", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = recurringEnabled, onCheckedChange = { recurringEnabled = it })
                    }
                    if (recurringEnabled) {
                        Spacer(Modifier.height(Spacing.s))
                        Text("频率", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            listOf(
                                "每日" to RecurringFrequency.DAILY,
                                "每周" to RecurringFrequency.WEEKLY,
                                "每月" to RecurringFrequency.MONTHLY,
                                "每年" to RecurringFrequency.YEARLY,
                            ).forEach { (label, freq) ->
                                FilterChip(
                                    selected = recurringFrequency == freq,
                                    onClick = { recurringFrequency = freq },
                                    label = { Text(label) },
                                )
                            }
                        }
                        Spacer(Modifier.height(Spacing.s))
                        val nextRunText = SimpleDateFormat("yyyy-MM-dd E", Locale.getDefault())
                            .format(Date(calculateNextRun(recurringFrequency)))
                        Text(
                            "下次自动记录:$nextRunText",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // === 8. 标签 ===
                UnifiedCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = UnifiedCardVariant.ELEVATED,
                    contentPadding = PaddingValues(Spacing.m),
                ) {
                    // 已选标签 Chip 行
                    if (selectedTags.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            selectedTags.forEach { tag ->
                                InputChip(
                                    selected = false,
                                    onClick = { selectedTags.remove(tag) },
                                    label = { Text(tag) },
                                    trailingIcon = {
                                        Icon(Icons.Filled.Close, "移除", Modifier.size(12.dp))
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(Spacing.s))
                    }
                    // 标签输入行
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Label, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(Spacing.s))
                        Text(if (selectedTags.isEmpty()) "添加标签" else "标签", modifier = Modifier.weight(1f))
                        TextButton(onClick = { showTagDialog = true }) {
                            Text("+ 添加")
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.xs))
            }
        }
    }

    // 日期对话框(M3 DatePickerDialog)
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Button(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDate = it }
                        showDatePicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // OCR 识别中对话框:识别完成自动关闭
    // 标签选择底部弹窗
    if (showTagDialog) {
        val recommendedTags = remember {
            listOf("刚需", "可选", "报销中", "AA待收", "家人", "朋友")
        }
        val displayTags = if (existingTags.isNotEmpty()) existingTags else recommendedTags
        ModalBottomSheet(
            onDismissRequest = { showTagDialog = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text("选择标签", style = MaterialTheme.typography.titleMedium)
                if (selectedTags.isNotEmpty()) {
                    Text(
                        "已选:${selectedTags.joinToString("、")}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    displayTags.forEach { tag ->
                        FilterChip(
                            selected = tag in selectedTags,
                            onClick = {
                                if (tag in selectedTags) selectedTags.remove(tag)
                                else selectedTags.add(tag)
                            },
                            label = { Text(tag) },
                        )
                    }
                }
                if (existingTags.isEmpty()) {
                    Text(
                        "以上为推荐标签,开始记账后会按你的使用习惯排序",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.s))
                OutlinedTextField(
                    value = newTagText,
                    onValueChange = { newTagText = it },
                    label = { Text("自定义标签") },
                    singleLine = true,
                    shape = RoundedCornerShape(Corner.medium),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            if (newTagText.isNotBlank() && newTagText !in selectedTags) {
                                selectedTags.add(newTagText.trim())
                                newTagText = ""
                            }
                        }) { Icon(Icons.Filled.Add, "添加") }
                    },
                )
                Spacer(Modifier.height(Spacing.m))
                Button(onClick = { showTagDialog = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("完成")
                }
                Spacer(Modifier.height(Spacing.m))
            }
        }
    }
}

/** 根据表单状态构造 Transaction */
private fun buildTransaction(
    viewModel: TransactionEditViewModel,
    type: TransactionType,
    amountText: String,
    accountId: Long?,
    toAccountId: Long?,
    categoryId: Long?,
    date: Long,
    note: String,
    selectedTags: List<String>,
): Transaction {
    val cents = amountText.toDoubleOrNull()?.yuanToCents() ?: 0L
    return Transaction(
        id = if (viewModel.isEditMode) viewModel.transactionId else 0L,
        type = type,
        amount = cents,
        accountId = accountId ?: 0L,
        toAccountId = if (type == TransactionType.TRANSFER) toAccountId else null,
        categoryId = if (type == TransactionType.TRANSFER) null else categoryId,
        note = note,
        tags = selectedTags.toList(),
        date = date,
    )
}

/** 根据频率计算下次自动记录的时间戳(毫秒),以当前时间为起点 */
private fun calculateNextRun(freq: RecurringFrequency): Long {
    val now = System.currentTimeMillis()
    val day = 86_400_000L
    return when (freq) {
        RecurringFrequency.DAILY -> now + day
        RecurringFrequency.WEEKLY -> now + day * 7
        RecurringFrequency.MONTHLY -> now + day * 30
        RecurringFrequency.YEARLY -> now + day * 365
    }
}

/** 分类网格:4 列,每个 item 居中显示圆形渐变底图标 + 名称,末尾固定一个「+」新增入口 */
@Composable
private fun CategoryGrid(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onAddCategory: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(categories, key = { it.id }) { cat ->
            CategoryItem(
                name = cat.name,
                iconKey = cat.icon,
                color = cat.color,
                selected = selectedId == cat.id,
                onClick = { onSelect(cat.id) },
            )
        }
        item(key = "add") {
            CategoryItem(
                name = "新建",
                iconKey = null,
                color = 0,
                selected = false,
                isAdd = true,
                onClick = onAddCategory,
            )
        }
    }
}

/** 单个分类项:圆形渐变底图标,选中加主题色描边 + 缩放动效 */
@Composable
private fun CategoryItem(
    name: String,
    iconKey: String?,
    color: Int,
    selected: Boolean,
    isAdd: Boolean = false,
    onClick: () -> Unit,
) {
    val categoryColor = if (color != 0) Color(color) else MaterialTheme.colorScheme.primary
    // 圆形渐变底色:分类色 → 半透明渐变;新建项用主题色渐变
    val circleBrush = if (isAdd) {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                categoryColor,
                categoryColor.copy(alpha = 0.25f),
            )
        )
    }
    // 缩放动效:选中 1.0,未选 0.9
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.9f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "categoryScale",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(Corner.medium))
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 圆形渐变底图标,选中时加主题色描边
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(circleBrush)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isAdd) Icons.Filled.Add else categoryIconByKey(iconKey),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.White,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** 单个账户下拉选择 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSelector(
    accounts: List<Account>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    val selected = accounts.firstOrNull { it.id == selectedId }
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected?.name ?: "请选择账户",
            onValueChange = { },
            readOnly = true,
            label = { Text("账户") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(Corner.medium),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            accounts.forEach { acc ->
                DropdownMenuItem(
                    text = { Text(acc.name) },
                    onClick = {
                        onSelect(acc.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

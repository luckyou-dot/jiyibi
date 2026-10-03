package com.jiyibi.app.ui.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiyibi.app.core.designsystem.component.LoadingState
import com.jiyibi.app.core.designsystem.component.Spacing
import com.jiyibi.app.core.designsystem.theme.gradientBrush
import com.jiyibi.app.core.domain.model.RecurringFrequency
import com.jiyibi.app.core.domain.model.TransactionType
import com.jiyibi.app.core.domain.model.centsToYuan
import com.jiyibi.app.core.domain.model.yuanToCents
import java.net.URLDecoder
import kotlinx.coroutines.launch
import org.json.JSONObject


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
                AmountHeroCard(
                    currentType = currentType,
                    amountText = amountText,
                    onTypeSelect = { type ->
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
                    onAmountChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    focusRequester = focusRequester,
                    noteFocusRequester = noteFocusRequester,
                )

                // === 3. 分类网格 ===
                CategoryPickCard(
                    categories = if (currentType == TransactionType.EXPENSE) expenseCategories else incomeCategories,
                    selectedId = selectedCategoryId,
                    onSelect = { selectedCategoryId = it },
                    onAddCategory = onAddCategory,
                )

                // === 4-6. 详情分组:备注 + 账户 + 日期 ===
                TransactionDetailCard(
                    note = note,
                    onNoteChange = { note = it },
                    accounts = accounts,
                    selectedAccountId = selectedAccountId,
                    onAccountSelect = {
                        selectedAccountId = it
                        // 标记用户已手动修改账户,后续切换收支类型不再自动切换
                        userModifiedAccount = true
                    },
                    selectedDate = selectedDate,
                    onPickDate = { showDatePicker = true },
                    noteFocusRequester = noteFocusRequester,
                    onNoteDone = performSave,
                )

                // === 7. 周期性记账(ELEVATED 卡) ===
                RecurringCard(
                    enabled = recurringEnabled,
                    onEnabledChange = { recurringEnabled = it },
                    frequency = recurringFrequency,
                    onFrequencyChange = { recurringFrequency = it },
                )

                // === 8. 标签 ===
                TagsCard(
                    selectedTags = selectedTags,
                    onRemoveTag = { selectedTags.remove(it) },
                    onOpenPicker = { showTagDialog = true },
                )

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
        TagPickerSheet(
            existingTags = existingTags,
            selectedTags = selectedTags,
            newTagText = newTagText,
            onNewTagChange = { newTagText = it },
            onToggleTag = { tag ->
                if (tag in selectedTags) selectedTags.remove(tag) else selectedTags.add(tag)
            },
            onAddNewTag = {
                if (newTagText.isNotBlank() && newTagText !in selectedTags) {
                    selectedTags.add(newTagText.trim())
                    newTagText = ""
                }
            },
            onDismiss = { showTagDialog = false },
        )
    }
}

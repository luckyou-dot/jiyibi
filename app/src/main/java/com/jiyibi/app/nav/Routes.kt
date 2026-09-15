package com.jiyibi.app.nav

/**
 * 导航路由常量。
 */
object Routes {
    const val HOME = "home"
    const val STATISTICS = "statistics"
    const val BUDGET = "budget"
    const val SETTINGS = "settings"

    /** 新增/编辑交易，可选交易 id；prefill 用于智能补记预填 JSON */
    const val TRANSACTION_EDIT = "transaction/edit?transactionId={transactionId}&prefill={prefill}"

    /** 搜索页 */
    const val SEARCH = "search"

    /** 分类管理（二级页，挂在 settings 下） */
    const val CATEGORY_MANAGE = "settings/category"

    /** 账户管理（二级页，挂在 settings 下） */
    const val ACCOUNT_MANAGE = "settings/account"

    /** 周期性记账列表（二级页，挂在 settings 下） */
    const val RECURRING = "settings/recurring"

    /** 债务列表（二级页，挂在 settings 下） */
    const val DEBT = "settings/debt"

    /** 标签管理（二级页，挂在 settings 下） */
    const val TAG_MANAGE = "tag_manage"

    /** 自动记账（通知监听，二级页，挂在 settings 下） */
    const val AUTO_RECORD = "settings/auto_record"

    /** 备份与导出（二级页，挂在 settings 下） */
    const val BACKUP = "settings/backup"

    /** 关于应用（二级页，挂在 settings 下） */
    const val ABOUT = "settings/about"

    /** 意见反馈（二级页，挂在 settings 下） */
    const val FEEDBACK = "settings/feedback"

    /** 新增/编辑预算，可选预算 id；forceCategory=true 强制分类预算模式 */
    const val BUDGET_EDIT = "budget/edit?budgetId={budgetId}&forceCategory={forceCategory}"

    fun transactionEdit(transactionId: Long? = null, prefill: String = ""): String {
        val id = transactionId ?: -1L
        val encodedPrefill = if (prefill.isEmpty()) "" else java.net.URLEncoder.encode(prefill, "UTF-8")
        return "transaction/edit?transactionId=$id&prefill=$encodedPrefill"
    }

    /** budgetId 为 null 表示新建预算，使用 -1 作为占位
     *  forceCategory=true 强制进入分类预算模式（用于 BudgetScreen 的分类预算"+"按钮）
     */
    fun budgetEdit(budgetId: Long? = null, forceCategory: Boolean = false): String {
        val id = if (budgetId == null) -1L else budgetId
        return "budget/edit?budgetId=$id&forceCategory=${forceCategory}"
    }
}

package com.jiyibi.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jiyibi.app.BuildConfig
import com.jiyibi.app.core.database.AppDatabase
import com.jiyibi.app.core.database.dao.AccountDao
import com.jiyibi.app.core.database.dao.BudgetDao
import com.jiyibi.app.core.database.dao.CategoryDao
import com.jiyibi.app.core.database.dao.DebtDao
import com.jiyibi.app.core.database.dao.RecurringRuleDao
import com.jiyibi.app.core.database.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        // 每次应用版本变化后、首次打开数据库前先把 .db 复制一份兜底：
        // 将来某次 schema 迁移写错，或迁移中途进程被杀，用户仍能从 filesDir/db_backup/ 恢复。
        backupDatabaseOncePerAppUpgrade(context)
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            // 缺失 Migration 时抛 IllegalStateException，而不是静默删表清空用户账目
            // （策略与升版本流程见 AppDatabase.MIGRATIONS 的注释）
            .addMigrations(*AppDatabase.MIGRATIONS)
            .addCallback(object : RoomDatabase.Callback() {
                // 首次创建数据库时预置默认账户与分类，避免「记一笔」页选择不到账户/分类
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    seedDefaultData(db)
                }
            })
            .build()
    }

    /**
     * 每个 [BuildConfig.VERSION_CODE] 只备份一次数据库文件，保留最近 [MAX_DB_BACKUPS] 份。
     *
     * 触发时机是 Hilt 第一次提供 AppDatabase 的时候（主线程）。一个个人账本通常几百 KB，
     * 复制耗时在几十毫秒量级，且每个应用版本只发生一次，不值得为它另开协程把问题复杂化。
     * 备份失败一律忽略：宁可少一份保险，也不能让数据库打不开。
     */
    private fun backupDatabaseOncePerAppUpgrade(context: Context) {
        val marker = context.getSharedPreferences("db_backup_marker", Context.MODE_PRIVATE)
        val current = BuildConfig.VERSION_CODE
        val previous = marker.getInt(KEY_BACKED_UP_VERSION, -1)
        if (previous == current) return
        runCatching {
            val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
            if (dbFile.exists()) {
                val dir = File(context.filesDir, DB_BACKUP_DIR).apply { mkdirs() }
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val target = File(dir, "${AppDatabase.DB_NAME}-v$previous-$stamp.db")
                dbFile.copyTo(target, overwrite = true)
                // WAL 模式下最近的写入还在 -wal 里，只拷主文件会丢掉最后几笔
                val wal = File(dbFile.parentFile, "${AppDatabase.DB_NAME}-wal")
                if (wal.exists()) wal.copyTo(File(dir, "${target.name}-wal"), overwrite = true)
                dir.listFiles()
                    ?.sortedByDescending { it.lastModified() }
                    ?.drop(MAX_DB_BACKUPS)
                    ?.forEach { it.delete() }
            }
        }
        marker.edit().putInt(KEY_BACKED_UP_VERSION, current).apply()
    }

    private const val KEY_BACKED_UP_VERSION = "backed_up_version_code"
    private const val DB_BACKUP_DIR = "db_backup"
    private const val MAX_DB_BACKUPS = 5

    /** 预置：4 个默认账户 + 8 个支出分类 + 4 个收入分类 */
    private fun seedDefaultData(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()

        // 默认账户：现金 / 支付宝 / 微信 / 银行卡
        listOf(
            Triple("现金", "CASH", 0xFF2E7D6F.toInt()),
            Triple("支付宝", "ALIPAY", 0xFF1976D2.toInt()),
            Triple("微信", "WECHAT", 0xFF00897B.toInt()),
            Triple("银行卡", "BANK", 0xFFFFB300.toInt()),
        ).forEachIndexed { idx, (name, type, color) ->
            db.execSQL(
                "INSERT INTO accounts (name, type, balance, initialBalance, color, sortOrder, archived, createdAt) " +
                    "VALUES ('$name', '$type', 0, 0, $color, $idx, 0, $now)",
            )
        }

        // 默认支出分类（含色值）
        val expenseCategories = listOf(
            Triple("餐饮", "Restaurant", 0xFFFF7043.toInt()),
            Triple("交通", "DirectionsCar", 0xFF42A5F5.toInt()),
            Triple("购物", "ShoppingBag", 0xFFAB47BC.toInt()),
            Triple("娱乐", "SportsEsports", 0xFF26A69A.toInt()),
            Triple("居住", "Home", 0xFF8D6E63.toInt()),
            Triple("医疗", "LocalHospital", 0xFFEF5350.toInt()),
            Triple("教育", "School", 0xFF5C6BC0.toInt()),
            Triple("转账", "SwapHoriz", 0xFF26A69A.toInt()),
            Triple("其他", "Category", 0xFF78909C.toInt()),
        )
        expenseCategories.forEachIndexed { idx, (name, icon, color) ->
            db.execSQL(
                "INSERT INTO categories (name, kind, icon, color, sortOrder, builtin, archived) " +
                    "VALUES ('$name', 'EXPENSE', '$icon', $color, $idx, 1, 0)",
            )
        }

        // 默认收入分类（9 个，覆盖常见收入场景，含色值）
        val incomeCategories = listOf(
            Triple("工资", "Work", 0xFF66BB6A.toInt()),
            Triple("副业", "Business", 0xFF26C6DA.toInt()),
            Triple("理财收益", "TrendingUp", 0xFFFFCA28.toInt()),
            Triple("储蓄", "Savings", 0xFF7E57C2.toInt()),
            Triple("退款", "Undo", 0xFF42A5F5.toInt()),
            Triple("报销", "Paid", 0xFF9CCC65.toInt()),
            Triple("红包礼金", "CardGiftcard", 0xFFEC407A.toInt()),
            Triple("中奖", "EmojiEvents", 0xFFFFA726.toInt()),
            Triple("其他", "Category", 0xFF78909C.toInt()),
        )
        incomeCategories.forEachIndexed { idx, (name, icon, color) ->
            db.execSQL(
                "INSERT INTO categories (name, kind, icon, color, sortOrder, builtin, archived) " +
                    "VALUES ('$name', 'INCOME', '$icon', $color, $idx, 1, 0)",
            )
        }
    }

    @Provides fun transactionDao(db: AppDatabase): TransactionDao = db.transactionDao()
    @Provides fun accountDao(db: AppDatabase): AccountDao = db.accountDao()
    @Provides fun categoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
    @Provides fun budgetDao(db: AppDatabase): BudgetDao = db.budgetDao()
    @Provides fun recurringRuleDao(db: AppDatabase): RecurringRuleDao = db.recurringRuleDao()
    @Provides fun debtDao(db: AppDatabase): DebtDao = db.debtDao()
}

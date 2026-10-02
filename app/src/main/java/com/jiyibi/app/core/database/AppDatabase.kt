package com.jiyibi.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jiyibi.app.core.database.dao.AccountDao
import com.jiyibi.app.core.database.dao.BudgetDao
import com.jiyibi.app.core.database.dao.CategoryDao
import com.jiyibi.app.core.database.dao.DebtDao
import com.jiyibi.app.core.database.dao.RecurringRuleDao
import com.jiyibi.app.core.database.dao.TransactionDao
import com.jiyibi.app.core.database.entity.AccountEntity
import com.jiyibi.app.core.database.entity.BudgetEntity
import com.jiyibi.app.core.database.entity.CategoryEntity
import com.jiyibi.app.core.database.entity.DebtEntity
import com.jiyibi.app.core.database.entity.RecurringRuleEntity
import com.jiyibi.app.core.database.entity.TransactionEntity

/**
 * Room 数据库，应用唯一数据源。
 *
 * 版本号自 1 起，后续 schema 变更需提供 Migration。
 */
@Database(
    entities = [
        TransactionEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
        RecurringRuleEntity::class,
        DebtEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun debtDao(): DebtDao

    companion object {
        const val DB_NAME = "jiyibi.db"

        /**
         * 显式迁移表。
         *
         * 本项目刻意**不用** `fallbackToDestructiveMigration()`：那个开关一旦生效，
         * 只要版本号提升而没有配好 Migration，Room 就会删表重建 —— 用户的全部账目
         * 在毫无提示的情况下清零，且本应用没有其他恢复手段。
         * 现在的策略是：缺 Migration 时 Room 抛 `IllegalStateException`（启动失败、
         * logcat 里有明确原因），坏在开发期比坏在用户手机上便宜得多；
         * 同时 `DatabaseModule` 会在每次应用版本变化后首次打开数据库前把 .db 复制到
         * `filesDir/db_backup/`，迁移真写错了也还能捞回来。
         *
         * 升版本三步走：
         * 1. 改实体 + `version` 提到 N，跑 `./gradlew :app:kspDebugKotlin`，
         *    生成 `app/schemas/com.jiyibi.app.core.database.AppDatabase/N.json`；
         * 2. 对照 N-1.json 与 N.json 的差异写 `Migration(N-1, N)`，加进本数组；
         * 3. 用旧版本 APK 造数据 → 覆盖安装新版本 → 确认数据仍在（别只测全新安装）。
         */
        val MIGRATIONS: Array<Migration> = arrayOf(
            // v3：补预置支出分类「转账」。种子数据只在 onCreate 插入，
            // 存量用户（v2 建的库）拿不到新分类，必须用迁移补齐。
            // sortOrder 接在「其他」(7) 之后；builtin=1 与其余预置分类一致。
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "INSERT INTO categories (name, kind, icon, color, sortOrder, builtin, archived) " +
                            "SELECT '转账', 'EXPENSE', 'SwapHoriz', 0xFF26A69A, 8, 1, 0 " +
                            "WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = '转账' AND kind = 'EXPENSE')",
                    )
                }
            },
        )
    }
}

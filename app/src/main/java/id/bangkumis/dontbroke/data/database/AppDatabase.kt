package id.bangkumis.dontbroke.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import id.bangkumis.dontbroke.data.local.dao.AccountDao
import id.bangkumis.dontbroke.data.local.dao.RECALC_ALL_BALANCES
import id.bangkumis.dontbroke.data.local.dao.TransactionDao
import id.bangkumis.dontbroke.data.local.entity.AccountEntity
import id.bangkumis.dontbroke.data.local.entity.TransactionEntity

@Database(
    entities = [TransactionEntity::class, AccountEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao

    companion object {
        /** Adds sourceOrAccount + location; existing rows keep their data. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN sourceOrAccount TEXT NOT NULL DEFAULT 'Cash'")
                db.execSQL("ALTER TABLE transactions ADD COLUMN location TEXT")
            }
        }

        /**
         * Adds the accounts table, adopts any account name already used by
         * existing transactions, then computes every balance.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_ACCOUNTS)
                db.execSQL(CREATE_ACCOUNTS_INDEX)
                // keep pre-migration transactions linked to a real account row
                db.execSQL(
                    "INSERT OR IGNORE INTO accounts (name, type, initialBalance, currentBalance, iconResOrName) " +
                        "SELECT DISTINCT sourceOrAccount, 'OTHER', 0.0, 0.0, NULL FROM transactions"
                )
                db.execSQL(RECALC_ALL_BALANCES)
            }
        }

        /**
         * Drops the accounts we used to pre-seed so everyone starts from a clean
         * slate, then heals any balance left over from an older formula.
         *
         * Only untouched seeds go: a row the user gave a starting balance or
         * already spent from is their data, not ours.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(DELETE_UNTOUCHED_SEEDS)
                db.execSQL(RECALC_ALL_BALANCES)
            }
        }

        /** Indexes transactions.timestamp — every list and aggregate sorts on it. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_TRANSACTIONS_TIMESTAMP_INDEX)
            }
        }

        // Must match Room's generated schema exactly or startup validation fails.
        internal const val CREATE_ACCOUNTS =
            "CREATE TABLE IF NOT EXISTS `accounts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`initialBalance` REAL NOT NULL, " +
                "`currentBalance` REAL NOT NULL, " +
                "`iconResOrName` TEXT)"

        internal const val CREATE_TRANSACTIONS_TIMESTAMP_INDEX =
            "CREATE INDEX IF NOT EXISTS `index_transactions_timestamp` ON `transactions` (`timestamp`)"

        internal const val CREATE_ACCOUNTS_INDEX =
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_accounts_name` ON `accounts` (`name`)"

        /**
         * Names v3 pre-seeded into every install. Kept only so [MIGRATION_3_4]
         * can recognise and remove them — never inserted again.
         */
        private val LEGACY_SEEDED_NAMES = listOf(
            "Cash", "BCA", "BNI", "Mandiri", "BRI", "Bank Jago", "SeaBank",
            "ShopeePay", "GoPay", "DANA", "OVO", "Flazz", "TapCash", "e-Money",
        )

        /**
         * Deletes a v3 seed only if the user never made it theirs — no starting
         * balance set and no transaction pointing at it. Names are inlined rather
         * than bound because the list is a compile-time constant containing no
         * quotes, which keeps the statement runnable verbatim in tests.
         */
        internal val DELETE_UNTOUCHED_SEEDS =
            "DELETE FROM accounts WHERE name IN (" +
                LEGACY_SEEDED_NAMES.joinToString(",") { "'$it'" } + ") " +
                "AND initialBalance = 0.0 " +
                "AND name NOT IN (SELECT DISTINCT sourceOrAccount FROM transactions)"
    }
}

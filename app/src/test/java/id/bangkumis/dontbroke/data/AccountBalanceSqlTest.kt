package id.bangkumis.dontbroke.data

import id.bangkumis.dontbroke.data.database.AppDatabase
import id.bangkumis.dontbroke.data.local.dao.RECALC_ALL_BALANCES
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runs the production balance SQL against real SQLite on the JVM. This is money
 * logic in a correlated subquery — a wrong sign or a missed recalc shows up as a
 * silently wrong wallet balance, so exercise the actual strings, not a copy.
 */
class AccountBalanceSqlTest {

    private lateinit var db: Connection

    /** v2 transactions schema, as Room generates it. */
    private val createTransactions =
        "CREATE TABLE IF NOT EXISTS `transactions` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount` INTEGER NOT NULL, " +
            "`type` TEXT NOT NULL, `category` TEXT NOT NULL, `note` TEXT NOT NULL, " +
            "`sourceOrAccount` TEXT NOT NULL DEFAULT 'Cash', `location` TEXT, " +
            "`timestamp` INTEGER NOT NULL)"

    // Room's ":name" binding is not JDBC syntax.
    private val recalcOne = "$RECALC_ALL_BALANCES WHERE accounts.name = ?"

    @Before
    fun setUp() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        db.createStatement().use {
            it.executeUpdate(createTransactions)
            it.executeUpdate(AppDatabase.CREATE_ACCOUNTS)
            it.executeUpdate(AppDatabase.CREATE_ACCOUNTS_INDEX)
        }
    }

    @After
    fun tearDown() = db.close()

    private fun addAccount(name: String, initial: Double = 0.0) {
        db.prepareStatement(
            "INSERT INTO accounts (name, type, initialBalance, currentBalance, iconResOrName) " +
                "VALUES (?, 'BANK', ?, ?, NULL)"
        ).use { it.setString(1, name); it.setDouble(2, initial); it.setDouble(3, initial); it.executeUpdate() }
    }

    private fun addTransaction(account: String, amount: Long, type: String): Long {
        db.prepareStatement(
            "INSERT INTO transactions (amount, type, category, note, sourceOrAccount, timestamp) " +
                "VALUES (?, ?, 'x', '', ?, 0)"
        ).use { it.setLong(1, amount); it.setString(2, type); it.setString(3, account); it.executeUpdate() }
        db.createStatement().use { s ->
            s.executeQuery("SELECT last_insert_rowid()").use { return it.also { it.next() }.getLong(1) }
        }
    }

    private fun recalc(account: String) =
        db.prepareStatement(recalcOne).use { it.setString(1, account); it.executeUpdate() }

    private fun balanceOf(account: String): Double =
        db.prepareStatement("SELECT currentBalance FROM accounts WHERE name = ?").use { st ->
            st.setString(1, account)
            st.executeQuery().use { it.next(); it.getDouble(1) }
        }

    @Test
    fun `income adds and expense subtracts on top of the initial balance`() {
        addAccount("BCA", initial = 100.0)
        addTransaction("BCA", 50, "INCOME")
        addTransaction("BCA", 20, "EXPENSE")
        recalc("BCA")
        assertEquals(130.0, balanceOf("BCA"), 0.001)
    }

    @Test
    fun `an account with no transactions falls back to its initial balance`() {
        addAccount("SeaBank", initial = 500.0)
        recalc("SeaBank")
        assertEquals(500.0, balanceOf("SeaBank"), 0.001)
    }

    @Test
    fun `each account only counts its own transactions`() {
        addAccount("Cash")
        addAccount("GoPay")
        addTransaction("Cash", 70, "INCOME")
        addTransaction("GoPay", 30, "INCOME")
        recalc("Cash")
        recalc("GoPay")
        assertEquals(70.0, balanceOf("Cash"), 0.001)
        assertEquals(30.0, balanceOf("GoPay"), 0.001)
    }

    @Test
    fun `deleting a transaction reverts its effect`() {
        addAccount("OVO")
        val id = addTransaction("OVO", 40, "EXPENSE")
        recalc("OVO")
        assertEquals(-40.0, balanceOf("OVO"), 0.001)

        db.createStatement().use { it.executeUpdate("DELETE FROM transactions WHERE id = $id") }
        recalc("OVO")
        assertEquals(0.0, balanceOf("OVO"), 0.001)
    }

    @Test
    fun `moving a transaction to another account rebalances both sides`() {
        addAccount("Cash")
        addAccount("ShopeePay")
        val id = addTransaction("Cash", 25, "EXPENSE")
        recalc("Cash")
        assertEquals(-25.0, balanceOf("Cash"), 0.001)

        db.createStatement().use {
            it.executeUpdate("UPDATE transactions SET sourceOrAccount = 'ShopeePay' WHERE id = $id")
        }
        recalc("Cash")
        recalc("ShopeePay")
        assertEquals("old account must be released", 0.0, balanceOf("Cash"), 0.001)
        assertEquals(-25.0, balanceOf("ShopeePay"), 0.001)
    }

    @Test
    fun `the bulk migration statement balances every account at once`() {
        addAccount("Cash", initial = 10.0)
        addAccount("BNI", initial = 1000.0)
        addTransaction("Cash", 5, "EXPENSE")
        addTransaction("BNI", 500, "INCOME")
        db.createStatement().use { it.executeUpdate(RECALC_ALL_BALANCES) }
        assertEquals(5.0, balanceOf("Cash"), 0.001)
        assertEquals(1500.0, balanceOf("BNI"), 0.001)
    }

    @Test
    fun `adjusting the initial balance shifts the current balance by the same delta`() {
        addAccount("Jenius", initial = 100.0)
        addTransaction("Jenius", 30, "EXPENSE")
        recalc("Jenius")
        assertEquals(70.0, balanceOf("Jenius"), 0.001)

        // mirrors AccountDao.setInitialBalance
        db.createStatement().use {
            it.executeUpdate(
                "UPDATE accounts SET currentBalance = currentBalance - initialBalance + 250.0, " +
                    "initialBalance = 250.0 WHERE name = 'Jenius'"
            )
        }
        assertEquals("history must survive an initial-balance edit", 220.0, balanceOf("Jenius"), 0.001)
    }

    private fun accountNames(): List<String> =
        db.createStatement().use { st ->
            st.executeQuery("SELECT name FROM accounts ORDER BY name").use {
                generateSequence { if (it.next()) it.getString(1) else null }.toList()
            }
        }

    private fun cleanSeeds() =
        db.createStatement().use { it.executeUpdate(AppDatabase.DELETE_UNTOUCHED_SEEDS) }

    @Test
    fun `the v4 cleanup clears every untouched seed`() {
        listOf("Cash", "BCA", "GoPay", "e-Money", "Bank Jago").forEach { addAccount(it) }
        cleanSeeds()
        assertEquals(emptyList<String>(), accountNames())
    }

    @Test
    fun `the v4 cleanup keeps a seed the user put money into`() {
        addAccount("Cash", initial = 100_000.0)
        addAccount("BCA")
        cleanSeeds()
        assertEquals(listOf("Cash"), accountNames())
    }

    @Test
    fun `the v4 cleanup keeps a seed that already has transactions`() {
        addAccount("GoPay")
        addAccount("OVO")
        addTransaction("GoPay", 15_000, "EXPENSE")
        cleanSeeds()
        assertEquals("deleting it would orphan its transactions", listOf("GoPay"), accountNames())
    }

    @Test
    fun `the v4 cleanup never touches an account the user named themselves`() {
        addAccount("Dompet Celana")
        cleanSeeds()
        assertEquals(listOf("Dompet Celana"), accountNames())
    }
}

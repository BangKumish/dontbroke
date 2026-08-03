package id.bangkumis.dontbroke.data

import id.bangkumis.dontbroke.data.local.dao.BucketTotal
import id.bangkumis.dontbroke.data.local.dao.CategoryTotal
import id.bangkumis.dontbroke.data.local.dao.EXPENSE_BY_BUCKET
import id.bangkumis.dontbroke.data.local.dao.EXPENSE_BY_CATEGORY
import id.bangkumis.dontbroke.data.local.dao.MONTH_TOTALS
import id.bangkumis.dontbroke.data.local.dao.MonthTotals
import id.bangkumis.dontbroke.data.local.dao.TransactionDao
import id.bangkumis.dontbroke.data.local.entity.TransactionEntity
import id.bangkumis.dontbroke.data.repository.AnalyticsRepository
import id.bangkumis.dontbroke.domain.model.AnalyticsTimeFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

private const val DAY = 86_400_000L
private const val HOUR = 3_600_000L

/** Only the three analytics queries answer; the rest never run in this test. */
private class FakeDao(
    private val categories: List<CategoryTotal> = emptyList(),
    private val buckets: List<BucketTotal> = emptyList(),
    private val totals: MonthTotals = MonthTotals(0, 0)
) : TransactionDao {
    override fun expenseByCategory(from: Long, until: Long): Flow<List<CategoryTotal>> = flowOf(categories)
    override fun expenseByBucket(from: Long, until: Long, bucketMs: Long): Flow<List<BucketTotal>> = flowOf(buckets)
    override fun monthTotals(from: Long, until: Long): Flow<MonthTotals> = flowOf(totals)

    override fun getRecent(limit: Int): Flow<List<TransactionEntity>> = TODO()
    override fun getFiltered(
        from: Long,
        until: Long,
        category: String,
        account: String,
        location: String
    ): Flow<List<TransactionEntity>> = TODO()
    override fun distinctCategories(): Flow<List<String>> = TODO()
    override fun distinctLocations(): Flow<List<String>> = TODO()
    override fun getAll(): Flow<List<TransactionEntity>> = TODO()
    override suspend fun getById(id: Long): TransactionEntity? = TODO()
    override fun totalIncome(): Flow<Long> = TODO()
    override fun totalExpense(): Flow<Long> = TODO()
    override fun expenseBetween(from: Long, until: Long): Flow<Long> = TODO()
    override suspend fun insert(entity: TransactionEntity) = TODO()
    override suspend fun delete(entity: TransactionEntity) = TODO()
    override suspend fun recalcAccountBalance(accountName: String) = TODO()
}

/**
 * Money aggregates, both halves: the production SQL run against real SQLite, and
 * the repository mapping on top of it. A wrong window bound, a lost bucket or a
 * rescaled amount all read as plausible numbers on a chart, so assert them.
 */
class AnalyticsSqlTest {

    private lateinit var db: Connection

    /** v2 transactions schema, as Room generates it. */
    private val createTransactions =
        "CREATE TABLE IF NOT EXISTS `transactions` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount` INTEGER NOT NULL, " +
            "`type` TEXT NOT NULL, `category` TEXT NOT NULL, `note` TEXT NOT NULL, " +
            "`sourceOrAccount` TEXT NOT NULL DEFAULT 'Cash', `location` TEXT, " +
            "`timestamp` INTEGER NOT NULL)"

    @Before
    fun setUp() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        db.createStatement().use { it.executeUpdate(createTransactions) }
    }

    @After
    fun tearDown() = db.close()

    // Room's ":name" binding is not JDBC syntax; positional order is left to right.
    private fun jdbc(sql: String) = Regex(":\\w+").replace(sql, "?")

    private fun add(amount: Long, type: String, category: String, timestamp: Long) {
        db.prepareStatement(
            "INSERT INTO transactions (amount, type, category, note, timestamp) VALUES (?, ?, ?, '', ?)"
        ).use {
            it.setLong(1, amount); it.setString(2, type); it.setString(3, category)
            it.setLong(4, timestamp); it.executeUpdate()
        }
    }

    private fun byCategory(from: Long, until: Long): List<Pair<String, Long>> =
        db.prepareStatement(jdbc(EXPENSE_BY_CATEGORY)).use { st ->
            st.setLong(1, from); st.setLong(2, until)
            st.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs.getString("category") to rs.getLong("total") else null }.toList()
            }
        }

    private fun byBucket(from: Long, until: Long, bucketMs: Long): List<Pair<Int, Long>> =
        db.prepareStatement(jdbc(EXPENSE_BY_BUCKET)).use { st ->
            // SELECT binds :from and :bucketMs first, then the WHERE clause's :from and :until
            st.setLong(1, from); st.setLong(2, bucketMs)
            st.setLong(3, from); st.setLong(4, until)
            st.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs.getInt("bucketIndex") to rs.getLong("total") else null }.toList()
            }
        }

    private fun monthTotals(from: Long, until: Long): Pair<Long, Long> =
        db.prepareStatement(jdbc(MONTH_TOTALS)).use { st ->
            st.setLong(1, from); st.setLong(2, until)
            st.executeQuery().use { it.next(); it.getLong("income") to it.getLong("expense") }
        }

    @Test
    fun `categories are summed biggest first and income is not spending`() {
        add(30_000, "EXPENSE", "Food", 10)
        add(45_000, "EXPENSE", "Food", 20)
        add(50_000, "EXPENSE", "Transport", 30)
        add(9_000_000, "INCOME", "Salary", 40)
        assertEquals(listOf("Food" to 75_000L, "Transport" to 50_000L), byCategory(0, 100))
    }

    @Test
    fun `the window is half-open so a midnight row lands in exactly one month`() {
        add(10_000, "EXPENSE", "Food", 100)   // == from, counted
        add(20_000, "EXPENSE", "Food", 199)
        add(40_000, "EXPENSE", "Food", 200)   // == until, next month's
        assertEquals(listOf("Food" to 30_000L), byCategory(100, 200))
    }

    @Test
    fun `an empty table yields no category rows rather than a null total`() {
        assertEquals(emptyList<Pair<String, Long>>(), byCategory(0, 100))
    }

    @Test
    fun `daily buckets count whole days elapsed since the window opened`() {
        val mon = 1_000_000_000_000L
        add(5_000, "EXPENSE", "Food", mon)
        add(7_000, "EXPENSE", "Transport", mon + 2 * DAY)
        add(3_000, "EXPENSE", "Food", mon + 2 * DAY + HOUR)
        add(1_000, "EXPENSE", "Food", mon + 6 * DAY + DAY - 1)
        add(9_000, "INCOME", "Salary", mon + DAY)
        assertEquals(
            listOf(0 to 5_000L, 2 to 10_000L, 6 to 1_000L),
            byBucket(mon, mon + 7 * DAY, DAY)
        )
    }

    /** The same statement drives the hourly axis — only :bucketMs changes. */
    @Test
    fun `hourly buckets come out of the same query with a smaller slice`() {
        val start = 1_000_000_000_000L
        add(5_000, "EXPENSE", "Coffee", start + 30 * 60_000)          // hour 0
        add(2_000, "EXPENSE", "Coffee", start + HOUR + 59 * 60_000)   // hour 1
        add(8_000, "EXPENSE", "Lunch", start + 12 * HOUR)             // hour 12
        assertEquals(
            listOf(0 to 5_000L, 1 to 2_000L, 12 to 8_000L),
            byBucket(start, start + 24 * HOUR, HOUR)
        )
    }

    @Test
    fun `month totals keep income and expense apart`() {
        add(9_000_000, "INCOME", "Salary", 10)
        add(250_000, "EXPENSE", "Food", 20)
        add(150_000, "EXPENSE", "Transport", 30)
        assertEquals(9_000_000L to 400_000L, monthTotals(0, 100))
    }

    @Test
    fun `month totals return a zeroed row on an empty table`() {
        assertEquals("no GROUP BY, so there is always exactly one row", 0L to 0L, monthTotals(0, 100))
    }

    @Test
    fun `percentages are of the window total and amounts are whole rupiah`() = runBlocking {
        val repo = AnalyticsRepository(
            FakeDao(categories = listOf(CategoryTotal("Food", 75_000), CategoryTotal("Transport", 25_000)))
        )
        val slices = repo.categorySpending(0, 100).first()
        assertEquals(75_000.0, slices[0].totalAmount, 0.001)
        assertEquals(75f, slices[0].percentage, 0.01f)
        assertEquals(25f, slices[1].percentage, 0.01f)
    }

    @Test
    fun `a week with one spending day still yields seven Monday-first buckets`() = runBlocking {
        val repo = AnalyticsRepository(FakeDao(buckets = listOf(BucketTotal(2, 40_000))))
        val week = repo.spendingTrend(AnalyticsTimeFrame.THIS_WEEK, 0, 7 * DAY).first()
        assertEquals(7, week.size)
        assertEquals("Wednesday", week[2].dayName)
        assertEquals(40_000.0, week[2].totalSpent, 0.001)
        assertEquals(0.0, week[0].totalSpent, 0.001)
    }

    /** A day is 24 hourly bars whether or not anything was spent in them. */
    @Test
    fun `a day yields twenty-four hourly buckets`() = runBlocking {
        val repo = AnalyticsRepository(FakeDao(buckets = listOf(BucketTotal(9, 15_000))))
        val day = repo.spendingTrend(AnalyticsTimeFrame.TODAY, 0, 24 * HOUR).first()
        assertEquals(24, day.size)
        assertEquals("09:00", day[9].dayName)
        assertEquals(15_000.0, day[9].totalSpent, 0.001)
    }

    /**
     * A stale window can outlive its data by one emission; that must not throw.
     * The out-of-range total is clamped into the last bar rather than lost.
     */
    @Test
    fun `a bucket index past the end of the window is clamped, not thrown`() = runBlocking {
        val repo = AnalyticsRepository(FakeDao(buckets = listOf(BucketTotal(99, 20_000))))
        val week = repo.spendingTrend(AnalyticsTimeFrame.THIS_WEEK, 0, 7 * DAY).first()
        assertEquals(7, week.size)
        assertEquals(20_000.0, week[6].totalSpent, 0.001)
    }

    @Test
    fun `an empty month yields no slices instead of dividing by zero`() = runBlocking {
        val repo = AnalyticsRepository(FakeDao())
        assertEquals(emptyList<Any>(), repo.categorySpending(0, 100).first())
        assertEquals(0.0, repo.comparison(0, 100).first().net, 0.001)
    }
}

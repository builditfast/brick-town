package ai.freeapp.bricktown.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.minutes

const val YOU = "u-you"
const val ANA = "u-ana"

val NOV_24: LocalDate = LocalDate(2026, 11, 24)

fun d(year: Int, month: Int, day: Int): LocalDate = LocalDate(year, month, day)

private var clock = 0

fun instantOf(date: LocalDate, minutes: Int = 0): Instant = Instant.parse("${date}T12:00:00Z") + minutes.minutes

fun expense(
    id: String,
    category: String,
    cents: Long,
    date: LocalDate,
    note: String = "",
    recurring: Boolean = false,
    shared: Boolean = false,
    refund: Boolean = false,
    paidBy: String = YOU,
    settledAt: Instant? = null,
) = Expense(
    id = id, categoryId = category, amountCents = cents, date = date, note = note,
    recurring = recurring, shared = shared, refund = refund, paidBy = paidBy,
    settledAt = settledAt, createdAt = instantOf(date, clock++),
)

val MEMBERS = listOf(
    Member(YOU, "Emanuel", "E", 0, MemberRole.OWNER, canEdit = true, canManage = true, joinedAt = instantOf(d(2026, 9, 3))),
    Member(ANA, "Ana", "A", 1, MemberRole.MEMBER, canEdit = true, canManage = false, joinedAt = instantOf(d(2026, 9, 4))),
)

fun categories(): List<Category> = listOf(
    Category("groceries", "Groceries", "Food", 0, null, 0),
    Category("eating", "Eating out", "Out", 1, 25_000L, 1),
    Category("home", "Home", "Home", 2, null, 2),
    Category("shopping", "Shopping", "Shop", 3, 20_000L, 3),
    Category("transport", "Transport", "Move", 4, null, 4),
    Category("subs", "Subscriptions", "Subs", 5, null, 5),
)

fun settings(budget: Long = 500_000L, payday: Int = 1, billsFirst: Boolean = true, currency: Currency = Currency.EUR) =
    TownSettings(budget, payday, currency, billsFirst, "Europe/Bucharest")

fun snapshot(
    expenses: List<Expense> = emptyList(),
    settings: TownSettings = settings(),
    categories: List<Category> = categories(),
    goldMarks: List<GoldMark> = emptyList(),
    billSkips: List<BillSkip> = emptyList(),
    members: List<Member> = MEMBERS,
    serverTime: Instant = Instant.parse("2026-11-24T10:00:00Z"),
) = TownSnapshot(
    town = Town("t1", YOU, "Emanuel's town", settings, instantOf(d(2026, 9, 3))),
    viewerId = YOU,
    members = members,
    categories = categories,
    expenses = expenses,
    goldMarks = goldMarks,
    billSkips = billSkips,
    invites = emptyList(),
    serverTime = serverTime,
)

/** A plain €1 brick that is not a bill: one logged day. */
fun logged(vararg dates: LocalDate): List<Expense> = dates.mapIndexed { i, date -> expense("log$i-$date", "groceries", 100, date) }

fun gold(vararg dates: LocalDate): List<GoldMark> = dates.map { GoldMark(it, YOU) }

/**
 * The 5b screen: budget 5 000, payday 1, today 24 Nov 2026, 31 November bricks
 * (towers 342, 298, 275, 214, 96, 87; bills 303; Ana's ids 2, 9, 12, 20) and
 * last month 1 455, all dated in Oct 1 to 24 so "same day" is the whole of it.
 */
fun fixture5b(): TownSnapshot {
    val nov = listOf(
        expense("1", "home", 18_000, d(2026, 11, 2), "Electricity", recurring = true, shared = true),
        expense("2", "groceries", 4_820, d(2026, 11, 22), "Albert Heijn", shared = true, paidBy = ANA),
        expense("3", "groceries", 3_120, d(2026, 11, 3), "Lidl"),
        expense("4", "eating", 7_450, d(2026, 11, 4), "Cafe"),
        expense("5", "groceries", 3_000, d(2026, 11, 4), "Lidl"),
        expense("6", "groceries", 2_430, d(2026, 11, 7), "Kaufland"),
        expense("7", "groceries", 3_000, d(2026, 11, 8), "Kaufland"),
        expense("8", "groceries", 4_210, d(2026, 11, 11), "Lidl"),
        expense("9", "eating", 2_760, d(2026, 11, 16), "Wolt", shared = true, paidBy = ANA),
        expense("10", "groceries", 3_390, d(2026, 11, 14), "Profi"),
        expense("11", "groceries", 5_200, d(2026, 11, 18), "Lidl"),
        expense("12", "eating", 1_400, d(2026, 11, 23), "Wolt", shared = true, paidBy = ANA),
        expense("13", "groceries", 5_030, d(2026, 11, 21), "Mega Image"),
        expense("14", "eating", 6_200, d(2026, 11, 7), "Restaurant"),
        expense("15", "eating", 5_100, d(2026, 11, 14), "Restaurant"),
        expense("16", "eating", 3_890, d(2026, 11, 19), "Glovo"),
        expense("17", "eating", 3_000, d(2026, 11, 21), "McDonald's"),
        expense("18", "home", 5_000, d(2026, 11, 19), "Cleaning", shared = true),
        expense("19", "home", 4_500, d(2026, 11, 10), "Dedeman"),
        expense("20", "shopping", 8_900, d(2026, 11, 15), "Zara", paidBy = ANA),
        expense("21", "shopping", 7_100, d(2026, 11, 9), "H&M"),
        expense("22", "shopping", 3_000, d(2026, 11, 20), "Decathlon"),
        expense("23", "transport", 3_600, d(2026, 11, 1), "Metro pass", recurring = true),
        expense("24", "transport", 2_350, NOV_24, "Bolt"),
        expense("25", "transport", 3_650, d(2026, 11, 12), "Petrom"),
        expense("26", "subs", 2_500, d(2026, 11, 1), "Netflix", recurring = true),
        expense("27", "subs", 2_000, d(2026, 11, 2), "Spotify", recurring = true),
        expense("28", "subs", 1_500, d(2026, 11, 3), "iCloud", recurring = true),
        expense("29", "subs", 1_700, d(2026, 11, 4), "Gym", recurring = true),
        expense("30", "subs", 1_000, d(2026, 11, 5), "Vodafone", recurring = true),
        expense("31", "shopping", 2_400, d(2026, 11, 20), "Decathlon"),
    )
    val oct = listOf(
        expense("o1", "home", 18_000, d(2026, 10, 2), "Electricity", recurring = true),
        expense("o2", "transport", 3_600, d(2026, 10, 1), "Metro pass", recurring = true),
        expense("o3", "subs", 2_500, d(2026, 10, 1), "Netflix", recurring = true),
        expense("o4", "subs", 2_000, d(2026, 10, 2), "Spotify", recurring = true),
        expense("o5", "subs", 1_500, d(2026, 10, 3), "iCloud", recurring = true),
        expense("o6", "subs", 1_700, d(2026, 10, 4), "Gym", recurring = true),
        expense("o7", "subs", 1_000, d(2026, 10, 5), "Vodafone", recurring = true),
        expense("o8", "groceries", 20_000, d(2026, 10, 5), "Lidl"),
        expense("o9", "groceries", 18_000, d(2026, 10, 15), "Kaufland"),
        expense("o10", "eating", 27_000, d(2026, 10, 10), "Restaurant"),
        expense("o11", "home", 12_000, d(2026, 10, 12), "Dedeman"),
        expense("o12", "shopping", 28_200, d(2026, 10, 20), "Zara"),
        expense("o13", "transport", 10_000, d(2026, 10, 8), "Petrom"),
    )
    return snapshot(nov + oct)
}

/**
 * The 6c screen: budget 1 800, a 30-day pay month starting on payday 28 (the
 * only way 1 800 gives "€60 a day"), nothing laid yet, 358 of bills last time.
 */
fun fixture6c(): TownSnapshot = snapshot(
    settings = settings(budget = 180_000L, payday = 28),
    expenses = listOf(
        expense("p1", "home", 19_000, d(2026, 10, 30), "Electricity", recurring = true),
        expense("p2", "transport", 3_600, d(2026, 11, 1), "Metro pass", recurring = true),
        expense("p3", "subs", 2_500, d(2026, 10, 29), "Netflix", recurring = true),
        expense("p4", "subs", 2_000, d(2026, 11, 2), "Spotify", recurring = true),
        expense("p5", "subs", 1_500, d(2026, 11, 3), "iCloud", recurring = true),
        expense("p6", "subs", 4_200, d(2026, 11, 4), "Gym", recurring = true),
        expense("p7", "subs", 3_000, d(2026, 11, 5), "Vodafone", recurring = true),
    ),
)

package ai.freeapp.bricktown.domain

/** The ten onboarding suggestions, in grid order, with their tower labels. */
val SUGGESTED_TOWERS: List<Pair<String, String>> = listOf(
    "Groceries" to "Food",
    "Eating out" to "Out",
    "Home" to "Home",
    "Shopping" to "Shop",
    "Transport" to "Move",
    "Subscriptions" to "Subs",
    "Pets" to "Pets",
    "Kids" to "Kids",
    "Health" to "Care",
    "Travel" to "Trip",
)

/** The suggestion's label for a suggested name, else the first word cut to 5 characters. */
fun shortNameFor(name: String): String {
    val trimmed = name.trim()
    SUGGESTED_TOWERS.firstOrNull { it.first.equals(trimmed, ignoreCase = true) }?.let { return it.second }
    return trimmed.split(Regex("\\s+")).first().take(5)
}

/** Chips on the cap row and in the tower editor (Custom is typed). */
val CAP_CHOICES_CENTS: List<Long?> = listOf(null, 15_000L, 25_000L, 40_000L)

sealed interface TowerProblem {
    data object NameRequired : TowerProblem
    data object NameTooLong : TowerProblem
    data object NameTaken : TowerProblem
    data object LastTower : TowerProblem
    data object MergeIntoItself : TowerProblem
}

/** Name required, at most [MAX_TOWER_NAME] characters, and unique within the town, compared trimmed and case-insensitive. [editingId] is the tower being renamed. */
fun checkTowerName(name: String, categories: List<Category>, editingId: String?): TowerProblem? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return TowerProblem.NameRequired
    if (trimmed.length > MAX_TOWER_NAME) return TowerProblem.NameTooLong
    val taken = categories.any { it.id != editingId && it.name.trim().equals(trimmed, ignoreCase = true) }
    return if (taken) TowerProblem.NameTaken else null
}

const val MAX_TOWER_NAME: Int = 24

/** Deleting the last tower is refused. A tower with bricks must be merged (bricks are never lost). */
fun checkTowerDelete(categoryId: String, mergeInto: String?, categories: List<Category>): TowerProblem? = when {
    mergeInto == categoryId -> TowerProblem.MergeIntoItself
    categories.size <= 1 -> TowerProblem.LastTower
    else -> null
}

/** The next palette index for a new tower: PALETTE[i % 8] by tower count. */
fun nextColorIndex(categories: List<Category>): Int = categories.size % 8

/** Bricks whose note or category name contains [query], case-insensitive, newest first. Blank query: all of them. */
fun searchBricks(snapshot: TownSnapshot, query: String): List<Expense> {
    val q = query.trim()
    val names = snapshot.categories.associate { it.id to it.name }
    return snapshot.expenses
        .filter { q.isEmpty() || it.note.contains(q, ignoreCase = true) || (names[it.categoryId] ?: "").contains(q, ignoreCase = true) }
        .sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.createdAt })
}

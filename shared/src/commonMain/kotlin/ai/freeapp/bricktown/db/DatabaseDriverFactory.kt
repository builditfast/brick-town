package ai.freeapp.bricktown.db

import app.cash.sqldelight.db.SqlDriver

/**
 * Creates a SQLDelight driver. Actuals provide platform-specific drivers:
 *  - Android: AndroidSqliteDriver(AppDatabase.Schema, context, "app.db")
 *  - iOS:     NativeSqliteDriver(AppDatabase.Schema, "app.db")
 */
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

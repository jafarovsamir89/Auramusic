package az.simplesoft.aura.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuraDatabaseMigrationTest {
    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        requireNotNull(AuraDatabase::class.java.canonicalName),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun everyHistoricalVersionMigratesToCurrentSchema() {
        (1..4).forEach { startVersion ->
            val name = "aura_migration_$startVersion"
            helper.createDatabase(name, startVersion).close()
            helper.runMigrationsAndValidate(
                name,
                5,
                true,
                AuraDatabase.MIGRATION_1_2,
                AuraDatabase.MIGRATION_2_3,
                AuraDatabase.MIGRATION_3_4,
                AuraDatabase.MIGRATION_4_5
            ).close()
        }
    }

    @Test
    fun versionThreeStateColumnsAreAddedExactlyOnce() {
        val name = "aura_migration_state"
        helper.createDatabase(name, 3).use { database ->
            database.execSQL(
                "INSERT INTO assistant_conversation_state (id, nodeId, topic, emotion, updatedAt) " +
                    "VALUES ('active', 'greeting', 'general', 'calm', 1)"
            )
        }
        helper.runMigrationsAndValidate(
            name,
            5,
            true,
            AuraDatabase.MIGRATION_3_4,
            AuraDatabase.MIGRATION_4_5
        ).use { database ->
            database.query("PRAGMA table_info(assistant_conversation_state)").use { cursor ->
                val columns = mutableSetOf<String>()
                while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
                assertTrue(columns.containsAll(setOf("expectedIntent", "failureCount", "lastBranch")))
                assertTrue(columns.size == 8)
            }
        }
    }
}

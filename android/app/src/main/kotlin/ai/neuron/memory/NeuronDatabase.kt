package ai.neuron.memory

import ai.neuron.character.dao.CharacterDao
import ai.neuron.character.model.CharacterState
import ai.neuron.memory.dao.AppWorkflowDao
import ai.neuron.memory.dao.AuditDao
import ai.neuron.memory.dao.ContactAssociationDao
import ai.neuron.memory.dao.UserPreferenceDao
import ai.neuron.memory.entity.AppWorkflow
import ai.neuron.memory.entity.AuditEntry
import ai.neuron.memory.entity.ContactAssociation
import ai.neuron.memory.entity.UserPreference
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserPreference::class,
        AppWorkflow::class,
        ContactAssociation::class,
        CharacterState::class,
        AuditEntry::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class NeuronDatabase : RoomDatabase() {
    abstract fun userPreferenceDao(): UserPreferenceDao

    abstract fun appWorkflowDao(): AppWorkflowDao

    abstract fun contactAssociationDao(): ContactAssociationDao

    abstract fun characterDao(): CharacterDao

    abstract fun auditDao(): AuditDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS character_state (
                            id INTEGER NOT NULL PRIMARY KEY,
                            selected_type TEXT NOT NULL DEFAULT 'ANIME_GIRL',
                            name TEXT NOT NULL DEFAULT 'Aiko',
                            personality_json TEXT NOT NULL DEFAULT '',
                            current_emotion TEXT NOT NULL DEFAULT 'NEUTRAL',
                            voice_profile_id TEXT NOT NULL DEFAULT 'calm',
                            created_at INTEGER NOT NULL DEFAULT 0,
                            total_interactions INTEGER NOT NULL DEFAULT 0,
                            last_interaction_at INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent(),
                    )
                }
            }

        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS audit_log (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            timestamp INTEGER NOT NULL DEFAULT 0,
                            actionType TEXT NOT NULL DEFAULT '',
                            targetPackage TEXT NOT NULL DEFAULT '',
                            command TEXT NOT NULL DEFAULT '',
                            success INTEGER NOT NULL DEFAULT 0,
                            reasoning TEXT,
                            stepIndex INTEGER NOT NULL DEFAULT 0,
                            llmTier TEXT,
                            durationMs INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent(),
                    )
                }
            }
    }
}

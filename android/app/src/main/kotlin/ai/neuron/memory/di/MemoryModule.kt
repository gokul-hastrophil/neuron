package ai.neuron.memory.di

import ai.neuron.character.dao.CharacterDao
import ai.neuron.memory.NeuronDatabase
import ai.neuron.memory.dao.AppWorkflowDao
import ai.neuron.memory.dao.AuditDao
import ai.neuron.memory.dao.ContactAssociationDao
import ai.neuron.memory.dao.UserPreferenceDao
import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MemoryModule {
    @Provides
    @Singleton
    fun provideNeuronDatabase(
        @ApplicationContext context: Context,
    ): NeuronDatabase =
        Room.databaseBuilder(
            context,
            NeuronDatabase::class.java,
            "neuron_memory.db",
        )
            .addMigrations(NeuronDatabase.MIGRATION_1_2, NeuronDatabase.MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideUserPreferenceDao(db: NeuronDatabase): UserPreferenceDao = db.userPreferenceDao()

    @Provides
    fun provideAppWorkflowDao(db: NeuronDatabase): AppWorkflowDao = db.appWorkflowDao()

    @Provides
    fun provideContactAssociationDao(db: NeuronDatabase): ContactAssociationDao = db.contactAssociationDao()

    @Provides
    fun provideCharacterDao(db: NeuronDatabase): CharacterDao = db.characterDao()

    @Provides
    fun provideAuditDao(db: NeuronDatabase): AuditDao = db.auditDao()
}

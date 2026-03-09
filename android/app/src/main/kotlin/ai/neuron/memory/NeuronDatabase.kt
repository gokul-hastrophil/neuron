package ai.neuron.memory

import ai.neuron.memory.dao.AppWorkflowDao
import ai.neuron.memory.dao.ContactAssociationDao
import ai.neuron.memory.dao.UserPreferenceDao
import ai.neuron.memory.entity.AppWorkflow
import ai.neuron.memory.entity.ContactAssociation
import ai.neuron.memory.entity.UserPreference
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserPreference::class,
        AppWorkflow::class,
        ContactAssociation::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class NeuronDatabase : RoomDatabase() {
    abstract fun userPreferenceDao(): UserPreferenceDao
    abstract fun appWorkflowDao(): AppWorkflowDao
    abstract fun contactAssociationDao(): ContactAssociationDao
}

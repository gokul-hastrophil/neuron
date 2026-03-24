package ai.neuron.memory.dao

import ai.neuron.memory.entity.ContactAssociation
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ContactAssociationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: ContactAssociation): Long

    @Update
    suspend fun update(contact: ContactAssociation)

    @Delete
    suspend fun delete(contact: ContactAssociation)

    @Query("SELECT * FROM contact_associations WHERE canonicalKey = :canonicalKey LIMIT 1")
    suspend fun findByCanonicalKey(canonicalKey: String): ContactAssociation?

    @Query("SELECT * FROM contact_associations WHERE displayName LIKE '%' || :name || '%' ORDER BY lastUsed DESC")
    suspend fun searchByName(name: String): List<ContactAssociation>

    @Query("SELECT * FROM contact_associations ORDER BY lastUsed DESC")
    suspend fun getAll(): List<ContactAssociation>

    @Query("DELETE FROM contact_associations")
    suspend fun deleteAll()
}

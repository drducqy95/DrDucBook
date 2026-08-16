package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.QuickDictionaryEntryEntity
import io.legado.app.data.entities.QuickDictionaryUniverseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickDictionaryDao {

    @Query("select * from quick_dictionary_entries order by updatedAt desc, id desc")
    fun observeEntries(): Flow<List<QuickDictionaryEntryEntity>>

    @Query("select * from quick_dictionary_entries where id = :id limit 1")
    suspend fun getEntry(id: Long): QuickDictionaryEntryEntity?

    @Query("select * from quick_dictionary_entries where id in (:ids)")
    suspend fun getEntries(ids: List<Long>): List<QuickDictionaryEntryEntity>

    @Query(
        """
        select * from quick_dictionary_entries
        where scope = 'GLOBAL'
            or (scope = :scope and scopeKey = :scopeKey)
        """
    )
    suspend fun getImportDuplicateEntries(
        scope: String,
        scopeKey: String,
    ): List<QuickDictionaryEntryEntity>

    @Query(
        """
        select * from quick_dictionary_entries
        where enabled = 1 and (
            scope = 'GLOBAL'
            or (scope = 'UNIVERSE' and scopeKey = :activeUniverseKey)
            or (scope = 'PROJECT' and scopeKey = :projectKey)
        )
        order by case scope
            when 'PROJECT' then 0
            when 'UNIVERSE' then 1
            else 2
        end, updatedAt desc
        """
    )
    suspend fun getEffectiveEntries(
        activeUniverseKey: String,
        projectKey: String,
    ): List<QuickDictionaryEntryEntity>

    @Query(
        "select * from quick_dictionary_universes where enabled = 1 order by name collate nocase"
    )
    suspend fun getEnabledUniverses(): List<QuickDictionaryUniverseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUniverse(universe: QuickDictionaryUniverseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: QuickDictionaryEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<QuickDictionaryEntryEntity>): List<Long>

    @Query("delete from quick_dictionary_entries where id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("delete from quick_dictionary_universes where universeKey = :key")
    suspend fun deleteUniverse(key: String)

    @Query("delete from quick_dictionary_entries where scope = 'UNIVERSE' and scopeKey = :key")
    suspend fun deleteUniverseEntries(key: String)
}

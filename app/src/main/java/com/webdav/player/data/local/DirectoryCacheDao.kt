package com.webdav.player.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DirectoryCacheDao {
    @Query("SELECT * FROM directory_cache WHERE serverId = :serverId AND path = :path LIMIT 1")
    suspend fun getCache(
        serverId: Long,
        path: String,
    ): DirectoryCacheEntity?

    @Query("SELECT * FROM directory_cache")
    fun getAllFlow(): Flow<List<DirectoryCacheEntity>>

    @Query("SELECT * FROM directory_cache WHERE serverId = :serverId")
    fun getAllByServerIdFlow(serverId: Long): Flow<List<DirectoryCacheEntity>>

    @Query("SELECT * FROM directory_cache")
    suspend fun getAll(): List<DirectoryCacheEntity>

    @Query("SELECT * FROM directory_cache WHERE serverId = :serverId")
    suspend fun getAllByServerId(serverId: Long): List<DirectoryCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: DirectoryCacheEntity)

    @Query("DELETE FROM directory_cache WHERE serverId = :serverId AND path = :path")
    suspend fun deleteCache(
        serverId: Long,
        path: String,
    )

    @Query("DELETE FROM directory_cache WHERE serverId = :serverId")
    suspend fun deleteCacheByServerId(serverId: Long)

    @Query("DELETE FROM directory_cache")
    suspend fun clearAll()
}

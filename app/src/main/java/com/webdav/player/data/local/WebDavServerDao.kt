package com.webdav.player.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WebDavServerDao {

    @Query("SELECT * FROM webdav_servers ORDER BY id ASC")
    fun getAllServersFlow(): Flow<List<WebDavServerEntity>>

    @Query("SELECT * FROM webdav_servers WHERE id = :id LIMIT 1")
    suspend fun getServerById(id: Long): WebDavServerEntity?

    @Query("SELECT * FROM webdav_servers WHERE isDefault = 1 LIMIT 1")
    fun getActiveServerFlow(): Flow<WebDavServerEntity?>

    @Query("SELECT COUNT(*) FROM webdav_servers")
    suspend fun getServerCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(entity: WebDavServerEntity): Long

    @Update
    suspend fun updateServer(entity: WebDavServerEntity)

    @Query("DELETE FROM webdav_servers WHERE id = :id")
    suspend fun deleteServerById(id: Long): Int

    @Query("UPDATE webdav_servers SET isDefault = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setActiveServer(id: Long)
}

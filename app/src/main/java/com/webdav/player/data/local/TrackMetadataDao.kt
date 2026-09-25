package com.webdav.player.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackMetadataDao {

    @Query("SELECT * FROM track_metadata WHERE serverId = :serverId AND remotePath = :remotePath LIMIT 1")
    suspend fun getMetadata(serverId: Long, remotePath: String): TrackMetadataEntity?

    @Query("SELECT * FROM track_metadata WHERE serverId = :serverId AND remotePath = :remotePath LIMIT 1")
    fun getMetadataFlow(serverId: Long, remotePath: String): Flow<TrackMetadataEntity?>

    @Query("SELECT * FROM track_metadata WHERE serverId = :serverId")
    fun getAllMetadataForServerFlow(serverId: Long): Flow<List<TrackMetadataEntity>>

    @Query("SELECT * FROM track_metadata WHERE serverId = :serverId AND remotePath IN (:remotePaths)")
    suspend fun getMetadataForPaths(serverId: Long, remotePaths: List<String>): List<TrackMetadataEntity>

    @Query("SELECT * FROM track_metadata WHERE serverId = :serverId AND remotePath IN (:remotePaths)")
    fun getMetadataForPathsFlow(serverId: Long, remotePaths: List<String>): Flow<List<TrackMetadataEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: TrackMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(entities: List<TrackMetadataEntity>)

    @Query("DELETE FROM track_metadata WHERE serverId = :serverId AND remotePath = :remotePath")
    suspend fun deleteMetadata(serverId: Long, remotePath: String): Int

    @Query("DELETE FROM track_metadata WHERE serverId = :serverId")
    suspend fun deleteMetadataForServer(serverId: Long): Int
}

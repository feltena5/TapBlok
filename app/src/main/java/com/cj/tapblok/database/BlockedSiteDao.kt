package com.cj.tapblok.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedSiteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(blockedSite: BlockedSite)

    @Delete
    suspend fun delete(blockedSite: BlockedSite)

    @Query("SELECT * FROM blocked_sites ORDER BY pattern")
    fun getAllBlockedSites(): Flow<List<BlockedSite>>
}

package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash_items ORDER BY deletedTimestamp DESC")
    fun getAllTrashItems(): Flow<List<TrashEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrashItem(item: TrashEntity)

    @Query("DELETE FROM trash_items WHERE id = :id")
    suspend fun deleteTrashById(id: Int)

    @Query("SELECT * FROM trash_items WHERE deletedTimestamp < :threshold")
    suspend fun getOldTrashItems(threshold: Long): List<TrashEntity>

    @Query("DELETE FROM trash_items WHERE deletedTimestamp < :threshold")
    suspend fun deleteOldTrashItems(threshold: Long)

    @Query("DELETE FROM trash_items")
    suspend fun emptyTrash()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_items ORDER BY addedTimestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorite_items ORDER BY addedTimestamp DESC")
    suspend fun getAllFavoritesList(): List<FavoriteEntity>

    @Query("SELECT path FROM favorite_items")
    suspend fun getAllFavoritePaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(item: FavoriteEntity)

    @Query("DELETE FROM favorite_items WHERE path = :path")
    suspend fun deleteFavoriteByPath(path: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_items WHERE path = :path)")
    suspend fun isFavorite(path: String): Boolean
}

@Dao
interface TabDao {
    @Query("SELECT * FROM tab_items")
    fun getAllTabs(): Flow<List<TabEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTab(tab: TabEntity)

    @Query("DELETE FROM tab_items WHERE tabId = :tabId")
    suspend fun deleteTabById(tabId: String)

    @Query("DELETE FROM tab_items")
    suspend fun clearAllTabs()
}

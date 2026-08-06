package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trash_items")
data class TrashEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val originalPath: String,
    val displayName: String,
    val size: Long,
    val deletedTimestamp: Long,
    val trashTempPath: String,
    val isDirectory: Boolean
)

@Entity(tableName = "favorite_items")
data class FavoriteEntity(
    @PrimaryKey val path: String,
    val displayName: String,
    val isDirectory: Boolean,
    val addedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "tab_items")
data class TabEntity(
    @PrimaryKey val tabId: String,
    val title: String,
    val currentPath: String,
    val safUriString: String? = null
)

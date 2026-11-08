package com.cj.tapblok.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A user-added website rule: a domain ("example.com") or a keyword matched in the URL. */
@Entity(tableName = "blocked_sites")
data class BlockedSite(
    @PrimaryKey
    val pattern: String
)

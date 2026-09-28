package io.legado.app.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.legado.app.data.dao.RemoteBookMetadataDao
import io.legado.app.data.entities.RemoteBookMetadata
import splitties.init.appCtx

@Database(
    entities = [RemoteBookMetadata::class],
    version = 1,
    exportSchema = false
)
abstract class RemoteBookCacheDatabase : RoomDatabase() {
    abstract val remoteBookMetadataDao: RemoteBookMetadataDao

    companion object {
        val instance: RemoteBookCacheDatabase by lazy {
            Room.databaseBuilder(
                appCtx,
                RemoteBookCacheDatabase::class.java,
                "remote_book_cache.db"
            ).fallbackToDestructiveMigration(dropAllTables = true)
             .build()
        }
    }
}

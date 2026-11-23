package bd.nib.songbadpatro.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import bd.nib.songbadpatro.data.local.dao.ArticleDao
import bd.nib.songbadpatro.data.local.dao.AuthorDao
import bd.nib.songbadpatro.data.local.dao.BookmarkDao
import bd.nib.songbadpatro.data.local.dao.FeedSourceDao
import bd.nib.songbadpatro.data.local.dao.KeywordAlertDao
import bd.nib.songbadpatro.data.local.dao.NotifiedArticleDao
import bd.nib.songbadpatro.data.local.dao.PublisherDao
import bd.nib.songbadpatro.data.local.dao.ReadingHistoryDao
import bd.nib.songbadpatro.data.local.dao.SyncLogDao
import bd.nib.songbadpatro.data.local.entity.ArticleEntity
import bd.nib.songbadpatro.data.local.entity.AuthorEntity
import bd.nib.songbadpatro.data.local.entity.BookmarkEntity
import bd.nib.songbadpatro.data.local.entity.FeedSourceEntity
import bd.nib.songbadpatro.data.local.entity.KeywordAlertEntity
import bd.nib.songbadpatro.data.local.entity.NotifiedArticleEntity
import bd.nib.songbadpatro.data.local.entity.PublisherEntity
import bd.nib.songbadpatro.data.local.entity.ReadingHistoryEntity
import bd.nib.songbadpatro.data.local.entity.SyncLogEntity

@Database(
    entities = [
        ArticleEntity::class,
        PublisherEntity::class,
        FeedSourceEntity::class,
        AuthorEntity::class,
        BookmarkEntity::class,
        ReadingHistoryEntity::class,
        SyncLogEntity::class,
        KeywordAlertEntity::class,
        NotifiedArticleEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MyNewsDatabase : RoomDatabase() {

    abstract fun articleDao(): ArticleDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun readingHistoryDao(): ReadingHistoryDao
    abstract fun authorDao(): AuthorDao
    abstract fun publisherDao(): PublisherDao
    abstract fun feedSourceDao(): FeedSourceDao
    abstract fun syncLogDao(): SyncLogDao
    abstract fun keywordAlertDao(): KeywordAlertDao
    abstract fun notifiedArticleDao(): NotifiedArticleDao

    companion object {
        const val DATABASE_NAME = "my_news_editorial.db"

        /**
         * Explicit Room migration from v1 to v2 adding sync_logs table and
         * normalizedTitle index for fast duplicate & search lookups.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_logs` (
                        `logId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sourceId` TEXT NOT NULL,
                        `publisherId` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `completedAt` INTEGER NOT NULL,
                        `success` INTEGER NOT NULL,
                        `fetchedCount` INTEGER NOT NULL,
                        `acceptedCount` INTEGER NOT NULL,
                        `pendingCount` INTEGER NOT NULL,
                        `rejectedPreCutoffCount` INTEGER NOT NULL,
                        `rejectedNonEditorialCount` INTEGER NOT NULL,
                        `errorMessage` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_logs_startedAt` ON `sync_logs` (`startedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_logs_publisherId` ON `sync_logs` (`publisherId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_normalizedTitle` ON `articles` (`normalizedTitle`)")
            }
        }
    }
}

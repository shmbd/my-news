package bd.nib.songbadpatro.di

import android.content.Context
import androidx.room.Room
import bd.nib.songbadpatro.data.local.MyNewsDatabase
import bd.nib.songbadpatro.data.local.dao.ArticleDao
import bd.nib.songbadpatro.data.local.dao.AuthorDao
import bd.nib.songbadpatro.data.local.dao.BookmarkDao
import bd.nib.songbadpatro.data.local.dao.FeedSourceDao
import bd.nib.songbadpatro.data.local.dao.KeywordAlertDao
import bd.nib.songbadpatro.data.local.dao.NotifiedArticleDao
import bd.nib.songbadpatro.data.local.dao.PublisherDao
import bd.nib.songbadpatro.data.local.dao.ReadingHistoryDao
import bd.nib.songbadpatro.data.local.dao.SyncLogDao
import bd.nib.songbadpatro.data.remote.RssAtomParser
import bd.nib.songbadpatro.data.remote.SectionAndMetadataExtractor
import bd.nib.songbadpatro.data.repository.AlertRepositoryImpl
import bd.nib.songbadpatro.data.repository.AuthorRepositoryImpl
import bd.nib.songbadpatro.data.repository.EditorialRepositoryImpl
import bd.nib.songbadpatro.data.repository.PublisherRepositoryImpl
import bd.nib.songbadpatro.data.repository.SettingsRepositoryImpl
import bd.nib.songbadpatro.domain.repository.AlertRepository
import bd.nib.songbadpatro.domain.repository.AuthorRepository
import bd.nib.songbadpatro.domain.repository.EditorialRepository
import bd.nib.songbadpatro.domain.repository.PublisherRepository
import bd.nib.songbadpatro.domain.repository.SettingsRepository
import bd.nib.songbadpatro.domain.usecase.ClassifyEditorialContentUseCase
import bd.nib.songbadpatro.domain.usecase.DateValidationUseCase
import bd.nib.songbadpatro.domain.usecase.DeduplicateAndSyndicateUseCase
import bd.nib.songbadpatro.domain.usecase.EditorialAlertMatcher
import bd.nib.songbadpatro.domain.usecase.SortAndFilterArticlesUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.Cache
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MyNewsDatabase {
        return Room.databaseBuilder(
            context,
            MyNewsDatabase::class.java,
            MyNewsDatabase.DATABASE_NAME
        )
            .addMigrations(MyNewsDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideArticleDao(db: MyNewsDatabase): ArticleDao = db.articleDao()

    @Provides
    fun provideBookmarkDao(db: MyNewsDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideReadingHistoryDao(db: MyNewsDatabase): ReadingHistoryDao = db.readingHistoryDao()

    @Provides
    fun provideAuthorDao(db: MyNewsDatabase): AuthorDao = db.authorDao()

    @Provides
    fun providePublisherDao(db: MyNewsDatabase): PublisherDao = db.publisherDao()

    @Provides
    fun provideFeedSourceDao(db: MyNewsDatabase): FeedSourceDao = db.feedSourceDao()

    @Provides
    fun provideSyncLogDao(db: MyNewsDatabase): SyncLogDao = db.syncLogDao()

    @Provides
    fun provideKeywordAlertDao(db: MyNewsDatabase): KeywordAlertDao = db.keywordAlertDao()

    @Provides
    fun provideNotifiedArticleDao(db: MyNewsDatabase): NotifiedArticleDao = db.notifiedArticleDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val httpCacheDir = File(context.cacheDir, "my_news_http_cache")
        val httpCache = Cache(httpCacheDir, 20L * 1024L * 1024L) // 20 MB HTTP cache
        return OkHttpClient.Builder()
            .cache(httpCache)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRssAtomParser(): RssAtomParser = RssAtomParser()

    @Provides
    @Singleton
    fun provideMetadataExtractor(): SectionAndMetadataExtractor = SectionAndMetadataExtractor()

    @Provides
    @Singleton
    fun provideDateValidator(): DateValidationUseCase = DateValidationUseCase()

    @Provides
    @Singleton
    fun provideClassifier(): ClassifyEditorialContentUseCase = ClassifyEditorialContentUseCase()

    @Provides
    @Singleton
    fun provideDeduplicator(): DeduplicateAndSyndicateUseCase = DeduplicateAndSyndicateUseCase()

    @Provides
    @Singleton
    fun provideSorterAndFilter(): SortAndFilterArticlesUseCase = SortAndFilterArticlesUseCase()

    @Provides
    @Singleton
    fun provideEditorialAlertMatcher(): EditorialAlertMatcher = EditorialAlertMatcher()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindingsModule {

    @Binds
    @Singleton
    abstract fun bindEditorialRepository(impl: EditorialRepositoryImpl): EditorialRepository

    @Binds
    @Singleton
    abstract fun bindPublisherRepository(impl: PublisherRepositoryImpl): PublisherRepository

    @Binds
    @Singleton
    abstract fun bindAuthorRepository(impl: AuthorRepositoryImpl): AuthorRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindAlertRepository(impl: AlertRepositoryImpl): AlertRepository
}

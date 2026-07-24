package com.example.devmanager.di

import android.content.Context
import androidx.room.Room
import com.example.devmanager.data.local.AppDatabase
import com.example.devmanager.data.local.dao.BookmarkDao
import com.example.devmanager.data.local.dao.RecentFileDao
import com.example.devmanager.data.local.dao.TrashDao
import com.example.devmanager.data.repository.BookmarkRepository
import com.example.devmanager.data.repository.FileRepository
import com.example.devmanager.data.repository.FileRepositoryImpl
import com.example.devmanager.data.repository.TrashRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "devmanager.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideBookmarkDao(db: AppDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideTrashDao(db: AppDatabase): TrashDao = db.trashDao()

    @Provides
    fun provideRecentFileDao(db: AppDatabase): RecentFileDao = db.recentFileDao()

    @Provides
    @Singleton
    fun provideFileRepository(impl: FileRepositoryImpl): FileRepository = impl
}

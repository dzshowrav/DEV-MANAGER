package com.example.devmanager.data.repository

import com.example.devmanager.data.local.dao.BookmarkDao
import com.example.devmanager.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao
) {
    fun getAllBookmarks(): Flow<List<BookmarkEntity>> = bookmarkDao.getAllBookmarks()

    suspend fun isBookmarked(path: String): Boolean = bookmarkDao.isBookmarked(path) > 0

    suspend fun toggle(path: String, label: String) {
        val existing = bookmarkDao.getBookmark(path)
        if (existing != null) bookmarkDao.deleteByPath(path)
        else bookmarkDao.insert(BookmarkEntity(path = path, label = label))
    }

    suspend fun add(path: String, label: String) {
        bookmarkDao.insert(BookmarkEntity(path = path, label = label))
    }

    suspend fun remove(path: String) {
        bookmarkDao.deleteByPath(path)
    }
}

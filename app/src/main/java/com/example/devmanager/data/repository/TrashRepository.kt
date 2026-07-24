package com.example.devmanager.data.repository

import com.example.devmanager.data.local.dao.TrashDao
import com.example.devmanager.data.local.entity.TrashEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrashRepository @Inject constructor(
    private val trashDao: TrashDao,
    private val fileRepository: FileRepository
) {
    fun getAllTrash(): Flow<List<TrashEntity>> = trashDao.getAllTrash()

    suspend fun moveToTrash(file: File): Boolean {
        return try {
            val trashDir = fileRepository.getTrashDir()
            val trashPath = fileRepository.resolveConflict(file.name, trashDir.absolutePath, com.example.devmanager.data.model.ConflictStrategy.RENAME)
            if (file.renameTo(trashPath)) {
                trashDao.insert(TrashEntity(
                    originalPath = file.absolutePath,
                    trashPath = trashPath.absolutePath,
                    size = if (file.isDirectory) fileRepository.getTotalSize(file) else file.length()
                ))
                true
            } else false
        } catch (_: Exception) { false }
    }

    suspend fun restore(trashItem: TrashEntity): Boolean {
        return try {
            val trashFile = File(trashItem.trashPath)
            val originalFile = File(trashItem.originalPath)
            originalFile.parentFile?.mkdirs()
            if (trashFile.renameTo(originalFile)) {
                trashDao.deleteByOriginalPath(trashItem.originalPath)
                true
            } else false
        } catch (_: Exception) { false }
    }

    suspend fun deletePermanently(trashItem: TrashEntity): Boolean {
        return try {
            fileRepository.delete(File(trashItem.trashPath))
            trashDao.deleteByOriginalPath(trashItem.originalPath)
            true
        } catch (_: Exception) { false }
    }

    suspend fun emptyTrash(): Boolean {
        return try {
            val items = trashDao.getAllTrash().first()
            items.forEach { fileRepository.delete(File(it.trashPath)) }
            trashDao.clearAll()
            true
        } catch (_: Exception) { false }
    }
}

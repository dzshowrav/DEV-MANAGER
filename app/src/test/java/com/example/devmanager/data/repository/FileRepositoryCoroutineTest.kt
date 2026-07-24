package com.example.devmanager.data.repository

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileRepositoryCoroutineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val repository = FileRepositoryImpl()

    @Test
    fun `enqueue and update operation works correctly`() = runTest {
        val op = com.example.devmanager.data.model.FileOperation(
            id = "test-1",
            type = com.example.devmanager.data.model.OperationType.COPY,
            source = File("/test")
        )
        repository.enqueueOperation(op)
        val operations = repository.operations
        assertEquals(1, (operations.value).size)
    }

    @Test
    fun `cancel operation marks as cancelled`() = runTest {
        val op = com.example.devmanager.data.model.FileOperation(
            id = "cancel-1",
            type = com.example.devmanager.data.model.OperationType.DELETE,
            source = File("/test")
        )
        repository.enqueueOperation(op)
        repository.cancelOperation("cancel-1")
        val updated = repository.operations.value.find { it.id == "cancel-1" }
        assertEquals(com.example.devmanager.data.model.OperationStatus.CANCELLED, updated?.status)
    }

    @Test
    fun `trash dir exists after access`() {
        val trashDir = repository.getTrashDir()
        assertTrue(trashDir.exists())
        assertTrue(trashDir.isDirectory)
    }

    @Test
    fun `getStorageVolumes returns at least one volume`() {
        val volumes = repository.getStorageVolumes()
        assertTrue(volumes.isNotEmpty())
    }

    @Test
    fun `delete recursive removes directory with contents`() = runTest {
        val dir = tempFolder.newFolder("recursive")
        File(dir, "sub").mkdir()
        File(File(dir, "sub"), "file.txt").writeText("content")

        val result = repository.delete(dir)
        assertTrue(result.isSuccess)
        assertFalse(dir.exists())
    }

    @Test
    fun `copy file preserves content`() = runTest {
        val source = tempFolder.newFile("source.txt")
        source.writeText("Hello Copy Test")

        val result = repository.copy(source, tempFolder.root.absolutePath, com.example.devmanager.data.model.ConflictStrategy.REPLACE)
        assertTrue(result.isSuccess)

        val destName = result.getOrNull()?.name ?: "source.txt"
        val dest = File(tempFolder.root, destName)
        assertTrue(dest.exists())
        assertEquals("Hello Copy Test", dest.readText())
    }
}

package com.example.devmanager.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: FileRepositoryImpl

    @Before
    fun setup() {
        repository = FileRepositoryImpl()
    }

    @Test
    fun `formatSize returns correct byte string`() {
        assertEquals("500 B", repository.formatSize(500))
        assertEquals("1.0 KB", repository.formatSize(1024))
        assertEquals("1.5 MB", repository.formatSize(1572864))
        assertEquals("1.0 GB", repository.formatSize(1073741824))
    }

    @Test
    fun `formatDate returns non-empty string`() {
        val result = repository.formatDate(System.currentTimeMillis())
        assertNotNull(result)
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `createDirectory creates directory successfully`() {
        val parent = tempFolder.root.absolutePath
        val result = repository.createDirectory(parent, "testdir")
        assertTrue(result.isSuccess)
        assertTrue(File(parent, "testdir").exists())
    }

    @Test
    fun `createDirectory fails for invalid path`() {
        val result = repository.createDirectory("/nonexistent/path", "dir")
        assertTrue(result.isFailure)
    }

    @Test
    fun `createFile creates file successfully`() {
        val parent = tempFolder.root.absolutePath
        val result = repository.createFile(parent, "test.txt")
        assertTrue(result.isSuccess)
        assertTrue(File(parent, "test.txt").exists())
    }

    @Test
    fun `rename renames file successfully`() {
        val file = tempFolder.newFile("old.txt")
        val result = repository.rename(file, "new.txt")
        assertTrue(result.isSuccess)
        assertFalse(File(file.parent, "old.txt").exists())
        assertTrue(File(file.parent, "new.txt").exists())
    }

    @Test
    fun `delete removes file successfully`() {
        val file = tempFolder.newFile("todelete.txt")
        assertTrue(file.exists())
        val result = repository.delete(file)
        assertTrue(result.isSuccess)
        assertFalse(file.exists())
    }

    @Test
    fun `getFileDetails returns map with name and size`() {
        val file = tempFolder.newFile("details.txt")
        file.writeText("Hello World")
        val details = repository.getFileDetails(file)
        assertEquals("details.txt", details["name"])
        assertEquals(repository.formatSize(file.length()), details["size"])
    }

    @Test
    fun `listFiles returns files in directory`() {
        val dir = tempFolder.newFolder("listdir")
        File(dir, "a.txt").createNewFile()
        File(dir, "b.txt").createNewFile()
        val files = repository.listFiles(dir.absolutePath, true)
        assertEquals(2, files.size)
    }

    @Test
    fun `listFiles filters hidden files`() {
        val dir = tempFolder.newFolder("hiddendir")
        File(dir, "visible.txt").createNewFile()
        File(dir, ".hidden.txt").createNewFile()
        val files = repository.listFiles(dir.absolutePath, false)
        assertEquals(1, files.size)
        assertEquals("visible.txt", files[0].name)
    }

    @Test
    fun `getStorageInfo returns non-zero values`() {
        val info = repository.getStorageInfo(tempFolder.root.absolutePath)
        assertTrue(info.first > 0)
        assertTrue(info.second >= 0)
    }

    @Test
    fun `compressToZip creates valid zip`() {
        val dir = tempFolder.newFolder("ziptest")
        val file1 = File(dir, "file1.txt").apply { writeText("content1") }
        val file2 = File(dir, "file2.txt").apply { writeText("content2") }
        val output = File(tempFolder.root, "test.zip")
        val result = repository.compressToZip(listOf(file1, file2), output.absolutePath)
        assertTrue(result.isSuccess)
        assertTrue(output.exists())
        assertTrue(output.length() > 0)
    }

    @Test
    fun `resolveConflict returns unique name on rename strategy`() {
        val dir = tempFolder.newFolder("conflict")
        File(dir, "file.txt").createNewFile()
        val resolved = repository.resolveConflict("file.txt", dir.absolutePath, com.example.devmanager.data.model.ConflictStrategy.RENAME)
        assertNotNull(resolved)
        assertFalse(resolved.name == "file.txt")
    }

    @Test
    fun `findLargeFiles returns files above threshold`() {
        val dir = tempFolder.newFolder("largefiles")
        val small = File(dir, "small.txt").apply { writeText("small") }
        val large = File(dir, "large.txt").apply {
            writeText("x".repeat(500))
        }
        val largeFiles = repository.findLargeFiles(dir.absolutePath, 100)
        assertTrue(largeFiles.contains(large))
        assertFalse(largeFiles.contains(small))
    }
}

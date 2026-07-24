package com.example.devmanager.ui.archive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ArchiveManager {

    suspend fun createZip(files: List<File>, outputPath: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val zipFile = File(outputPath)
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                for (file in files) addToZip(file, file.name, zos)
            }
            Result.success(zipFile)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun extractZip(zipFile: File, outputDir: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(outputDir, entry.name)
                    if (entry.isDirectory) outFile.mkdirs()
                    else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { zis.copyTo(it) }
                    }
                    entry = zis.nextEntry
                }
            }
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun extractTarGz(tarGzFile: File, outputDir: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(
                "tar", "-xzf", tarGzFile.absolutePath, "-C", outputDir
            ).redirectErrorStream(true).start()
            process.waitFor()
            if (process.exitValue() == 0) Result.success(Unit)
            else Result.failure(Exception("tar extraction failed: ${process.inputStream.bufferedReader().readText()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun extract7z(sevenZFile: File, outputDir: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(
                "7z", "x", sevenZFile.absolutePath, "-o$outputDir", "-y"
            ).redirectErrorStream(true).start()
            process.waitFor()
            if (process.exitValue() == 0) Result.success(Unit)
            else Result.failure(Exception("7z extraction failed: ${process.inputStream.bufferedReader().readText()}")
            )
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun extractRar(rarFile: File, outputDir: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(
                "unrar", "x", "-y", rarFile.absolutePath, "$outputDir/"
            ).redirectErrorStream(true).start()
            process.waitFor()
            if (process.exitValue() == 0) Result.success(Unit)
            else Result.failure(Exception("rar extraction failed: ${process.inputStream.bufferedReader().readText()}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    fun getSupportedFormats(): List<String> = listOf("zip", "tar.gz", "7z", "rar")

    fun isFormatSupported(ext: String): Boolean = ext.lowercase() in listOf("zip", "tar", "gz", "7z", "rar")

    private fun addToZip(file: File, entryName: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            val name = if (entryName.endsWith("/")) entryName else "$entryName/"
            zos.putNextEntry(ZipEntry(name)); zos.closeEntry()
            file.listFiles()?.forEach { addToZip(it, "$entryName/${it.name}", zos) }
        } else {
            zos.putNextEntry(ZipEntry(entryName))
            FileInputStream(file).use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }
}

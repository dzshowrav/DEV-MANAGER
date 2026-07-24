package com.example.devmanager.ui.vault

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SecureVault(private val context: Context) {

    private val vaultDir: File
        get() = File(context.filesDir, ".secure_vault").also { it.mkdirs() }

    private val vaultIndexFile: File
        get() = File(vaultDir, "index.vault")

    private fun deriveKey(pin: String): SecretKeySpec {
        val keyBytes = pin.toByteArray().let {
            val padded = ByteArray(32)
            it.copyInto(padded, 0, 0, minOf(it.size, 32))
            padded
        }
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun createCipher(mode: Int, key: SecretKeySpec): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { if (mode == Cipher.ENCRYPT_MODE) java.security.SecureRandom().nextBytes(it) }
        cipher.init(mode, key, GCMParameterSpec(128, iv))
        return cipher
    }

    suspend fun encryptFile(inputFile: File, pin: String, outputName: String? = null): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val key = deriveKey(pin)
                val cipher = createCipher(Cipher.ENCRYPT_MODE, key)
                val outputFile = File(vaultDir, outputName ?: "${inputFile.name}.enc")
                FileOutputStream(outputFile).use { fos ->
                    fos.write(cipher.iv)
                    FileInputStream(inputFile).use { fis ->
                        CipherOutputStream(fos, cipher).use { cos -> fis.copyTo(cos) }
                    }
                }
                addToIndex(outputFile.name, inputFile.absolutePath)
                Result.success(outputFile)
            } catch (e: Exception) { Result.failure(e) }
        }

    suspend fun decryptFile(encryptedFile: File, pin: String, outputDir: String): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val key = deriveKey(pin)
                val iv = ByteArray(12)
                FileInputStream(encryptedFile).use { fis ->
                    fis.read(iv)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                    val originalName = getOriginalName(encryptedFile.name) ?: encryptedFile.name.removeSuffix(".enc")
                    val outputFile = File(outputDir, originalName)
                    FileOutputStream(outputFile).use { fos ->
                        CipherInputStream(fis, cipher).use { cis -> cis.copyTo(fos) }
                    }
                    Result.success(outputFile)
                }
            } catch (e: Exception) { Result.failure(e) }
        }

    fun getVaultFiles(): List<File> = vaultDir.listFiles()?.filter { it.extension == "enc" }?.sortedByDescending { it.lastModified() } ?: emptyList()

    suspend fun deleteVaultFile(file: File): Boolean = withContext(Dispatchers.IO) {
        removeFromIndex(file.name)
        file.delete()
    }

    fun isVaultEmpty(): Boolean = getVaultFiles().isEmpty()

    private fun addToIndex(encName: String, originalPath: String) {
        val index = loadIndex().toMutableMap()
        index[encName] = originalPath
        indexFile().writeText(index.entries.joinToString("\n") { "${it.key}|${it.value}" })
    }

    private fun removeFromIndex(encName: String) {
        val index = loadIndex().toMutableMap()
        index.remove(encName)
        indexFile().writeText(index.entries.joinToString("\n") { "${it.key}|${it.value}" })
    }

    private fun getOriginalName(encName: String): String? = loadIndex()[encName]

    private fun loadIndex(): Map<String, String> {
        return try {
            indexFile().readLines().map { line ->
                val parts = line.split("|", limit = 2)
                parts[0] to parts.getOrElse(1) { "unknown" }
            }.toMap()
        } catch (_: Exception) { emptyMap() }
    }

    private fun indexFile(): File = File(vaultDir, "index.dat")
}

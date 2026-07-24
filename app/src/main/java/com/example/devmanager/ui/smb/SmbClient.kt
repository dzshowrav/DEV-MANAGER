package com.example.devmanager.ui.smb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Socket

data class SmbServer(
    val host: String,
    val shareName: String,
    val isReachable: Boolean = false
)

data class SmbFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0
)

object SmbClient {

    suspend fun discoverServers(timeoutMs: Int = 2000): List<SmbServer> = withContext(Dispatchers.IO) {
        val servers = mutableListOf<SmbServer>()
        try {
            val subnet = getLocalSubnet()
            for (i in 1..254) {
                val host = "$subnet.$i"
                try {
                    val socket = Socket()
                    socket.connect(java.net.InetSocketAddress(host, 445), timeoutMs)
                    socket.close()
                    servers.add(SmbServer(host, "\\\\$host\\shared", true))
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        servers
    }

    suspend fun listShares(host: String, timeoutMs: Int = 3000): List<String> = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            socket.connect(java.net.InetSocketAddress(host, 445), timeoutMs)
            socket.close()
            listOf("shared", "documents", "downloads")
        } catch (_: Exception) { emptyList() }
    }

    suspend fun listFiles(host: String, share: String, path: String = ""): List<SmbFile> =
        withContext(Dispatchers.IO) {
            mutableListOf(
                SmbFile("SampleFolder", "$path/SampleFolder", true),
                SmbFile("Notes.txt", "$path/Notes.txt", false, 2048),
                SmbFile("Photo.jpg", "$path/Photo.jpg", false, 1024000),
                SmbFile("Documents", "$path/Documents", true)
            )
        }

    private fun getLocalSubnet(): String {
        return try {
            val ip = java.net.InetAddress.getLocalHost().hostAddress ?: return "192.168.1"
            ip.substringBeforeLast(".")
        } catch (_: Exception) { "192.168.1" }
    }
}

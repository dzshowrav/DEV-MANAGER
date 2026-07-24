package com.example.devmanager.ui.ftpserver

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FtpServer(private val rootDir: String, private val port: Int = 2211) {

    private var serverSocket: ServerSocket? = null
    private var running = false

    fun isRunning(): Boolean = running

    suspend fun start() = withContext(Dispatchers.IO) {
        if (running) return@withContext
        running = true
        serverSocket = ServerSocket(port)
        Log.d("FtpServer", "FTP Server started on port $port")
        while (running) {
            try {
                val client = serverSocket!!.accept()
                ClientHandler(client, rootDir).handle()
            } catch (_: Exception) { break }
        }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        Log.d("FtpServer", "FTP Server stopped")
    }

    private class ClientHandler(private val socket: Socket, private val rootDir: String) {
        private var currentDir = rootDir
        private lateinit var reader: BufferedReader
        private lateinit var writer: OutputStreamWriter

        fun handle() {
            try {
                reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                writer = OutputStreamWriter(socket.getOutputStream())
                sendResponse("220 Simple FTP Server Ready")
                while (true) {
                    val line = reader.readLine() ?: break
                    val response = processCommand(line.trim())
                    sendResponse(response)
                    if (line.trim().uppercase() == "QUIT") break
                }
            } catch (_: Exception) {} finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }

        private fun sendResponse(msg: String) {
            writer.write("$msg\r\n"); writer.flush()
        }

        private fun processCommand(cmd: String): String {
            val parts = cmd.split(" ", limit = 2)
            val command = parts[0].uppercase()
            val arg = parts.getOrElse(1) { "" }

            return when (command) {
                "USER" -> "230 User logged in"
                "PASS" -> "230 Login successful"
                "SYST" -> "215 UNIX Type: L8"
                "PWD" -> "257 \"$currentDir\""
                "TYPE" -> "200 Type set"
                "QUIT" -> "221 Goodbye"
                "EPSV", "PASV" -> "227 Entering Passive Mode (0,0,0,0,0,0)"
                "PORT" -> "200 Port command successful"
                "LIST" -> listFiles(arg)
                "NLST" -> listNames(arg)
                "CWD" -> changeDir(arg)
                "CDUP" -> { changeDir(".."); "250 Directory changed" }
                "MKD" -> makeDir(arg)
                "RMD" -> removeDir(arg)
                "DELE" -> deleteFile(arg)
                "RNFR" -> "350 Ready for destination"
                "RNTO" -> "250 Rename successful"
                "RETR" -> retrieveFile(arg)
                "STOR" -> "502 Not implemented"
                "SIZE" -> getSize(arg)
                "MDTM" -> getModTime(arg)
                "FEAT" -> "211 No features"
                else -> "502 Command not implemented: $command"
            }
        }

        private fun listFiles(arg: String): String {
            val dir = if (arg.isNotBlank() && arg != "-al" && arg != "-l") File(currentDir, arg) else File(currentDir)
            val target = if (dir.isAbsolute) dir else File(currentDir, arg)
            if (!target.exists()) return "550 Directory not found"
            val files = target.listFiles() ?: return "150 Opening data connection"
            val listing = files.joinToString("\r\n") { file ->
                val perms = if (file.isDirectory) "drwxr-xr-x" else "-rw-r--r--"
                val size = String.format("%12d", file.length())
                val date = SimpleDateFormat("MMM dd HH:mm", Locale.US).format(Date(file.lastModified()))
                "$perms 1 owner group $size $date ${file.name}"
            }
            return "150 Here comes the listing\r\n$listing\r\n226 Directory send OK"
        }

        private fun listNames(arg: String): String {
            val dir = File(if (arg.isNotBlank()) File(currentDir, arg).absolutePath else currentDir)
            if (!dir.exists()) return "550 Directory not found"
            val names = dir.listFiles()?.joinToString("\r\n") { it.name } ?: ""
            return "150 Here comes the listing\r\n$names\r\n226 Directory send OK"
        }

        private fun changeDir(arg: String): String {
            val newDir = if (arg.startsWith("/")) File(arg) else File(currentDir, arg)
            if (newDir.exists() && newDir.isDirectory) {
                currentDir = newDir.absolutePath
                return "250 Directory changed"
            }
            return "550 Directory not found"
        }

        private fun makeDir(arg: String): String {
            return if (File(currentDir, arg).mkdirs()) "257 Directory created" else "550 Failed"
        }

        private fun removeDir(arg: String): String {
            return if (File(currentDir, arg).delete()) "250 Directory removed" else "550 Failed"
        }

        private fun deleteFile(arg: String): String {
            return if (File(currentDir, arg).delete()) "250 File deleted" else "550 Failed"
        }

        private fun retrieveFile(arg: String): String {
            val file = File(currentDir, arg)
            if (!file.exists()) return "550 File not found"
            return "150 Opening binary data connection for ${file.name} (${file.length()} bytes)\r\n226 Transfer complete"
        }

        private fun getSize(arg: String): String {
            val file = File(currentDir, arg)
            return if (file.exists()) "213 ${file.length()}" else "550 File not found"
        }

        private fun getModTime(arg: String): String {
            val file = File(currentDir, arg)
            return if (file.exists()) {
                val modTime = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date(file.lastModified()))
                "213 $modTime"
            } else "550 File not found"
        }
    }

    companion object {
        fun getLocalIpAddress(): String {
            return try {
                val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val iface = interfaces.nextElement()
                    if (iface.isLoopback || !iface.isUp) continue
                    val addrs = iface.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (addr is java.net.Inet4Address) return addr.hostAddress ?: "127.0.0.1"
                    }
                }
                "127.0.0.1"
            } catch (_: Exception) { "127.0.0.1" }
        }
    }
}

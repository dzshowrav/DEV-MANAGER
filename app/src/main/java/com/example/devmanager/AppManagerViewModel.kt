package com.example.devmanager

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppInfo(
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val sourceDir: String,
    val isSystemApp: Boolean,
    val iconBitmap: Bitmap?,
    val size: Long,
    val installTime: Long,
    val updateTime: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val permissions: List<String>
)

data class ApkFileItem(
    val file: File,
    val size: Long,
    val label: String,
    val packageName: String?,
    val versionName: String?,
    val iconBitmap: Bitmap?,
    val isInstalled: Boolean
)

class AppManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val packageManager: PackageManager = context.packageManager

    private val _userApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val userApps: StateFlow<List<AppInfo>> = _userApps.asStateFlow()

    private val _systemApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val systemApps: StateFlow<List<AppInfo>> = _systemApps.asStateFlow()

    private val _apkFiles = MutableStateFlow<List<ApkFileItem>>(emptyList())
    val apkFiles: StateFlow<List<ApkFileItem>> = _apkFiles.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isScanningApks = MutableStateFlow(false)
    val isScanningApks: StateFlow<Boolean> = _isScanningApks.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    val selectedPackages: StateFlow<Set<String>> = _selectedPackages.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    // Batch uninstall operation queue
    private val uninstallQueue = mutableListOf<String>()
    private var isBatchUninstallActive = false

    init {
        loadApps()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSelection(packageName: String) {
        val current = _selectedPackages.value
        if (current.contains(packageName)) {
            _selectedPackages.value = current - packageName
        } else {
            _selectedPackages.value = current + packageName
        }
    }

    fun clearSelection() {
        _selectedPackages.value = emptySet()
    }

    fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            val (userList, systemList) = withContext(Dispatchers.IO) {
                val user = mutableListOf<AppInfo>()
                val system = mutableListOf<AppInfo>()
                try {
                    val flags = PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS
                    val packages = packageManager.getInstalledPackages(flags)
                    for (pkg in packages) {
                        try {
                            val appInfo = pkg.applicationInfo ?: continue
                            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                            
                            val name = appInfo.loadLabel(packageManager).toString()
                            val pName = pkg.packageName ?: ""
                            val vName = pkg.versionName ?: "1.0"
                            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                pkg.longVersionCode
                            } else {
                                @Suppress("DEPRECATION")
                                pkg.versionCode.toLong()
                            }
                            val sourceDir = appInfo.sourceDir ?: ""
                            val file = File(sourceDir)
                            val size = if (file.exists()) file.length() else 0L

                            val installTime = pkg.firstInstallTime
                            val updateTime = pkg.lastUpdateTime
                            val targetSdk = appInfo.targetSdkVersion
                            val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                appInfo.minSdkVersion
                            } else {
                                19
                            }

                            val perms = pkg.requestedPermissions?.toList() ?: emptyList()

                            // Convert app icon to a bitmap for compose caching & safe drawing
                            var iconBmp: Bitmap? = null
                            try {
                                val drawable = appInfo.loadIcon(packageManager)
                                iconBmp = drawableToBitmap(drawable)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            val appItem = AppInfo(
                                name = name,
                                packageName = pName,
                                versionName = vName,
                                versionCode = vCode,
                                sourceDir = sourceDir,
                                isSystemApp = isSystem,
                                iconBitmap = iconBmp,
                                size = size,
                                installTime = installTime,
                                updateTime = updateTime,
                                minSdk = minSdk,
                                targetSdk = targetSdk,
                                permissions = perms
                            )

                            if (isSystem) {
                                system.add(appItem)
                            } else {
                                user.add(appItem)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Sort by name
                user.sortBy { it.name.lowercase(Locale.getDefault()) }
                system.sortBy { it.name.lowercase(Locale.getDefault()) }

                Pair(user, system)
            }

            _userApps.value = userList
            _systemApps.value = systemList
            _isLoading.value = false
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        try {
            if (drawable is android.graphics.drawable.BitmapDrawable) {
                if (drawable.bitmap != null) {
                    return drawable.bitmap
                }
            }
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 120
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 120
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun extractApk(app: AppInfo): File? {
        val srcFile = File(app.sourceDir)
        if (!srcFile.exists()) {
            viewModelScope.launch { _toastMessage.emit("Source APK file not found!") }
            return null
        }

        val targetDir = File(Environment.getExternalStorageDirectory(), "ExtractedAPKs")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        // Clean label name for naming APK
        val cleanName = app.name.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        val targetFile = File(targetDir, "${cleanName}_${app.versionName}.apk")

        try {
            srcFile.copyTo(targetFile, overwrite = true)
            viewModelScope.launch {
                _toastMessage.emit("Extracted successfully to ExtractedAPKs/${targetFile.name}")
            }
            return targetFile
        } catch (e: Exception) {
            e.printStackTrace()
            viewModelScope.launch {
                _toastMessage.emit("Extraction failed: ${e.localizedMessage}")
            }
            return null
        }
    }

    fun batchExtractSelected() {
        val selected = _selectedPackages.value
        if (selected.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            withContext(Dispatchers.IO) {
                var count = 0
                val allApps = _userApps.value + _systemApps.value
                val toExtract = allApps.filter { selected.contains(it.packageName) }
                for (app in toExtract) {
                    val result = extractApk(app)
                    if (result != null) {
                        count++
                    }
                }
                _toastMessage.emit("Extracted $count of ${toExtract.size} apps to ExtractedAPKs folder")
            }
            clearSelection()
            _isLoading.value = false
        }
    }

    fun shareApk(app: AppInfo, context: Context) {
        val file = File(app.sourceDir)
        if (!file.exists()) {
            viewModelScope.launch { _toastMessage.emit("Source file not found!") }
            return
        }
        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share ${app.name} APK")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            viewModelScope.launch { _toastMessage.emit("Failed to share app: ${e.localizedMessage}") }
        }
    }

    fun uninstallApp(packageName: String, context: Context, launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }
            launcher.launch(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            viewModelScope.launch { _toastMessage.emit("Could not start uninstallation: ${e.localizedMessage}") }
        }
    }

    fun startBatchUninstall(packages: List<String>, context: Context, launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        if (packages.isEmpty()) return
        uninstallQueue.clear()
        uninstallQueue.addAll(packages)
        isBatchUninstallActive = true
        processNextUninstall(context, launcher)
    }

    fun processNextUninstall(context: Context, launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        if (!isBatchUninstallActive) return
        if (uninstallQueue.isEmpty()) {
            isBatchUninstallActive = false
            viewModelScope.launch {
                _toastMessage.emit("Batch uninstall finished")
                loadApps()
            }
            clearSelection()
            return
        }

        val nextPackage = uninstallQueue.removeAt(0)
        // Verify if package is still installed
        if (isAppInstalled(nextPackage)) {
            uninstallApp(nextPackage, context, launcher)
        } else {
            // Skip and go to next
            processNextUninstall(context, launcher)
        }
    }

    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun scanApkFiles() {
        viewModelScope.launch {
            _isScanningApks.value = true
            val results = withContext(Dispatchers.IO) {
                val apkList = mutableListOf<ApkFileItem>()
                // We scan standard directories recursively, but skip /Android of root directories and hidden directories
                val storageDir = Environment.getExternalStorageDirectory()
                val listToScan = mutableListOf<File>()
                
                // Prioritize standard directories to scan
                val standardDirs = listOf(
                    Environment.DIRECTORY_DOWNLOADS,
                    Environment.DIRECTORY_DOCUMENTS,
                    "ExtractedAPKs",
                    "DevManager",
                    "Bluetooth"
                )

                for (dirName in standardDirs) {
                    val dir = File(storageDir, dirName)
                    if (dir.exists() && dir.isDirectory) {
                        listToScan.add(dir)
                    }
                }
                
                // Add storage root itself, but we will scan selectively
                listToScan.add(storageDir)

                val visited = mutableSetOf<String>()
                
                fun scanDir(dir: File) {
                    val path = dir.absolutePath
                    if (visited.contains(path)) return
                    visited.add(path)

                    val filesAndDirs = dir.listFiles() ?: return
                    for (f in filesAndDirs) {
                        // Skip hidden files/dirs and system Android folders
                        if (f.name.startsWith(".")) continue
                        if (f.isDirectory) {
                            if (f.name.equals("Android", ignoreCase = true)) continue
                            if (f.name.equals("data", ignoreCase = true) && f.absolutePath.contains("Android")) continue
                            // Limit recursive depth or only scan 2 levels deep if scanning root to avoid freezing
                            if (dir == storageDir) {
                                // For root directory, selectively scan 2 levels inside directories
                                val subFiles = f.listFiles() ?: continue
                                for (sf in subFiles) {
                                    if (sf.name.startsWith(".")) continue
                                    if (sf.isDirectory) {
                                        val extraFiles = sf.listFiles() ?: continue
                                        for (ef in extraFiles) {
                                            if (ef.isFile && ef.name.lowercase(Locale.getDefault()).endsWith(".apk")) {
                                                parseApkFile(ef)?.let { apkList.add(it) }
                                            }
                                        }
                                    } else if (sf.name.lowercase(Locale.getDefault()).endsWith(".apk")) {
                                        parseApkFile(sf)?.let { apkList.add(it) }
                                    }
                                }
                            } else {
                                scanDir(f)
                            }
                        } else if (f.isFile && f.name.lowercase(Locale.getDefault()).endsWith(".apk")) {
                            parseApkFile(f)?.let { apkList.add(it) }
                        }
                    }
                }

                // Scan all gathered dirs
                for (dir in listToScan) {
                    scanDir(dir)
                }

                apkList.sortedByDescending { it.file.lastModified() }
            }
            _apkFiles.value = results.distinctBy { it.file.absolutePath }
            _isScanningApks.value = false
        }
    }

    private fun parseApkFile(file: File): ApkFileItem? {
        try {
            if (!file.exists() || file.length() == 0L) return null
            val packageInfo = packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
            
            // Set public source directories so PM can load metadata properly
            packageInfo.applicationInfo?.let { appInfo ->
                appInfo.sourceDir = file.absolutePath
                appInfo.publicSourceDir = file.absolutePath
            }
            val appInfo = packageInfo.applicationInfo ?: return null
            
            val label = try {
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                file.nameWithoutExtension
            }

            val packageName = packageInfo.packageName ?: ""
            val versionName = packageInfo.versionName ?: "1.0"
            
            val isInstalled = isAppInstalled(packageName)

            var iconBmp: Bitmap? = null
            try {
                val drawable = packageManager.getApplicationIcon(appInfo)
                iconBmp = drawableToBitmap(drawable)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return ApkFileItem(
                file = file,
                size = file.length(),
                label = label,
                packageName = packageName,
                versionName = versionName,
                iconBitmap = iconBmp,
                isInstalled = isInstalled
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun deleteApkFile(apk: ApkFileItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (apk.file.exists()) {
                        apk.file.delete()
                        _toastMessage.emit("APK deleted successfully")
                    }
                } catch (e: Exception) {
                    _toastMessage.emit("Failed to delete APK")
                }
            }
            scanApkFiles()
        }
    }

    fun formatSize(sizeBytes: Long): String {
        if (sizeBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.2f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun formatTime(timeMillis: Long): String {
        if (timeMillis <= 0) return "N/A"
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timeMillis))
    }
}

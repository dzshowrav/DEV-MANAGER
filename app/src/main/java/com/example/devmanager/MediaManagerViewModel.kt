package com.example.devmanager

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

// Media elements definition
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val path: String,
    val displayName: String,
    val album: String,
    val artist: String?,
    val duration: Long, // Ms
    val size: Long,
    val dateAdded: Long,
    val width: Int,
    val height: Int,
    val mimeType: String
)

data class AlbumItem(
    val name: String,
    val thumbnailUri: Uri?,
    val itemCount: Int,
    val items: List<MediaItem>
)

data class Playlist(
    val id: String,
    val name: String,
    val trackPaths: List<String>
)

class MediaManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("devmanager_media_pref", Context.MODE_PRIVATE)

    // State flows
    private val _images = MutableStateFlow<List<MediaItem>>(emptyList())
    val images: StateFlow<List<MediaItem>> = _images.asStateFlow()

    private val _imageAlbums = MutableStateFlow<List<AlbumItem>>(emptyList())
    val imageAlbums: StateFlow<List<AlbumItem>> = _imageAlbums.asStateFlow()

    private val _videos = MutableStateFlow<List<MediaItem>>(emptyList())
    val videos: StateFlow<List<MediaItem>> = _videos.asStateFlow()

    private val _videoAlbums = MutableStateFlow<List<AlbumItem>>(emptyList())
    val videoAlbums: StateFlow<List<AlbumItem>> = _videoAlbums.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<MediaItem>>(emptyList())
    val audioTracks: StateFlow<List<MediaItem>> = _audioTracks.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    init {
        loadAllMedia()
        loadPlaylists()
    }

    fun loadAllMedia() {
        viewModelScope.launch {
            _isLoading.value = true
            withContext(Dispatchers.IO) {
                loadImages()
                loadVideos()
                loadAudios()
            }
            _isLoading.value = false
        }
    }

    private fun loadImages() {
        val list = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.MIME_TYPE
        )

        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )

        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val widthCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val path = c.getString(pathCol)
                // Skip if file doesn't exist
                if (path == null || !File(path).exists()) continue

                val name = c.getString(nameCol) ?: File(path).name
                val album = c.getString(albumCol) ?: "Screenshots/Pictures"
                val size = c.getLong(sizeCol)
                val date = c.getLong(dateCol) * 1000L
                val width = c.getInt(widthCol)
                val height = c.getInt(heightCol)
                val mime = c.getString(mimeCol) ?: "image/jpeg"
                val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())

                list.add(
                    MediaItem(
                        id = id,
                        uri = uri,
                        path = path,
                        displayName = name,
                        album = album,
                        artist = null,
                        duration = 0L,
                        size = size,
                        dateAdded = date,
                        width = width,
                        height = height,
                        mimeType = mime
                    )
                )
            }
        }

        _images.value = list

        // Group into albums
        val albumsMap = list.groupBy { it.album }
        _imageAlbums.value = albumsMap.map { (name, items) ->
            AlbumItem(
                name = name,
                thumbnailUri = items.firstOrNull()?.uri,
                itemCount = items.size,
                items = items
            )
        }.sortedByDescending { it.itemCount }
    }

    private fun loadVideos() {
        val list = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.MIME_TYPE
        )

        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )

        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val widthCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val path = c.getString(pathCol)
                if (path == null || !File(path).exists()) continue

                val name = c.getString(nameCol) ?: File(path).name
                val album = c.getString(albumCol) ?: "Videos"
                val size = c.getLong(sizeCol)
                val date = c.getLong(dateCol) * 1000L
                val width = c.getInt(widthCol)
                val height = c.getInt(heightCol)
                val duration = c.getLong(durCol)
                val mime = c.getString(mimeCol) ?: "video/mp4"
                val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())

                list.add(
                    MediaItem(
                        id = id,
                        uri = uri,
                        path = path,
                        displayName = name,
                        album = album,
                        artist = null,
                        duration = duration,
                        size = size,
                        dateAdded = date,
                        width = width,
                        height = height,
                        mimeType = mime
                    )
                )
            }
        }

        _videos.value = list

        val albumsMap = list.groupBy { it.album }
        _videoAlbums.value = albumsMap.map { (name, items) ->
            AlbumItem(
                name = name,
                thumbnailUri = items.firstOrNull()?.uri,
                itemCount = items.size,
                items = items
            )
        }.sortedByDescending { it.itemCount }
    }

    private fun loadAudios() {
        val list = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media._ID, // We use it twice or just get details
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE
        )

        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )

        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val path = c.getString(pathCol)
                if (path == null || !File(path).exists()) continue

                val name = c.getString(nameCol) ?: File(path).name
                val album = c.getString(albumCol) ?: "Music"
                val artist = c.getString(artistCol) ?: "Unknown Artist"
                val size = c.getLong(sizeCol)
                val date = c.getLong(dateCol) * 1000L
                val duration = c.getLong(durCol)
                val mime = c.getString(mimeCol) ?: "audio/mpeg"
                val uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())

                list.add(
                    MediaItem(
                        id = id,
                        uri = uri,
                        path = path,
                        displayName = name,
                        album = album,
                        artist = artist,
                        duration = duration,
                        size = size,
                        dateAdded = date,
                        width = 0,
                        height = 0,
                        mimeType = mime
                    )
                )
            }
        }

        _audioTracks.value = list
    }

    // Playlist Operations
    private fun loadPlaylists() {
        val serialized = sharedPrefs.getString("playlists", "[]") ?: "[]"
        try {
            val jsonArr = JSONArray(serialized)
            val list = mutableListOf<Playlist>()
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val pathsArr = obj.getJSONArray("trackPaths")
                val paths = mutableListOf<String>()
                for (j in 0 until pathsArr.length()) {
                    paths.add(pathsArr.getString(j))
                }
                list.add(Playlist(id, name, paths))
            }
            _playlists.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun savePlaylists(list: List<Playlist>) {
        try {
            val jsonArr = JSONArray()
            for (p in list) {
                val obj = JSONObject()
                obj.put("id", p.id)
                obj.put("name", p.name)
                val pathsArr = JSONArray()
                p.trackPaths.forEach { pathsArr.put(it) }
                obj.put("trackPaths", pathsArr)
                jsonArr.put(obj)
            }
            sharedPrefs.edit().putString("playlists", jsonArr.toString()).apply()
            _playlists.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        val current = _playlists.value.toMutableList()
        val id = System.currentTimeMillis().toString()
        current.add(Playlist(id, name, emptyList()))
        savePlaylists(current)
        viewModelScope.launch { _toastMessage.emit("Playlist '$name' created successfully!") }
    }

    fun deletePlaylist(playlistId: String) {
        val current = _playlists.value.toMutableList()
        current.removeAll { it.id == playlistId }
        savePlaylists(current)
        viewModelScope.launch { _toastMessage.emit("Playlist removed") }
    }

    fun addTrackToPlaylist(playlistId: String, trackPath: String) {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val p = current[index]
            if (p.trackPaths.contains(trackPath)) {
                viewModelScope.launch { _toastMessage.emit("Track already exists in playlist") }
                return
            }
            val updatedPaths = p.trackPaths.toMutableList().apply { add(trackPath) }
            current[index] = p.copy(trackPaths = updatedPaths)
            savePlaylists(current)
            viewModelScope.launch { _toastMessage.emit("Added to playlist") }
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackPath: String) {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val p = current[index]
            val updatedPaths = p.trackPaths.toMutableList().apply { remove(trackPath) }
            current[index] = p.copy(trackPaths = updatedPaths)
            savePlaylists(current)
            viewModelScope.launch { _toastMessage.emit("Removed from playlist") }
        }
    }

    // Media Metadata Viewer Extraction
    fun extractRichMetadata(item: MediaItem): Map<String, String> {
        val map = mutableMapOf<String, String>()
        map["File Name"] = item.displayName
        map["File Path"] = item.path
        map["File Size"] = formatSize(item.size)
        map["Mime Type"] = item.mimeType

        val file = File(item.path)
        if (file.exists()) {
            map["Last Modified"] = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(java.util.Date(file.lastModified()))
        }

        // Handle Image EXIF
        if (item.mimeType.contains("image", ignoreCase = true)) {
            try {
                val exif = ExifInterface(item.path)
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)
                val make = exif.getAttribute(ExifInterface.TAG_MAKE)
                val date = exif.getAttribute(ExifInterface.TAG_DATETIME)
                val exposure = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                val fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
                val iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                val latLong = FloatArray(2)
                val hasGps = exif.getLatLong(latLong)

                if (!model.isNullOrEmpty()) map["Camera Model"] = model
                if (!make.isNullOrEmpty()) map["Camera Maker"] = make
                if (!date.isNullOrEmpty()) map["Date Token"] = date
                if (!exposure.isNullOrEmpty()) map["Exposure Time"] = "$exposure sec"
                if (!fNumber.isNullOrEmpty()) map["F-Stop"] = "f/$fNumber"
                if (!iso.isNullOrEmpty()) map["ISO Value"] = iso
                if (hasGps) map["GPS Location"] = "${latLong[0]}, ${latLong[1]}"
                if (item.width > 0 && item.height > 0) {
                    map["Resolution"] = "${item.width} x ${item.height}"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Handle Video or Audio retriever specifications
        if (item.mimeType.contains("video", ignoreCase = true) || item.mimeType.contains("audio", ignoreCase = true)) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(item.path)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)

                if (duration != null) {
                    val ms = duration.toLong()
                    map["Duration"] = formatDuration(ms)
                }
                if (!artist.isNullOrEmpty()) map["Artist / Creator"] = artist
                if (!album.isNullOrEmpty()) map["Album Group"] = album
                if (!genre.isNullOrEmpty()) map["Genre Category"] = genre
                if (!bitrate.isNullOrEmpty()) {
                    val kbps = bitrate.toInt() / 1000
                    map["Bitrate Speed"] = "$kbps kbps"
                }

                if (item.mimeType.contains("video", ignoreCase = true)) {
                    val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    if (w != null && h != null) {
                        map["Video Frame Size"] = "$w x $h"
                    }
                    if (rotation != null) {
                        map["Orientation Angle"] = "$rotation°"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return map
    }

    fun formatSize(sizeBytes: Long): String {
        if (sizeBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.2f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun formatDuration(durationMs: Long): String {
        val totalSecs = durationMs / 1000
        val hours = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }
}

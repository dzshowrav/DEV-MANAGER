package com.example.devmanager

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import java.io.File

class AudioAlbumArtFetcher(
    private val file: File,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val art = retriever.embeddedPicture
            if (art != null) {
                val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                if (bitmap != null) {
                    val drawable = BitmapDrawable(options.context.resources, bitmap)
                    return DrawableResult(
                        drawable = drawable,
                        isSampled = false,
                        dataSource = DataSource.DISK
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {}
        }
        return null
    }

    class Factory : Fetcher.Factory<Any> {
        override fun create(data: Any, options: Options, imageLoader: ImageLoader): Fetcher? {
            val file = when (data) {
                is File -> data
                is String -> File(data)
                is android.net.Uri -> if (data.scheme == "file") File(data.path!!) else null
                else -> null
            }
            if (file != null && !file.isDirectory && (file.extension.lowercase() in listOf("mp3", "flac", "wav", "ogg", "m4a", "aac"))) {
                return AudioAlbumArtFetcher(file, options)
            }
            return null
        }
    }
}

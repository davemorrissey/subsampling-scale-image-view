package com.davemorrissey.labs.subscaleview.decoder

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.TextUtils
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import java.io.InputStream

/**
 * Default implementation of [com.davemorrissey.labs.subscaleview.decoder.ImageDecoder]
 * using Android's [android.graphics.BitmapFactory], based on the Skia library. This
 * works well in most circumstances and has reasonable performance, however it has some problems
 * with grayscale, indexed and CMYK images.
 */
class SkiaImageDecoder(bitmapConfig: Bitmap.Config?) : ImageDecoder {
    private val FILE_PREFIX = "file://"
    private val ASSET_PREFIX = "$FILE_PREFIX/android_asset/"
    private val RESOURCE_PREFIX = ContentResolver.SCHEME_ANDROID_RESOURCE + "://"

    private val bitmapConfig: Bitmap.Config

    init {
        val globalBitmapConfig = SubsamplingScaleImageView.preferredBitmapConfig
        when {
            bitmapConfig != null -> this.bitmapConfig = bitmapConfig
            globalBitmapConfig != null -> this.bitmapConfig = globalBitmapConfig
            else -> this.bitmapConfig = Bitmap.Config.RGB_565
        }
    }

    @Throws(Exception::class)
    override fun decode(context: Context, uri: Uri): Bitmap {
        val uriString = uri.toString()
        val options = BitmapFactory.Options()
        val bitmap: Bitmap?
        options.inPreferredConfig = bitmapConfig
        when {
            uriString.startsWith(RESOURCE_PREFIX) -> {
                val packageName = uri.authority
                val res = if (context.packageName == packageName) {
                    context.resources
                } else {
                    val pm = context.packageManager
                    pm.getResourcesForApplication(packageName)
                }

                var id = 0
                val segments = uri.pathSegments
                val size = segments.size
                if (size == 2 && segments[0] == "drawable") {
                    val resName = segments[1]
                    id = res.getIdentifier(resName, "drawable", packageName)
                } else if (size == 1 && TextUtils.isDigitsOnly(segments[0])) {
                    try {
                        id = Integer.parseInt(segments[0])
                    } catch (ignored: NumberFormatException) {
                    }
                }

                bitmap = BitmapFactory.decodeResource(context.resources, id, options)
            }
            uriString.startsWith(ASSET_PREFIX) -> {
                val assetName = uriString.substring(ASSET_PREFIX.length)
                bitmap = BitmapFactory.decodeStream(context.assets.open(assetName), null, options)
            }

            uriString.startsWith(FILE_PREFIX) -> bitmap = BitmapFactory.decodeFile(uriString.substring(FILE_PREFIX.length), options)
            else -> {
                var inputStream: InputStream? = null
                try {
                    val contentResolver = context.contentResolver
                    inputStream = contentResolver.openInputStream(uri)
                    bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                } finally {
                    try {
                        inputStream?.close()
                    } catch (e: Exception) {
                    }
                }
            }
        }

        if (bitmap == null) {
            throw RuntimeException("Skia image region decoder returned null bitmap - image format may not be supported")
        }
        return bitmap
    }
}

package com.davemorrissey.labs.subscaleview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.Keep
import java.io.InputStream

class SkiaImageDecoder(bitmapConfig: Bitmap.Config?) : ImageDecoder {
    private val FILE_PREFIX = "file://"
    private val ASSET_PREFIX = "$FILE_PREFIX/android_asset/"

    private val bitmapConfig: Bitmap.Config

    @Keep
    constructor() : this(null)

    init {
        val globalBitmapConfig = SubsamplingScaleImageView.preferredBitmapConfig
        when {
            bitmapConfig != null -> this.bitmapConfig = bitmapConfig
            globalBitmapConfig != null -> this.bitmapConfig = globalBitmapConfig
            else -> this.bitmapConfig = Bitmap.Config.RGB_565
        }
    }

    override fun decode(context: Context, uri: Uri): Bitmap {
        val uriString = uri.toString()
        val options = BitmapFactory.Options()
        val bitmap: Bitmap?
        options.inPreferredConfig = bitmapConfig
        when {
            uriString.startsWith(ASSET_PREFIX) -> {
                val assetName = uriString.substring(ASSET_PREFIX.length)
                bitmap = BitmapFactory.decodeStream(context.assets.open(assetName), null, options)
            }
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
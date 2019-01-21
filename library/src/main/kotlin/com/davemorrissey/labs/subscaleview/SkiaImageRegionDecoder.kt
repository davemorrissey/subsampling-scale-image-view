package com.davemorrissey.labs.subscaleview

import android.content.Context
import android.content.res.AssetManager
import android.graphics.*
import android.net.Uri
import androidx.annotation.Keep
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.Companion.ASSET_PREFIX
import java.io.InputStream
import java.util.concurrent.locks.ReentrantReadWriteLock

class SkiaImageRegionDecoder(bitmapConfig: Bitmap.Config?) : ImageRegionDecoder {
    private val bitmapConfig: Bitmap.Config

    private var decoder: BitmapRegionDecoder? = null
    private val decoderLock = ReentrantReadWriteLock(true)

    @Synchronized
    override fun isReady() = decoder?.isRecycled == false

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

    override fun init(context: Context, uri: Uri): Point {
        val uriString = uri.toString()
        when {
            uriString.startsWith(ASSET_PREFIX) -> {
                val assetName = uriString.substring(ASSET_PREFIX.length)
                decoder = BitmapRegionDecoder.newInstance(context.assets.open(assetName, AssetManager.ACCESS_RANDOM), false)
            }
            else -> {
                var inputStream: InputStream? = null
                try {
                    val contentResolver = context.contentResolver
                    inputStream = contentResolver.openInputStream(uri)
                    decoder = BitmapRegionDecoder.newInstance(inputStream, false)
                } finally {
                    try {
                        inputStream?.close()
                    } catch (e: Exception) {
                    }
                }
            }
        }
        return Point(decoder!!.width, decoder!!.height)
    }

    override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
        decoderLock.readLock().lock()
        try {
            if (decoder?.isRecycled == false) {
                val options = BitmapFactory.Options()
                options.inSampleSize = sampleSize
                options.inPreferredConfig = bitmapConfig
                return decoder!!.decodeRegion(sRect, options)
                        ?: throw RuntimeException("Skia image decoder returned null bitmap - image format may not be supported")
            } else {
                throw IllegalStateException("Cannot decode region after decoder has been recycled")
            }
        } finally {
            decoderLock.readLock().unlock()
        }
    }

    @Synchronized
    override fun recycle() {
        decoderLock.writeLock().lock()
        try {
            decoder?.recycle()
            decoder = null
        } finally {
            decoderLock.writeLock().unlock()
        }
    }
}

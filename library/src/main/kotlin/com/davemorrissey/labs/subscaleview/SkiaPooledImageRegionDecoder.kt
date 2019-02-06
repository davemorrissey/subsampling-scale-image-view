package com.davemorrissey.labs.subscaleview

import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.res.AssetManager
import android.graphics.*
import android.net.Uri
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.Companion.ASSET_PREFIX
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.Companion.FILE_SCHEME
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock

class SkiaPooledImageRegionDecoder : ImageRegionDecoder {
    private var decoderPool: DecoderPool? = DecoderPool()
    private val decoderLock = ReentrantReadWriteLock(true)

    private var context: Context? = null
    private var uri: Uri? = null

    private var fileLength = Long.MAX_VALUE
    private val imageDimensions = Point(0, 0)
    private val lazyInited = AtomicBoolean(false)

    @Synchronized
    override fun isReady() = decoderPool?.getIsEmpty() == false

    private fun getNumberOfCores() = Runtime.getRuntime().availableProcessors()

    private fun getIsLowMemory(): Boolean {
        val activityManager = context!!.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.lowMemory
    }

    override fun init(context: Context, uri: Uri): Point {
        this.context = context
        this.uri = uri
        initialiseDecoder()
        return imageDimensions
    }

    private fun lazyInit() {
        if (lazyInited.compareAndSet(false, true) && fileLength < Long.MAX_VALUE) {
            Thread {
                while (decoderPool != null && allowAdditionalDecoder(decoderPool!!.size(), fileLength)) {
                    try {
                        if (decoderPool != null) {
                            initialiseDecoder()
                        }
                    } catch (e: Exception) {
                    }
                }
            }.start()
        }
    }

    private fun initialiseDecoder() {
        val uriString = uri!!.toString()
        val decoder: BitmapRegionDecoder
        var fileLength = Long.MAX_VALUE
        when {
            uriString.startsWith(ASSET_PREFIX) -> {
                val assetName = uriString.substring(ASSET_PREFIX.length)
                try {
                    val descriptor = context!!.assets.openFd(assetName)
                    fileLength = descriptor.length
                } catch (e: Exception) {
                }

                decoder = BitmapRegionDecoder.newInstance(context!!.assets.open(assetName, AssetManager.ACCESS_RANDOM), false)
            }
            uriString.startsWith(FILE_SCHEME) -> {
                decoder = BitmapRegionDecoder.newInstance(uriString.substring(FILE_SCHEME.length), false)
                try {
                    val file = File(uriString)
                    if (file.exists()) {
                        fileLength = file.length()
                    }
                } catch (e: Exception) {
                }
            }
            else -> {
                var inputStream: InputStream? = null
                try {
                    val contentResolver = context!!.contentResolver
                    inputStream = contentResolver.openInputStream(uri!!)
                    decoder = BitmapRegionDecoder.newInstance(inputStream, false)
                    try {
                        val descriptor = contentResolver.openAssetFileDescriptor(uri!!, "r")
                        if (descriptor != null) {
                            fileLength = descriptor.length
                        }
                    } catch (e: Exception) {
                    }
                } finally {
                    try {
                        inputStream?.close()
                    } catch (e: Exception) {
                    }
                }
            }
        }

        this.fileLength = fileLength
        imageDimensions.set(decoder.width, decoder.height)
        decoderLock.writeLock().lock()
        try {
            decoderPool?.add(decoder)
        } finally {
            decoderLock.writeLock().unlock()
        }
    }

    override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
        if (sRect.width() < imageDimensions.x || sRect.height() < imageDimensions.y) {
            lazyInit()
        }

        decoderLock.readLock().lock()
        try {
            if (decoderPool != null) {
                val decoder = decoderPool!!.acquire()
                try {
                    if (decoder?.isRecycled == false) {
                        val options = BitmapFactory.Options()
                        options.inSampleSize = sampleSize
                        options.inPreferredConfig = Bitmap.Config.RGB_565
                        return decoder.decodeRegion(sRect, options)
                                ?: throw RuntimeException("Skia image decoder returned null bitmap - image format may not be supported")
                    }
                } finally {
                    if (decoder != null) {
                        decoderPool!!.release(decoder)
                    }
                }
            }
            throw IllegalStateException("Cannot decode region after decoder has been recycled")
        } finally {
            decoderLock.readLock().unlock()
        }
    }

    @Synchronized
    override fun recycle() {
        decoderLock.writeLock().lock()
        try {
            decoderPool?.recycle()
            decoderPool = null
            context = null
            uri = null
        } finally {
            decoderLock.writeLock().unlock()
        }
    }

    private fun allowAdditionalDecoder(numberOfDecoders: Int, fileLength: Long): Boolean {
        return when {
            numberOfDecoders >= 4 -> false
            numberOfDecoders * fileLength > 20 * 1024 * 1024 -> false
            numberOfDecoders >= getNumberOfCores() -> false
            getIsLowMemory() -> false
            else -> true
        }
    }

    class DecoderPool {
        private val available = Semaphore(0, true)
        private val decoders = ConcurrentHashMap<BitmapRegionDecoder, Boolean>()

        @Synchronized
        fun getIsEmpty() = decoders.isEmpty()

        private val nextAvailable: BitmapRegionDecoder?
            @Synchronized get() {
                for (entry in decoders.entries) {
                    if (!entry.value) {
                        entry.setValue(true)
                        return entry.key
                    }
                }
                return null
            }

        @Synchronized
        fun size() = decoders.size

        fun acquire(): BitmapRegionDecoder? {
            available.acquireUninterruptibly()
            return nextAvailable
        }

        fun release(decoder: BitmapRegionDecoder) {
            if (markAsUnused(decoder)) {
                available.release()
            }
        }

        @Synchronized
        fun add(decoder: BitmapRegionDecoder) {
            decoders[decoder] = false
            available.release()
        }

        @Synchronized
        fun recycle() {
            while (!decoders.isEmpty()) {
                val decoder = acquire()
                decoder!!.recycle()
                decoders.remove(decoder)
            }
        }

        @Synchronized
        fun markAsUnused(decoder: BitmapRegionDecoder): Boolean {
            for (entry in decoders.entries) {
                if (decoder == entry.key) {
                    return if (entry.value) {
                        entry.setValue(false)
                        true
                    } else {
                        false
                    }
                }
            }
            return false
        }
    }
}

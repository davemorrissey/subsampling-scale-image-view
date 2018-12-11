package com.davemorrissey.labs.subscaleview.decoder

import android.app.ActivityManager
import android.content.ContentResolver
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.res.AssetManager
import android.graphics.*
import android.net.Uri
import androidx.annotation.Keep
import android.text.TextUtils
import android.util.Log
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 *
 *
 * An implementation of [ImageRegionDecoder] using a pool of [BitmapRegionDecoder]s,
 * to provide true parallel loading of tiles. This is only effective if parallel loading has been
 * enabled in the view by calling [com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.setExecutor]
 * with a multi-threaded [Executor] instance.
 *
 *
 * One decoder is initialised when the class is initialised. This is enough to decode base layer tiles.
 * Additional decoders are initialised when a subregion of the image is first requested, which indicates
 * interaction with the view. Creation of additional encoders stops when [.allowAdditionalDecoder]
 * returns false. The default implementation takes into account the file size, number of CPU cores,
 * low memory status and a hard limit of 4. Extend this class to customise this.
 *
 *
 * **WARNING:** This class is highly experimental and not proven to be stable on a wide range of
 * devices. You are advised to test it thoroughly on all available devices, and code your app to use
 * [SkiaImageRegionDecoder] on old or low powered devices you could not test.
 *
 */
class SkiaPooledImageRegionDecoder(bitmapConfig: Bitmap.Config?) : ImageRegionDecoder {

    companion object {
        private val TAG = SkiaPooledImageRegionDecoder::class.java.simpleName
        private val FILE_PREFIX = "file://"
        private val ASSET_PREFIX = "$FILE_PREFIX/android_asset/"
        private val RESOURCE_PREFIX = "${ContentResolver.SCHEME_ANDROID_RESOURCE}://"

        private var debug = false

        fun setDebug(debug: Boolean) {
            SkiaPooledImageRegionDecoder.debug = debug
        }
    }

    private var decoderPool: DecoderPool? = DecoderPool()
    private val decoderLock = ReentrantReadWriteLock(true)

    private val bitmapConfig: Bitmap.Config

    private var context: Context? = null
    private var uri: Uri? = null

    private var fileLength = java.lang.Long.MAX_VALUE
    private val imageDimensions = Point(0, 0)
    private val lazyInited = AtomicBoolean(false)

    /**
     * Holding a read lock to avoid returning true while the pool is being recycled, this returns
     * true if the pool has at least one decoder available.
     */
    override val isReady: Boolean
        @Synchronized get() = decoderPool != null && !decoderPool!!.getIsEmpty()

    private fun getNumberOfCores() = Runtime.getRuntime().availableProcessors()

    private fun getIsLowMemory(): Boolean {
        val activityManager = context!!.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.lowMemory
    }

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

    /**
     * Initialises the decoder pool. This method creates one decoder on the current thread and uses
     * it to decode the bounds, then spawns an independent thread to populate the pool with an
     * additional three decoders. The thread will abort if [.recycle] is called.
     */
    @Throws(Exception::class)
    override fun init(context: Context, uri: Uri): Point {
        this.context = context
        this.uri = uri
        initialiseDecoder()
        return imageDimensions
    }

    /**
     * Initialises extra decoders for as long as [.allowAdditionalDecoder] returns
     * true and the pool has not been recycled.
     */
    private fun lazyInit() {
        if (lazyInited.compareAndSet(false, true) && fileLength < java.lang.Long.MAX_VALUE) {
            debug("Starting lazy init of additional decoders")
            val thread = object : Thread() {
                override fun run() {
                    while (decoderPool != null && allowAdditionalDecoder(decoderPool!!.size(), fileLength)) {
                        // New decoders can be created while reading tiles but this read lock prevents
                        // them being initialised while the pool is being recycled.
                        try {
                            if (decoderPool != null) {
                                val start = System.currentTimeMillis()
                                debug("Starting decoder")
                                initialiseDecoder()
                                val end = System.currentTimeMillis()
                                debug("Started decoder, took ${end - start} ms")
                            }
                        } catch (e: Exception) {
                            // A decoder has already been successfully created so we can ignore this
                            debug("Failed to start decoder: ${e.message}")
                        }

                    }
                }
            }
            thread.start()
        }
    }

    /**
     * Initialises a new [BitmapRegionDecoder] and adds it to the pool, unless the pool has
     * been recycled while it was created.
     */
    @Throws(Exception::class)
    private fun initialiseDecoder() {
        val uriString = uri!!.toString()
        val decoder: BitmapRegionDecoder
        var fileLength = java.lang.Long.MAX_VALUE
        when {
            uriString.startsWith(RESOURCE_PREFIX) -> {
                val packageName = uri!!.authority
                val res = if (context!!.packageName == packageName) {
                    context!!.resources
                } else {
                    val pm = context!!.packageManager
                    pm.getResourcesForApplication(packageName)
                }

                var id = 0
                val segments = uri!!.pathSegments
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

                try {
                    val descriptor = context!!.resources.openRawResourceFd(id)
                    fileLength = descriptor.length
                } catch (e: Exception) {
                }

                decoder = BitmapRegionDecoder.newInstance(context!!.resources.openRawResource(id), false)
            }
            uriString.startsWith(ASSET_PREFIX) -> {
                val assetName = uriString.substring(ASSET_PREFIX.length)
                try {
                    val descriptor = context!!.assets.openFd(assetName)
                    fileLength = descriptor.length
                } catch (e: Exception) {
                }

                decoder = BitmapRegionDecoder.newInstance(context!!.assets.open(assetName, AssetManager.ACCESS_RANDOM), false)
            }
            uriString.startsWith(FILE_PREFIX) -> {
                decoder = BitmapRegionDecoder.newInstance(uriString.substring(FILE_PREFIX.length), false)
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

    /**
     * Acquire a read lock to prevent decoding overlapping with recycling, then check the pool still
     * exists and acquire a decoder to load the requested region. There is no check whether the pool
     * currently has decoders, because it's guaranteed to have one decoder after [.init]
     * is called and be null once [.recycle] is called. In practice the view can't call this
     * method until after [.init], so there will be no blocking on an empty pool.
     */
    override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
        debug("Decode region $sRect on thread ${Thread.currentThread().name}")
        if (sRect.width() < imageDimensions.x || sRect.height() < imageDimensions.y) {
            lazyInit()
        }
        decoderLock.readLock().lock()
        try {
            if (decoderPool != null) {
                val decoder = decoderPool!!.acquire()
                try {
                    // Decoder can't be null or recycled in practice
                    if (decoder != null && !decoder.isRecycled) {
                        val options = BitmapFactory.Options()
                        options.inSampleSize = sampleSize
                        options.inPreferredConfig = bitmapConfig
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

    /**
     * Wait until all read locks held by [.decodeRegion] are released, then recycle
     * and destroy the pool. Elsewhere, when a read lock is acquired, we must check the pool is not null.
     */
    @Synchronized
    override fun recycle() {
        decoderLock.writeLock().lock()
        try {
            if (decoderPool != null) {
                decoderPool!!.recycle()
                decoderPool = null
                context = null
                uri = null
            }
        } finally {
            decoderLock.writeLock().unlock()
        }
    }

    /**
     * Called before creating a new decoder. Based on number of CPU cores, available memory, and the
     * size of the image file, determines whether another decoder can be created. Subclasses can
     * override and customise this.
     *
     * @param numberOfDecoders the number of decoders that have been created so far
     * @param fileLength       the size of the image file in bytes. Creating another decoder will use approximately this much native memory.
     * @return true if another decoder can be created.
     */
    private fun allowAdditionalDecoder(numberOfDecoders: Int, fileLength: Long): Boolean {
        return when {
            numberOfDecoders >= 4 -> {
                debug("No additional decoders allowed, reached hard limit (4)")
                false
            }
            numberOfDecoders * fileLength > 20 * 1024 * 1024 -> {
                debug("No additional encoders allowed, reached hard memory limit (20Mb)")
                false
            }
            numberOfDecoders >= getNumberOfCores() -> {
                debug("No additional encoders allowed, limited by CPU cores (${getNumberOfCores()})")
                false
            }
            getIsLowMemory() -> {
                debug("No additional encoders allowed, memory is low")
                false
            }
            else -> {
                debug("Additional decoder allowed, current count is $numberOfDecoders, estimated native memory ${fileLength * numberOfDecoders / (1024 * 1024)} Mb")
                true
            }
        }
    }

    /**
     * A simple pool of [BitmapRegionDecoder] instances, all loading from the same source.
     */
    class DecoderPool {
        private val available = Semaphore(0, true)
        private val decoders = ConcurrentHashMap<BitmapRegionDecoder, Boolean>()

        /**
         * Returns false if there is at least one decoder in the pool.
         */
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

        /**
         * Acquire a decoder. Blocks until one is available.
         */
        fun acquire(): BitmapRegionDecoder? {
            available.acquireUninterruptibly()
            return nextAvailable
        }

        /**
         * Release a decoder back to the pool.
         */
        fun release(decoder: BitmapRegionDecoder) {
            if (markAsUnused(decoder)) {
                available.release()
            }
        }

        /**
         * Adds a newly created decoder to the pool, releasing an additional permit.
         */
        @Synchronized
        fun add(decoder: BitmapRegionDecoder) {
            decoders[decoder] = false
            available.release()
        }

        /**
         * While there are decoders in the map, wait until each is available before acquiring,
         * recycling and removing it. After this is called, any call to [.acquire] will
         * block forever, so this call should happen within a write lock, and all calls to
         * [.acquire] should be made within a read lock so they cannot end up blocking on
         * the semaphore when it has no permits.
         */
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

    private fun debug(message: String) {
        if (debug) {
            Log.d(TAG, message)
        }
    }
}

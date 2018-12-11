package com.davemorrissey.labs.subscaleview.decoder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri

/**
 * Interface for image decoding classes, allowing the default [android.graphics.BitmapRegionDecoder]
 * based on the Skia library to be replaced with a custom class.
 */
interface ImageRegionDecoder {

    /**
     * Status check. Should return false before initialisation and after recycle.
     * @return true if the decoder is ready to be used.
     */
    fun isReady(): Boolean

    /**
     * Initialise the decoder. When possible, perform initial setup work once in this method. The
     * dimensions of the image must be returned. The URI can be in one of the following formats:
     * <br></br>
     * File: `file:///scard/picture.jpg`
     * <br></br>
     * Asset: `file:///android_asset/picture.png`
     * <br></br>
     * Resource: `android.resource://com.example.app/drawable/picture`
     * @param context Application context. A reference may be held, but must be cleared on recycle.
     * @param uri URI of the image.
     * @return Dimensions of the image.
     * @throws Exception if initialisation fails.
     */
    @Throws(Exception::class)
    fun init(context: Context, uri: Uri): Point

    /**
     *
     *
     * Decode a region of the image with the given sample size. This method is called off the UI
     * thread so it can safely load the image on the current thread. It is called from
     * [android.os.AsyncTask]s running in an executor that may have multiple threads, so
     * implementations must be thread safe. Adding `synchronized` to the method signature
     * is the simplest way to achieve this, but bear in mind the [.recycle] method can be
     * called concurrently.
     *
     *
     * See [SkiaImageRegionDecoder] and [SkiaPooledImageRegionDecoder] for examples of
     * internal locking and synchronization.
     *
     * @param sRect Source image rectangle to decode.
     * @param sampleSize Sample size.
     * @return The decoded region. It is safe to return null if decoding fails.
     */
    fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap

    /**
     * This method will be called when the decoder is no longer required. It should clean up any resources still in use.
     */
    fun recycle()
}

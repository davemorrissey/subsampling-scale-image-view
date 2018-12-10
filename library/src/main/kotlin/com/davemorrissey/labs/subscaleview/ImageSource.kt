package com.davemorrissey.labs.subscaleview

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri

import java.io.File
import java.io.UnsupportedEncodingException
import java.net.URLDecoder

/**
 * Helper class used to set the source and additional attributes from a variety of sources. Supports
 * use of a bitmap, asset, resource, external file or any other URI.
 *
 * When you are using a preview image, you must set the dimensions of the full size image on the
 * ImageSource object for the full size image using the [.dimensions] method.
 */
class ImageSource {
    companion object {
        val FILE_SCHEME = "file:///"
        val ASSET_SCHEME = "file:///android_asset/"

        /**
         * Create an instance from a resource. The correct resource for the device screen resolution will be used.
         * @param resId resource ID.
         * @return an [ImageSource] instance.
         */
        fun resource(resId: Int) = ImageSource(resId)

        /**
         * Create an instance from an asset name.
         * @param assetName asset name.
         * @return an [ImageSource] instance.
         */
        fun asset(assetName: String) = uri(ASSET_SCHEME + assetName)

        /**
         * Create an instance from a URI. If the URI does not start with a scheme, it's assumed to be the URI
         * of a file.
         * @param uri image URI.
         * @return an [ImageSource] instance.
         */
        fun uri(uri: String): ImageSource {
            var newUri = uri

            if (!newUri.contains("://")) {
                if (newUri.startsWith("/")) {
                    newUri = uri.substring(1)
                }
                newUri = FILE_SCHEME + newUri
            }
            return ImageSource(Uri.parse(newUri))
        }

        /**
         * Create an instance from a URI.
         * @param uri image URI.
         * @return an [ImageSource] instance.
         */
        fun uri(uri: Uri) = ImageSource(uri)

        /**
         * Provide a loaded bitmap for display.
         * @param bitmap bitmap to be displayed.
         * @return an [ImageSource] instance.
         */
        fun bitmap(bitmap: Bitmap) = ImageSource(bitmap, false)

        /**
         * Provide a loaded and cached bitmap for display. This bitmap will not be recycled when it is no
         * longer needed. Use this method if you loaded the bitmap with an image loader such as Picasso
         * or Volley.
         * @param bitmap bitmap to be displayed.
         * @return an [ImageSource] instance.
         */
        fun cachedBitmap(bitmap: Bitmap) = ImageSource(bitmap, true)
    }

    val uri: Uri?
    val bitmap: Bitmap?
    val resource: Int?
    var tile = false
    var sWidth = 0
    var sHeight = 0
    var isCached = false
    var sRegion: Rect? = null

    private constructor(bitmap: Bitmap, cached: Boolean) {
        this.bitmap = bitmap
        uri = null
        resource = null
        tile = false
        sWidth = bitmap.width
        sHeight = bitmap.height
        isCached = cached
    }

    private constructor(uri: Uri) {
        var newUri = uri
        // #114 If file doesn't exist, attempt to url decode the URI and try again
        val uriString = uri.toString()
        if (uriString.startsWith(FILE_SCHEME)) {
            val uriFile = File(uriString.substring(FILE_SCHEME.length - 1))
            if (!uriFile.exists()) {
                try {
                    newUri = Uri.parse(URLDecoder.decode(uriString, "UTF-8"))
                } catch (e: UnsupportedEncodingException) {
                    // Fallback to encoded URI. This exception is not expected.
                }

            }
        }
        this.uri = newUri
        bitmap = null
        resource = null
        tile = true
    }

    private constructor(resource: Int) {
        this.resource = resource
        bitmap = null
        uri = null
        tile = true
    }

    /**
     * Enable tiling of the image. This does not apply to preview images which are always loaded as a single bitmap.,
     * and tiling cannot be disabled when displaying a region of the source image.
     * @return this instance for chaining.
     */
    fun tilingEnabled() = tiling(true)

    /**
     * Disable tiling of the image. This does not apply to preview images which are always loaded as a single bitmap,
     * and tiling cannot be disabled when displaying a region of the source image.
     * @return this instance for chaining.
     */
    fun tilingDisabled() = tiling(false)

    /**
     * Enable or disable tiling of the image. This does not apply to preview images which are always loaded as a single bitmap,
     * and tiling cannot be disabled when displaying a region of the source image.
     * @param tile whether tiling should be enabled.
     * @return this instance for chaining.
     */
    fun tiling(tile: Boolean): ImageSource {
        this.tile = tile
        return this
    }

    /**
     * Use a region of the source image. Region must be set independently for the full size image and the preview if
     * you are using one.
     * @param sRegion the region of the source image to be displayed.
     * @return this instance for chaining.
     */
    fun region(sRegion: Rect): ImageSource {
        this.sRegion = sRegion
        setInvariants()
        return this
    }

    /**
     * Declare the dimensions of the image. This is only required for a full size image, when you are specifying a URI
     * and also a preview image. When displaying a bitmap object, or not using a preview, you do not need to declare
     * the image dimensions. Note if the declared dimensions are found to be incorrect, the view will reset.
     * @param sWidth width of the source image.
     * @param sHeight height of the source image.
     * @return this instance for chaining.
     */
    fun dimensions(sWidth: Int, sHeight: Int): ImageSource {
        if (bitmap == null) {
            this.sWidth = sWidth
            this.sHeight = sHeight
        }
        setInvariants()
        return this
    }

    private fun setInvariants() {
        if (sRegion != null) {
            tile = true
            sWidth = sRegion!!.width()
            sHeight = sRegion!!.height()
        }
    }
}

package com.davemorrissey.labs.subscaleview

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri

import java.io.File
import java.io.UnsupportedEncodingException
import java.net.URLDecoder

class ImageSource private constructor(uri: Uri) {
    companion object {
        const val FILE_SCHEME = "file:///"
        const val ASSET_SCHEME = "file:///android_asset/"

        fun asset(assetName: String) = uri(ASSET_SCHEME + assetName)

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
    }

    val uri: Uri?
    val bitmap: Bitmap?
    val resource: Int?
    var tile = false
    var sWidth = 0
    var sHeight = 0
    var isCached = false
    var sRegion: Rect? = null

    init {
        var newUri = uri
        val uriString = uri.toString()
        if (uriString.startsWith(FILE_SCHEME)) {
            val uriFile = File(uriString.substring(FILE_SCHEME.length - 1))
            if (!uriFile.exists()) {
                try {
                    newUri = Uri.parse(URLDecoder.decode(uriString, "UTF-8"))
                } catch (e: UnsupportedEncodingException) {
                }
            }
        }
        this.uri = newUri
        bitmap = null
        resource = null
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

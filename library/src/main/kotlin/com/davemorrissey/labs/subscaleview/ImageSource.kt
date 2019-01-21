package com.davemorrissey.labs.subscaleview

import android.graphics.Bitmap
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
    }
}

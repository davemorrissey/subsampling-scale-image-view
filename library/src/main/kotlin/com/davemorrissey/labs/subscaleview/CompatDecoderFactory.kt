package com.davemorrissey.labs.subscaleview

import android.graphics.Bitmap

class CompatDecoderFactory<T> constructor(private val clazz: Class<out T>, private val bitmapConfig: Bitmap.Config? = null) : DecoderFactory<T> {
    constructor(clazz: Class<out T>) : this(clazz, null)

    override fun make(): T {
        return if (bitmapConfig == null) {
            clazz.newInstance()
        } else {
            clazz.getConstructor(Bitmap.Config::class.java).newInstance(bitmapConfig)
        }
    }
}

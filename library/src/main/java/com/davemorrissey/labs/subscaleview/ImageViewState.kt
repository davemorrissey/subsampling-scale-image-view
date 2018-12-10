package com.davemorrissey.labs.subscaleview

import android.graphics.PointF

import java.io.Serializable

/**
 * Wraps the scale, center and orientation of a displayed image for easy restoration on screen rotate.
 */
class ImageViewState(val scale: Float, center: PointF, val orientation: Int) : Serializable {

    private val centerX = center.x
    private val centerY = center.y

    val center: PointF
        get() = PointF(centerX, centerY)
}

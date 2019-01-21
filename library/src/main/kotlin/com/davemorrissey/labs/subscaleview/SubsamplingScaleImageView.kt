package com.davemorrissey.labs.subscaleview

import android.content.Context
import android.graphics.*
import android.graphics.Paint.Style
import android.net.Uri
import android.os.AsyncTask
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import java.io.File
import java.io.UnsupportedEncodingException
import java.lang.ref.WeakReference
import java.net.URLDecoder
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock

open class SubsamplingScaleImageView @JvmOverloads constructor(context: Context, attr: AttributeSet? = null) : View(context, attr) {
    companion object {
        const val FILE_SCHEME = "file://"
        const val ASSET_PREFIX = "${FILE_SCHEME}/android_asset/"

        private val TAG = SubsamplingScaleImageView::class.java.simpleName

        private const val ORIENTATION_USE_EXIF = -1
        private const val ORIENTATION_0 = 0
        private const val ORIENTATION_90 = 90
        private const val ORIENTATION_180 = 180
        private const val ORIENTATION_270 = 270

        private const val EASE_OUT_QUAD = 1
        private const val EASE_IN_OUT_QUAD = 2

        private const val TILE_SIZE_AUTO = Integer.MAX_VALUE
        private const val DOUBLE_TAP_ZOOM_DURATION = 300L

        var preferredBitmapConfig: Bitmap.Config? = null
    }

    var maxScale = 2f
    var isOneToOneZoomEnabled = false
    var isQuickScaleEnabled = true
    var executor = AsyncTask.THREAD_POOL_EXECUTOR
    var eagerLoadingEnabled = false
    var debug = false
    var resetScaleOnSizeChange = false
    var onImageEventListener: OnImageEventListener? = null
    var doubleTapZoomScale = 1f
    var minScale = minScale()
    var bitmapDecoderFactory: DecoderFactory<out ImageDecoder> = CompatDecoderFactory(SkiaImageDecoder::class.java)
    var regionDecoderFactory: DecoderFactory<out ImageRegionDecoder> = CompatDecoderFactory(SkiaImageRegionDecoder::class.java)
    var scale = 0f
    var sWidth = 0
    var sHeight = 0
    var orientation = ORIENTATION_0

    private var bitmap: Bitmap? = null
    private var uri: Uri? = null
    private var fullImageSampleSize = 0
    private var tileMap: MutableMap<Int, List<Tile>>? = null
    private var minimumTileDpi = -1
    private var maxTileWidth = TILE_SIZE_AUTO
    private var maxTileHeight = TILE_SIZE_AUTO
    private var isZoomEnabled = true
    private var scaleStart = 0f

    private var vTranslate: PointF? = null
    private var vTranslateStart: PointF? = null
    private var vTranslateBefore: PointF? = null

    private var pendingScale: Float? = null
    private var sPendingCenter: PointF? = null
    private var sRequestedCenter: PointF? = null

    private var sOrientation = 0

    private var isZooming = false
    private var isPanning = false
    private var isQuickScaling = false
    private var maxTouchCount = 0

    private var detector: GestureDetector? = null
    private var singleDetector: GestureDetector? = null

    private var decoder: ImageRegionDecoder? = null
    private val decoderLock = ReentrantReadWriteLock(true)

    private var vCenterStart: PointF? = null
    private var vDistStart = 0f

    private val quickScaleThreshold: Float
    private var quickScaleLastDistance = 0f
    private var quickScaleMoved = false
    private var quickScaleVLastPoint: PointF? = null
    private var quickScaleSCenter: PointF? = null
    private var quickScaleVStart: PointF? = null

    private var anim: Anim? = null
    private var isReady = false
    private var isImageLoaded = false

    private var bitmapPaint: Paint? = null
    private var debugTextPaint: Paint? = null
    private var debugLinePaint: Paint? = null

    private var satTemp: ScaleAndTranslate? = null
    private var objectMatrix: Matrix? = null
    private var sRect: RectF? = null
    private val srcArray = FloatArray(8)
    private val dstArray = FloatArray(8)

    private val density: Float

    init {
        resetScaleOnSizeChange = true
        density = resources.displayMetrics.density
        setMinimumDpi(160)
        setDoubleTapZoomDpi(160)
        setMinimumTileDpi(320)
        setGestureDetector(context)
        quickScaleThreshold = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, context.resources.displayMetrics)
    }

    private fun getIsBaseLayerReady(): Boolean {
        if (bitmap != null) {
            return true
        } else if (tileMap != null) {
            var baseLayerReady = true
            for ((key, value) in tileMap!!) {
                if (key == fullImageSampleSize) {
                    for (tile in value) {
                        if (tile.loading || tile.bitmap == null) {
                            baseLayerReady = false
                        }
                    }
                }
            }
            return baseLayerReady
        }
        return false
    }

    private fun getRequiredRotation() = if (orientation == ORIENTATION_USE_EXIF) sOrientation else orientation

    private fun getCenter(): PointF? {
        val mX = width / 2
        val mY = height / 2
        return viewToSourceCoord(mX.toFloat(), mY.toFloat())
    }

    fun setImage(path: String) {
        reset(true)

        var newPath = path

        if (!newPath.contains("://")) {
            if (newPath.startsWith("/")) {
                newPath = path.substring(1)
            }
            newPath = "$FILE_SCHEME/$newPath"
        }

        if (newPath.startsWith(FILE_SCHEME)) {
            val uriFile = File(newPath.substring(FILE_SCHEME.length))
            if (!uriFile.exists()) {
                try {
                    newPath = URLDecoder.decode(newPath, "UTF-8")
                } catch (e: UnsupportedEncodingException) {
                }
            }
        }

        uri = Uri.parse(newPath)
        val task = TilesInitTask(this, context, regionDecoderFactory, uri!!)
        execute(task)
    }

    private fun reset(newImage: Boolean) {
        scale = 0f
        scaleStart = 0f
        vTranslate = null
        vTranslateStart = null
        vTranslateBefore = null
        pendingScale = 0f
        sPendingCenter = null
        sRequestedCenter = null
        isZooming = false
        isPanning = false
        isQuickScaling = false
        maxTouchCount = 0
        fullImageSampleSize = 0
        vCenterStart = null
        vDistStart = 0f
        quickScaleLastDistance = 0f
        quickScaleMoved = false
        quickScaleSCenter = null
        quickScaleVLastPoint = null
        quickScaleVStart = null
        anim = null
        satTemp = null
        objectMatrix = null
        sRect = null

        if (newImage) {
            uri = null
            decoderLock.writeLock().lock()
            try {
                decoder?.recycle()
                decoder = null
            } finally {
                decoderLock.writeLock().unlock()
            }

            bitmap?.recycle()

            sWidth = 0
            sHeight = 0
            sOrientation = 0
            isReady = false
            isImageLoaded = false
            bitmap = null
        }

        if (tileMap != null) {
            for ((key, value) in tileMap!!) {
                for (tile in value) {
                    tile.visible = false
                    tile.bitmap?.recycle()
                    tile.bitmap = null
                }
            }
            tileMap = null
        }
        setGestureDetector(context)
    }

    private fun setGestureDetector(context: Context) {
        detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {

            override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (isReady && vTranslate != null && (Math.abs(e1.x - e2.x) > 50 || Math.abs(e1.y - e2.y) > 50) && (Math.abs(velocityX) > 500 || Math.abs(velocityY) > 500) && !isZooming) {
                    val vTranslateEnd = PointF(vTranslate!!.x + velocityX * 0.25f, vTranslate!!.y + velocityY * 0.25f)
                    val sCenterXEnd = (width / 2 - vTranslateEnd.x) / scale
                    val sCenterYEnd = (height / 2 - vTranslateEnd.y) / scale
                    AnimationBuilder(PointF(sCenterXEnd, sCenterYEnd)).apply {
                        interruptible = true
                        easing = EASE_OUT_QUAD
                        start()
                    }
                    return true
                }
                return super.onFling(e1, e2, velocityX, velocityY)
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                performClick()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isZoomEnabled && isReady && vTranslate != null) {
                    setGestureDetector(context)
                    if (isQuickScaleEnabled) {
                        vCenterStart = PointF(e.x, e.y)
                        vTranslateStart = PointF(vTranslate!!.x, vTranslate!!.y)
                        scaleStart = scale
                        isQuickScaling = true
                        isZooming = true
                        quickScaleLastDistance = -1f
                        quickScaleSCenter = viewToSourceCoord(vCenterStart!!)
                        quickScaleVStart = PointF(e.x, e.y)
                        quickScaleVLastPoint = PointF(quickScaleSCenter!!.x, quickScaleSCenter!!.y)
                        quickScaleMoved = false
                        return false
                    } else {
                        doubleTapZoom(viewToSourceCoord(PointF(e.x, e.y)), PointF(e.x, e.y))
                        return true
                    }
                }
                return super.onDoubleTapEvent(e)
            }
        })

        singleDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                performClick()
                return true
            }
        })
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (resetScaleOnSizeChange) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (oldh != 0 && oldw != 0) {
                resetScaleAndCenter()
            }
        } else {
            val sCenter = getCenter()
            if (isReady && sCenter != null) {
                anim = null
                pendingScale = scale
                sPendingCenter = sCenter
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSpecMode = View.MeasureSpec.getMode(widthMeasureSpec)
        val heightSpecMode = View.MeasureSpec.getMode(heightMeasureSpec)
        val parentWidth = View.MeasureSpec.getSize(widthMeasureSpec)
        val parentHeight = View.MeasureSpec.getSize(heightMeasureSpec)
        val resizeWidth = widthSpecMode != View.MeasureSpec.EXACTLY
        val resizeHeight = heightSpecMode != View.MeasureSpec.EXACTLY
        var width = parentWidth
        var height = parentHeight
        if (sWidth > 0 && sHeight > 0) {
            if (resizeWidth && resizeHeight) {
                width = sWidth()
                height = sHeight()
            } else if (resizeHeight) {
                height = (sHeight().toDouble() / sWidth().toDouble() * width).toInt()
            } else if (resizeWidth) {
                width = (sWidth().toDouble() / sHeight().toDouble() * height).toInt()
            }
        }
        width = Math.max(width, suggestedMinimumWidth)
        height = Math.max(height, suggestedMinimumHeight)
        setMeasuredDimension(width, height)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (anim?.interruptible == false) {
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        } else {
            anim = null
        }

        if (vTranslate == null) {
            singleDetector?.onTouchEvent(event)
            return true
        }

        if (!isQuickScaling && (detector == null || detector!!.onTouchEvent(event))) {
            isZooming = false
            isPanning = false
            maxTouchCount = 0
            return true
        }

        if (vTranslateStart == null) {
            vTranslateStart = PointF(0f, 0f)
        }

        if (vTranslateBefore == null) {
            vTranslateBefore = PointF(0f, 0f)
        }

        if (vCenterStart == null) {
            vCenterStart = PointF(0f, 0f)
        }

        vTranslateBefore!!.set(vTranslate)
        val handled = onTouchEventInternal(event)
        return handled || super.onTouchEvent(event)
    }

    private fun onTouchEventInternal(event: MotionEvent): Boolean {
        val touchCount = event.pointerCount
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_1_DOWN, MotionEvent.ACTION_POINTER_2_DOWN -> {
                anim = null
                parent?.requestDisallowInterceptTouchEvent(true)
                maxTouchCount = Math.max(maxTouchCount, touchCount)
                if (touchCount >= 2) {
                    if (isZoomEnabled) {
                        val distance = distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1))
                        scaleStart = scale
                        vDistStart = distance
                        vTranslateStart!!.set(vTranslate!!.x, vTranslate!!.y)
                        vCenterStart!!.set((event.getX(0) + event.getX(1)) / 2, (event.getY(0) + event.getY(1)) / 2)
                    } else {
                        maxTouchCount = 0
                    }
                } else if (!isQuickScaling) {
                    vTranslateStart!!.set(vTranslate!!.x, vTranslate!!.y)
                    vCenterStart!!.set(event.x, event.y)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                var consumed = false
                if (maxTouchCount > 0) {
                    if (touchCount >= 2) {
                        val vDistEnd = distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1))
                        val vCenterEndX = (event.getX(0) + event.getX(1)) / 2
                        val vCenterEndY = (event.getY(0) + event.getY(1)) / 2

                        if (isZoomEnabled && (distance(vCenterStart!!.x, vCenterEndX, vCenterStart!!.y, vCenterEndY) > 5 || Math.abs(vDistEnd - vDistStart) > 5 || isPanning)) {
                            isZooming = true
                            isPanning = true
                            consumed = true

                            val previousScale = scale.toDouble()
                            scale = Math.min(maxScale, vDistEnd / vDistStart * scaleStart)

                            if (scale <= minScale()) {
                                vDistStart = vDistEnd
                                scaleStart = minScale()
                                vCenterStart!!.set(vCenterEndX, vCenterEndY)
                                vTranslateStart!!.set(vTranslate)
                            } else {
                                val vLeftStart = vCenterStart!!.x - vTranslateStart!!.x
                                val vTopStart = vCenterStart!!.y - vTranslateStart!!.y
                                val vLeftNow = vLeftStart * (scale / scaleStart)
                                val vTopNow = vTopStart * (scale / scaleStart)
                                vTranslate!!.x = vCenterEndX - vLeftNow
                                vTranslate!!.y = vCenterEndY - vTopNow
                                if (previousScale * sHeight() < height && scale * sHeight() >= height || previousScale * sWidth() < width && scale * sWidth() >= width) {
                                    fitToBounds(true)
                                    vCenterStart!!.set(vCenterEndX, vCenterEndY)
                                    vTranslateStart!!.set(vTranslate)
                                    scaleStart = scale
                                    vDistStart = vDistEnd
                                }
                            }

                            fitToBounds(true)
                            refreshRequiredTiles(eagerLoadingEnabled)
                        }
                    } else if (isQuickScaling) {
                        var dist = Math.abs(quickScaleVStart!!.y - event.y) * 2 + quickScaleThreshold

                        if (quickScaleLastDistance == -1f) {
                            quickScaleLastDistance = dist
                        }
                        val isUpwards = event.y > quickScaleVLastPoint!!.y
                        quickScaleVLastPoint!!.set(0f, event.y)

                        val spanDiff = Math.abs(1 - dist / quickScaleLastDistance) * 0.5f

                        if (spanDiff > 0.03f || quickScaleMoved) {
                            quickScaleMoved = true

                            var multiplier = 1f
                            if (quickScaleLastDistance > 0) {
                                multiplier = if (isUpwards) 1 + spanDiff else 1 - spanDiff
                            }

                            val previousScale = scale.toDouble()
                            scale = Math.max(minScale(), Math.min(maxScale, scale * multiplier))

                            val vLeftStart = vCenterStart!!.x - vTranslateStart!!.x
                            val vTopStart = vCenterStart!!.y - vTranslateStart!!.y
                            val vLeftNow = vLeftStart * (scale / scaleStart)
                            val vTopNow = vTopStart * (scale / scaleStart)
                            vTranslate!!.x = vCenterStart!!.x - vLeftNow
                            vTranslate!!.y = vCenterStart!!.y - vTopNow
                            if (previousScale * sHeight() < height && scale * sHeight() >= height || previousScale * sWidth() < width && scale * sWidth() >= width) {
                                fitToBounds(true)
                                vCenterStart!!.set(sourceToViewCoord(quickScaleSCenter!!))
                                vTranslateStart!!.set(vTranslate)
                                scaleStart = scale
                                dist = 0f
                            }
                        }

                        quickScaleLastDistance = dist

                        fitToBounds(true)
                        refreshRequiredTiles(eagerLoadingEnabled)
                        consumed = true
                    } else if (!isZooming) {
                        val dx = Math.abs(event.x - vCenterStart!!.x)
                        val dy = Math.abs(event.y - vCenterStart!!.y)

                        val offset = density * 5
                        if (dx > offset || dy > offset || isPanning) {
                            consumed = true
                            vTranslate!!.x = vTranslateStart!!.x + (event.x - vCenterStart!!.x)
                            vTranslate!!.y = vTranslateStart!!.y + (event.y - vCenterStart!!.y)

                            val lastX = vTranslate!!.x
                            val lastY = vTranslate!!.y
                            fitToBounds(true)
                            val atXEdge = lastX != vTranslate!!.x
                            val atYEdge = lastY != vTranslate!!.y
                            val edgeXSwipe = atXEdge && dx > dy && !isPanning
                            val edgeYSwipe = atYEdge && dy > dx && !isPanning
                            val yPan = lastY == vTranslate!!.y && dy > offset * 3
                            if (!edgeXSwipe && !edgeYSwipe && (!atXEdge || !atYEdge || yPan || isPanning)) {
                                isPanning = true
                            } else if (dx > offset || dy > offset) {
                                maxTouchCount = 0
                                parent?.requestDisallowInterceptTouchEvent(false)
                            }

                            refreshRequiredTiles(eagerLoadingEnabled)
                        }
                    }
                }

                if (consumed) {
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_POINTER_2_UP -> {
                if (isQuickScaling) {
                    isQuickScaling = false
                    if (!quickScaleMoved) {
                        doubleTapZoom(quickScaleSCenter, vCenterStart)
                    }
                }
                if (maxTouchCount > 0 && (isZooming || isPanning)) {
                    if (isZooming && touchCount == 2) {
                        isPanning = true
                        vTranslateStart!!.set(vTranslate!!.x, vTranslate!!.y)
                        if (event.actionIndex == 1) {
                            vCenterStart!!.set(event.getX(0), event.getY(0))
                        } else {
                            vCenterStart!!.set(event.getX(1), event.getY(1))
                        }
                    }

                    if (touchCount < 3) {
                        isZooming = false
                    }

                    if (touchCount < 2) {
                        isPanning = false
                        maxTouchCount = 0
                    }

                    refreshRequiredTiles(true)
                    return true
                }
                if (touchCount == 1) {
                    isZooming = false
                    isPanning = false
                    maxTouchCount = 0
                }
                return true
            }
        }
        return false
    }

    private fun doubleTapZoom(sCenter: PointF?, vFocus: PointF?) {
        val doubleTapZoomScale = Math.min(maxScale, doubleTapZoomScale)
        val zoomIn = scale <= doubleTapZoomScale * 0.9 || scale == minScale
        if (sWidth == sHeight || !isOneToOneZoomEnabled) {
            val targetScale = if (zoomIn) doubleTapZoomScale else minScale()

            if (zoomIn) {
                AnimationBuilder(targetScale, sCenter!!, vFocus!!).start()
            } else {
                AnimationBuilder(targetScale, sCenter!!).start()
            }
        } else {
            val targetScale = if (zoomIn && scale != 1f) doubleTapZoomScale else minScale()

            if (scale != 1f) {
                if (zoomIn) {
                    AnimationBuilder(targetScale, sCenter!!, vFocus!!).start()
                } else {
                    AnimationBuilder(1f, sCenter!!, vFocus!!).start()
                }
            } else {
                AnimationBuilder(targetScale, sCenter!!).start()
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        createPaints()

        if (sWidth == 0 || sHeight == 0 || width == 0 || height == 0) {
            return
        }

        if (tileMap == null && decoder != null) {
            initialiseBaseLayer(getMaxBitmapDimensions(canvas))
        }

        if (!checkReady()) {
            return
        }

        preDraw()

        if (anim != null && anim!!.vFocusStart != null) {
            if (vTranslateBefore == null) {
                vTranslateBefore = PointF(0f, 0f)
            }
            vTranslateBefore!!.set(vTranslate)

            var scaleElapsed = System.currentTimeMillis() - anim!!.time
            val finished = scaleElapsed > anim!!.duration
            scaleElapsed = Math.min(scaleElapsed, anim!!.duration)
            scale = ease(anim!!.easing, scaleElapsed, anim!!.scaleStart, anim!!.scaleEnd - anim!!.scaleStart, anim!!.duration)

            val vFocusNowX = ease(anim!!.easing, scaleElapsed, anim!!.vFocusStart!!.x, anim!!.vFocusEnd!!.x - anim!!.vFocusStart!!.x, anim!!.duration)
            val vFocusNowY = ease(anim!!.easing, scaleElapsed, anim!!.vFocusStart!!.y, anim!!.vFocusEnd!!.y - anim!!.vFocusStart!!.y, anim!!.duration)
            vTranslate!!.x -= sourceToViewX(anim!!.sCenterEnd!!.x) - vFocusNowX
            vTranslate!!.y -= sourceToViewY(anim!!.sCenterEnd!!.y) - vFocusNowY

            fitToBounds(finished || anim!!.scaleStart == anim!!.scaleEnd)
            refreshRequiredTiles(finished)
            if (finished) {
                anim = null
            }
            invalidate()
        }

        if (tileMap != null && getIsBaseLayerReady()) {
            val sampleSize = Math.min(fullImageSampleSize, calculateInSampleSize(scale))
            var hasMissingTiles = false
            for ((key, value) in tileMap!!) {
                if (key == sampleSize) {
                    for (tile in value) {
                        if (tile.visible && (tile.loading || tile.bitmap == null)) {
                            hasMissingTiles = true
                        }
                    }
                }
            }

            for ((key, value) in tileMap!!) {
                if (key == sampleSize || hasMissingTiles) {
                    for (tile in value) {
                        sourceToViewRect(tile.sRect!!, tile.vRect!!)
                        if (!tile.loading && tile.bitmap != null) {
                            if (objectMatrix == null) {
                                objectMatrix = Matrix()
                            }

                            objectMatrix!!.reset()
                            setMatrixArray(srcArray, 0f, 0f, tile.bitmap!!.width.toFloat(), 0f, tile.bitmap!!.width.toFloat(), tile.bitmap!!.height.toFloat(), 0f, tile.bitmap!!.height.toFloat())
                            when (getRequiredRotation()) {
                                ORIENTATION_0 -> setMatrixArray(dstArray, tile.vRect!!.left.toFloat(), tile.vRect!!.top.toFloat(), tile.vRect!!.right.toFloat(), tile.vRect!!.top.toFloat(), tile.vRect!!.right.toFloat(), tile.vRect!!.bottom.toFloat(), tile.vRect!!.left.toFloat(), tile.vRect!!.bottom.toFloat())
                                ORIENTATION_90 -> setMatrixArray(dstArray, tile.vRect!!.right.toFloat(), tile.vRect!!.top.toFloat(), tile.vRect!!.right.toFloat(), tile.vRect!!.bottom.toFloat(), tile.vRect!!.left.toFloat(), tile.vRect!!.bottom.toFloat(), tile.vRect!!.left.toFloat(), tile.vRect!!.top.toFloat())
                                ORIENTATION_180 -> setMatrixArray(dstArray, tile.vRect!!.right.toFloat(), tile.vRect!!.bottom.toFloat(), tile.vRect!!.left.toFloat(), tile.vRect!!.bottom.toFloat(), tile.vRect!!.left.toFloat(), tile.vRect!!.top.toFloat(), tile.vRect!!.right.toFloat(), tile.vRect!!.top.toFloat())
                                ORIENTATION_270 -> setMatrixArray(dstArray, tile.vRect!!.left.toFloat(), tile.vRect!!.bottom.toFloat(), tile.vRect!!.left.toFloat(), tile.vRect!!.top.toFloat(), tile.vRect!!.right.toFloat(), tile.vRect!!.top.toFloat(), tile.vRect!!.right.toFloat(), tile.vRect!!.bottom.toFloat())
                            }
                            objectMatrix!!.setPolyToPoly(srcArray, 0, dstArray, 0, 4)
                            canvas.drawBitmap(tile.bitmap!!, objectMatrix!!, bitmapPaint)
                            if (debug) {
                                canvas.drawRect(tile.vRect!!, debugLinePaint!!)
                            }
                        } else if (tile.loading && debug) {
                            canvas.drawText("LOADING", (tile.vRect!!.left + px(5)).toFloat(), (tile.vRect!!.top + px(35)).toFloat(), debugTextPaint!!)
                        }
                        if (tile.visible && debug) {
                            canvas.drawText("ISS ${tile.sampleSize} RECT ${tile.sRect!!.top}, ${tile.sRect!!.left}, ${tile.sRect!!.bottom}, ${tile.sRect!!.right}", (tile.vRect!!.left + px(5)).toFloat(), (tile.vRect!!.top + px(15)).toFloat(), debugTextPaint!!)
                        }
                    }
                }
            }
        } else if (bitmap?.isRecycled == false) {
            var xScale = scale
            var yScale = scale

            if (objectMatrix == null) {
                objectMatrix = Matrix()
            }

            objectMatrix!!.apply {
                reset()
                postScale(xScale, yScale)
                postRotate(getRequiredRotation().toFloat())
                postTranslate(vTranslate!!.x, vTranslate!!.y)

                when (getRequiredRotation()) {
                    ORIENTATION_90 -> postTranslate(scale * sHeight, 0f)
                    ORIENTATION_180 -> postTranslate(scale * sWidth, scale * sHeight)
                    ORIENTATION_270 -> postTranslate(0f, scale * sWidth)
                }
            }

            canvas.drawBitmap(bitmap!!, objectMatrix!!, bitmapPaint)
        }

        if (debug) {
            canvas.drawText("Scale: ${String.format(Locale.ENGLISH, "%.2f", scale)} (${String.format(Locale.ENGLISH, "%.2f", minScale())} - ${String.format(Locale.ENGLISH, "%.2f", maxScale)})", px(5).toFloat(), px(15).toFloat(), debugTextPaint!!)
            canvas.drawText("Translate: ${String.format(Locale.ENGLISH, "%.2f", vTranslate!!.x)}:${String.format(Locale.ENGLISH, "%.2f", vTranslate!!.y)}", px(5).toFloat(), px(30).toFloat(), debugTextPaint!!)
            val center = getCenter()

            canvas.drawText("Source center: ${String.format(Locale.ENGLISH, "%.2f", center!!.x)}:${String.format(Locale.ENGLISH, "%.2f", center.y)}", px(5).toFloat(), px(45).toFloat(), debugTextPaint!!)
            if (anim != null) {
                val vCenterStart = sourceToViewCoord(anim!!.sCenterStart!!)
                val vCenterEndRequested = sourceToViewCoord(anim!!.sCenterEndRequested!!)
                val vCenterEnd = sourceToViewCoord(anim!!.sCenterEnd!!)

                canvas.drawCircle(vCenterStart!!.x, vCenterStart.y, px(10).toFloat(), debugLinePaint!!)
                debugLinePaint!!.color = Color.RED

                canvas.drawCircle(vCenterEndRequested!!.x, vCenterEndRequested.y, px(20).toFloat(), debugLinePaint!!)
                debugLinePaint!!.color = Color.BLUE

                canvas.drawCircle(vCenterEnd!!.x, vCenterEnd.y, px(25).toFloat(), debugLinePaint!!)
                debugLinePaint!!.color = Color.CYAN
                canvas.drawCircle((width / 2).toFloat(), (height / 2).toFloat(), px(30).toFloat(), debugLinePaint!!)
            }

            if (vCenterStart != null) {
                debugLinePaint!!.color = Color.RED
                canvas.drawCircle(vCenterStart!!.x, vCenterStart!!.y, px(20).toFloat(), debugLinePaint!!)
            }

            if (quickScaleSCenter != null) {
                debugLinePaint!!.color = Color.BLUE
                canvas.drawCircle(sourceToViewX(quickScaleSCenter!!.x), sourceToViewY(quickScaleSCenter!!.y), px(35).toFloat(), debugLinePaint!!)
            }

            if (quickScaleVStart != null && isQuickScaling) {
                debugLinePaint!!.color = Color.CYAN
                canvas.drawCircle(quickScaleVStart!!.x, quickScaleVStart!!.y, px(30).toFloat(), debugLinePaint!!)
            }

            debugLinePaint!!.color = Color.MAGENTA
        }
    }

    private fun setMatrixArray(array: FloatArray, f0: Float, f1: Float, f2: Float, f3: Float, f4: Float, f5: Float, f6: Float, f7: Float) {
        array[0] = f0
        array[1] = f1
        array[2] = f2
        array[3] = f3
        array[4] = f4
        array[5] = f5
        array[6] = f6
        array[7] = f7
    }

    private fun checkReady(): Boolean {
        val ready = width > 0 && height > 0 && sWidth > 0 && sHeight > 0 && (bitmap != null || getIsBaseLayerReady())
        if (!isReady && ready) {
            preDraw()
            isReady = true
            onReady()
            onImageEventListener?.onReady()
        }
        return ready
    }

    private fun checkImageLoaded(): Boolean {
        val imageLoaded = getIsBaseLayerReady()
        if (!isImageLoaded && imageLoaded) {
            preDraw()
            isImageLoaded = true
            onImageLoaded()
            onImageEventListener?.onImageLoaded()
        }
        return imageLoaded
    }

    private fun createPaints() {
        if (bitmapPaint == null) {
            bitmapPaint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
                isDither = true
            }
        }

        if ((debugTextPaint == null || debugLinePaint == null) && debug) {
            debugTextPaint = Paint().apply {
                textSize = px(12).toFloat()
                color = Color.MAGENTA
                style = Style.FILL
            }

            debugLinePaint = Paint().apply {
                color = Color.MAGENTA
                style = Style.STROKE
                strokeWidth = px(1).toFloat()
            }
        }
    }

    @Synchronized
    private fun initialiseBaseLayer(maxTileDimensions: Point) {
        debug("initialiseBaseLayer maxTileDimensions=${maxTileDimensions.x}x${maxTileDimensions.y}")

        satTemp = ScaleAndTranslate(0f, PointF(0f, 0f))
        fitToBounds(true, satTemp!!)

        fullImageSampleSize = calculateInSampleSize(satTemp!!.scale)
        if (fullImageSampleSize > 1) {
            fullImageSampleSize /= 2
        }

        if (uri == null) {
            return
        }

        if (fullImageSampleSize == 1 && sWidth() < maxTileDimensions.x && sHeight() < maxTileDimensions.y) {
            decoder!!.recycle()
            decoder = null
            val task = BitmapLoadTask(this, context, bitmapDecoderFactory, uri!!)
            execute(task)
        } else {
            initialiseTileMap(maxTileDimensions)

            val baseGrid = tileMap!![fullImageSampleSize]
            for (baseTile in baseGrid!!) {
                val task = TileLoadTask(this, decoder!!, baseTile)
                execute(task)
            }
            refreshRequiredTiles(true)
        }
    }

    private fun refreshRequiredTiles(load: Boolean) {
        if (decoder == null || tileMap == null) {
            return
        }

        val sampleSize = Math.min(fullImageSampleSize, calculateInSampleSize(scale))

        for ((key, value) in tileMap!!) {
            for (tile in value) {
                if (tile.sampleSize < sampleSize || tile.sampleSize > sampleSize && tile.sampleSize != fullImageSampleSize) {
                    tile.visible = false
                    tile.bitmap?.recycle()
                    tile.bitmap = null
                }

                if (tile.sampleSize == sampleSize) {
                    if (tileVisible(tile)) {
                        tile.visible = true
                        if (!tile.loading && tile.bitmap == null && load) {
                            val task = TileLoadTask(this, decoder!!, tile)
                            execute(task)
                        }
                    } else if (tile.sampleSize != fullImageSampleSize) {
                        tile.visible = false
                        tile.bitmap?.recycle()
                        tile.bitmap = null
                    }
                } else if (tile.sampleSize == fullImageSampleSize) {
                    tile.visible = true
                }
            }
        }
    }

    private fun tileVisible(tile: Tile): Boolean {
        val sVisLeft = viewToSourceX(0f)
        val sVisRight = viewToSourceX(width.toFloat())
        val sVisTop = viewToSourceY(0f)
        val sVisBottom = viewToSourceY(height.toFloat())
        return !(sVisLeft > tile.sRect!!.right || tile.sRect!!.left > sVisRight || sVisTop > tile.sRect!!.bottom || tile.sRect!!.top > sVisBottom)
    }

    private fun preDraw() {
        if (width == 0 || height == 0 || sWidth <= 0 || sHeight <= 0) {
            return
        }

        if (sPendingCenter != null && pendingScale != null) {
            scale = pendingScale!!
            if (vTranslate == null) {
                vTranslate = PointF()
            }
            vTranslate!!.x = width / 2 - scale * sPendingCenter!!.x
            vTranslate!!.y = height / 2 - scale * sPendingCenter!!.y
            sPendingCenter = null
            pendingScale = null
            fitToBounds(true)
            refreshRequiredTiles(true)
        }

        fitToBounds(false)
    }

    private fun calculateInSampleSize(scale: Float): Int {
        var newScale = scale
        if (minimumTileDpi > 0) {
            val metrics = resources.displayMetrics
            val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
            newScale *= minimumTileDpi / averageDpi
        }

        val reqWidth = (sWidth() * newScale).toInt()
        val reqHeight = (sHeight() * newScale).toInt()

        var inSampleSize = 1
        if (reqWidth == 0 || reqHeight == 0) {
            return 32
        }

        if (sHeight() > reqHeight || sWidth() > reqWidth) {
            val heightRatio = Math.round(sHeight().toFloat() / reqHeight.toFloat())
            val widthRatio = Math.round(sWidth().toFloat() / reqWidth.toFloat())
            inSampleSize = if (heightRatio < widthRatio) heightRatio else widthRatio
        }

        var power = 1
        while (power * 2 < inSampleSize) {
            power *= 2
        }

        return power
    }

    private fun fitToBounds(center: Boolean, sat: ScaleAndTranslate) {
        val vTranslate = sat.vTranslate
        val scale = limitedScale(sat.scale)
        val scaleWidth = scale * sWidth()
        val scaleHeight = scale * sHeight()

        if (center) {
            vTranslate.x = Math.max(vTranslate.x, width - scaleWidth)
            vTranslate.y = Math.max(vTranslate.y, height - scaleHeight)
        } else {
            vTranslate.x = Math.max(vTranslate.x, -scaleWidth)
            vTranslate.y = Math.max(vTranslate.y, -scaleHeight)
        }

        val xPaddingRatio = if (paddingLeft > 0 || paddingRight > 0) paddingLeft / (paddingLeft + paddingRight).toFloat() else 0.5f
        val yPaddingRatio = if (paddingTop > 0 || paddingBottom > 0) paddingTop / (paddingTop + paddingBottom).toFloat() else 0.5f

        val maxTx: Float
        val maxTy: Float
        if (center) {
            maxTx = Math.max(0f, (width - scaleWidth) * xPaddingRatio)
            maxTy = Math.max(0f, (height - scaleHeight) * yPaddingRatio)
        } else {
            maxTx = Math.max(0, width).toFloat()
            maxTy = Math.max(0, height).toFloat()
        }

        vTranslate.x = Math.min(vTranslate.x, maxTx)
        vTranslate.y = Math.min(vTranslate.y, maxTy)

        sat.scale = scale
    }

    private fun fitToBounds(center: Boolean) {
        var init = false
        if (vTranslate == null) {
            init = true
            vTranslate = PointF(0f, 0f)
        }

        if (satTemp == null) {
            satTemp = ScaleAndTranslate(0f, PointF(0f, 0f))
        }

        satTemp!!.scale = scale
        satTemp!!.vTranslate.set(vTranslate)
        fitToBounds(center, satTemp!!)
        scale = satTemp!!.scale
        vTranslate!!.set(satTemp!!.vTranslate)

        if (init) {
            vTranslate!!.set(vTranslateForSCenter((sWidth() / 2).toFloat(), (sHeight() / 2).toFloat(), scale))
        }
    }

    private fun initialiseTileMap(maxTileDimensions: Point) {
        debug("initialiseTileMap maxTileDimensions=${maxTileDimensions.x}x${maxTileDimensions.y}")
        tileMap = LinkedHashMap()
        var sampleSize = fullImageSampleSize
        var xTiles = 1
        var yTiles = 1

        while (true) {
            var sTileWidth = sWidth() / xTiles
            var sTileHeight = sHeight() / yTiles
            var subTileWidth = sTileWidth / sampleSize
            var subTileHeight = sTileHeight / sampleSize
            while (subTileWidth + xTiles + 1 > maxTileDimensions.x || subTileWidth > width * 1.25 && sampleSize < fullImageSampleSize) {
                xTiles += 1
                sTileWidth = sWidth() / xTiles
                subTileWidth = sTileWidth / sampleSize
            }

            while (subTileHeight + yTiles + 1 > maxTileDimensions.y || subTileHeight > height * 1.25 && sampleSize < fullImageSampleSize) {
                yTiles += 1
                sTileHeight = sHeight() / yTiles
                subTileHeight = sTileHeight / sampleSize
            }

            val tileGrid = ArrayList<Tile>(xTiles * yTiles)
            for (x in 0 until xTiles) {
                for (y in 0 until yTiles) {
                    val tile = Tile()
                    tile.sampleSize = sampleSize
                    tile.visible = sampleSize == fullImageSampleSize
                    tile.sRect = Rect(
                            x * sTileWidth,
                            y * sTileHeight,
                            if (x == xTiles - 1) sWidth() else (x + 1) * sTileWidth,
                            if (y == yTiles - 1) sHeight() else (y + 1) * sTileHeight
                    )
                    tile.vRect = Rect(0, 0, 0, 0)
                    tile.fileSRect = Rect(tile.sRect)
                    tileGrid.add(tile)
                }
            }
            tileMap!![sampleSize] = tileGrid
            if (sampleSize == 1) {
                break
            } else {
                sampleSize /= 2
            }
        }
    }

    private class TilesInitTask internal constructor(view: SubsamplingScaleImageView, context: Context, decoderFactory: DecoderFactory<out ImageRegionDecoder>, private val source: Uri) : AsyncTask<Void, Void, IntArray>() {
        private val viewRef = WeakReference(view)
        private val contextRef = WeakReference(context)
        private val decoderFactoryRef = WeakReference(decoderFactory)
        private var decoder: ImageRegionDecoder? = null
        private var exception: Exception? = null

        override fun doInBackground(vararg params: Void): IntArray? {
            try {
                val context = contextRef.get()
                val decoderFactory = decoderFactoryRef.get()
                val view = viewRef.get()
                if (context != null && decoderFactory != null && view != null) {
                    view.debug("TilesInitTask.doInBackground")
                    decoder = decoderFactory.make()
                    val dimensions = decoder!!.init(context, source)
                    val sWidth = dimensions.x
                    val sHeight = dimensions.y
                    val exifOrientation = view.orientation
                    return intArrayOf(sWidth, sHeight, exifOrientation)
                }
            } catch (e: Exception) {
                exception = e
            }

            return null
        }

        override fun onPostExecute(xyo: IntArray?) {
            val view = viewRef.get()
            if (view != null) {
                if (decoder != null && xyo != null && xyo.size == 3) {
                    view.onTilesInited(decoder!!, xyo[0], xyo[1], xyo[2])
                } else if (exception != null) {
                    view.onImageEventListener?.onImageLoadError(exception!!)
                }
            }
        }
    }

    @Synchronized
    private fun onTilesInited(decoder: ImageRegionDecoder, sWidth: Int, sHeight: Int, sOrientation: Int) {
        debug("onTilesInited sWidth=$sWidth, sHeight=$sHeight, sOrientation=$orientation")
        if (this.sWidth > 0 && this.sHeight > 0 && (this.sWidth != sWidth || this.sHeight != sHeight)) {
            reset(false)
            if (bitmap != null) {
                bitmap!!.recycle()
                bitmap = null
            }
        }
        this.decoder = decoder
        this.sWidth = sWidth
        this.sHeight = sHeight
        this.sOrientation = sOrientation
        checkReady()
        if (!checkImageLoaded() && maxTileWidth > 0 && maxTileWidth != TILE_SIZE_AUTO && maxTileHeight > 0 && maxTileHeight != TILE_SIZE_AUTO && width > 0 && height > 0) {
            initialiseBaseLayer(Point(maxTileWidth, maxTileHeight))
        }

        invalidate()
        requestLayout()
    }

    private class TileLoadTask internal constructor(view: SubsamplingScaleImageView, decoder: ImageRegionDecoder, tile: Tile) : AsyncTask<Void, Void, Bitmap>() {
        private val viewRef = WeakReference(view)
        private val decoderRef = WeakReference(decoder)
        private val tileRef = WeakReference(tile)
        private var exception: Exception? = null

        init {
            tile.loading = true
        }

        override fun doInBackground(vararg params: Void): Bitmap? {
            try {
                val view = viewRef.get()
                val decoder = decoderRef.get()
                val tile = tileRef.get()
                if (decoder != null && tile != null && view != null && decoder.isReady() && tile.visible) {
                    view.debug("TileLoadTask.doInBackground, tile.sRect=${tile.sRect as Rect}, tile.sampleSize=${tile.sampleSize}")
                    view.decoderLock.readLock().lock()
                    try {
                        if (decoder.isReady()) {
                            view.fileSRect(tile.sRect, tile.fileSRect)
                            return decoder.decodeRegion(tile.fileSRect!!, tile.sampleSize)
                        } else {
                            tile.loading = false
                        }
                    } finally {
                        view.decoderLock.readLock().unlock()
                    }
                } else {
                    tile?.loading = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode tile $e")
                exception = e
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Failed to decode tile - OutOfMemoryError $e")
                exception = RuntimeException(e)
            }

            return null
        }

        override fun onPostExecute(bitmap: Bitmap?) {
            val subsamplingScaleImageView = viewRef.get()
            val tile = tileRef.get()
            if (subsamplingScaleImageView != null && tile != null) {
                if (bitmap != null) {
                    tile.bitmap = bitmap
                    tile.loading = false
                    subsamplingScaleImageView.onTileLoaded()
                } else if (exception != null) {
                    subsamplingScaleImageView.onImageEventListener?.onTileLoadError(exception!!)
                }
            }
        }
    }

    @Synchronized
    private fun onTileLoaded() {
        debug("onTileLoaded")
        checkReady()
        checkImageLoaded()
        if (getIsBaseLayerReady() && bitmap != null) {
            bitmap!!.recycle()
            bitmap = null
        }
        invalidate()
    }

    private class BitmapLoadTask internal constructor(view: SubsamplingScaleImageView, context: Context, decoderFactory: DecoderFactory<out ImageDecoder>, private val source: Uri) : AsyncTask<Void, Void, Int>() {
        private val viewRef = WeakReference(view)
        private val contextRef = WeakReference(context)
        private val decoderFactoryRef = WeakReference(decoderFactory)
        private var bitmap: Bitmap? = null
        private var exception: Exception? = null

        override fun doInBackground(vararg params: Void): Int? {
            try {
                val context = contextRef.get()
                val decoderFactory = decoderFactoryRef.get()
                val view = viewRef.get()

                if (context != null && decoderFactory != null && view != null) {
                    view.debug("BitmapLoadTask.doInBackground")
                    bitmap = decoderFactory.make().decode(context, source)
                    return view.orientation
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load bitmap", e)
                exception = e
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Failed to load bitmap - OutOfMemoryError $e")
                exception = RuntimeException(e)
            }

            return null
        }

        override fun onPostExecute(orientation: Int?) {
            val subsamplingScaleImageView = viewRef.get()
            if (bitmap != null && orientation != null) {
                subsamplingScaleImageView?.onImageLoaded(bitmap, orientation)
            } else if (exception != null) {
                subsamplingScaleImageView?.onImageEventListener?.onImageLoadError(exception!!)
            }
        }
    }

    @Synchronized
    private fun onImageLoaded(bitmap: Bitmap?, sOrientation: Int) {
        debug("onImageLoaded")
        if (sWidth > 0 && sHeight > 0 && (sWidth != bitmap!!.width || sHeight != bitmap.height)) {
            reset(false)
        }

        this.bitmap?.recycle()
        this.bitmap = bitmap
        sWidth = bitmap!!.width
        sHeight = bitmap.height
        this.sOrientation = sOrientation
        val ready = checkReady()
        val imageLoaded = checkImageLoaded()
        if (ready || imageLoaded) {
            invalidate()
            requestLayout()
        }
    }

    private fun execute(asyncTask: AsyncTask<Void, Void, *>) {
        asyncTask.executeOnExecutor(executor)
    }

    class Tile {
        var sRect: Rect? = null
        var sampleSize = 0
        var bitmap: Bitmap? = null
        var loading = false
        var visible = false
        var vRect: Rect? = null
        var fileSRect: Rect? = null
    }

    class ScaleAndTranslate constructor(var scale: Float, var vTranslate: PointF)

    fun setMaxTileSize(maxPixels: Int) {
        maxTileWidth = maxPixels
        maxTileHeight = maxPixels
    }

    fun setMaxTileSize(maxPixelsX: Int, maxPixelsY: Int) {
        maxTileWidth = maxPixelsX
        maxTileHeight = maxPixelsY
    }

    private fun getMaxBitmapDimensions(canvas: Canvas) = Point(Math.min(canvas.maximumBitmapWidth, maxTileWidth), Math.min(canvas.maximumBitmapHeight, maxTileHeight))

    private fun sWidth(): Int {
        val rotation = getRequiredRotation()
        return if (rotation == 90 || rotation == 270) {
            sHeight
        } else {
            sWidth
        }
    }

    private fun sHeight(): Int {
        val rotation = getRequiredRotation()
        return if (rotation == 90 || rotation == 270) {
            sWidth
        } else {
            sHeight
        }
    }

    private fun fileSRect(sRect: Rect?, target: Rect?) {
        when (getRequiredRotation()) {
            0 -> target!!.set(sRect)
            90 -> target!!.set(sRect!!.top, sHeight - sRect.right, sRect.bottom, sHeight - sRect.left)
            180 -> target!!.set(sWidth - sRect!!.right, sHeight - sRect.bottom, sWidth - sRect.left, sHeight - sRect.top)
            else -> target!!.set(sWidth - sRect!!.bottom, sRect.left, sWidth - sRect.top, sRect.right)
        }
    }

    private fun distance(x0: Float, x1: Float, y0: Float, y1: Float): Float {
        val x = x0 - x1
        val y = y0 - y1
        return Math.sqrt((x * x + y * y).toDouble()).toFloat()
    }

    fun recycle() {
        reset(true)
        bitmapPaint = null
        debugTextPaint = null
        debugLinePaint = null
    }

    private fun viewToSourceX(vx: Float): Float {
        return if (vTranslate == null) {
            Float.NaN
        } else {
            (vx - vTranslate!!.x) / scale
        }
    }

    private fun viewToSourceY(vy: Float): Float {
        return if (vTranslate == null) {
            Float.NaN
        } else {
            (vy - vTranslate!!.y) / scale
        }
    }

    fun viewToSourceCoord(vxy: PointF) = viewToSourceCoord(vxy.x, vxy.y, PointF())

    private fun viewToSourceCoord(vx: Float, vy: Float, sTarget: PointF = PointF()): PointF? {
        if (vTranslate == null) {
            return null
        }

        sTarget.set(viewToSourceX(vx), viewToSourceY(vy))
        return sTarget
    }

    private fun sourceToViewX(sx: Float): Float {
        return if (vTranslate == null) {
            Float.NaN
        } else {
            sx * scale + vTranslate!!.x
        }
    }

    private fun sourceToViewY(sy: Float): Float {
        return if (vTranslate == null) {
            Float.NaN
        } else {
            sy * scale + vTranslate!!.y
        }
    }

    fun sourceToViewCoord(sxy: PointF) = sourceToViewCoord(sxy.x, sxy.y, PointF())

    private fun sourceToViewCoord(sx: Float, sy: Float, vTarget: PointF = PointF()): PointF? {
        if (vTranslate == null) {
            return null
        }

        vTarget.set(sourceToViewX(sx), sourceToViewY(sy))
        return vTarget
    }

    private fun sourceToViewRect(sRect: Rect, vTarget: Rect) {
        vTarget.set(
                sourceToViewX(sRect.left.toFloat()).toInt(),
                sourceToViewY(sRect.top.toFloat()).toInt(),
                sourceToViewX(sRect.right.toFloat()).toInt(),
                sourceToViewY(sRect.bottom.toFloat()).toInt()
        )
    }

    private fun vTranslateForSCenter(sCenterX: Float, sCenterY: Float, scale: Float): PointF {
        val vxCenter = paddingLeft + (width - paddingRight - paddingLeft) / 2
        val vyCenter = paddingTop + (height - paddingBottom - paddingTop) / 2
        if (satTemp == null) {
            satTemp = ScaleAndTranslate(0f, PointF(0f, 0f))
        }

        satTemp!!.scale = scale
        satTemp!!.vTranslate.set(vxCenter - sCenterX * scale, vyCenter - sCenterY * scale)
        fitToBounds(true, satTemp!!)
        return satTemp!!.vTranslate
    }

    private fun limitedSCenter(sCenterX: Float, sCenterY: Float, scale: Float, sTarget: PointF): PointF {
        val vTranslate = vTranslateForSCenter(sCenterX, sCenterY, scale)
        val vxCenter = paddingLeft + (width - paddingRight - paddingLeft) / 2
        val vyCenter = paddingTop + (height - paddingBottom - paddingTop) / 2
        val sx = (vxCenter - vTranslate.x) / scale
        val sy = (vyCenter - vTranslate.y) / scale
        sTarget.set(sx, sy)
        return sTarget
    }

    private fun minScale(): Float {
        val vPadding = paddingBottom + paddingTop
        val hPadding = paddingLeft + paddingRight
        return Math.min((width - hPadding) / sWidth().toFloat(), (height - vPadding) / sHeight().toFloat())
    }

    private fun limitedScale(targetScale: Float): Float {
        var newTargetScale = targetScale
        newTargetScale = Math.max(minScale(), newTargetScale)
        newTargetScale = Math.min(maxScale, newTargetScale)
        return newTargetScale
    }

    private fun ease(type: Int, time: Long, from: Float, change: Float, duration: Long): Float {
        return when (type) {
            EASE_IN_OUT_QUAD -> easeInOutQuad(time, from, change, duration)
            EASE_OUT_QUAD -> easeOutQuad(time, from, change, duration)
            else -> throw IllegalStateException("Unexpected easing type: $type")
        }
    }

    private fun easeOutQuad(time: Long, from: Float, change: Float, duration: Long): Float {
        val progress = time.toFloat() / duration.toFloat()
        return -change * progress * (progress - 2) + from
    }

    private fun easeInOutQuad(time: Long, from: Float, change: Float, duration: Long): Float {
        var timeF = time / (duration / 2f)
        return if (timeF < 1) {
            change / 2f * timeF * timeF + from
        } else {
            timeF--
            -change / 2f * (timeF * (timeF - 2) - 1) + from
        }
    }

    private fun debug(message: String) {
        if (debug) {
            Log.d(TAG, message)
        }
    }

    private fun px(px: Int) = (density * px).toInt()

    fun setMinimumDpi(dpi: Int) {
        val metrics = resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
        maxScale = averageDpi / dpi
    }

    fun setMaximumDpi(dpi: Int) {
        val metrics = resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
        minScale = averageDpi / dpi
    }

    fun setMinimumTileDpi(minimumTileDpi: Int) {
        val metrics = resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
        this.minimumTileDpi = Math.min(averageDpi, minimumTileDpi.toFloat()).toInt()
        if (isReady) {
            reset(false)
            invalidate()
        }
    }

    fun setScaleAndCenter(scale: Float, sCenter: PointF?) {
        anim = null
        pendingScale = scale
        sPendingCenter = sCenter
        sRequestedCenter = sCenter
        invalidate()
    }

    fun resetScaleAndCenter() {
        anim = null
        pendingScale = limitedScale(0f)
        sPendingCenter = if (isReady) {
            PointF((sWidth() / 2).toFloat(), (sHeight() / 2).toFloat())
        } else {
            PointF(0f, 0f)
        }
        invalidate()
    }

    protected fun onReady() {}

    protected fun onImageLoaded() {}

    fun setDoubleTapZoomDpi(dpi: Int) {
        val metrics = resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
        doubleTapZoomScale = averageDpi / dpi
    }

    inner class AnimationBuilder {
        private val targetScale: Float
        private val targetSCenter: PointF?
        private val vFocus: PointF?
        private val duration = DOUBLE_TAP_ZOOM_DURATION
        var easing = EASE_IN_OUT_QUAD
        var interruptible = false

        constructor(sCenter: PointF) {
            targetScale = scale
            targetSCenter = sCenter
            vFocus = null
        }

        constructor(scale: Float, sCenter: PointF) {
            targetScale = scale
            targetSCenter = sCenter
            vFocus = null
        }

        constructor(scale: Float, sCenter: PointF, vFocus: PointF) {
            targetScale = scale
            targetSCenter = sCenter
            this.vFocus = vFocus
        }

        fun start() {
            val vxCenter = paddingLeft + (width - paddingRight - paddingLeft) / 2
            val vyCenter = paddingTop + (height - paddingBottom - paddingTop) / 2
            val targetScale = limitedScale(targetScale)
            val targetSCenter = limitedSCenter(targetSCenter!!.x, targetSCenter.y, targetScale, PointF())
            anim = Anim().apply {
                scaleStart = scale
                scaleEnd = targetScale
                time = System.currentTimeMillis()
                sCenterEndRequested = targetSCenter
                sCenterStart = getCenter()
                sCenterEnd = targetSCenter
                vFocusStart = sourceToViewCoord(targetSCenter)
                vFocusEnd = PointF(
                        vxCenter.toFloat(),
                        vyCenter.toFloat()
                )
                time = System.currentTimeMillis()
            }

            anim!!.duration = duration
            anim!!.interruptible = interruptible
            anim!!.easing = easing

            if (vFocus != null) {
                val vTranslateXEnd = vFocus.x - targetScale * anim!!.sCenterStart!!.x
                val vTranslateYEnd = vFocus.y - targetScale * anim!!.sCenterStart!!.y
                val satEnd = ScaleAndTranslate(targetScale, PointF(vTranslateXEnd, vTranslateYEnd))
                fitToBounds(true, satEnd)
                anim!!.vFocusEnd = PointF(
                        vFocus.x + (satEnd.vTranslate.x - vTranslateXEnd),
                        vFocus.y + (satEnd.vTranslate.y - vTranslateYEnd)
                )
            }

            invalidate()
        }
    }

    class Anim {
        var scaleStart = 0f
        var scaleEnd = 0f
        var sCenterStart: PointF? = null
        var sCenterEnd: PointF? = null
        var sCenterEndRequested: PointF? = null
        var vFocusStart: PointF? = null
        var vFocusEnd: PointF? = null
        var duration = 500L
        var interruptible = true
        var easing = EASE_IN_OUT_QUAD
        var time = System.currentTimeMillis()
    }

    interface OnImageEventListener {
        fun onReady()
        fun onImageLoaded()
        fun onImageLoadError(e: Exception)
        fun onTileLoadError(e: Exception)
    }
}

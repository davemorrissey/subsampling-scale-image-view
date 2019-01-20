package com.davemorrissey.labs.subscaleview

import android.content.ContentResolver
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
import com.davemorrissey.labs.subscaleview.decoder.*
import com.davemorrissey.labs.subscaleview.decoder.ImageDecoder
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantReadWriteLock

open class SubsamplingScaleImageView @JvmOverloads constructor(context: Context, attr: AttributeSet? = null) : View(context, attr) {
    companion object {
        private val TAG = SubsamplingScaleImageView::class.java.simpleName

        const val ORIENTATION_USE_EXIF = -1
        const val ORIENTATION_0 = 0
        const val ORIENTATION_90 = 90
        const val ORIENTATION_180 = 180
        const val ORIENTATION_270 = 270

        private val VALID_ORIENTATIONS = Arrays.asList(ORIENTATION_0, ORIENTATION_90, ORIENTATION_180, ORIENTATION_270, ORIENTATION_USE_EXIF)

        const val EASE_OUT_QUAD = 1
        const val EASE_IN_OUT_QUAD = 2

        private val VALID_EASING_STYLES = Arrays.asList(EASE_IN_OUT_QUAD, EASE_OUT_QUAD)

        const val ORIGIN_ANIM = 1
        const val ORIGIN_FLING = 2
        const val ORIGIN_DOUBLE_TAP_ZOOM = 3

        const val TILE_SIZE_AUTO = Integer.MAX_VALUE
        private const val DOUBLE_TAP_ZOOM_DURATION = 300L

        var preferredBitmapConfig: Bitmap.Config? = null
    }

    var maxScale = 2f
    var isOneToOneZoomEnabled = false
    var isQuickScaleEnabled = true
    private var bitmap: Bitmap? = null
    private var bitmapIsPreview = false
    private var bitmapIsCached = false
    private var uri: Uri? = null
    private var fullImageSampleSize = 0
    private var tileMap: MutableMap<Int, List<Tile>>? = null
    private var debug = false
    private var orientation = ORIENTATION_0
    private var minScale = minScale()
    private var minimumTileDpi = -1
    private var maxTileWidth = TILE_SIZE_AUTO
    private var maxTileHeight = TILE_SIZE_AUTO
    private var executor = AsyncTask.THREAD_POOL_EXECUTOR
    private var eagerLoadingEnabled = true
    private var isZoomEnabled = true
    private var doubleTapZoomScale = 1f
    private var scale = 0f
    private var scaleStart = 0f

    private var vTranslate: PointF? = null
    private var vTranslateStart: PointF? = null
    private var vTranslateBefore: PointF? = null

    private var pendingScale: Float? = null
    private var sPendingCenter: PointF? = null
    private var sRequestedCenter: PointF? = null

    private var sWidth = 0
    private var sHeight = 0
    private var sOrientation = 0
    private var sRegion: Rect? = null
    private var pRegion: Rect? = null

    private var isZooming = false
    private var isPanning = false
    private var isQuickScaling = false
    private var maxTouchCount = 0
    private var resetScaleOnSizeChange = false

    private var detector: GestureDetector? = null
    private var singleDetector: GestureDetector? = null

    private var decoder: ImageRegionDecoder? = null
    private val decoderLock = ReentrantReadWriteLock(true)
    private var bitmapDecoderFactory: DecoderFactory<out ImageDecoder> = CompatDecoderFactory(SkiaImageDecoder::class.java)
    private var regionDecoderFactory: DecoderFactory<out ImageRegionDecoder> = CompatDecoderFactory(SkiaImageRegionDecoder::class.java)

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
    private var onImageEventListener: OnImageEventListener? = null

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
        if (bitmap != null && !bitmapIsPreview) {
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

    fun setOrientation(orientation: Int) {
        if (!VALID_ORIENTATIONS.contains(orientation)) {
            throw IllegalArgumentException("Invalid orientation: $orientation")
        }
        this.orientation = orientation
        reset(false)
        invalidate()
        requestLayout()
    }

    fun setImage(imageSource: ImageSource) {
        setImage(imageSource, null)
    }

    fun setImage(imageSource: ImageSource, previewSource: ImageSource? = null) {
        reset(true)

        if (previewSource != null) {
            if (imageSource.bitmap != null) {
                throw IllegalArgumentException("Preview image cannot be used when a bitmap is provided for the main image")
            }
            if (imageSource.sWidth <= 0 || imageSource.sHeight <= 0) {
                throw IllegalArgumentException("Preview image cannot be used unless dimensions are provided for the main image")
            }
            sWidth = imageSource.sWidth
            sHeight = imageSource.sHeight
            pRegion = previewSource.sRegion
            if (previewSource.bitmap != null) {
                bitmapIsCached = previewSource.isCached
                onPreviewLoaded(previewSource.bitmap)
            } else {
                var uri = previewSource.uri
                if (uri == null && previewSource.resource != null) {
                    uri = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${previewSource.resource}")
                }
                val task = BitmapLoadTask(this, context, bitmapDecoderFactory, uri!!, true)
                execute(task)
            }
        }

        if (imageSource.bitmap != null && imageSource.sRegion != null) {
            onImageLoaded(Bitmap.createBitmap(imageSource.bitmap, imageSource.sRegion!!.left, imageSource.sRegion!!.top, imageSource.sRegion!!.width(), imageSource.sRegion!!.height()), ORIENTATION_0, false)
        } else if (imageSource.bitmap != null) {
            onImageLoaded(imageSource.bitmap, ORIENTATION_0, imageSource.isCached)
        } else {
            sRegion = imageSource.sRegion
            uri = imageSource.uri
            if (uri == null && imageSource.resource != null) {
                uri = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${imageSource.resource}")
            }
            if (imageSource.tile || sRegion != null) {
                val task = TilesInitTask(this, context, regionDecoderFactory, uri!!)
                execute(task)
            } else {
                val task = BitmapLoadTask(this, context, bitmapDecoderFactory, uri!!, false)
                execute(task)
            }
        }
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

            if (!bitmapIsCached) {
                bitmap?.recycle()
            }

            if (bitmap != null && bitmapIsCached) {
                onImageEventListener?.onPreviewReleased()
            }

            sWidth = 0
            sHeight = 0
            sOrientation = 0
            sRegion = null
            pRegion = null
            isReady = false
            isImageLoaded = false
            bitmap = null
            bitmapIsPreview = false
            bitmapIsCached = false
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
                    AnimationBuilder(PointF(sCenterXEnd, sCenterYEnd)).withEasing(EASE_OUT_QUAD).withOrigin(ORIGIN_FLING).start()
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
                AnimationBuilder(targetScale, sCenter!!, vFocus!!).withInterruptible(false).withDuration(DOUBLE_TAP_ZOOM_DURATION).withOrigin(ORIGIN_DOUBLE_TAP_ZOOM).start()
            } else {
                AnimationBuilder(targetScale, sCenter!!).withInterruptible(false).withDuration(DOUBLE_TAP_ZOOM_DURATION).withOrigin(ORIGIN_DOUBLE_TAP_ZOOM).start()
            }
        } else {
            val targetScale = if (zoomIn && scale != 1f) doubleTapZoomScale else minScale()

            if (scale != 1f) {
                if (zoomIn) {
                    AnimationBuilder(targetScale, sCenter!!, vFocus!!).withInterruptible(false).withDuration(DOUBLE_TAP_ZOOM_DURATION).withOrigin(ORIGIN_DOUBLE_TAP_ZOOM).start()
                } else {
                    AnimationBuilder(1f, sCenter!!, vFocus!!).withInterruptible(false).withDuration(DOUBLE_TAP_ZOOM_DURATION).withOrigin(ORIGIN_DOUBLE_TAP_ZOOM).start()
                }
            } else {
                AnimationBuilder(targetScale, sCenter!!).withInterruptible(false).withDuration(DOUBLE_TAP_ZOOM_DURATION).withOrigin(ORIGIN_DOUBLE_TAP_ZOOM).start()
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
            if (bitmapIsPreview) {
                xScale = scale * (sWidth.toFloat() / bitmap!!.width)
                yScale = scale * (sHeight.toFloat() / bitmap!!.height)
            }

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

        // Load double resolution - next level will be split into four tiles and at the center all four are required,
        // so don't bother with tiling until the next level 16 tiles are needed.
        fullImageSampleSize = calculateInSampleSize(satTemp!!.scale)
        if (fullImageSampleSize > 1) {
            fullImageSampleSize /= 2
        }

        if (uri == null) {
            return
        }

        if (fullImageSampleSize == 1 && sRegion == null && sWidth() < maxTileDimensions.x && sHeight() < maxTileDimensions.y) {
            // Whole image is required at native resolution, and is smaller than the canvas max bitmap size.
            // Use BitmapDecoder for better image support.
            decoder!!.recycle()
            decoder = null
            val task = BitmapLoadTask(this, context, bitmapDecoderFactory, uri!!, false)
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

    /**
     * Loads the optimum tiles for display at the current scale and translate, so the screen can be filled with tiles
     * that are at least as high resolution as the screen. Frees up bitmaps that are now off the screen.
     * @param load Whether to load the new tiles needed. Use false while scrolling/panning for performance.
     */
    private fun refreshRequiredTiles(load: Boolean) {
        if (decoder == null || tileMap == null) {
            return
        }

        val sampleSize = Math.min(fullImageSampleSize, calculateInSampleSize(scale))

        // Load tiles of the correct sample size that are on screen. Discard tiles off screen, and those that are higher
        // resolution than required, or lower res than required but not the base layer, so the base layer is always present.
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

    /**
     * Determine whether tile is visible.
     */
    private fun tileVisible(tile: Tile): Boolean {
        val sVisLeft = viewToSourceX(0f)
        val sVisRight = viewToSourceX(width.toFloat())
        val sVisTop = viewToSourceY(0f)
        val sVisBottom = viewToSourceY(height.toFloat())
        return !(sVisLeft > tile.sRect!!.right || tile.sRect!!.left > sVisRight || sVisTop > tile.sRect!!.bottom || tile.sRect!!.top > sVisBottom)
    }

    /**
     * Sets scale and translate ready for the next draw.
     */
    private fun preDraw() {
        if (width == 0 || height == 0 || sWidth <= 0 || sHeight <= 0) {
            return
        }

        // If waiting to translate to new center position, set translate now
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

        // On first display of base image set up position, and in other cases make sure scale is correct.
        fitToBounds(false)
    }

    /**
     * Calculates sample size to fit the source image in given bounds.
     */
    private fun calculateInSampleSize(scale: Float): Int {
        var newScale = scale
        if (minimumTileDpi > 0) {
            val metrics = resources.displayMetrics
            val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
            newScale *= minimumTileDpi / averageDpi
        }

        val reqWidth = (sWidth() * newScale).toInt()
        val reqHeight = (sHeight() * newScale).toInt()

        // Raw height and width of image
        var inSampleSize = 1
        if (reqWidth == 0 || reqHeight == 0) {
            return 32
        }

        if (sHeight() > reqHeight || sWidth() > reqWidth) {
            // Calculate ratios of height and width to requested height and width
            val heightRatio = Math.round(sHeight().toFloat() / reqHeight.toFloat())
            val widthRatio = Math.round(sWidth().toFloat() / reqWidth.toFloat())

            // Choose the smallest ratio as inSampleSize value, this will guarantee
            // a final image with both dimensions larger than or equal to the
            // requested height and width.
            inSampleSize = if (heightRatio < widthRatio) heightRatio else widthRatio
        }

        // We want the actual sample size that will be used, so round down to nearest power of 2.
        var power = 1
        while (power * 2 < inSampleSize) {
            power *= 2
        }

        return power
    }

    /**
     * Adjusts hypothetical future scale and translate values to keep scale within the allowed range and the image on screen. Minimum scale
     * is set so one dimension fills the view and the image is centered on the other dimension. Used to calculate what the target of an
     * animation should be.
     * @param center Whether the image should be centered in the dimension it's too small to fill. While animating this can be false to avoid changes in direction as bounds are reached.
     * @param sat The scale we want and the translation we're aiming for. The values are adjusted to be valid.
     */
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

        // Asymmetric padding adjustments
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

    /**
     * Adjusts current scale and translate values to keep scale within the allowed range and the image on screen. Minimum scale
     * is set so one dimension fills the view and the image is centered on the other dimension.
     * @param center Whether the image should be centered in the dimension it's too small to fill. While animating this can be false to avoid changes in direction as bounds are reached.
     */
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

    /**
     * Once source image and view dimensions are known, creates a map of sample size to tile grid.
     */
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

    /**
     * Async task used to get image details without blocking the UI thread.
     */
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
                    var sWidth = dimensions.x
                    var sHeight = dimensions.y
                    val exifOrientation = view.orientation
                    view.sRegion?.apply {
                        left = Math.max(0, left)
                        top = Math.max(0, top)
                        right = Math.min(sWidth, right)
                        bottom = Math.min(sHeight, bottom)
                        sWidth = width()
                        sHeight = height()
                    }
                    return intArrayOf(sWidth, sHeight, exifOrientation)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialise bitmap decoder", e)
                this.exception = e
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

    /**
     * Called by worker task when decoder is ready and image size and EXIF orientation is known.
     */
    @Synchronized
    private fun onTilesInited(decoder: ImageRegionDecoder, sWidth: Int, sHeight: Int, sOrientation: Int) {
        debug("onTilesInited sWidth=$sWidth, sHeight=$sHeight, sOrientation=$orientation")
        // If actual dimensions don't match the declared size, reset everything.
        if (this.sWidth > 0 && this.sHeight > 0 && (this.sWidth != sWidth || this.sHeight != sHeight)) {
            reset(false)
            if (bitmap != null) {
                if (!bitmapIsCached) {
                    bitmap!!.recycle()
                }
                bitmap = null
                if (bitmapIsCached) {
                    onImageEventListener?.onPreviewReleased()
                }

                bitmapIsPreview = false
                bitmapIsCached = false
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

    /**
     * Async task used to load images without blocking the UI thread.
     */
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
                            // Update tile's file sRect according to rotation
                            view.fileSRect(tile.sRect, tile.fileSRect)
                            if (view.sRegion != null) {
                                tile.fileSRect!!.offset(view.sRegion!!.left, view.sRegion!!.top)
                            }
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
                Log.e(TAG, "Failed to decode tile", e)
                exception = e
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Failed to decode tile - OutOfMemoryError", e)
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

    /**
     * Called by worker task when a tile has loaded. Redraws the view.
     */
    @Synchronized
    private fun onTileLoaded() {
        debug("onTileLoaded")
        checkReady()
        checkImageLoaded()
        if (getIsBaseLayerReady() && bitmap != null) {
            if (!bitmapIsCached) {
                bitmap!!.recycle()
            }
            bitmap = null

            if (bitmapIsCached) {
                onImageEventListener?.onPreviewReleased()
            }
            bitmapIsPreview = false
            bitmapIsCached = false
        }
        invalidate()
    }

    /**
     * Async task used to load bitmap without blocking the UI thread.
     */
    private class BitmapLoadTask internal constructor(view: SubsamplingScaleImageView, context: Context, decoderFactory: DecoderFactory<out ImageDecoder>, private val source: Uri, private val preview: Boolean) : AsyncTask<Void, Void, Int>() {
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
                Log.e(TAG, "Failed to load bitmap - OutOfMemoryError", e)
                exception = RuntimeException(e)
            }

            return null
        }

        override fun onPostExecute(orientation: Int?) {
            val subsamplingScaleImageView = viewRef.get()
            if (bitmap != null && orientation != null) {
                if (preview) {
                    subsamplingScaleImageView?.onPreviewLoaded(bitmap)
                } else {
                    subsamplingScaleImageView?.onImageLoaded(bitmap, orientation, false)
                }
            } else if (exception != null) {
                if (preview) {
                    subsamplingScaleImageView?.onImageEventListener?.onPreviewLoadError(exception!!)
                } else {
                    subsamplingScaleImageView?.onImageEventListener?.onImageLoadError(exception!!)
                }
            }
        }
    }

    /**
     * Called by worker task when preview image is loaded.
     */
    @Synchronized
    private fun onPreviewLoaded(previewBitmap: Bitmap?) {
        debug("onPreviewLoaded")
        if (bitmap != null || isImageLoaded) {
            previewBitmap!!.recycle()
            return
        }

        bitmap = if (pRegion != null) {
            Bitmap.createBitmap(previewBitmap!!, pRegion!!.left, pRegion!!.top, pRegion!!.width(), pRegion!!.height())
        } else {
            previewBitmap
        }

        bitmapIsPreview = true
        if (checkReady()) {
            invalidate()
            requestLayout()
        }
    }

    /**
     * Called by worker task when full size image bitmap is ready (tiling is disabled).
     */
    @Synchronized
    private fun onImageLoaded(bitmap: Bitmap?, sOrientation: Int, bitmapIsCached: Boolean) {
        debug("onImageLoaded")
        // If actual dimensions don't match the declared size, reset everything.
        if (sWidth > 0 && sHeight > 0 && (sWidth != bitmap!!.width || sHeight != bitmap.height)) {
            reset(false)
        }

        if (!this.bitmapIsCached) {
            this.bitmap?.recycle()
        }

        if (this.bitmap != null && this.bitmapIsCached) {
            onImageEventListener?.onPreviewReleased()
        }

        this.bitmapIsCached = bitmapIsCached
        this.bitmap = bitmap
        bitmapIsPreview = false
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

        // Volatile fields instantiated once then updated before use to reduce GC.
        var vRect: Rect? = null
        var fileSRect: Rect? = null
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
        var origin = ORIGIN_ANIM
        var time = System.currentTimeMillis()
    }

    class ScaleAndTranslate constructor(var scale: Float, var vTranslate: PointF)

    /**
     * By default the View automatically calculates the optimal tile size. Set this to override this, and force an upper limit to the dimensions of the generated tiles. Passing [.TILE_SIZE_AUTO] will re-enable the default behaviour.
     *
     * @param maxPixels Maximum tile size X and Y in pixels.
     */
    fun setMaxTileSize(maxPixels: Int) {
        maxTileWidth = maxPixels
        maxTileHeight = maxPixels
    }

    /**
     * By default the View automatically calculates the optimal tile size. Set this to override this, and force an upper limit to the dimensions of the generated tiles. Passing [.TILE_SIZE_AUTO] will re-enable the default behaviour.
     *
     * @param maxPixelsX Maximum tile width.
     * @param maxPixelsY Maximum tile height.
     */
    fun setMaxTileSize(maxPixelsX: Int, maxPixelsY: Int) {
        maxTileWidth = maxPixelsX
        maxTileHeight = maxPixelsY
    }

    /**
     * Use canvas max bitmap width and height instead of the default 2048, to avoid redundant tiling.
     */
    private fun getMaxBitmapDimensions(canvas: Canvas) = Point(Math.min(canvas.maximumBitmapWidth, maxTileWidth), Math.min(canvas.maximumBitmapHeight, maxTileHeight))

    /**
     * Get source width taking rotation into account.
     */
    private fun sWidth(): Int {
        val rotation = getRequiredRotation()
        return if (rotation == 90 || rotation == 270) {
            sHeight
        } else {
            sWidth
        }
    }

    /**
     * Get source height taking rotation into account.
     */
    private fun sHeight(): Int {
        val rotation = getRequiredRotation()
        return if (rotation == 90 || rotation == 270) {
            sWidth
        } else {
            sHeight
        }
    }

    /**
     * Converts source rectangle from tile, which treats the image file as if it were in the correct orientation already,
     * to the rectangle of the image that needs to be loaded.
     */
    private fun fileSRect(sRect: Rect?, target: Rect?) {
        when (getRequiredRotation()) {
            0 -> target!!.set(sRect)
            90 -> target!!.set(sRect!!.top, sHeight - sRect.right, sRect.bottom, sHeight - sRect.left)
            180 -> target!!.set(sWidth - sRect!!.right, sHeight - sRect.bottom, sWidth - sRect.left, sHeight - sRect.top)
            else -> target!!.set(sWidth - sRect!!.bottom, sRect.left, sWidth - sRect.top, sRect.right)
        }
    }

    /**
     * Pythagoras distance between two points.
     */
    private fun distance(x0: Float, x1: Float, y0: Float, y1: Float): Float {
        val x = x0 - x1
        val y = y0 - y1
        return Math.sqrt((x * x + y * y).toDouble()).toFloat()
    }

    /**
     * Releases all resources the view is using and resets the state, nulling any fields that use significant memory.
     * After you have called this method, the view can be re-used by setting a new image. Settings are remembered
     * but state (scale and center) is forgotten. You can restore these yourself if required.
     */
    fun recycle() {
        reset(true)
        bitmapPaint = null
        debugTextPaint = null
        debugLinePaint = null
    }

    /**
     * Convert screen to source x coordinate.
     */
    private fun viewToSourceX(vx: Float): Float {
        return if (vTranslate == null) {
            Float.NaN
        } else {
            (vx - vTranslate!!.x) / scale
        }
    }

    /**
     * Convert screen to source y coordinate.
     */
    private fun viewToSourceY(vy: Float): Float {
        return if (vTranslate == null) {
            Float.NaN
        } else {
            (vy - vTranslate!!.y) / scale
        }
    }

    /**
     * Convert screen coordinate to source coordinate.
     * @param vxy view X/Y coordinate.
     * @return a coordinate representing the corresponding source coordinate.
     */
    fun viewToSourceCoord(vxy: PointF) = viewToSourceCoord(vxy.x, vxy.y, PointF())

    /**
     * Convert screen coordinate to source coordinate.
     * @param vx view X coordinate.
     * @param vy view Y coordinate.
     * @param sTarget target object for result. The same instance is also returned.
     * @return source coordinates. This is the same instance passed to the sTarget param.
     */
    @JvmOverloads
    fun viewToSourceCoord(vx: Float, vy: Float, sTarget: PointF = PointF()): PointF? {
        if (vTranslate == null) {
            return null
        }

        sTarget.set(viewToSourceX(vx), viewToSourceY(vy))
        return sTarget
    }

    /**
     * Convert source to view x coordinate.
     */
    private fun sourceToViewX(sx: Float): Float {
        return if (vTranslate == null) {
            Float.NaN
        } else {
            sx * scale + vTranslate!!.x
        }
    }

    /**
     * Convert source to view y coordinate.
     */
    private fun sourceToViewY(sy: Float): Float {
        return if (vTranslate == null) {
            Float.NaN
        } else {
            sy * scale + vTranslate!!.y
        }
    }

    /**
     * Convert source coordinate to view coordinate.
     * @param sxy source coordinates to convert.
     * @return view coordinates.
     */
    fun sourceToViewCoord(sxy: PointF) = sourceToViewCoord(sxy.x, sxy.y, PointF())

    /**
     * Convert source coordinate to view coordinate.
     * @param sxy source coordinates to convert.
     * @param vTarget target object for result. The same instance is also returned.
     * @return view coordinates. This is the same instance passed to the vTarget param.
     */
    fun sourceToViewCoord(sxy: PointF, vTarget: PointF) = sourceToViewCoord(sxy.x, sxy.y, vTarget)

    /**
     * Convert source coordinate to view coordinate.
     * @param sx source X coordinate.
     * @param sy source Y coordinate.
     * @param vTarget target object for result. The same instance is also returned.
     * @return view coordinates. This is the same instance passed to the vTarget param.
     */
    @JvmOverloads
    fun sourceToViewCoord(sx: Float, sy: Float, vTarget: PointF = PointF()): PointF? {
        if (vTranslate == null) {
            return null
        }

        vTarget.set(sourceToViewX(sx), sourceToViewY(sy))
        return vTarget
    }

    /**
     * Convert source rect to screen rect, integer values.
     */
    private fun sourceToViewRect(sRect: Rect, vTarget: Rect) {
        vTarget.set(
                sourceToViewX(sRect.left.toFloat()).toInt(),
                sourceToViewY(sRect.top.toFloat()).toInt(),
                sourceToViewX(sRect.right.toFloat()).toInt(),
                sourceToViewY(sRect.bottom.toFloat()).toInt()
        )
    }

    /**
     * Get the translation required to place a given source coordinate at the center of the screen, with the center
     * adjusted for asymmetric padding. Accepts the desired scale as an argument, so this is independent of current
     * translate and scale. The result is fitted to bounds, putting the image point as near to the screen center as permitted.
     */
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

    /**
     * Given a requested source center and scale, calculate what the actual center will have to be to keep the image in
     * pan limits, keeping the requested center as near to the middle of the screen as allowed.
     */
    private fun limitedSCenter(sCenterX: Float, sCenterY: Float, scale: Float, sTarget: PointF): PointF {
        val vTranslate = vTranslateForSCenter(sCenterX, sCenterY, scale)
        val vxCenter = paddingLeft + (width - paddingRight - paddingLeft) / 2
        val vyCenter = paddingTop + (height - paddingBottom - paddingTop) / 2
        val sx = (vxCenter - vTranslate.x) / scale
        val sy = (vyCenter - vTranslate.y) / scale
        sTarget.set(sx, sy)
        return sTarget
    }

    /**
     * Returns the minimum allowed scale.
     */
    private fun minScale(): Float {
        val vPadding = paddingBottom + paddingTop
        val hPadding = paddingLeft + paddingRight
        return Math.min((width - hPadding) / sWidth().toFloat(), (height - vPadding) / sHeight().toFloat())
    }

    /**
     * Adjust a requested scale to be within the allowed limits.
     */
    private fun limitedScale(targetScale: Float): Float {
        var newTargetScale = targetScale
        newTargetScale = Math.max(minScale(), newTargetScale)
        newTargetScale = Math.min(maxScale, newTargetScale)
        return newTargetScale
    }

    /**
     * Apply a selected type of easing.
     * @param type Easing type, from static fields
     * @param time Elapsed time
     * @param from Start value
     * @param change Target value
     * @param duration Anm duration
     * @return Current value
     */
    private fun ease(type: Int, time: Long, from: Float, change: Float, duration: Long): Float {
        return when (type) {
            EASE_IN_OUT_QUAD -> easeInOutQuad(time, from, change, duration)
            EASE_OUT_QUAD -> easeOutQuad(time, from, change, duration)
            else -> throw IllegalStateException("Unexpected easing type: $type")
        }
    }

    /**
     * Quadratic easing for fling. With thanks to Robert Penner - http://gizma.com/easing/
     * @param time Elapsed time
     * @param from Start value
     * @param change Target value
     * @param duration Anm duration
     * @return Current value
     */
    private fun easeOutQuad(time: Long, from: Float, change: Float, duration: Long): Float {
        val progress = time.toFloat() / duration.toFloat()
        return -change * progress * (progress - 2) + from
    }

    /**
     * Quadratic easing for scale and center animations. With thanks to Robert Penner - http://gizma.com/easing/
     * @param time Elapsed time
     * @param from Start value
     * @param change Target value
     * @param duration Anm duration
     * @return Current value
     */
    private fun easeInOutQuad(time: Long, from: Float, change: Float, duration: Long): Float {
        var timeF = time / (duration / 2f)
        return if (timeF < 1) {
            change / 2f * timeF * timeF + from
        } else {
            timeF--
            -change / 2f * (timeF * (timeF - 2) - 1) + from
        }
    }

    /**
     * Debug logger
     */
    private fun debug(message: String) {
        if (debug) {
            Log.d(TAG, message)
        }
    }

    /**
     * For debug overlays. Scale pixel value according to screen density.
     */
    private fun px(px: Int) = (density * px).toInt()

    /**
     * Swap the default region decoder implementation for one of your own. You must do this before setting the image file or
     * asset, and you cannot use a custom decoder when using layout XML to set an asset name.
     * @param regionDecoderFactory The [DecoderFactory] implementation that produces [ImageRegionDecoder]
     * instances.
     */
    fun setRegionDecoderFactory(regionDecoderFactory: DecoderFactory<ImageRegionDecoder>) {
        this.regionDecoderFactory = regionDecoderFactory
    }

    /**
     * Swap the default bitmap decoder implementation for one of your own. You must do this before setting the image file or
     * asset, and you cannot use a custom decoder when using layout XML to set an asset name.
     * @param bitmapDecoderFactory The [DecoderFactory] implementation that produces [ImageDecoder] instances.
     */
    fun setBitmapDecoderFactory(bitmapDecoderFactory: DecoderFactory<out ImageDecoder>) {
        this.bitmapDecoderFactory = bitmapDecoderFactory
    }

    /**
     * Set the minimum scale allowed. A value of 1 means 1:1 pixels at minimum scale. You may wish to set this according
     * to screen density. Consider using [.setMaximumDpi], which is density aware.
     * @param minScale minimum scale expressed as a source/view pixels ratio.
     */
    fun setMinScale(minScale: Float) {
        this.minScale = minScale
    }

    /**
     * This is a screen density aware alternative to [.setMaxScale]; it allows you to express the maximum
     * allowed scale in terms of the minimum pixel density. This avoids the problem of 1:1 scale still being
     * too small on a high density screen. A sensible starting point is 160 - the default used by this view.
     * @param dpi Source image pixel density at maximum zoom.
     */
    fun setMinimumDpi(dpi: Int) {
        val metrics = resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
        maxScale = averageDpi / dpi
    }

    /**
     * This is a screen density aware alternative to [.setMinScale]; it allows you to express the minimum
     * allowed scale in terms of the maximum pixel density.
     * @param dpi Source image pixel density at minimum zoom.
     */
    fun setMaximumDpi(dpi: Int) {
        val metrics = resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
        setMinScale(averageDpi / dpi)
    }

    /**
     * Returns the minimum allowed scale.
     * @return the minimum scale as a source/view pixels ratio.
     */
    fun getMinScale() = minScale()

    /**
     * By default, image tiles are at least as high resolution as the screen. For a retina screen this may not be
     * necessary, and may increase the likelihood of an OutOfMemoryError. This method sets a DPI at which higher
     * resolution tiles should be loaded. Using a lower number will on average use less memory but result in a lower
     * quality image. 160-240dpi will usually be enough. This should be called before setting the image source,
     * because it affects which tiles get loaded. When using an untiled source image this method has no effect.
     * @param minimumTileDpi Tile loading threshold.
     */
    fun setMinimumTileDpi(minimumTileDpi: Int) {
        val metrics = resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
        this.minimumTileDpi = Math.min(averageDpi, minimumTileDpi.toFloat()).toInt()
        if (isReady) {
            reset(false)
            invalidate()
        }
    }

    /**
     * Externally change the scale and translation of the source image. This may be used with getCenter() and getScale()
     * to restore the scale and zoom after a screen rotate.
     * @param scale New scale to set.
     * @param sCenter New source image coordinate to center on the screen, subject to boundaries.
     */
    fun setScaleAndCenter(scale: Float, sCenter: PointF?) {
        anim = null
        pendingScale = scale
        sPendingCenter = sCenter
        sRequestedCenter = sCenter
        invalidate()
    }

    /**
     * Fully zoom out and return the image to the middle of the screen. This might be useful if you have a view pager
     * and want images to be reset when the user has moved to another page.
     */
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

    /**
     * Called once when the view is initialised, has dimensions, and will display an image on the
     * next draw. This is triggered at the same time as [OnImageEventListener.onReady] but
     * allows a subclass to receive this event without using a listener.
     */
    protected fun onReady() {}

    /**
     * Called once when the full size image or its base layer tiles have been loaded.
     */
    protected fun onImageLoaded() {}

    /**
     * Returns the orientation setting. This can return [.ORIENTATION_USE_EXIF], in which case it doesn't tell you
     * the applied orientation of the image. For that, use [.getAppliedOrientation].
     * @return the orientation setting. See static fields.
     */
    fun getOrientation() = orientation

    /**
     * Set the scale the image will zoom in to when double tapped. This also the scale point where a double tap is interpreted
     * as a zoom out gesture - if the scale is greater than 90% of this value, a double tap zooms out. Avoid using values
     * greater than the max zoom.
     * @param doubleTapZoomScale New value for double tap gesture zoom scale.
     */
    fun setDoubleTapZoomScale(doubleTapZoomScale: Float) {
        this.doubleTapZoomScale = doubleTapZoomScale
    }

    /**
     * A density aware alternative to [.setDoubleTapZoomScale]; this allows you to express the scale the
     * image will zoom in to when double tapped in terms of the image pixel density. Values lower than the max scale will
     * be ignored. A sensible starting point is 160 - the default used by this view.
     * @param dpi New value for double tap gesture zoom scale.
     */
    fun setDoubleTapZoomDpi(dpi: Int) {
        val metrics = resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
        setDoubleTapZoomScale(averageDpi / dpi)
    }

    fun setResetScaleOnSizeChange(resetScaleOnSizeChange: Boolean) {
        this.resetScaleOnSizeChange = resetScaleOnSizeChange
    }

    /**
     *
     *
     * Provide an [Executor] to be used for loading images. By default, [AsyncTask.THREAD_POOL_EXECUTOR]
     * is used to minimise contention with other background work the app is doing. You can also choose
     * to use [AsyncTask.SERIAL_EXECUTOR] if you want to limit concurrent background tasks.
     * Alternatively you can supply an [Executor] of your own to avoid any contention. It is
     * strongly recommended to use a single executor instance for the life of your application, not
     * one per view instance.
     *
     *
     * **Warning:** If you are using a custom implementation of [ImageRegionDecoder], and you
     * supply an executor with more than one thread, you must make sure your implementation supports
     * multi-threaded bitmap decoding or has appropriate internal synchronization. From SDK 21, Android's
     * [android.graphics.BitmapRegionDecoder] uses an internal lock so it is thread safe but
     * there is no advantage to using multiple threads.
     *
     * @param executor an [Executor] for image loading.
     */
    fun setExecutor(executor: Executor) {
        this.executor = executor
    }

    /**
     * Enable or disable eager loading of tiles that appear on screen during gestures or animations,
     * while the gesture or animation is still in progress. By default this is enabled to improve
     * responsiveness, but it can result in tiles being loaded and discarded more rapidly than
     * necessary and reduce the animation frame rate on old/cheap devices. Disable this on older
     * devices if you see poor performance. Tiles will then be loaded only when gestures and animations
     * are completed.
     * @param eagerLoadingEnabled true to enable loading during gestures, false to delay loading until gestures end
     */
    fun setEagerLoadingEnabled(eagerLoadingEnabled: Boolean) {
        this.eagerLoadingEnabled = eagerLoadingEnabled
    }

    /**
     * Enables visual debugging, showing tile boundaries and sizes.
     * @param debug true to enable debugging, false to disable.
     */
    fun setDebug(debug: Boolean) {
        this.debug = debug
    }

    /**
     * Add a listener allowing notification of load and error events. Extend [DefaultOnImageEventListener]
     * to simplify implementation.
     * @param onImageEventListener an [OnImageEventListener] instance.
     */
    fun setOnImageEventListener(onImageEventListener: OnImageEventListener) {
        this.onImageEventListener = onImageEventListener
    }

    /**
     * Creates a scale animation builder, that when started will animate a zoom in or out. If this would move the image
     * beyond the panning limits, the image is automatically panned during the animation.
     * @param scale Target scale.
     * @param sCenter Target source center.
     * @return [AnimationBuilder] instance. Call [SubsamplingScaleImageView.AnimationBuilder.start] to start the anim.
     */
    fun animateScaleAndCenter(scale: Float, sCenter: PointF): AnimationBuilder? {
        return if (!isReady) {
            null
        } else {
            AnimationBuilder(scale, sCenter)
        }
    }

    /**
     * Builder class used to set additional options for a scale animation. Create an instance using [.animateScale],
     * then set your options and call [.start].
     */
    inner class AnimationBuilder {
        private val targetScale: Float
        private val targetSCenter: PointF?
        private val vFocus: PointF?
        private var duration = 500L
        private var easing = EASE_IN_OUT_QUAD
        private var origin = ORIGIN_ANIM
        private var interruptible = true

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

        /**
         * Desired duration of the anim in milliseconds. Default is 500.
         * @param duration duration in milliseconds.
         * @return this builder for method chaining.
         */
        fun withDuration(duration: Long): AnimationBuilder {
            this.duration = duration
            return this
        }

        /**
         * Whether the animation can be interrupted with a touch. Default is true.
         * @param interruptible interruptible flag.
         * @return this builder for method chaining.
         */
        fun withInterruptible(interruptible: Boolean): AnimationBuilder {
            this.interruptible = interruptible
            return this
        }

        /**
         * Set the easing style. See static fields. [.EASE_IN_OUT_QUAD] is recommended, and the default.
         * @param easing easing style.
         * @return this builder for method chaining.
         */
        fun withEasing(easing: Int): AnimationBuilder {
            if (!VALID_EASING_STYLES.contains(easing)) {
                throw IllegalArgumentException("Unknown easing type: $easing")
            }
            this.easing = easing
            return this
        }

        /**
         * Only for internal use. Indicates what caused the animation.
         */
        fun withOrigin(origin: Int): AnimationBuilder {
            this.origin = origin
            return this
        }

        /**
         * Starts the animation.
         */
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
            anim!!.origin = origin

            if (vFocus != null) {
                // Calculate where translation will be at the end of the anim
                val vTranslateXEnd = vFocus.x - targetScale * anim!!.sCenterStart!!.x
                val vTranslateYEnd = vFocus.y - targetScale * anim!!.sCenterStart!!.y
                val satEnd = ScaleAndTranslate(targetScale, PointF(vTranslateXEnd, vTranslateYEnd))
                // Fit the end translation into bounds
                fitToBounds(true, satEnd)
                // Adjust the position of the focus point at end so image will be in bounds
                anim!!.vFocusEnd = PointF(
                        vFocus.x + (satEnd.vTranslate.x - vTranslateXEnd),
                        vFocus.y + (satEnd.vTranslate.y - vTranslateYEnd)
                )
            }

            invalidate()
        }
    }

    /**
     * An event listener, allowing subclasses and activities to be notified of significant events.
     */
    interface OnImageEventListener {

        /**
         * Called when the dimensions of the image and view are known, and either a preview image,
         * the full size image, or base layer tiles are loaded. This indicates the scale and translate
         * are known and the next draw will display an image. This event can be used to hide a loading
         * graphic, or inform a subclass that it is safe to draw overlays.
         */
        fun onReady()

        /**
         * Called when the full size image is ready. When using tiling, this means the lowest resolution
         * base layer of tiles are loaded, and when tiling is disabled, the image bitmap is loaded.
         * This event could be used as a trigger to enable gestures if you wanted interaction disabled
         * while only a preview is displayed, otherwise for most cases [.onReady] is the best
         * event to listen to.
         */
        fun onImageLoaded()

        /**
         * Called when a preview image could not be loaded. This method cannot be relied upon; certain
         * encoding types of supported image formats can result in corrupt or blank images being loaded
         * and displayed with no detectable error. The view will continue to load the full size image.
         * @param e The exception thrown. This error is logged by the view.
         */
        fun onPreviewLoadError(e: Exception)

        /**
         * Indicates an error initiliasing the decoder when using a tiling, or when loading the full
         * size bitmap when tiling is disabled. This method cannot be relied upon; certain encoding
         * types of supported image formats can result in corrupt or blank images being loaded and
         * displayed with no detectable error.
         * @param e The exception thrown. This error is also logged by the view.
         */
        fun onImageLoadError(e: Exception)

        /**
         * Called when an image tile could not be loaded. This method cannot be relied upon; certain
         * encoding types of supported image formats can result in corrupt or blank images being loaded
         * and displayed with no detectable error. Most cases where an unsupported file is used will
         * result in an error caught by [.onImageLoadError].
         * @param e The exception thrown. This error is logged by the view.
         */
        fun onTileLoadError(e: Exception)

        /**
         * Called when a bitmap set using ImageSource.cachedBitmap is no longer being used by the View.
         * This is useful if you wish to manage the bitmap after the preview is shown
         */
        fun onPreviewReleased()
    }

    /**
     * Default implementation of [OnImageEventListener] for extension. This does nothing in any method.
     */
    class DefaultOnImageEventListener : OnImageEventListener {

        override fun onReady() {}
        override fun onImageLoaded() {}
        override fun onPreviewLoadError(e: Exception) {}
        override fun onImageLoadError(e: Exception) {}
        override fun onTileLoadError(e: Exception) {}
        override fun onPreviewReleased() {}
    }
}

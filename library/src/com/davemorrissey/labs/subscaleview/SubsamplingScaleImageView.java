/*
Copyright 2014 David Morrissey

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.davemorrissey.labs.subscaleview;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.TypedArray;
import android.graphics.*;
import android.graphics.Bitmap.Config;
import android.graphics.Paint.Style;
import android.media.ExifInterface;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.FloatMath;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import com.davemorrissey.labs.subscaleview.R.styleable;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * Displays an image subsampled as necessary to avoid loading too much image data into memory. After a pinch to zoom in,
 * a set of image tiles subsampled at higher resolution are loaded and displayed over the base layer. During pinch and
 * zoom, tiles off screen or higher/lower resolution than required are discarded from memory.
 *
 * Tiles over 2048px are not used due to hardware rendering limitations.
 *
 * This view will not work very well with images that are far larger in one dimension than the other because the tile grid
 * for each subsampling level has the same number of rows as columns, so each tile has the same width:height ratio as
 * the source image. This could result in image data totalling several times the screen area being loaded.
 *
 * v prefixes - coordinates, translations and distances measured in screen (view) pixels
 * s prefixes - coordinates, translations and distances measured in source image pixels (scaled)
 */
public class SubsamplingScaleImageView extends View {

    private static final String TAG = SubsamplingScaleImageView.class.getSimpleName();

    /** Attempt to use EXIF information on the image to rotate it. Works for external files only. */
    public static final int ORIENTATION_USE_EXIF = -1;
    /** Display the image file in its native orientation. */
    public static final int ORIENTATION_0 = 0;
    /** Rotate the image 90 degrees clockwise. */
    public static final int ORIENTATION_90 = 90;
    /** Rotate the image 180 degrees. */
    public static final int ORIENTATION_180 = 180;
    /** Rotate the image 270 degrees clockwise. */
    public static final int ORIENTATION_270 = 270;

    private static final List<Integer> VALID_ORIENTATIONS = Arrays.asList(ORIENTATION_0, ORIENTATION_90, ORIENTATION_180, ORIENTATION_270, ORIENTATION_USE_EXIF);

    /** During zoom animation, keep the point of the image that was tapped in the same place, and scale the image around it. */
    public static final int ZOOM_FOCUS_FIXED = 1;
    /** During zoom animation, move the point of the image that was tapped to the center of the screen. */
    public static final int ZOOM_FOCUS_CENTER = 2;
    /** Zoom in to and center the tapped point immediately without animating. */
    public static final int ZOOM_FOCUS_CENTER_IMMEDIATE = 3;

    private static final List<Integer> VALID_ZOOM_STYLES = Arrays.asList(ZOOM_FOCUS_FIXED, ZOOM_FOCUS_CENTER, ZOOM_FOCUS_CENTER_IMMEDIATE);

    /** Quadratic ease out. Not recommended for scale animation, but good for panning. */
    public static final int EASE_OUT_QUAD = 1;
    /** Quadratic ease in and out. */
    public static final int EASE_IN_OUT_QUAD = 2;

    private static final List<Integer> VALID_EASING_STYLES = Arrays.asList(EASE_IN_OUT_QUAD, EASE_OUT_QUAD);

    // Overlay tile boundaries and other info
    private boolean debug = false;

    // Image orientation setting
    private int orientation = ORIENTATION_0;

    // Max scale allowed (prevent infinite zoom)
    private float maxScale = 2F;

    // Gesture detection settings
    private boolean panEnabled = true;
    private boolean zoomEnabled = true;

    // Double tap zoom behaviour
    private float doubleTapZoomScale = 1F;
    private int doubleTapZoomStyle = ZOOM_FOCUS_FIXED;

    // Current scale and scale at start of zoom
    private float scale;
    private float scaleStart;

    // Screen coordinate of top-left corner of source image
    private PointF vTranslate;
    private PointF vTranslateStart;

    // Source coordinate to center on, used when new position is set externally before view is ready
    private Float pendingScale;
    private PointF sPendingCenter;
    private PointF sRequestedCenter;

    // Source image dimensions and orientation - dimensions relate to the unrotated image
    private int sWidth;
    private int sHeight;
    private int sOrientation;

    // Is two-finger zooming in progress
    private boolean isZooming;
    // Is one-finger panning in progress
    private boolean isPanning;
    // Max touches used in current gesture
    private int maxTouchCount;

    // Fling detector
    private GestureDetector detector;

    // Tile decoder
    private BitmapRegionDecoder decoder;
    private final Object decoderLock = new Object();

    // Sample size used to display the whole image when fully zoomed out
    private int fullImageSampleSize;

    // Map of zoom level to tile grid
    private Map<Integer, List<Tile>> tileMap;

    // Debug values
    private PointF vCenterStart;
    private float vDistStart;

    // Scale animation tracking
    private ScaleAnim scaleAnim;
    // Translate animation tracking
    private TranslateAnim translateAnim;

    // Whether a ready notification has been sent to subclasses
    private boolean readySent = false;

    // Long click listener
    private OnLongClickListener onLongClickListener;

    // Long click handler
    private Handler handler;
    private static final int MESSAGE_LONG_CLICK = 1;

    // Paint objects created once and reused for efficiency
    private Paint bitmapPaint;
    private Paint debugPaint;

    public SubsamplingScaleImageView(Context context, AttributeSet attr) {
        super(context, attr);
        setMinimumDpi(160);
        setDoubleTapZoomDpi(160);
        this.handler = new Handler(new Handler.Callback() {
            public boolean handleMessage(Message message) {
                if (message.what == MESSAGE_LONG_CLICK && onLongClickListener != null) {
                    maxTouchCount = 0;
                    SubsamplingScaleImageView.super.setOnLongClickListener(onLongClickListener);
                    performLongClick();
                    SubsamplingScaleImageView.super.setOnLongClickListener(null);
                }
                return true;
            }
        });
        this.detector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (panEnabled && readySent && vTranslate != null && (Math.abs(e1.getX() - e2.getX()) > 50 || Math.abs(e1.getY() - e2.getY()) > 50) && (Math.abs(velocityX) > 500 || Math.abs(velocityY) > 500) && !isZooming) {
                    translateAnim = new TranslateAnim();
                    translateAnim.vTranslateStart = new PointF(vTranslate.x, vTranslate.y);
                    translateAnim.vTranslateEnd = new PointF(vTranslate.x + (velocityX * 0.25f), vTranslate.y + (velocityY * 0.25f));
                    translateAnim.easing = EASE_OUT_QUAD;
                    translateAnim.fitToBounds = true;
                    translateAnim.time = System.currentTimeMillis();
                    invalidate();
                    return true;
                }
                return super.onFling(e1, e2, velocityX, velocityY);
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                performClick();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (zoomEnabled && readySent && vTranslate != null) {
                    float doubleTapZoomScale = Math.min(maxScale, SubsamplingScaleImageView.this.doubleTapZoomScale);
                    boolean zoomIn = scale <= doubleTapZoomScale * 0.9;
                    float targetScale = zoomIn ? doubleTapZoomScale : Math.min(getWidth() / (float) sWidth(), getHeight() / (float) sHeight());
                    if (doubleTapZoomStyle == ZOOM_FOCUS_CENTER_IMMEDIATE) {
                        setScaleAndCenter(targetScale, viewToSourceCoord(e.getX(), e.getY()));
                    } else {
                        scaleAnim = new ScaleAnim();
                        scaleAnim.scaleStart = scale;
                        scaleAnim.scaleEnd = targetScale;
                        scaleAnim.time = System.currentTimeMillis();
                        scaleAnim.vFocusStart = new PointF(e.getX(), e.getY());
                        scaleAnim.sFocus = viewToSourceCoord(scaleAnim.vFocusStart);
                        if (zoomIn && doubleTapZoomStyle == ZOOM_FOCUS_CENTER) {
                            scaleAnim.vFocusEnd = new PointF(
                                    getWidth()/2,
                                    getHeight()/2
                            );
                        } else {
                            // Calculate where translation will be at the end of the anim
                            float vTranslateXEnd = e.getX() - (targetScale * scaleAnim.sFocus.x);
                            float vTranslateYEnd = e.getY() - (targetScale * scaleAnim.sFocus.y);
                            ScaleAndTranslate satEnd = new ScaleAndTranslate(targetScale, new PointF(vTranslateXEnd, vTranslateYEnd));
                            // Fit the end translation into bounds
                            fitToBounds(true, satEnd);
                            // Adjust the position of the focus point at end so image will be in bounds
                            scaleAnim.vFocusEnd = new PointF(
                                    e.getX() + (satEnd.translate.x - vTranslateXEnd),
                                    e.getY() + (satEnd.translate.y - vTranslateYEnd)
                            );
                        }
                    }

                    invalidate();
                    return true;
                }
                return super.onDoubleTapEvent(e);
            }
        });

        // Handle XML attributes
        if (attr != null) {
            TypedArray typedAttr = getContext().obtainStyledAttributes(attr, styleable.SubsamplingScaleImageView);
            if (typedAttr.hasValue(styleable.SubsamplingScaleImageView_assetName)) {
                String assetName = typedAttr.getString(styleable.SubsamplingScaleImageView_assetName);
                if (assetName != null && assetName.length() > 0) {
                    setImageAsset(assetName);
                }
            }
            if (typedAttr.hasValue(styleable.SubsamplingScaleImageView_panEnabled)) {
                setPanEnabled(typedAttr.getBoolean(styleable.SubsamplingScaleImageView_panEnabled, true));
            }
            if (typedAttr.hasValue(styleable.SubsamplingScaleImageView_zoomEnabled)) {
                setZoomEnabled(typedAttr.getBoolean(styleable.SubsamplingScaleImageView_zoomEnabled, true));
            }
        }
    }

    public SubsamplingScaleImageView(Context context) {
        this(context, null);
    }

    /**
     * Sets the image orientation. It's best to call this before setting the image file or asset, because it may waste
     * loading of tiles. However, this can be freely called at any time.
     */
    public final void setOrientation(int orientation) {
        if (!VALID_ORIENTATIONS.contains(orientation)) {
            throw new IllegalArgumentException("Invalid orientation: " + orientation);
        }
        this.orientation = orientation;
        reset(false);
        invalidate();
        requestLayout();
    }

    /**
     * Display an image from a file in internal or external storage.
     * @param extFile URI of the file to display.
     */
    public final void setImageFile(String extFile) {
        reset(true);
        BitmapInitTask task = new BitmapInitTask(this, getContext(), extFile, false);
        task.execute();
        invalidate();
    }

    /**
     * Display an image from a file in internal or external storage, starting with a given orientation setting, scale
     * and center. This is the best method to use when you want scale and center to be restored after screen orientation
     * change; it avoids any redundant loading of tiles in the wrong orientation.
     * @param extFile URI of the file to display.
     * @param state State to be restored. Nullable.
     */
    public final void setImageFile(String extFile, ImageViewState state) {
        reset(true);
        restoreState(state);
        BitmapInitTask task = new BitmapInitTask(this, getContext(), extFile, false);
        task.execute();
        invalidate();
    }

    /**
     * Display an image from a file in assets.
     * @param assetName asset name.
     */
    public final void setImageAsset(String assetName) {
        setImageAsset(assetName, null);
    }

    /**
     * Display an image from a file in assets, starting with a given orientation setting, scale and center. This is the
     * best method to use when you want scale and center to be restored after screen orientation change; it avoids any
     * redundant loading of tiles in the wrong orientation.
     * @param assetName asset name.
     * @param state State to be restored. Nullable.
     */
    public final void setImageAsset(String assetName, ImageViewState state) {
        reset(true);
        restoreState(state);
        BitmapInitTask task = new BitmapInitTask(this, getContext(), assetName, true);
        task.execute();
        invalidate();
    }

    /**
     * Reset all state before setting/changing image or setting new rotation.
     */
    private void reset(boolean newImage) {
        scale = 0f;
        scaleStart = 0f;
        vTranslate = null;
        vTranslateStart = null;
        pendingScale = 0f;
        sPendingCenter = null;
        sRequestedCenter = null;
        isZooming = false;
        isPanning = false;
        maxTouchCount = 0;
        fullImageSampleSize = 0;
        vCenterStart = null;
        vDistStart = 0;
        scaleAnim = null;
        translateAnim = null;
        if (newImage) {
            if (decoder != null) {
                synchronized (decoderLock) {
                    decoder.recycle();
                    decoder = null;
                }
            }
            sWidth = 0;
            sHeight = 0;
            sOrientation = 0;
            readySent = false;
        }
        if (tileMap != null) {
            for (Map.Entry<Integer, List<Tile>> tileMapEntry : tileMap.entrySet()) {
                for (Tile tile : tileMapEntry.getValue()) {
                    if (tile.bitmap != null) {
                        tile.bitmap.recycle();
                    }
                }
            }
            tileMap = null;
        }
    }

    /**
     * On resize, zoom out to full size again. Various behaviours are possible, override this method to use another.
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (readySent) {
            reset(false);
        }
    }

    /**
     * Measures the width and height of the view, preserving the aspect ratio of the image displayed if wrap_content is
     * used. The image will scale within this box, not resizing the view as it is zoomed.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
        boolean resizeWidth = widthSpecMode != MeasureSpec.EXACTLY;
        boolean resizeHeight = heightSpecMode != MeasureSpec.EXACTLY;
        int width = parentWidth;
        int height = parentHeight;
        if (sWidth > 0 && sHeight > 0) {
            if (resizeWidth && resizeHeight) {
                width = sWidth();
                height = sHeight();
            } else if (resizeHeight) {
                height = (int)((((double)sHeight()/(double)sWidth()) * width));
            } else if (resizeWidth) {
                width = (int)((((double)sWidth()/(double)sHeight()) * height));
            }
        }
        width = Math.max(width, getSuggestedMinimumWidth());
        height = Math.max(height, getSuggestedMinimumHeight());
        setMeasuredDimension(width, height);
    }

    /**
     * Handle touch events. One finger pans, and two finger pinch and zoom plus panning.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        PointF vCenterEnd;
        float vDistEnd;
        // During non-interruptible anims, ignore all touch events
        if (translateAnim != null && !translateAnim.interruptible) {
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        } else {
            translateAnim = null;
        }

        // Abort if not ready
        if (vTranslate == null) {
            return true;
        }
        // Detect flings, taps and double taps
        if (detector == null || detector.onTouchEvent(event)) {
            return true;
        }

        int touchCount = event.getPointerCount();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_1_DOWN:
            case MotionEvent.ACTION_POINTER_2_DOWN:
                scaleAnim = null;
                getParent().requestDisallowInterceptTouchEvent(true);
                maxTouchCount = Math.max(maxTouchCount, touchCount);
                if (touchCount >= 2) {
                    if (zoomEnabled) {
                        // Start pinch to zoom. Calculate distance between touch points and center point of the pinch.
                        float distance = distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1));
                        scaleStart = scale;
                        vDistStart = distance;
                        vTranslateStart = new PointF(vTranslate.x, vTranslate.y);
                        vCenterStart = new PointF((event.getX(0) + event.getX(1))/2, (event.getY(0) + event.getY(1))/2);
                    } else {
                        // Abort all gestures on second touch
                        maxTouchCount = 0;
                    }
                    // Cancel long click timer
                    handler.removeMessages(MESSAGE_LONG_CLICK);
                } else {
                    // Start one-finger pan
                    vTranslateStart = new PointF(vTranslate.x, vTranslate.y);
                    vCenterStart = new PointF(event.getX(), event.getY());

                    // Start long click timer
                    handler.sendEmptyMessageDelayed(MESSAGE_LONG_CLICK, 600);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                boolean consumed = false;
                if (maxTouchCount > 0) {
                    if (touchCount >= 2) {
                        // Calculate new distance between touch points, to scale and pan relative to start values.
                        vDistEnd = distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1));
                        vCenterEnd = new PointF((event.getX(0) + event.getX(1))/2, (event.getY(0) + event.getY(1))/2);

                        if (zoomEnabled && (distance(vCenterStart.x, vCenterEnd.x, vCenterStart.y, vCenterEnd.y) > 5 || Math.abs(vDistEnd - vDistStart) > 5 || isPanning)) {
                            isZooming = true;
                            isPanning = true;
                            consumed = true;

                            scale = Math.min(maxScale, (vDistEnd / vDistStart) * scaleStart);

                            if (panEnabled) {
                                // Translate to place the source image coordinate that was at the center of the pinch at the start
                                // at the center of the pinch now, to give simultaneous pan + zoom.
                                float vLeftStart = vCenterStart.x - vTranslateStart.x;
                                float vTopStart = vCenterStart.y - vTranslateStart.y;
                                float vLeftNow = vLeftStart * (scale/scaleStart);
                                float vTopNow = vTopStart * (scale/scaleStart);
                                vTranslate.x = vCenterEnd.x - vLeftNow;
                                vTranslate.y = vCenterEnd.y - vTopNow;
                            } else if (sRequestedCenter != null) {
                                // With a center specified from code, zoom around that point.
                                vTranslate.x = (getWidth()/2) - (scale * sRequestedCenter.x);
                                vTranslate.y = (getHeight()/2) - (scale * sRequestedCenter.y);
                            } else {
                                // With no requested center, scale around the image center.
                                vTranslate.x = (getWidth()/2) - (scale * (sWidth()/2));
                                vTranslate.y = (getHeight()/2) - (scale * (sHeight()/2));
                            }

                            fitToBounds(true);
                            refreshRequiredTiles(false);
                        }
                    } else if (!isZooming) {
                        // One finger pan - translate the image. We do this calculation even with pan disabled so click
                        // and long click behaviour is preserved.
                        float dx = Math.abs(event.getX() - vCenterStart.x);
                        float dy = Math.abs(event.getY() - vCenterStart.y);
                        if (dx > 5 || dy > 5 || isPanning) {
                            consumed = true;
                            vTranslate.x = vTranslateStart.x + (event.getX() - vCenterStart.x);
                            vTranslate.y = vTranslateStart.y + (event.getY() - vCenterStart.y);

                            float lastX = vTranslate.x;
                            float lastY = vTranslate.y;
                            fitToBounds(true);
                            if (lastX == vTranslate.x || (lastY == vTranslate.y && dy > 10) || isPanning) {
                                isPanning = true;
                            } else if (dx > 5) {
                                // Haven't panned the image, and we're at the left or right edge. Switch to page swipe.
                                maxTouchCount = 0;
                                handler.removeMessages(MESSAGE_LONG_CLICK);
                                getParent().requestDisallowInterceptTouchEvent(false);
                            }

                            if (!panEnabled) {
                                vTranslate.x = vTranslateStart.x;
                                vTranslate.y = vTranslateStart.y;
                                getParent().requestDisallowInterceptTouchEvent(false);
                            }

                            refreshRequiredTiles(false);
                        }
                    }
                }
                if (consumed) {
                    handler.removeMessages(MESSAGE_LONG_CLICK);
                    invalidate();
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_POINTER_2_UP:
                handler.removeMessages(MESSAGE_LONG_CLICK);
                if (maxTouchCount > 0 && (isZooming || isPanning)) {
                    if (isZooming && touchCount == 2) {
                        // Convert from zoom to pan with remaining touch
                        isPanning = true;
                        vTranslateStart = new PointF(vTranslate.x, vTranslate.y);
                        if (event.getActionIndex() == 1) {
                            vCenterStart = new PointF(event.getX(0), event.getY(0));
                        } else {
                            vCenterStart = new PointF(event.getX(1), event.getY(1));
                        }
                    }
                    if (touchCount < 3) {
                        // End zooming when only one touch point
                        isZooming = false;
                    }
                    if (touchCount < 2) {
                        // End panning when no touch points
                        isPanning = false;
                        maxTouchCount = 0;
                    }
                    // Trigger load of tiles now required
                    refreshRequiredTiles(true);
                    return true;
                }
                if (touchCount == 1) {
                    isZooming = false;
                    isPanning = false;
                    maxTouchCount = 0;
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener onLongClickListener) {
        this.onLongClickListener = onLongClickListener;
    }

    /**
     * Draw method should not be called until the view has dimensions so the first calls are used as triggers to calculate
     * the scaling and tiling required. Once the view is setup, tiles are displayed as they are loaded.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        createPaints();

        // If image or view dimensions are not known yet, abort.
        if (sWidth == 0 || sHeight == 0 || decoder == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }

        // On first render with no tile map ready, initialise it and kick off async base image loading.
        if (tileMap == null) {
            initialiseBaseLayer(getMaxBitmapDimensions(canvas));
            return;
        }

        // If waiting to translate to new center position, set translate now
        if (sPendingCenter != null && pendingScale != null) {
            scale = pendingScale;
            vTranslate.x = (getWidth()/2) - (scale * sPendingCenter.x);
            vTranslate.y = (getHeight()/2) - (scale * sPendingCenter.y);
            sPendingCenter = null;
            pendingScale = null;
            fitToBounds(true);
            refreshRequiredTiles(true);
        }

        // On first display of base image set up position, and in other cases make sure scale is correct.
        fitToBounds(false);

        // Everything is set up and coordinates are valid. Inform subclasses.
        if (!readySent) {
            readySent = true;
            new Thread(new Runnable() {
                public void run() {
                    onImageReady();
                }
            }).start();
        }

        // If animating scale, calculate current scale with easing equations
        if (scaleAnim != null) {
            long scaleElapsed = System.currentTimeMillis() - scaleAnim.time;
            boolean finished = scaleElapsed > 500;
            scaleElapsed = Math.min(scaleElapsed, 500);
            scale = easeInOutQuad(scaleElapsed, scaleAnim.scaleStart, scaleAnim.scaleEnd - scaleAnim.scaleStart, 500);

            // Apply required animation to the focal point
            float vFocusNowX = easeInOutQuad(scaleElapsed, scaleAnim.vFocusStart.x, scaleAnim.vFocusEnd.x - scaleAnim.vFocusStart.x, 500);
            float vFocusNowY = easeInOutQuad(scaleElapsed, scaleAnim.vFocusStart.y, scaleAnim.vFocusEnd.y - scaleAnim.vFocusStart.y, 500);
            // Find out where the focal point is at this scale and adjust its position to follow the animation path
            PointF vFocus = sourceToViewCoord(scaleAnim.sFocus);
            vTranslate.x -= vFocus.x - vFocusNowX;
            vTranslate.y -= vFocus.y - vFocusNowY;

            fitToBounds(finished);
            refreshRequiredTiles(finished);
            if (finished) {
                scaleAnim = null;
            }
            invalidate();
        }

        // If animating translation, calculate the position with easing equations.
        if (translateAnim != null) {
            long translateElapsed = System.currentTimeMillis() - translateAnim.time;
            boolean finished = translateElapsed > translateAnim.duration;
            translateElapsed = Math.min(translateElapsed, translateAnim.duration);
            vTranslate.x = ease(translateAnim.easing, translateElapsed, translateAnim.vTranslateStart.x, translateAnim.vTranslateEnd.x - translateAnim.vTranslateStart.x, translateAnim.duration);
            vTranslate.y = ease(translateAnim.easing, translateElapsed, translateAnim.vTranslateStart.y, translateAnim.vTranslateEnd.y - translateAnim.vTranslateStart.y, translateAnim.duration);

            fitToBounds(finished || translateAnim.fitToBounds);
            refreshRequiredTiles(finished);
            if (finished) {
                translateAnim = null;
            }
            invalidate();
        }

        // Optimum sample size for current scale
        int sampleSize = Math.min(fullImageSampleSize, calculateInSampleSize((int) (sWidth() * scale), (int) (sHeight() * scale)));

        // First check for missing tiles - if there are any we need the base layer underneath to avoid gaps
        boolean hasMissingTiles = false;
        for (Map.Entry<Integer, List<Tile>> tileMapEntry : tileMap.entrySet()) {
            if (tileMapEntry.getKey() == sampleSize) {
                for (Tile tile : tileMapEntry.getValue()) {
                    if (tile.visible && (tile.loading || tile.bitmap == null)) {
                        hasMissingTiles = true;
                    }
                }
            }
        }

        // Render all loaded tiles. LinkedHashMap used for bottom up rendering - lower res tiles underneath.
        for (Map.Entry<Integer, List<Tile>> tileMapEntry : tileMap.entrySet()) {
            if (tileMapEntry.getKey() == sampleSize || hasMissingTiles) {
                for (Tile tile : tileMapEntry.getValue()) {
                    Rect vRect = convertRect(sourceToViewRect(tile.sRect));
                    if (!tile.loading && tile.bitmap != null) {
                        canvas.drawBitmap(tile.bitmap, null, vRect, bitmapPaint);
                        if (debug) {
                            canvas.drawRect(vRect, debugPaint);
                        }
                    } else if (tile.loading && debug) {
                        canvas.drawText("LOADING", vRect.left + 5, vRect.top + 35, debugPaint);
                    }
                    if (tile.visible && debug) {
                        canvas.drawText("ISS " + tile.sampleSize + " RECT " + tile.sRect.top + "," + tile.sRect.left + "," + tile.sRect.bottom + "," + tile.sRect.right, vRect.left + 5, vRect.top + 15, debugPaint);
                    }
                }
            }
        }

        if (debug) {
            canvas.drawText("Scale: " + String.format("%.2f", scale), 5, 15, debugPaint);
            canvas.drawText("Translate: " + String.format("%.2f", vTranslate.x) + ":" + String.format("%.2f", vTranslate.y), 5, 35, debugPaint);
            PointF center = getCenter();
            canvas.drawText("Source center: " + String.format("%.2f", center.x) + ":" + String.format("%.2f", center.y), 5, 55, debugPaint);

            if (scaleAnim != null) {
                PointF vCenter = sourceToViewCoord(scaleAnim.sFocus);
                canvas.drawCircle(vCenter.x, vCenter.y, 20, debugPaint);
                canvas.drawCircle(getWidth()/2, getHeight()/2, 30, debugPaint);
            }
            if (translateAnim != null && translateAnim.sCenter != null) {
                PointF vTarget = sourceToViewCoord(translateAnim.sCenter);
                canvas.drawCircle(vTarget.x, vTarget.y, 20, debugPaint);
                canvas.drawCircle(getWidth()/2, getHeight()/2, 30, debugPaint);
            }
        }
    }

    /**
     * Creates Paint objects once when first needed.
     */
    private void createPaints() {
        if (bitmapPaint == null) {
            bitmapPaint = new Paint();
            bitmapPaint.setAntiAlias(true);
            bitmapPaint.setFilterBitmap(true);
            bitmapPaint.setDither(true);
        }
        if (debugPaint == null && debug) {
            debugPaint = new Paint();
            debugPaint.setTextSize(18);
            debugPaint.setColor(Color.MAGENTA);
            debugPaint.setStyle(Style.STROKE);
        }
    }

    /**
     * Called on first draw when the view has dimensions. Calculates the initial sample size and starts async loading of
     * the base layer image - the whole source subsampled as necessary.
     */
    private synchronized void initialiseBaseLayer(Point maxTileDimensions) {

        fitToBounds(true);

        // Load double resolution - next level will be split into four tiles and at the center all four are required,
        // so don't bother with tiling until the next level 16 tiles are needed.
        fullImageSampleSize = calculateInSampleSize((int) (sWidth() * scale), (int) (sHeight() * scale));
        if (fullImageSampleSize > 1) {
            fullImageSampleSize /= 2;
        }

        initialiseTileMap(maxTileDimensions);

        List<Tile> baseGrid = tileMap.get(fullImageSampleSize);
        for (Tile baseTile : baseGrid) {
            BitmapTileTask task = new BitmapTileTask(this, decoder, decoderLock, baseTile);
            task.execute();
        }

    }

    /**
     * Loads the optimum tiles for display at the current scale and translate, so the screen can be filled with tiles
     * that are at least as high resolution as the screen. Frees up bitmaps that are now off the screen.
     * @param load Whether to load the new tiles needed. Use false while scrolling/panning for performance.
     */
    private void refreshRequiredTiles(boolean load) {
        int sampleSize = Math.min(fullImageSampleSize, calculateInSampleSize((int) (scale * sWidth()), (int) (scale * sHeight())));
        RectF vVisRect = new RectF(0, 0, getWidth(), getHeight());
        RectF sVisRect = viewToSourceRect(vVisRect);

        // Load tiles of the correct sample size that are on screen. Discard tiles off screen, and those that are higher
        // resolution than required, or lower res than required but not the base layer, so the base layer is always present.
        for (Map.Entry<Integer, List<Tile>> tileMapEntry : tileMap.entrySet()) {
            for (Tile tile : tileMapEntry.getValue()) {
                if (tile.sampleSize < sampleSize || (tile.sampleSize > sampleSize && tile.sampleSize != fullImageSampleSize)) {
                    tile.visible = false;
                    if (tile.bitmap != null) {
                        tile.bitmap.recycle();
                        tile.bitmap = null;
                    }
                }
                if (tile.sampleSize == sampleSize) {
                    if (RectF.intersects(sVisRect, convertRect(tile.sRect))) {
                        tile.visible = true;
                        if (!tile.loading && tile.bitmap == null && load) {
                            BitmapTileTask task = new BitmapTileTask(this, decoder, decoderLock, tile);
                            task.execute();
                        }
                    } else if (tile.sampleSize != fullImageSampleSize) {
                        tile.visible = false;
                        if (tile.bitmap != null) {
                            tile.bitmap.recycle();
                            tile.bitmap = null;
                        }
                    }
                } else if (tile.sampleSize == fullImageSampleSize) {
                    tile.visible = true;
                }
            }
        }

    }

    /**
     * Calculates sample size to fit the source image in given bounds.
     */
    private int calculateInSampleSize(int reqWidth, int reqHeight) {
        // Raw height and width of image
        int inSampleSize = 1;
        if (reqWidth == 0 || reqHeight == 0) {
            return 32;
        }

        if (sHeight() > reqHeight || sWidth() > reqWidth) {

            // Calculate ratios of height and width to requested height and width
            final int heightRatio = Math.round((float) sHeight() / (float) reqHeight);
            final int widthRatio = Math.round((float) sWidth() / (float) reqWidth);

            // Choose the smallest ratio as inSampleSize value, this will guarantee
            // a final image with both dimensions larger than or equal to the
            // requested height and width.
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }

        // We want the actual sample size that will be used, so round down to nearest power of 2.
        int power = 1;
        while (power * 2 < inSampleSize) {
            power = power * 2;
        }

        return power;
    }

    /**
     * Adjusts hypothetical future scale and translate values to keep scale within the allowed range and the image on screen. Minimum scale
     * is set so one dimension fills the view and the image is centered on the other dimension. Used to calculate what the target of an
     * animation should be.
     * @param center Whether the image should be centered in the dimension it's too small to fill. While animating this can be false to avoid changes in direction as bounds are reached.
     * @param scaleAndTranslate The scale we want and the translation we're aiming for. The values are adjusted to be valid.
     */
    private void fitToBounds(boolean center, ScaleAndTranslate scaleAndTranslate) {
        float scale = scaleAndTranslate.scale;
        PointF vTranslate = scaleAndTranslate.translate;

        float minScale = Math.min(getWidth() / (float) sWidth(), getHeight() / (float) sHeight());
        scale = Math.max(minScale, scale);
        scale = Math.min(maxScale, scale);

        float scaleWidth = scale * sWidth();
        float scaleHeight = scale * sHeight();

        vTranslate.x = Math.max(vTranslate.x, center ? getWidth() - scaleWidth : -scaleWidth);
        vTranslate.y = Math.max(vTranslate.y, center ? getHeight() - scaleHeight : -scaleHeight);

        float maxTx = Math.max(0, center ? (getWidth() - scaleWidth) / 2 : getWidth());
        float maxTy = Math.max(0, center ? (getHeight() - scaleHeight) / 2 : getHeight());

        vTranslate.x = Math.min(vTranslate.x, maxTx);
        vTranslate.y = Math.min(vTranslate.y, maxTy);

        scaleAndTranslate.scale = scale;
    }

    /**
     * Adjusts current scale and translate values to keep scale within the allowed range and the image on screen. Minimum scale
     * is set so one dimension fills the view and the image is centered on the other dimension.
     * @param center Whether the image should be centered in the dimension it's too small to fill. While animating this can be false to avoid changes in direction as bounds are reached.
     */
    private void fitToBounds(boolean center) {
        if (vTranslate == null) {
            vTranslate = new PointF(0, 0);
        }
        ScaleAndTranslate input = new ScaleAndTranslate(scale, vTranslate);
        fitToBounds(center, input);
        scale = input.scale;
    }

    /**
     * Once source image and view dimensions are known, creates a map of sample size to tile grid.
     */
    private void initialiseTileMap(Point maxTileDimensions) {
        this.tileMap = new LinkedHashMap<Integer, List<Tile>>();
        int sampleSize = fullImageSampleSize;
        int tilesPerSide = 1;
        while (true) {
            int sTileWidth = sWidth()/tilesPerSide;
            int sTileHeight = sHeight()/tilesPerSide;
            int subTileWidth = sTileWidth/sampleSize;
            int subTileHeight = sTileHeight/sampleSize;
            while (subTileWidth > maxTileDimensions.x || subTileHeight > maxTileDimensions.y) {
                tilesPerSide *= 2;
                sTileWidth = sWidth()/tilesPerSide;
                sTileHeight = sHeight()/tilesPerSide;
                subTileWidth = sTileWidth/sampleSize;
                subTileHeight = sTileHeight/sampleSize;
            }
            List<Tile> tileGrid = new ArrayList<Tile>(tilesPerSide * tilesPerSide);
            for (int x = 0; x < tilesPerSide; x++) {
                for (int y = 0; y < tilesPerSide; y++) {
                    Tile tile = new Tile();
                    tile.sampleSize = sampleSize;
                    tile.sRect = new Rect(
                            x * sTileWidth,
                            y * sTileHeight,
                            (x + 1) * sTileWidth,
                            (y + 1) * sTileHeight
                    );
                    tileGrid.add(tile);
                }
            }
            tileMap.put(sampleSize, tileGrid);
            tilesPerSide = (tilesPerSide == 1) ? 4 : tilesPerSide * 2;
            if (sampleSize == 1) {
                break;
            } else {
                sampleSize /= 2;
            }
        }
    }

    /**
     * Called by worker task when decoder is ready and image size and EXIF orientation is known.
     */
    private void onImageInited(BitmapRegionDecoder decoder, int sWidth, int sHeight, int sOrientation) {
        this.decoder = decoder;
        this.sWidth = sWidth;
        this.sHeight = sHeight;
        this.sOrientation = sOrientation;
        requestLayout();
        invalidate();
    }

    /**
     * Called by worker task when a tile has loaded. Redraws the view.
     */
    private void onTileLoaded() {
        invalidate();
    }

    /**
     * Async task used to get image details without blocking the UI thread.
     */
    private static class BitmapInitTask extends AsyncTask<Void, Void, int[]> {
        private final WeakReference<SubsamplingScaleImageView> viewRef;
        private final WeakReference<Context> contextRef;
        private final String source;
        private final boolean sourceIsAsset;
        private WeakReference<BitmapRegionDecoder> decoderRef;

        public BitmapInitTask(SubsamplingScaleImageView view, Context context, String source, boolean sourceIsAsset) {
            this.viewRef = new WeakReference<SubsamplingScaleImageView>(view);
            this.contextRef = new WeakReference<Context>(context);
            this.source = source;
            this.sourceIsAsset = sourceIsAsset;
        }

        @Override
        protected int[] doInBackground(Void... params) {
            try {
                if (viewRef != null && contextRef != null) {
                    Context context = contextRef.get();
                    if (context != null) {
                        BitmapRegionDecoder decoder;
                        int exifOrientation = ORIENTATION_0;
                        if (sourceIsAsset) {
                            decoder = BitmapRegionDecoder.newInstance(context.getAssets().open(source, AssetManager.ACCESS_RANDOM), true);
                        } else {
                            decoder = BitmapRegionDecoder.newInstance(source, true);
                            try {
                                ExifInterface exifInterface = new ExifInterface(source);
                                int orientationAttr = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                                if (orientationAttr == ExifInterface.ORIENTATION_NORMAL) {
                                    exifOrientation = ORIENTATION_0;
                                } else if (orientationAttr == ExifInterface.ORIENTATION_ROTATE_90) {
                                    exifOrientation = ORIENTATION_90;
                                } else if (orientationAttr == ExifInterface.ORIENTATION_ROTATE_180) {
                                    exifOrientation = ORIENTATION_180;
                                } else if (orientationAttr == ExifInterface.ORIENTATION_ROTATE_270) {
                                    exifOrientation = ORIENTATION_270;
                                } else {
                                    Log.w(TAG, "Unsupported EXIF orientation: " + orientationAttr);
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Could not get EXIF orientation of image");
                            }

                        }
                        decoderRef = new WeakReference<BitmapRegionDecoder>(decoder);
                        return new int[] { decoder.getWidth(), decoder.getHeight(), exifOrientation };
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialise bitmap decoder", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(int[] xyo) {
            if (viewRef != null && decoderRef != null) {
                final SubsamplingScaleImageView subsamplingScaleImageView = viewRef.get();
                final BitmapRegionDecoder decoder = decoderRef.get();
                if (subsamplingScaleImageView != null && decoder != null && xyo != null && xyo.length == 3) {
                    subsamplingScaleImageView.onImageInited(decoder, xyo[0], xyo[1], xyo[2]);
                }
            }
        }
    }

    /**
     * Async task used to load images without blocking the UI thread.
     */
    private static class BitmapTileTask extends AsyncTask<Void, Void, Bitmap> {
        private final WeakReference<SubsamplingScaleImageView> viewRef;
        private final WeakReference<BitmapRegionDecoder> decoderRef;
        private final WeakReference<Object> decoderLockRef;
        private final WeakReference<Tile> tileRef;

        public BitmapTileTask(SubsamplingScaleImageView view, BitmapRegionDecoder decoder, Object decoderLock, Tile tile) {
            this.viewRef = new WeakReference<SubsamplingScaleImageView>(view);
            this.decoderRef = new WeakReference<BitmapRegionDecoder>(decoder);
            this.decoderLockRef = new WeakReference<Object>(decoderLock);
            this.tileRef = new WeakReference<Tile>(tile);
            tile.loading = true;
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            try {
                if (decoderRef != null && tileRef != null && viewRef != null) {
                    final BitmapRegionDecoder decoder = decoderRef.get();
                    final Object decoderLock = decoderLockRef.get();
                    final Tile tile = tileRef.get();
                    final SubsamplingScaleImageView view = viewRef.get();
                    if (decoder != null && decoderLock != null && tile != null && view != null && !decoder.isRecycled()) {
                        synchronized (decoderLock) {
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inSampleSize = tile.sampleSize;
                            options.inPreferredConfig = Config.RGB_565;
                            Bitmap bitmap = decoder.decodeRegion(view.fileSRect(tile.sRect), options);
                            int rotation = view.getRequiredRotation();
                            if (rotation != 0) {
                                Matrix matrix = new Matrix();
                                matrix.postRotate(rotation);
                                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                            }
                            return bitmap;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to decode tile", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (viewRef != null && tileRef != null && bitmap != null) {
                final SubsamplingScaleImageView subsamplingScaleImageView = viewRef.get();
                final Tile tile = tileRef.get();
                if (subsamplingScaleImageView != null && tile != null) {
                    tile.bitmap = bitmap;
                    tile.loading = false;
                    subsamplingScaleImageView.onTileLoaded();
                }
            }
        }
    }

    private static class Tile {

        private Rect sRect;
        private int sampleSize;
        private Bitmap bitmap;
        private boolean loading;
        private boolean visible;

    }

    private static class ScaleAnim {

        private float scaleStart; // Scale at start of anim
        private float scaleEnd; // Scale at end of anim (target)
        private PointF sFocus; // Source point that was double tapped
        private PointF vFocusStart; // View point that was double tapped
        private PointF vFocusEnd; // Where the view focal point should be moved to during the anim
        private long time; // Start time

    }

    private static class TranslateAnim {

        private PointF sCenter; // Requested center. For debug use only, can be null.
        private PointF vTranslateStart; // Translation at start of anim
        private PointF vTranslateEnd; // Translation at end of anim
        private long duration = 500; // How long the anim takes
        private boolean interruptible = true; // Whether the anim can be interrupted by a touch
        private int easing = EASE_IN_OUT_QUAD; // Easing style
        private long time = System.currentTimeMillis(); // Start time
        private boolean fitToBounds = false; // Animate in bounds - used for fling

    }

    private static class ScaleAndTranslate {
        private ScaleAndTranslate(float scale, PointF translate) {
            this.scale = scale;
            this.translate = translate;
        }
        private float scale;
        private PointF translate;
    }

    /**
     * Set scale, center and orientation from saved state.
     */
    private void restoreState(ImageViewState state) {
        if (state != null && state.getCenter() != null && VALID_ORIENTATIONS.contains(state.getOrientation())) {
            this.orientation = state.getOrientation();
            this.pendingScale = state.getScale();
            this.sPendingCenter = state.getCenter();
            invalidate();
        }
    }

    /**
     * In SDK 14 and above, use canvas max bitmap width and height instead of the default 2048, to avoid redundant tiling.
     */
    private Point getMaxBitmapDimensions(Canvas canvas) {
        if (VERSION.SDK_INT >= 14) {
            try {
                int maxWidth = (Integer)Canvas.class.getMethod("getMaximumBitmapWidth").invoke(canvas);
                int maxHeight = (Integer)Canvas.class.getMethod("getMaximumBitmapHeight").invoke(canvas);
                return new Point(maxWidth, maxHeight);
            } catch (Exception e) {
                // Return default
            }
        }
        return new Point(2048, 2048);
    }

    /**
     * Get source width taking rotation into account.
     */
    private int sWidth() {
        int rotation = getRequiredRotation();
        if (rotation == 90 || rotation == 270) {
            return sHeight;
        } else {
            return sWidth;
        }
    }

    /**
     * Get source height taking rotation into account.
     */
    private int sHeight() {
        int rotation = getRequiredRotation();
        if (rotation == 90 || rotation == 270) {
            return sWidth;
        } else {
            return sHeight;
        }
    }

    /**
     * Converts source rectangle from tile, which treats the image file as if it were in the correct orientation already,
     * to the rectangle of the image that needs to be loaded.
     */
    private Rect fileSRect(Rect sRect) {
        if (getRequiredRotation() == 0) {
            return sRect;
        } else if (getRequiredRotation() == 90) {
            return new Rect(sRect.top, sHeight - sRect.right, sRect.bottom, sHeight - sRect.left);
        } else if (getRequiredRotation() == 180) {
            return new Rect(sWidth - sRect.right, sHeight - sRect.bottom, sWidth - sRect.left, sHeight - sRect.top);
        } else {
            return new Rect(sWidth - sRect.bottom, sRect.left, sWidth - sRect.top, sRect.right);
        }
    }

    /**
     * Determines the rotation to be applied to tiles, based on EXIF orientation or chosen setting.
     */
    private int getRequiredRotation() {
        if (orientation == ORIENTATION_USE_EXIF) {
            return sOrientation;
        } else {
            return orientation;
        }
    }

    /**
     * Pythagoras distance between two points.
     */
    private float distance(float x0, float x1, float y0, float y1) {
        float x = x0 - x1;
        float y = y0 - y1;
        return FloatMath.sqrt(x * x + y * y);
    }

    /**
     * Convert screen coordinate to source coordinate.
     */
    public final PointF viewToSourceCoord(PointF vxy) {
        return viewToSourceCoord(vxy.x, vxy.y);
    }

    /**
     * Convert screen coordinate to source coordinate.
     */
    public final PointF viewToSourceCoord(float vx, float vy) {
        if (vTranslate == null) {
            return null;
        }
        float sx = (vx - vTranslate.x)/scale;
        float sy = (vy - vTranslate.y)/scale;
        return new PointF(sx, sy);
    }

    /**
     * Convert source coordinate to screen coordinate.
     */
    public final PointF sourceToViewCoord(PointF sxy) {
        return sourceToViewCoord(sxy.x, sxy.y);
    }

    /**
     * Convert source coordinate to screen coordinate.
     */
    public final PointF sourceToViewCoord(float sx, float sy) {
        if (vTranslate == null) {
            return null;
        }
        float vx = (sx * scale) + vTranslate.x;
        float vy = (sy * scale) + vTranslate.y;
        return new PointF(vx, vy);
    }

    /**
     * Convert source rect to screen rect.
     */
    private RectF sourceToViewRect(Rect sRect) {
        return sourceToViewRect(convertRect(sRect));
    }

    /**
     * Convert source rect to screen rect.
     */
    private RectF sourceToViewRect(RectF sRect) {
        PointF vLT = sourceToViewCoord(new PointF(sRect.left, sRect.top));
        PointF vRB = sourceToViewCoord(new PointF(sRect.right, sRect.bottom));
        return new RectF(vLT.x, vLT.y, vRB.x, vRB.y);
    }

    /**
     * Convert screen rect to source rect.
     */
    private RectF viewToSourceRect(RectF vRect) {
        PointF sLT = viewToSourceCoord(new PointF(vRect.left, vRect.top));
        PointF sRB = viewToSourceCoord(new PointF(vRect.right, vRect.bottom));
        return new RectF(sLT.x, sLT.y, sRB.x, sRB.y);
    }

    /**
     * Int to float rect conversion.
     */
    private RectF convertRect(Rect rect) {
        return new RectF(rect.left, rect.top, rect.right, rect.bottom);
    }

    /**
     * Float to int rect conversion.
     */
    private Rect convertRect(RectF rect) {
        return new Rect((int)rect.left, (int)rect.top, (int)rect.right, (int)rect.bottom);
    }

    /**
     * Get the translation required to place a given source coordinate at the center of the screen. Accepts the desired
     * scale as an argument, so this is independent of current translate and scale. The result is fitted to bounds, putting
     * the image point as near to the screen center as permitted.
     */
    private PointF vTranslateForSCenter(PointF sCenter, float scale) {
        PointF vTranslate = new PointF((getWidth()/2) - (sCenter.x * scale), (getHeight()/2) - (sCenter.y * scale));
        ScaleAndTranslate sat = new ScaleAndTranslate(scale, vTranslate);
        fitToBounds(true, sat);
        return vTranslate;
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
    private float ease(int type, long time, float from, float change, long duration) {
        switch (type) {
            case EASE_IN_OUT_QUAD:
                return easeInOutQuad(time, from, change, duration);
            case EASE_OUT_QUAD:
                return easeOutQuad(time, from, change, duration);
            default:
                throw new IllegalStateException("Unexpected easing type: " + type);
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
    private float easeOutQuad(long time, float from, float change, long duration) {
        float progress = (float)time/(float)duration;
        return -change * progress*(progress-2) + from;
    }

    /**
     * Quadratic easing for scale and center animations. With thanks to Robert Penner - http://gizma.com/easing/
     * @param time Elapsed time
     * @param from Start value
     * @param change Target value
     * @param duration Anm duration
     * @return Current value
     */
    private float easeInOutQuad(long time, float from, float change, long duration) {
        float timeF = time/(duration/2f);
        if (timeF < 1) {
            return (change/2f * timeF * timeF) + from;
        } else {
            timeF--;
            return (-change/2f) * (timeF * (timeF - 2) - 1) + from;
        }
    }

    /**
     * Set the maximum scale allowed. A value of 1 means 1:1 pixels at maximum scale. You may wish to set this according
     * to screen density - on a retina screen, 1:1 may still be too small. Consider using {@link #setMinimumDpi(int)},
     * which is density aware.
     */
    public final void setMaxScale(float maxScale) {
        this.maxScale = maxScale;
    }

    /**
     * This is a screen density aware alternative to {@link #setMaxScale(float)}; it allows you to express the maximum
     * allowed scale in terms of the minimum pixel density. This avoids the problem of 1:1 scale still being
     * too small on a high density screen. A sensible starting point is 160 - the default used by this view.
     * @param dpi Source image pixel density at maximum zoom.
     */
    public final void setMinimumDpi(int dpi) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float averageDpi = (metrics.xdpi + metrics.ydpi)/2;
        setMaxScale(averageDpi/dpi);
    }

    /**
     * Returns the source point at the center of the view.
     */
    public final PointF getCenter() {
        int mX = getWidth()/2;
        int mY = getHeight()/2;
        return viewToSourceCoord(mX, mY);
    }

    /**
     * Returns the current scale value.
     */
    public final float getScale() {
        return scale;
    }

    /**
     * Externally change the scale and translation of the source image. This may be used with getCenter() and getScale()
     * to restore the scale and zoom after a screen rotate.
     * @param scale New scale to set.
     * @param sCenter New source image coordinate to center on the screen, subject to boundaries.
     */
    public final void setScaleAndCenter(float scale, PointF sCenter) {
        this.pendingScale = scale;
        this.sPendingCenter = sCenter;
        this.sRequestedCenter = sCenter;
        invalidate();
    }

    /**
     * Subclasses can override this method to be informed when the view is set up and ready for rendering, so they can
     * skip their own rendering until the base layer (and its scale and translate) are known.
     */
    protected void onImageReady() {

    }

    /**
     * Call to find whether the view is initialised and ready for rendering tiles.
     */
    public final boolean isImageReady() {
        return readySent;
    }

    /**
     * Get source width, ignoring orientation. If {@link #getOrientation()} returns 90 or 270, you can use {@link #getSHeight()}
     * for the apparent width.
     */
    public final int getSWidth() {
        return sWidth;
    }

    /**
     * Get source height, ignoring orientation. If {@link #getOrientation()} returns 90 or 270, you can use {@link #getSWidth()}
     * for the apparent height.
     */
    public final int getSHeight() {
        return sHeight;
    }

    /**
     * Returns the orientation setting. This can return {@link #ORIENTATION_USE_EXIF}, in which case it doesn't tell you
     * the applied orientation of the image. For that, use {@link #getAppliedOrientation()}.
     */
    public final int getOrientation() {
        return orientation;
    }

    /**
     * Returns the actual orientation of the image relative to the source file. This will be based on the source file's
     * EXIF orientation if you're using ORIENTATION_USE_EXIF. Values are 0, 90, 180, 270.
     */
    public final int getAppliedOrientation() {
        return getRequiredRotation();
    }

    /**
     * Get the current state of the view (scale, center, orientation) for restoration after rotate. Will return null if
     * the view is not ready.
     */
    public final ImageViewState getState() {
        if (vTranslate != null && sWidth > 0 && sHeight > 0) {
            return new ImageViewState(getScale(), getCenter(), getOrientation());
        }
        return null;
    }

    /**
     * Returns true if zoom gesture detection is enabled.
     */
    public final boolean isZoomEnabled() {
        return zoomEnabled;
    }

    /**
     * Enable or disable zoom gesture detection. Disabling zoom locks the the current scale.
     */
    public final void setZoomEnabled(boolean zoomEnabled) {
        this.zoomEnabled = zoomEnabled;
    }

    /**
     * Returns true if pan gesture detection is enabled.
     */
    public final boolean isPanEnabled() {
        return panEnabled;
    }

    /**
     * Enable or disable pan gesture detection. Disabling pan causes the image to be centered.
     */
    public final void setPanEnabled(boolean panEnabled) {
        this.panEnabled = panEnabled;
        if (!panEnabled && vTranslate != null) {
            vTranslate.x = (getWidth()/2) - (scale * (sWidth()/2));
            vTranslate.y = (getHeight()/2) - (scale * (sHeight()/2));
            refreshRequiredTiles(true);
            invalidate();
        }
    }

    /**
     * Set the scale the image will zoom in to when double tapped. This also the scale point where a double tap is interpreted
     * as a zoom out gesture - if the scale is greater than 90% of this value, a double tap zooms out. Avoid using values
     * greater than the max zoom.
     * @param doubleTapZoomScale New value for double tap gesture zoom scale.
     */
    public final void setDoubleTapZoomScale(float doubleTapZoomScale) {
        this.doubleTapZoomScale = doubleTapZoomScale;
    }

    /**
     * A density aware alternative to {@link #setDoubleTapZoomScale(float)}; this allows you to express the scale the
     * image will zoom in to when double tapped in terms of the image pixel density. Values lower than the max scale will
     * be ignored. A sensible starting point is 160 - the default used by this view.
     * @param dpi New value for double tap gesture zoom scale.
     */
    public final void setDoubleTapZoomDpi(int dpi) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float averageDpi = (metrics.xdpi + metrics.ydpi)/2;
        setDoubleTapZoomScale(averageDpi/dpi);
    }

    /**
     * Set the type of zoom animation to be used for double taps. See static fields.
     * @param doubleTapZoomStyle New value for zoom style.
     */
    public final void setDoubleTapZoomStyle(int doubleTapZoomStyle) {
        if (!VALID_ZOOM_STYLES.contains(doubleTapZoomStyle)) {
            throw new IllegalArgumentException("Invalid zoom style: " + doubleTapZoomStyle);
        }
        this.doubleTapZoomStyle = doubleTapZoomStyle;
    }

    /**
     * Enables visual debugging, showing tile boundaries and sizes.
     */
    public final void setDebug(boolean debug) {
        this.debug = debug;
    }

    /**
     * Creates a panning animation builder, that when started will animate the image to place the given coordinates of
     * the image in the center of the screen. If doing this would move the image beyond the edges of the screen, the
     * image is instead animated to move the center point as near to the center of the screen as is allowed - it's
     * guaranteed to be on screen.
     * @param sCenter Target center point
     * @return {@link CenterAnimationBuilder} instance. Call {@link com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.CenterAnimationBuilder#start()} to start the anim.
     */
    public CenterAnimationBuilder animateCenter(PointF sCenter) {
        if (!isImageReady()) {
            return null;
        }
        return new CenterAnimationBuilder(sCenter);
    }

    /**
     * Builder class used to set additional options for a pan animation. Create an instance using {@link #animateCenter(android.graphics.PointF)},
     * then set your options and call {@link #start()}.
     */
    public final class CenterAnimationBuilder {

        private final PointF sCenter;
        private long duration = 500;
        private int easing = EASE_IN_OUT_QUAD;
        private boolean interruptible = true;

        private CenterAnimationBuilder(PointF sCenter) {
            this.sCenter = sCenter;
        }

        /**
         * Desired duration of the anim in milliseconds.
         * @param duration duration in milliseconds.
         * @return this builder for method chaining.
         */
        public CenterAnimationBuilder withDuration(long duration) {
            this.duration = duration;
            return this;
        }

        /**
         * Whether the animation can be interrupted with a touch.
         * @param interruptible interruptible flag.
         * @return this builder for method chaining.
         */
        public CenterAnimationBuilder withInterruptible(boolean interruptible) {
            this.interruptible = interruptible;
            return this;
        }

        public CenterAnimationBuilder withEasing(int easing) {
            if (!VALID_EASING_STYLES.contains(easing)) {
                throw new IllegalArgumentException("Unknown easing type: " + easing);
            }
            this.easing = easing;
            return this;
        }

        /**
         * Starts the animation.
         */
        public void start() {
            translateAnim = new TranslateAnim();
            translateAnim.sCenter = sCenter;
            translateAnim.vTranslateStart = new PointF(vTranslate.x, vTranslate.y);
            translateAnim.vTranslateEnd = vTranslateForSCenter(sCenter, scale);
            translateAnim.duration = duration;
            translateAnim.interruptible = interruptible;
            translateAnim.easing = easing;
            translateAnim.time = System.currentTimeMillis();
            invalidate();
        }

    }
}

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
import android.graphics.*;
import android.graphics.Bitmap.Config;
import android.media.ExifInterface;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public static final int ORIENTATION_USE_EXIF = -1;
    public static final int ORIENTATION_0 = 0;
    public static final int ORIENTATION_90 = 90;
    public static final int ORIENTATION_180 = 180;
    public static final int ORIENTATION_270 = 270;

    // Image orientation setting
    private int orientation = ORIENTATION_0;

    // Max scale allowed (prevent infinite zoom)
    private float maxScale = 2F;

    private Context context;

    // Current scale and scale at start of zoom
    private float scale;
    private float scaleStart;

    // Screen coordinate of top-left corner of source image
    private PointF vTranslate;
    private PointF vTranslateStart;

    // Source coordinate to center on, used when new position is set externally before view is ready
    private Float pendingScale;
    private PointF sPendingCenter;

    // Source image dimensions and orientation - dimensions relate to the unrotated image
    private int sWidth;
    private int sHeight;
    private int sOrientation;

    // Is two-finger zooming in progress
    private boolean isZooming;
    // Is one-finger panning in progress
    private boolean isPanning;
    // Is an press in progress
    private boolean isPressed;

    // Fling detector
    private GestureDetector detector;

    // Tile decoder
    private BitmapRegionDecoder decoder;

    // Sample size used to display the whole image when fully zoomed out
    private int fullImageSampleSize;

    // Map of zoom level to tile grid
    private Map<Integer, List<Tile>> tileMap;

    // Debug values
    private PointF vCenterStart;
    private float vDistStart;

    // One-finger pan fling tracking variables
    private long flingStart = 0;
    private PointF flingFrom;
    private PointF flingMomentum;

    // Whether a ready notification has been sent to subclasses
    private boolean readySent = false;

    // Long click listener
    private OnLongClickListener onLongClickListener;

    // Long click handler
    private Handler handler;
    private static final int MESSAGE_LONG_CLICK = 1;

    public SubsamplingScaleImageView(Context context, AttributeSet attr) {
        super(context, attr);
        this.context = context;
        this.handler = new Handler(new Handler.Callback() {
            public boolean handleMessage(Message message) {
                if (message.what == MESSAGE_LONG_CLICK && onLongClickListener != null) {
                    isPressed = false;
                    SubsamplingScaleImageView.super.setOnLongClickListener(onLongClickListener);
                    performLongClick();
                    SubsamplingScaleImageView.super.setOnLongClickListener(null);
                }
                return true;
            }
        });
    }

    public SubsamplingScaleImageView(Context context) {
        this(context, null);
    }

    /**
     * Sets the image orientation. It's best to call this before setting the image file or asset, because it may waste
     * loading of tiles. However, this can be freely called at any time.
     */
    public void setOrientation(int orientation) {
        if (orientation != ORIENTATION_0 &&
                orientation != ORIENTATION_90 &&
                orientation != ORIENTATION_180 &&
                orientation != ORIENTATION_270 &&
                orientation != ORIENTATION_USE_EXIF) {
            throw new IllegalArgumentException("Invalid orientation: " + orientation);
        }
        this.orientation = orientation;
        reset(false);
        try {
            initialize();
            invalidate();
        } catch (IOException e) {
            Log.e(TAG, "Image view orientation change failed", e);
        }
    }

    /**
     * Display an image from a file in internal or external storage
     * @param extFile URI of the file to display
     */
    public void setImageFile(String extFile) throws IOException {
        reset(true);
        BitmapInitTask task = new BitmapInitTask(this, getContext(), extFile, false);
        task.execute();
        try {
            initialize();
            invalidate();
        } catch (IOException e) {
            Log.e(TAG, "Image view init failed", e);
        }
    }

    /**
     * Display an image from a file in assets.
     * @param assetName asset name.
     */
    public void setImageAsset(String assetName) throws IOException {
        reset(true);
        BitmapInitTask task = new BitmapInitTask(this, getContext(), assetName, true);
        task.execute();
        try {
            initialize();
            invalidate();
        } catch (IOException e) {
            Log.e(TAG, "Image view init failed", e);
        }
    }

    /**
     * Reset all state before setting/changing image or setting new rotation.
     */
    private void reset(boolean newImage) {
        setOnTouchListener(null);
        scale = 0f;
        scaleStart = 0f;
        vTranslate = null;
        vTranslateStart = null;
        pendingScale = 0f;
        sPendingCenter = null;
        isZooming = false;
        isPanning = false;
        isPressed = false;
        detector = null;
        fullImageSampleSize = 0;
        tileMap = null;
        vCenterStart = null;
        vDistStart = 0;
        flingStart = 0;
        flingFrom = null;
        flingMomentum = null;
        if (newImage) {
            if (decoder != null) {
                synchronized (decoder) {
                    decoder.recycle();
                }
                decoder = null;
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
        }
    }

    /**
     * Set the maximum scale allowed
     */
    public void setMaxScale(float maxScale) {
        this.maxScale = maxScale;
    }

    /**
     * Sets up gesture detection. Nothing else is done until onDraw() is called because the view dimensions will normally
     * be unknown when this method is called.
     */
    private void initialize() throws IOException {
        detector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (vTranslate != null && (Math.abs(e1.getX() - e2.getX()) > 50 || Math.abs(e1.getY() - e2.getY()) > 50) && (Math.abs(velocityX) > 500 || Math.abs(velocityY) > 500) && !isZooming) {
                    flingMomentum = new PointF(velocityX * 0.5f, velocityY * 0.5f);
                    flingFrom = new PointF(vTranslate.x, vTranslate.y);
                    flingStart = System.currentTimeMillis();
                    invalidate();
                    return true;
                }
                return super.onFling(e1, e2, velocityX, velocityY);
            }
        });
    }

    /**
     * Handle touch events. One finger pans, and two finger pinch and zoom plus panning.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        PointF vCenterEnd;
        float vDistEnd;
        flingMomentum = null;
        flingFrom = null;
        // Abort if not ready
        if (vTranslate == null) {
            return true;
        }
        // Detect flings
        if (detector == null || detector.onTouchEvent(event)) {
            return true;
        }

        int touchCount = event.getPointerCount();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_1_DOWN:
            case MotionEvent.ACTION_POINTER_2_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                isPressed = true;
                if (touchCount >= 2) {
                    // Start pinch to zoom. Calculate distance between touch points and center point of the pinch.
                    float distance = distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1));
                    scaleStart = scale;
                    vDistStart = distance;
                    vTranslateStart = new PointF(vTranslate.x, vTranslate.y);
                    vCenterStart = new PointF((event.getX(0) + event.getX(1))/2, (event.getY(0) + event.getY(1))/2);

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
                if (isPressed) {
                    if (touchCount >= 2) {
                        // Calculate new distance between touch points, to scale and pan relative to start values.
                        vDistEnd = distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1));
                        vCenterEnd = new PointF((event.getX(0) + event.getX(1))/2, (event.getY(0) + event.getY(1))/2);

                        if (distance(vCenterStart.x, vCenterEnd.x, vCenterStart.y, vCenterEnd.y) > 5 || Math.abs(vDistEnd - vDistStart) > 5 || isPanning) {
                            isZooming = true;
                            isPanning = true;
                            consumed = true;

                            scale = Math.min(maxScale, (vDistEnd / vDistStart) * scaleStart);

                            // Translate to place the source image coordinate that was at the center of the pinch at the start
                            // at the center of the pinch now, to give simultaneous pan + zoom.
                            float vLeftStart = vCenterStart.x - vTranslateStart.x;
                            float vTopStart = vCenterStart.y - vTranslateStart.y;
                            float vLeftNow = vLeftStart * (scale/scaleStart);
                            float vTopNow = vTopStart * (scale/scaleStart);
                            vTranslate.x = vCenterEnd.x - vLeftNow;
                            vTranslate.y = vCenterEnd.y - vTopNow;

                            fitToBounds();
                            refreshRequiredTiles(false);
                        }
                    } else if (!isZooming) {
                        // One finger pan - translate the image
                        float dx = Math.abs(event.getX() - vCenterStart.x);
                        float dy = Math.abs(event.getY() - vCenterStart.y);
                        if (dx > 5 || dy > 5 || isPanning) {
                            consumed = true;
                            vTranslate.x = vTranslateStart.x + (event.getX() - vCenterStart.x);
                            vTranslate.y = vTranslateStart.y + (event.getY() - vCenterStart.y);

                            float lastX = vTranslate.x;
                            float lastY = vTranslate.y;
                            fitToBounds();
                            if (lastX == vTranslate.x || (lastY == vTranslate.y && dy > 10) || isPanning) {
                                isPanning = true;
                            } else if (dx > 5) {
                                // Haven't panned the image, and we're at the left or right edge. Switch to page swipe.
                                isPressed = false;
                                handler.removeMessages(MESSAGE_LONG_CLICK);
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
                if (isPressed && (isZooming || isPanning)) {
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
                    }
                    // Trigger load of tiles now required
                    refreshRequiredTiles(true);
                    return true;
                } else if (isPressed) {
                    performClick();
                }
                if (touchCount == 1) {
                    isZooming = false;
                    isPanning = false;
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

        Paint paint = new Paint();

        // If image or view dimensions are not known yet, abort.
        if (sWidth == 0 || sHeight == 0 || decoder == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }

        // On first render with no tile map ready, initialise it and kick off async base image loading.
        if (tileMap == null) {
            initialiseBaseLayer();
            return;
        }

        // If waiting to translate to new center position, set translate now
        if (sPendingCenter != null && pendingScale != null) {
            scale = pendingScale;
            vTranslate.x = (getWidth()/2) - (scale * sPendingCenter.x);
            vTranslate.y = (getHeight()/2) - (scale * sPendingCenter.y);
            sPendingCenter = null;
            pendingScale = null;
            refreshRequiredTiles(true);
        }

        // On first display of base image set up position, and in other cases make sure scale is correct.
        fitToBounds();

        // Everything is set up and coordinates are valid. Inform subclasses.
        if (!readySent) {
            onImageReady();
            readySent = true;
        }

        // If animating a fling, calculate the position with easing equations.
        long flingElapsed = System.currentTimeMillis() - flingStart;
        if (flingMomentum != null && flingFrom != null && flingElapsed < 500) {
            vTranslate.x = easeOutQuad(flingElapsed, flingFrom.x, flingMomentum.x/2, 500);
            vTranslate.y = easeOutQuad(flingElapsed, flingFrom.y, flingMomentum.y/2, 500);
            fitToBounds();
            refreshRequiredTiles(false);
            invalidate();
        } else if (flingMomentum != null && flingFrom != null && flingElapsed >= 500) {
            refreshRequiredTiles(true);
            flingMomentum = null;
            flingFrom = null;
            isPanning = false;
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
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);
        for (Map.Entry<Integer, List<Tile>> tileMapEntry : tileMap.entrySet()) {
            if (tileMapEntry.getKey() == sampleSize || hasMissingTiles) {
                for (Tile tile : tileMapEntry.getValue()) {
                    if (!tile.loading && tile.bitmap != null) {
                        canvas.drawBitmap(tile.bitmap, null, convertRect(sourceToViewRect(tile.sRect)), paint);
                    }
                }
            }
        }

    }

    /**
     * Called on first draw when the view has dimensions. Calculates the initial sample size and starts async loading of
     * the base layer image - the whole source subsampled as necessary.
     */
    private synchronized void initialiseBaseLayer() {

        fitToBounds();

        // Load double resolution - next level will be split into four tiles and at the center all four are required,
        // so don't bother with tiling until the next level 16 tiles are needed.
        fullImageSampleSize = calculateInSampleSize((int)(sWidth() * scale), (int)(sHeight() * scale));
        if (fullImageSampleSize > 1) {
            fullImageSampleSize /= 2;
        }

        initialiseTileMap();

        List<Tile> baseGrid = tileMap.get(fullImageSampleSize);
        for (Tile baseTile : baseGrid) {
            BitmapTileTask task = new BitmapTileTask(this, decoder, baseTile);
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
                            BitmapTileTask task = new BitmapTileTask(this, decoder, tile);
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
     * Adjusts scale and translate values to keep scale within the allowed range and the image on screen. Minimum scale
     * is set to one dimension fills the view and the image is centered on the other dimension.
     */
    private void fitToBounds() {
        if (vTranslate == null) {
            vTranslate = new PointF(0, 0);
        }

        float minScale = Math.min(getWidth() / (float) sWidth(), getHeight() / (float) sHeight);
        scale = Math.max(minScale, scale);
        scale = Math.min(maxScale, scale);

        float scaleWidth = scale * sWidth();
        float scaleHeight = scale * sHeight();

        vTranslate.x = Math.max(vTranslate.x, getWidth() - scaleWidth);
        vTranslate.y = Math.max(vTranslate.y, getHeight() - scaleHeight);

        float maxTx = Math.max(0, (getWidth() - scaleWidth) / 2);
        float maxTy = Math.max(0, (getHeight() - scaleHeight) / 2);

        vTranslate.x = Math.min(vTranslate.x, maxTx);
        vTranslate.y = Math.min(vTranslate.y, maxTy);
    }

    /**
     * Once source image and view dimensions are known, creates a map of sample size to tile grid.
     */
    private void initialiseTileMap() {
        this.tileMap = new LinkedHashMap<Integer, List<Tile>>();
        int sampleSize = fullImageSampleSize;
        int tilesPerSide = 1;
        while (true) {
            int sTileWidth = sWidth()/tilesPerSide;
            int sTileHeight = sHeight()/tilesPerSide;
            int subTileWidth = sTileWidth/sampleSize;
            int subTileHeight = sTileHeight/sampleSize;
            while (subTileWidth > 2048 || subTileHeight > 2048) {
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
        private final WeakReference<Tile> tileRef;

        public BitmapTileTask(SubsamplingScaleImageView view, BitmapRegionDecoder decoder, Tile tile) {
            this.viewRef = new WeakReference<SubsamplingScaleImageView>(view);
            this.decoderRef = new WeakReference<BitmapRegionDecoder>(decoder);
            this.tileRef = new WeakReference<Tile>(tile);
            tile.loading = true;
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            try {
                if (decoderRef != null && tileRef != null && viewRef != null) {
                    final BitmapRegionDecoder decoder = decoderRef.get();
                    final Tile tile = tileRef.get();
                    final SubsamplingScaleImageView view = viewRef.get();
                    if (decoder != null && tile != null && view != null && !decoder.isRecycled()) {
                        synchronized (decoder) {
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
    public PointF viewToSourceCoord(PointF vxy) {
        return viewToSourceCoord(vxy.x, vxy.y);
    }

    /**
     * Convert screen coordinate to source coordinate.
     */
    public PointF viewToSourceCoord(float vx, float vy) {
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
    public PointF sourceToViewCoord(PointF sxy) {
        return sourceToViewCoord(sxy.x, sxy.y);
    }

    /**
     * Convert source coordinate to screen coordinate.
     */
    public PointF sourceToViewCoord(float sx, float sy) {
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
     * Returns the source point at the center of the view.
     */
    public PointF getCenter() {
        int mX = getWidth()/2;
        int mY = getHeight()/2;
        return viewToSourceCoord(mX, mY);
    }

    /**
     * Returns the current scale value.
     */
    public float getScale() {
        return scale;
    }

    /**
     * Externally change the scale and translation of the source image. This may be used with getCenter() and getScale()
     * to restore the scale and zoom after a screen rotate.
     * @param scale New scale to set.
     * @param sCenter New source image coordinate to center on the screen, subject to boundaries.
     */
    public void setScaleAndCenter(float scale, PointF sCenter) {
        this.pendingScale = scale;
        this.sPendingCenter = sCenter;
        invalidate();
    }

    /**
     * Subclasses can override this method to be informed when the view is set up and ready for rendering, so they can
     * skip their own rendering until the base layer (and its scale and translate) are known.
     */
    protected void onImageReady() {

    }

    /**
     * Call from subclasses to find whether the view is initialised and ready for rendering tiles.
     */
    protected boolean isImageReady() {
        return readySent;
    }

}

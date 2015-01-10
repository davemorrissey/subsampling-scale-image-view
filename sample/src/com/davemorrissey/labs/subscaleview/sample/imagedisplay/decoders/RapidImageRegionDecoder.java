package com.davemorrissey.labs.subscaleview.sample.imagedisplay.decoders;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;

import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder;

import java.io.InputStream;

import rapid.decoder.BitmapDecoder;

/**
 * A very simple implementation of {@link com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder}
 * using the RapidDecoder library (https://github.com/suckgamony/RapidDecoder). For PNGs, this can
 * give more reliable decoding and better performance. For JPGs, it has limitations and bugs that
 * make it unsuitable.
 *
 * This is an incomplete and untested implementation provided as an example only.
 */
public class RapidImageRegionDecoder implements ImageRegionDecoder {

    private static final String ASSET_PREFIX = "file:///android_asset/";

    private Context context;
    private Uri uri;

    @Override
    public Point init(Context context, Uri uri) throws Exception {
        this.context = context;
        this.uri = uri;
        BitmapDecoder decoder = newDecoder();
        return new Point(decoder.sourceWidth(), decoder.sourceHeight());
    }

    private BitmapDecoder newDecoder() throws Exception {
        BitmapDecoder decoder;
        String uriString = uri.toString();
        if (uriString.startsWith(ASSET_PREFIX)) {
            String assetName = uriString.substring(ASSET_PREFIX.length());
            InputStream in = context.getAssets().open(assetName);
            decoder = BitmapDecoder.from(in);
        } else {
            decoder = BitmapDecoder.from(context, uri);
        }
        // RapidDecoder's PNG library works well, the JPG library has serious problems. This is a crude check,
        // and won't work for resources. An app should not use this decoder for JPGs.
        return decoder.useBuiltInDecoder(uriString.toLowerCase().endsWith(".png"));
    }

    @Override
    public synchronized Bitmap decodeRegion(Rect sRect, int sampleSize) {
        try {
            return newDecoder().region(sRect).scale(sRect.width() / sampleSize, sRect.height()/sampleSize).decode();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isReady() {
        return context != null && uri != null;
    }

    @Override
    public void recycle() {
        BitmapDecoder.destroyMemoryCache();
        BitmapDecoder.destroyDiskCache();
        context = null;
        uri = null;
    }
}

package com.davemorrissey.labs.subscaleview;

import android.app.Activity;
import android.graphics.PointF;
import android.os.Bundle;
import android.util.Log;
import com.davemorrissey.labs.subscaleview.R.id;

import java.io.IOException;

public class DemoActivity extends Activity {

    private static final String STATE_SCALE = "state-scale";
    private static final String STATE_CENTER = "state-center";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        try {
            SubsamplingScaleImageView imageView = (SubsamplingScaleImageView)findViewById(id.imageView);
            imageView.setImageAsset("DSC00266.JPG");

            if (savedInstanceState != null && savedInstanceState.containsKey(STATE_SCALE) && savedInstanceState.containsKey(STATE_CENTER)) {
                imageView.setScaleAndCenter(savedInstanceState.getFloat(STATE_SCALE), (PointF)savedInstanceState.getParcelable(STATE_CENTER));
            }
        } catch (IOException e) {
            Log.e(DemoActivity.class.getSimpleName(), "Could not load asset", e);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        SubsamplingScaleImageView imageView = (SubsamplingScaleImageView)findViewById(id.imageView);
        outState.putFloat(STATE_SCALE, imageView.getScale());
        outState.putParcelable(STATE_CENTER, imageView.getCenter());
    }
}

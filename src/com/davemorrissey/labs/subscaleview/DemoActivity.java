package com.davemorrissey.labs.subscaleview;

import android.app.Activity;
import android.graphics.PointF;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import com.davemorrissey.labs.subscaleview.R.id;

import java.io.IOException;

public class DemoActivity extends Activity implements OnClickListener {

    private static final String STATE_SCALE = "state-scale";
    private static final String STATE_CENTER_X = "state-center-x";
    private static final String STATE_CENTER_Y = "state-center-y";

    private int orientation = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        findViewById(id.rotate).setOnClickListener(this);
        try {
            SubsamplingScaleImageView imageView = (SubsamplingScaleImageView)findViewById(id.imageView);
            imageView.setOrientation(orientation);
            imageView.setImageAsset("DSC04285.JPG");

            if (savedInstanceState != null &&
                    savedInstanceState.containsKey(STATE_SCALE) &&
                    savedInstanceState.containsKey(STATE_CENTER_X) &&
                    savedInstanceState.containsKey(STATE_CENTER_Y)) {
                imageView.setScaleAndCenter(savedInstanceState.getFloat(STATE_SCALE), new PointF(savedInstanceState.getFloat(STATE_CENTER_X), savedInstanceState.getFloat(STATE_CENTER_Y)));
            }
        } catch (IOException e) {
            Log.e(DemoActivity.class.getSimpleName(), "Could not load asset", e);
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == id.rotate) {
            orientation = (orientation + 90) % 360;
            SubsamplingScaleImageView imageView = (SubsamplingScaleImageView)findViewById(id.imageView);
            imageView.setOrientation(orientation);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        SubsamplingScaleImageView imageView = (SubsamplingScaleImageView)findViewById(id.imageView);
        outState.putFloat(STATE_SCALE, imageView.getScale());
        PointF center = imageView.getCenter();
        if (center != null) {
            outState.putFloat(STATE_CENTER_X, center.x);
            outState.putFloat(STATE_CENTER_Y, center.y);
        }
    }
}

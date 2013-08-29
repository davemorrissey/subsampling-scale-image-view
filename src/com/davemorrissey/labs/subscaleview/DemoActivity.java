package com.davemorrissey.labs.subscaleview;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;
import com.davemorrissey.labs.subscaleview.R.id;

import java.io.FileDescriptor;
import java.io.IOException;

public class DemoActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        try {
            ((SubsamplingScaleImageView)findViewById(id.imageView)).setImageAsset("DSC00266.JPG");
        } catch (IOException e) {
            Log.e(DemoActivity.class.getSimpleName(), "Could not load asset", e);
        }
    }

}

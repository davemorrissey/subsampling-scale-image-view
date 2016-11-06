package com.davemorrissey.labs.subscaleview.sample.flipping;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.davemorrissey.labs.subscaleview.sample.R;

/**
 * Created by vitaly on 22.10.16.
 */

public class FlippingActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.flipping_activity);
        final SubsamplingScaleImageView imageView = (SubsamplingScaleImageView) findViewById(R.id.flip_image);
        imageView.setImage(ImageSource.asset("card.png"));

        findViewById(R.id.horizontal_flip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                imageView.setFlipping(SubsamplingScaleImageView.Flipping.HORIZONTAL);
                imageView.setImage(ImageSource.asset("card.png"));
            }
        });

        findViewById(R.id.vertical_flip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                imageView.setFlipping(SubsamplingScaleImageView.Flipping.VERTICAL);
                imageView.setImage(ImageSource.asset("card.png"));
            }
        });
    }
}

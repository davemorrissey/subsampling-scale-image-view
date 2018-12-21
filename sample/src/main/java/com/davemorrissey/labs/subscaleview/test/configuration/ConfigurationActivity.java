package com.davemorrissey.labs.subscaleview.test.configuration;

import android.graphics.PointF;
import android.os.Bundle;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.davemorrissey.labs.subscaleview.test.AbstractPagesActivity;
import com.davemorrissey.labs.subscaleview.test.Page;
import com.davemorrissey.labs.subscaleview.test.R.id;

import java.util.Arrays;

import androidx.annotation.Nullable;

import static com.davemorrissey.labs.subscaleview.test.R.layout.pages_activity;
import static com.davemorrissey.labs.subscaleview.test.R.string.configuration_p10_subtitle;
import static com.davemorrissey.labs.subscaleview.test.R.string.configuration_p10_text;
import static com.davemorrissey.labs.subscaleview.test.R.string.configuration_p1_subtitle;
import static com.davemorrissey.labs.subscaleview.test.R.string.configuration_p1_text;
import static com.davemorrissey.labs.subscaleview.test.R.string.configuration_p2_subtitle;
import static com.davemorrissey.labs.subscaleview.test.R.string.configuration_p2_text;
import static com.davemorrissey.labs.subscaleview.test.R.string.configuration_p3_subtitle;
import static com.davemorrissey.labs.subscaleview.test.R.string.configuration_p3_text;
import static com.davemorrissey.labs.subscaleview.test.R.string.configuration_p4_subtitle;
import static com.davemorrissey.labs.subscaleview.test.R.string.configuration_p4_text;
import static com.davemorrissey.labs.subscaleview.test.R.string.configuration_p5_subtitle;
import static com.davemorrissey.labs.subscaleview.test.R.string.configuration_p5_text;
import static com.davemorrissey.labs.subscaleview.test.R.string.configuration_p6_subtitle;
import static com.davemorrissey.labs.subscaleview.test.R.string.configuration_p6_text;
import static com.davemorrissey.labs.subscaleview.test.R.string.configuration_p7_subtitle;
import static com.davemorrissey.labs.subscaleview.test.R.string.configuration_p7_text;
import static com.davemorrissey.labs.subscaleview.test.R.string.configuration_p8_subtitle;
import static com.davemorrissey.labs.subscaleview.test.R.string.configuration_p8_text;
import static com.davemorrissey.labs.subscaleview.test.R.string.configuration_p9_subtitle;
import static com.davemorrissey.labs.subscaleview.test.R.string.configuration_p9_text;
import static com.davemorrissey.labs.subscaleview.test.R.string.configuration_title;

public class ConfigurationActivity extends AbstractPagesActivity {

    private SubsamplingScaleImageView view;

    public ConfigurationActivity() {
        super(configuration_title, pages_activity, Arrays.asList(
                new Page(configuration_p1_subtitle, configuration_p1_text),
                new Page(configuration_p2_subtitle, configuration_p2_text),
                new Page(configuration_p3_subtitle, configuration_p3_text),
                new Page(configuration_p4_subtitle, configuration_p4_text),
                new Page(configuration_p5_subtitle, configuration_p5_text),
                new Page(configuration_p6_subtitle, configuration_p6_text),
                new Page(configuration_p7_subtitle, configuration_p7_text),
                new Page(configuration_p8_subtitle, configuration_p8_text),
                new Page(configuration_p9_subtitle, configuration_p9_text),
                new Page(configuration_p10_subtitle, configuration_p10_text)
        ));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = findViewById(id.imageView);
        view.setImage(ImageSource.Companion.asset("card.png"));
    }

    @Override
    protected void onPageChanged(int page) {
        if (page == 0) {
            view.setMinimumDpi(50);
        } else {
            view.setMaxScale(2F);
        }
        if (page == 1) {
            view.setMinimumTileDpi(50);
        } else {
            view.setMinimumTileDpi(320);
        }
        if (page == 6) {
            view.setDoubleTapZoomDpi(240);
        } else {
            view.setDoubleTapZoomScale(1F);
        }
        if (page == 7) {
            view.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_CENTER);
        } else if (page == 8) {
            view.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_OUTSIDE);
        } else {
            view.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE);
        }
        if (page == 9) {
            view.setDebug(true);
        } else {
            view.setDebug(false);
        }
        if (page == 2) {
            view.setScaleAndCenter(0f, new PointF(3900, 3120));
        }
        if (page == 3) {
            view.setScaleAndCenter(1f, new PointF(3900, 3120));
            view.setZoomEnabled(false);
        } else {
            view.setZoomEnabled(true);
        }
    }

}

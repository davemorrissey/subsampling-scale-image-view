package com.davemorrissey.labs.subscaleview.test.animation;

import android.graphics.PointF;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.AnimationBuilder;
import com.davemorrissey.labs.subscaleview.test.AbstractPagesActivity;
import com.davemorrissey.labs.subscaleview.test.Page;
import com.davemorrissey.labs.subscaleview.test.R.id;
import com.davemorrissey.labs.subscaleview.test.extension.views.PinView;

import java.util.Arrays;
import java.util.Random;

import static com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.*;
import static com.davemorrissey.labs.subscaleview.test.R.string.*;
import static com.davemorrissey.labs.subscaleview.test.R.layout.*;

public class AnimationActivity extends AbstractPagesActivity {

    private PinView view;

    public AnimationActivity() {
        super(animation_title, animation_activity, Arrays.asList(
                new Page(animation_p1_subtitle, animation_p1_text),
                new Page(animation_p2_subtitle, animation_p2_text),
                new Page(animation_p3_subtitle, animation_p3_text),
                new Page(animation_p4_subtitle, animation_p4_text)
        ));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        findViewById(id.play).setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { AnimationActivity.this.play(); }
        });
        view = findViewById(id.imageView);
        view.setImage(ImageSource.asset("sanmartino.jpg"));
    }

    @Override
    protected void onPageChanged(int page) {
        if (page == 2) {
            view.setPanLimit(PAN_LIMIT_CENTER);
        } else {
            view.setPanLimit(PAN_LIMIT_INSIDE);
        }
    }

    private void play() {
        Random random = new Random();
        if (view.isReady()) {
            float maxScale = view.getMaxScale();
            float minScale = view.getMinScale();
            float scale = (random.nextFloat() * (maxScale - minScale)) + minScale;
            PointF center = new PointF(random.nextInt(view.getSWidth()), random.nextInt(view.getSHeight()));
            view.setPin(center);
            AnimationBuilder animationBuilder = view.animateScaleAndCenter(scale, center);
            if (getPage() == 3) {
                animationBuilder.withDuration(2000).withEasing(EASE_OUT_QUAD).withInterruptible(false).start();
            } else {
                animationBuilder.withDuration(750).start();
            }
        }
    }

}

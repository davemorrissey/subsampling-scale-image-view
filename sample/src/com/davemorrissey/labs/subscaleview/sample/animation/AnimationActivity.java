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

package com.davemorrissey.labs.subscaleview.sample.animation;

import android.app.Activity;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.AnimationBuilder;
import com.davemorrissey.labs.subscaleview.sample.R.id;
import com.davemorrissey.labs.subscaleview.sample.R.layout;
import com.davemorrissey.labs.subscaleview.sample.extension.views.PinView;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class AnimationActivity extends Activity implements OnClickListener {

    private static final String BUNDLE_POSITION = "position";

    private int position;

    private List<Note> notes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layout.animation_activity);
        getActionBar().setTitle("Animation");
        getActionBar().setDisplayHomeAsUpEnabled(true);
        findViewById(id.next).setOnClickListener(this);
        findViewById(id.previous).setOnClickListener(this);
        findViewById(id.play).setOnClickListener(this);
        if (savedInstanceState != null && savedInstanceState.containsKey(BUNDLE_POSITION)) {
            position = savedInstanceState.getInt(BUNDLE_POSITION);
        }
        notes = Arrays.asList(
                new Note("A demo", "Tap the play button. The image will scale and zoom to a random point, shown by a marker."),
                new Note("Limited pan", "If the target point is near the edge of the image, it will be moved as near to the center as possible."),
                new Note("Unlimited pan", "With unlimited or center-limited pan, the target point can always be animated to the center."),
                new Note("Customisation", "Duration and easing are configurable. You can also make animations non-interruptible.")
        );

        initialiseImage();
        updateNotes();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(BUNDLE_POSITION, position);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == id.next) {
            position++;
            updateNotes();
        } else if (view.getId() == id.previous) {
            position--;
            updateNotes();
        } else if (view.getId() == id.play) {
            PinView pinView = (PinView)findViewById(id.imageView);
            Random random = new Random();
            if (pinView.isReady()) {
                float maxScale = pinView.getMaxScale();
                float minScale = pinView.getMinScale();
                float scale = (random.nextFloat() * (maxScale - minScale)) + minScale;
                PointF center = new PointF(random.nextInt(pinView.getSWidth()), random.nextInt(pinView.getSHeight()));
                pinView.setPin(center);
                AnimationBuilder animationBuilder = pinView.animateScaleAndCenter(scale, center);
                if (position == 3) {
                    animationBuilder.withDuration(2000).withEasing(SubsamplingScaleImageView.EASE_OUT_QUAD).withInterruptible(false).start();
                } else {
                    animationBuilder.withDuration(750).start();
                }
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        finish();
        return true;
    }

    private void initialiseImage() {
        SubsamplingScaleImageView imageView = (SubsamplingScaleImageView)findViewById(id.imageView);
        imageView.setImage(ImageSource.asset("squirrel.jpg"));
    }

    private void updateNotes() {
        if (position > notes.size() - 1) {
            return;
        }
        getActionBar().setSubtitle(notes.get(position).subtitle);
        ((TextView)findViewById(id.note)).setText(notes.get(position).text);
        findViewById(id.next).setVisibility(position >= notes.size() - 1 ? View.INVISIBLE : View.VISIBLE);
        findViewById(id.previous).setVisibility(position <= 0 ? View.INVISIBLE : View.VISIBLE);

        SubsamplingScaleImageView imageView = (SubsamplingScaleImageView)findViewById(id.imageView);
        if (position == 2) {
            imageView.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_CENTER);
        } else {
            imageView.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE);
        }

    }

    private static final class Note {
        private final String text;
        private final String subtitle;
        private Note(String subtitle, String text) {
            this.subtitle = subtitle;
            this.text = text;
        }
    }

}

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

package com.davemorrissey.labs.subscaleview.sample.configuration;

import android.app.Activity;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.davemorrissey.labs.subscaleview.sample.R.id;
import com.davemorrissey.labs.subscaleview.sample.R.layout;

import java.util.Arrays;
import java.util.List;

public class ConfigurationActivity extends Activity implements OnClickListener {

    private static final String BUNDLE_POSITION = "position";

    private int position;

    private List<Note> notes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layout.notes_activity);
        getActionBar().setTitle("Configuration");
        getActionBar().setDisplayHomeAsUpEnabled(true);
        findViewById(id.next).setOnClickListener(this);
        findViewById(id.previous).setOnClickListener(this);
        if (savedInstanceState != null && savedInstanceState.containsKey(BUNDLE_POSITION)) {
            position = savedInstanceState.getInt(BUNDLE_POSITION);
        }
        notes = Arrays.asList(
                new Note("Maximum scale", "The maximum scale has been set to 50dpi. You can zoom in until the image is very pixellated."),
                new Note("Minimum tile DPI", "The minimum tile DPI has been set to 50dpi, to reduce memory usage. The next layer of tiles will not be loaded until the image is very pixellated."),
                new Note("Pan disabled", "Dragging has been disabled. You can only zoom in to the centre point."),
                new Note("Zoom disabled", "Zooming has been disabled. You can drag the image around."),
                new Note("Double tap style", "On double tap, the tapped point is now zoomed to the center of the screen instead of remaining in the same place."),
                new Note("Double tap style", "On double tap, the zoom now happens immediately."),
                new Note("Double tap scale", "The double tap zoom scale has been set to 240dpi."),
                new Note("Pan limit center", "The pan limit has been changed to PAN_LIMIT_CENTER. Panning stops when a corner reaches the centre of the screen."),
                new Note("Pan limit outside", "The pan limit has been changed to PAN_LIMIT_OUTSIDE. Panning stops when the image is just off screen."),
                new Note("Debug", "Debug has been enabled. This shows the tile boundaries and sizes.")
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
        if (position == 0) {
            imageView.setMinimumDpi(50);
        } else {
            imageView.setMaxScale(2F);
        }
        if (position == 1) {
            imageView.setMinimumTileDpi(50);
        } else {
            imageView.setMinimumTileDpi(500);
        }
        if (position == 4) {
            imageView.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER);
        } else if (position == 5) {
            imageView.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER_IMMEDIATE);
        } else {
            imageView.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_FIXED);
        }
        if (position == 6) {
            imageView.setDoubleTapZoomDpi(240);
        } else {
            imageView.setDoubleTapZoomScale(1F);
        }
        if (position == 7) {
            imageView.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_CENTER);
        } else if (position == 8) {
            imageView.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_OUTSIDE);
        } else {
            imageView.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE);
        }
        if (position == 9) {
            imageView.setDebug(true);
        } else {
            imageView.setDebug(false);
        }
        if (position == 2) {
            imageView.setScaleAndCenter(0f, new PointF(1228, 816));
            imageView.setPanEnabled(false);
        } else {
            imageView.setPanEnabled(true);
        }
        if (position == 3) {
            imageView.setScaleAndCenter(1f, new PointF(1228, 816));
            imageView.setZoomEnabled(false);
        } else {
            imageView.setZoomEnabled(true);
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

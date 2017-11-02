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

package com.davemorrissey.labs.subscaleview.sample.basicfeatures;

import android.app.Activity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.davemorrissey.labs.subscaleview.sample.R.id;
import com.davemorrissey.labs.subscaleview.sample.R.layout;
import com.davemorrissey.labs.subscaleview.sample.imagedisplay.decoders.RapidImageRegionDecoder;

import java.util.Arrays;
import java.util.List;

public class BasicFeaturesActivity extends Activity implements OnClickListener {

    private static final String BUNDLE_POSITION = "position";

    private int position;

    private List<Note> notes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layout.notes_activity);
        getActionBar().setTitle("Basic features");
        getActionBar().setDisplayHomeAsUpEnabled(true);
        findViewById(id.next).setOnClickListener(this);
        findViewById(id.previous).setOnClickListener(this);
        if (savedInstanceState != null && savedInstanceState.containsKey(BUNDLE_POSITION)) {
            position = savedInstanceState.getInt(BUNDLE_POSITION);
        }
        notes = Arrays.asList(
                new Note("Pinch to zoom", "Use a two finger pinch to zoom in and out. The zoom is centred on the pinch gesture, and you can pan at the same time."),
                new Note("Quick scale", "Double tap and swipe up or down to zoom in or out. The zoom is centred where you tapped."),
                new Note("Drag", "Use one finger to drag the image around."),
                new Note("Fling", "If you drag quickly and let go, fling momentum keeps the image moving."),
                new Note("Double tap", "Double tap the image to zoom in to that spot. Double tap again to zoom out.")
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

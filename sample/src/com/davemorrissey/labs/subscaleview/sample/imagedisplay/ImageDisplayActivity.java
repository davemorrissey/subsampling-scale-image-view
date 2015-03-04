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

package com.davemorrissey.labs.subscaleview.sample.imagedisplay;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;
import com.davemorrissey.labs.subscaleview.sample.R.id;
import com.davemorrissey.labs.subscaleview.sample.R.layout;

import java.util.Arrays;
import java.util.List;

public class ImageDisplayActivity extends FragmentActivity {

    private static final String BUNDLE_POSITION = "position";

    private int position;

    private List<Page> pages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layout.extension_activity);
        getActionBar().setTitle("Image display");
        getActionBar().setDisplayHomeAsUpEnabled(true);
        if (savedInstanceState != null && savedInstanceState.containsKey(BUNDLE_POSITION)) {
            position = savedInstanceState.getInt(BUNDLE_POSITION);
        }
        pages = Arrays.asList(
                new Page("Large images", ImageDisplayLargeFragment.class),
                new Page("Rotation", ImageDisplayRotateFragment.class),
                new Page("Display region", ImageDisplayRegionFragment.class)
        );

        updatePage();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(BUNDLE_POSITION, position);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        finish();
        return true;
    }

    public void next() {
        position++;
        updatePage();
    }

    public void previous() {
        position--;
        updatePage();
    }

    private void updatePage() {
        if (position > pages.size() - 1) {
            return;
        }
        getActionBar().setSubtitle(pages.get(position).subtitle);
        try {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(id.frame, (Fragment)pages.get(position).clazz.newInstance())
                    .commit();
        } catch (Exception e) {
            Log.e("something", "Failed to load fragment", e);
            Toast.makeText(this, "Whoops, couldn't load the fragment!", Toast.LENGTH_SHORT).show();
        }
    }

    private static final class Page {
        private final String subtitle;
        private final Class<?> clazz;
        private Page(String subtitle, Class<?> clazz) {
            this.subtitle = subtitle;
            this.clazz = clazz;
        }
    }

}

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

package com.davemorrissey.labs.subscaleview.sample.viewpager;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;

import com.davemorrissey.labs.subscaleview.sample.R;
import com.davemorrissey.labs.subscaleview.sample.R.layout;

import java.util.Arrays;
import java.util.List;

public class ViewPagerActivity extends FragmentActivity implements OnClickListener {

    private static final String[] IMAGES = { "ness.jpg", "squirrel.jpg" };

    private static final String BUNDLE_POSITION = "position";

    private int position;

    private List<Note> notes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layout.view_pager);
        getActionBar().setTitle("View pager gallery");
        getActionBar().setDisplayHomeAsUpEnabled(true);
        findViewById(R.id.next).setOnClickListener(this);
        findViewById(R.id.previous).setOnClickListener(this);
        if (savedInstanceState != null && savedInstanceState.containsKey(BUNDLE_POSITION)) {
            position = savedInstanceState.getInt(BUNDLE_POSITION);
        }
        notes = Arrays.asList(
                new Note("Horizontal", "This gallery has two images in a ViewPager. Swipe to move to the next image. If you're zoomed in on an image, you need to pan to the right of it, then swipe again to activate the pager."),
                new Note("Vertical", "Vertical view pagers are also supported. Swipe up to move to the next image. If you're zoomed in on an image, you need to pan to the bottom of it, then swipe again to activate the pager.")
        );

        updateNotes();

        ViewPager horizontalPager = (ViewPager)findViewById(R.id.horizontal_pager);
        horizontalPager.setAdapter(new ScreenSlidePagerAdapter(getSupportFragmentManager()));
        ViewPager verticalPager = (ViewPager)findViewById(R.id.vertical_pager);
        verticalPager.setAdapter(new ScreenSlidePagerAdapter(getSupportFragmentManager()));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(BUNDLE_POSITION, position);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.next) {
            position++;
            updateNotes();
        } else if (view.getId() == R.id.previous) {
            position--;
            updateNotes();
        } else if (view.getId() == R.id.imageView) {
            Toast.makeText(this, "Clicked", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        ViewPager viewPager = (ViewPager)findViewById(R.id.horizontal_pager);
        if (position == 1) {
            viewPager = (ViewPager)findViewById(R.id.vertical_pager);
        }
        if (viewPager.getCurrentItem() == 0) {
            super.onBackPressed();
        } else {
            viewPager.setCurrentItem(viewPager.getCurrentItem() - 1);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        finish();
        return true;
    }

    private class ScreenSlidePagerAdapter extends FragmentStatePagerAdapter {
        public ScreenSlidePagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            ViewPagerFragment fragment = new ViewPagerFragment();
            fragment.setAsset(IMAGES[position]);
            return fragment;
        }

        @Override
        public int getCount() {
            return IMAGES.length;
        }
    }

    private void updateNotes() {
        if (position > notes.size() - 1) {
            return;
        }
        if (position == 0) {
            findViewById(R.id.horizontal_pager).setVisibility(View.VISIBLE);
            findViewById(R.id.vertical_pager).setVisibility(View.GONE);
        } else {
            findViewById(R.id.horizontal_pager).setVisibility(View.GONE);
            findViewById(R.id.vertical_pager).setVisibility(View.VISIBLE);
        }
        getActionBar().setSubtitle(notes.get(position).subtitle);
        ((TextView)findViewById(R.id.note)).setText(notes.get(position).text);
        findViewById(R.id.next).setVisibility(position >= notes.size() - 1 ? View.INVISIBLE : View.VISIBLE);
        findViewById(R.id.previous).setVisibility(position <= 0 ? View.INVISIBLE : View.VISIBLE);
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

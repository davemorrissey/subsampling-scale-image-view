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
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.MenuItem;
import com.davemorrissey.labs.subscaleview.sample.R;
import com.davemorrissey.labs.subscaleview.sample.R.layout;

public class ViewPagerActivity extends FragmentActivity {

    private static final String[] IMAGES = { "ness.jpg", "squirrel.jpg" };

    private ViewPager page;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layout.view_pager);
        getActionBar().setTitle("View pager gallery");
        getActionBar().setDisplayHomeAsUpEnabled(true);
        PagerAdapter pagerAdapter = new ScreenSlidePagerAdapter(getSupportFragmentManager());
        page = (ViewPager)findViewById(R.id.pager);
        page.setAdapter(pagerAdapter);
    }

    @Override
    public void onBackPressed() {
        if (page.getCurrentItem() == 0) {
            super.onBackPressed();
        } else {
            page.setCurrentItem(page.getCurrentItem() - 1);
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


}

package com.davemorrissey.labs.subscaleview.test.viewpager;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;

import com.davemorrissey.labs.subscaleview.test.AbstractPagesActivity;
import com.davemorrissey.labs.subscaleview.test.Page;
import com.davemorrissey.labs.subscaleview.test.R;

import java.util.Arrays;

import static com.davemorrissey.labs.subscaleview.test.R.layout.view_pager;
import static com.davemorrissey.labs.subscaleview.test.R.string.pager_p1_subtitle;
import static com.davemorrissey.labs.subscaleview.test.R.string.pager_p1_text;
import static com.davemorrissey.labs.subscaleview.test.R.string.pager_p2_subtitle;
import static com.davemorrissey.labs.subscaleview.test.R.string.pager_p2_text;
import static com.davemorrissey.labs.subscaleview.test.R.string.pager_title;

public class ViewPagerActivity extends AbstractPagesActivity {

    private static final String[] IMAGES = { "sanmartino.jpg", "swissroad.jpg" };

    public ViewPagerActivity() {
        super(pager_title, view_pager, Arrays.asList(
                new Page(pager_p1_subtitle, pager_p1_text),
                new Page(pager_p2_subtitle, pager_p2_text)
        ));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ViewPager horizontalPager = findViewById(R.id.horizontal_pager);
        horizontalPager.setAdapter(new ScreenSlidePagerAdapter(getSupportFragmentManager()));
        ViewPager verticalPager = findViewById(R.id.vertical_pager);
        verticalPager.setAdapter(new ScreenSlidePagerAdapter(getSupportFragmentManager()));
    }

    @Override
    public void onBackPressed() {
        ViewPager viewPager = findViewById(getPage() == 0 ? R.id.horizontal_pager : R.id.vertical_pager);
        if (viewPager.getCurrentItem() == 0) {
            super.onBackPressed();
        } else {
            viewPager.setCurrentItem(viewPager.getCurrentItem() - 1);
        }
    }

    @Override
    protected void onPageChanged(int page) {
        if (getPage() == 0) {
            findViewById(R.id.horizontal_pager).setVisibility(View.VISIBLE);
            findViewById(R.id.vertical_pager).setVisibility(View.GONE);
        } else {
            findViewById(R.id.horizontal_pager).setVisibility(View.GONE);
            findViewById(R.id.vertical_pager).setVisibility(View.VISIBLE);
        }
    }

    private class ScreenSlidePagerAdapter extends FragmentStatePagerAdapter {
        ScreenSlidePagerAdapter(FragmentManager fm) {
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

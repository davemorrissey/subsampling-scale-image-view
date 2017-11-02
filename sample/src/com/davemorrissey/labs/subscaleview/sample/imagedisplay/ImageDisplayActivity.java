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

import android.support.v4.app.Fragment;
import android.util.Log;

import com.davemorrissey.labs.subscaleview.sample.AbstractFragmentsActivity;
import com.davemorrissey.labs.subscaleview.sample.Page;
import com.davemorrissey.labs.subscaleview.sample.R.id;

import java.util.Arrays;
import java.util.List;

import static com.davemorrissey.labs.subscaleview.sample.R.string.*;
import static com.davemorrissey.labs.subscaleview.sample.R.layout.*;

public class ImageDisplayActivity extends AbstractFragmentsActivity {

    private static final List<Class<? extends Fragment>> FRAGMENTS = Arrays.asList(
            ImageDisplayLargeFragment.class,
            ImageDisplayRotateFragment.class,
            ImageDisplayRegionFragment.class
    );

    public ImageDisplayActivity() {
        super(display_title, fragments_activity, Arrays.asList(
                new Page(display_p1_subtitle, display_p1_text),
                new Page(display_p2_subtitle, display_p2_text),
                new Page(display_p3_subtitle, display_p3_text)
        ));
    }

    @Override
    protected void onPageChanged(int page) {
        try {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(id.frame, FRAGMENTS.get(page).newInstance())
                    .commit();
        } catch (Exception e) {
            Log.e(ImageDisplayActivity.class.getName(), "Failed to load fragment", e);
        }
    }

}

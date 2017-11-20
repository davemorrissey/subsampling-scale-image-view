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

package com.davemorrissey.labs.subscaleview.test.extension;

import android.support.v4.app.Fragment;
import android.util.Log;

import com.davemorrissey.labs.subscaleview.test.AbstractFragmentsActivity;
import com.davemorrissey.labs.subscaleview.test.Page;
import com.davemorrissey.labs.subscaleview.test.R.id;
import com.davemorrissey.labs.subscaleview.test.imagedisplay.ImageDisplayActivity;

import java.util.Arrays;
import java.util.List;

import static com.davemorrissey.labs.subscaleview.test.R.layout.fragments_activity;
import static com.davemorrissey.labs.subscaleview.test.R.string.extension_p1_subtitle;
import static com.davemorrissey.labs.subscaleview.test.R.string.extension_p1_text;
import static com.davemorrissey.labs.subscaleview.test.R.string.extension_p2_subtitle;
import static com.davemorrissey.labs.subscaleview.test.R.string.extension_p2_text;
import static com.davemorrissey.labs.subscaleview.test.R.string.extension_p3_subtitle;
import static com.davemorrissey.labs.subscaleview.test.R.string.extension_p3_text;
import static com.davemorrissey.labs.subscaleview.test.R.string.extension_title;

public class ExtensionActivity extends AbstractFragmentsActivity {

    private static final List<Class<? extends Fragment>> FRAGMENTS = Arrays.asList(
            ExtensionPinFragment.class,
            ExtensionCircleFragment.class,
            ExtensionFreehandFragment.class
    );
    
    public ExtensionActivity() {
        super(extension_title, fragments_activity, Arrays.asList(
                new Page(extension_p1_subtitle, extension_p1_text),
                new Page(extension_p2_subtitle, extension_p2_text),
                new Page(extension_p3_subtitle, extension_p3_text)
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

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

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

package com.davemorrissey.labs.subscaleview.sample.autozoom;

import android.os.Bundle;
import android.support.annotation.Nullable;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.davemorrissey.labs.subscaleview.sample.AbstractPagesActivity;
import com.davemorrissey.labs.subscaleview.sample.Page;
import com.davemorrissey.labs.subscaleview.sample.R;

import java.util.Arrays;

import static com.davemorrissey.labs.subscaleview.sample.R.layout.pages_activity;
import static com.davemorrissey.labs.subscaleview.sample.R.string.autozoom_p1_text;
import static com.davemorrissey.labs.subscaleview.sample.R.string.autozoom_title;

public class AutoZoomActivity extends AbstractPagesActivity {

    private SubsamplingScaleImageView view;

    public AutoZoomActivity() {
        super(autozoom_title, pages_activity, Arrays.asList(
                new Page(autozoom_p1_text, autozoom_p1_text),
                new Page(autozoom_p1_text, autozoom_p1_text)
        ));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = findViewById(R.id.imageView);
    }

    @Override
    protected void onPageChanged(int page) {
        super.onPageChanged(page);
        if(page == 0) {
            view.setImage(ImageSource.asset("long_image2.jpg"));
        } else {
            view.setImage(ImageSource.asset("long_image.jpg"));
        }
    }
}

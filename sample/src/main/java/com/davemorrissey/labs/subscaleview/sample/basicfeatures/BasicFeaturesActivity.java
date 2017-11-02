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

import android.os.Bundle;
import android.support.annotation.Nullable;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.davemorrissey.labs.subscaleview.sample.AbstractPagesActivity;
import com.davemorrissey.labs.subscaleview.sample.Page;
import com.davemorrissey.labs.subscaleview.sample.R.id;

import java.util.Arrays;

import static com.davemorrissey.labs.subscaleview.sample.R.string.*;
import static com.davemorrissey.labs.subscaleview.sample.R.layout.*;

public class BasicFeaturesActivity extends AbstractPagesActivity {

    public BasicFeaturesActivity() {
        super(basic_title, pages_activity, Arrays.asList(
                new Page(basic_p1_subtitle, basic_p1_text),
                new Page(basic_p2_subtitle, basic_p2_text),
                new Page(basic_p3_subtitle, basic_p3_text),
                new Page(basic_p4_subtitle, basic_p4_text),
                new Page(basic_p5_subtitle, basic_p5_text)
        ));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SubsamplingScaleImageView view = findViewById(id.imageView);
        view.setImage(ImageSource.asset("pony.jpg"));
    }

}

package com.davemorrissey.labs.subscaleview.test.basicfeatures;

import android.os.Bundle;
import android.support.annotation.Nullable;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.davemorrissey.labs.subscaleview.test.AbstractPagesActivity;
import com.davemorrissey.labs.subscaleview.test.Page;
import com.davemorrissey.labs.subscaleview.test.R.id;

import java.util.Arrays;

import static com.davemorrissey.labs.subscaleview.test.R.string.*;
import static com.davemorrissey.labs.subscaleview.test.R.layout.*;

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
        view.setImage(ImageSource.asset("sanmartino.jpg"));
    }

}

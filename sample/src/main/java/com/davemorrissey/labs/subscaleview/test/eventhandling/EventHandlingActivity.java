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

package com.davemorrissey.labs.subscaleview.test.eventhandling;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.davemorrissey.labs.subscaleview.test.AbstractPagesActivity;
import com.davemorrissey.labs.subscaleview.test.Page;
import com.davemorrissey.labs.subscaleview.test.R.id;

import java.util.Arrays;

import static com.davemorrissey.labs.subscaleview.test.R.layout.*;
import static com.davemorrissey.labs.subscaleview.test.R.string.*;

public class EventHandlingActivity extends AbstractPagesActivity {

    public EventHandlingActivity() {
        super(event_title, pages_activity, Arrays.asList(
                new Page(event_p1_subtitle, event_p1_text),
                new Page(event_p2_subtitle, event_p2_text),
                new Page(event_p3_subtitle, event_p3_text)
        ));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SubsamplingScaleImageView imageView = findViewById(id.imageView);
        imageView.setImage(ImageSource.asset("pony.jpg"));
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Toast.makeText(v.getContext(), "Clicked", Toast.LENGTH_SHORT).show(); }
        });
        imageView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { Toast.makeText(v.getContext(), "Long clicked", Toast.LENGTH_SHORT).show(); return true; }
        });
    }

}

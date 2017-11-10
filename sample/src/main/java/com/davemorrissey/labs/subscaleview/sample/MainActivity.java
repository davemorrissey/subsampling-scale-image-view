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

package com.davemorrissey.labs.subscaleview.sample;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import com.davemorrissey.labs.subscaleview.sample.R.id;
import com.davemorrissey.labs.subscaleview.sample.animation.AnimationActivity;
import com.davemorrissey.labs.subscaleview.sample.autozoom.AutoZoomActivity;
import com.davemorrissey.labs.subscaleview.sample.basicfeatures.BasicFeaturesActivity;
import com.davemorrissey.labs.subscaleview.sample.configuration.ConfigurationActivity;
import com.davemorrissey.labs.subscaleview.sample.eventhandling.EventHandlingActivity;
import com.davemorrissey.labs.subscaleview.sample.eventhandlingadvanced.AdvancedEventHandlingActivity;
import com.davemorrissey.labs.subscaleview.sample.extension.ExtensionActivity;
import com.davemorrissey.labs.subscaleview.sample.imagedisplay.ImageDisplayActivity;
import com.davemorrissey.labs.subscaleview.sample.viewpager.ViewPagerActivity;

public class MainActivity extends Activity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.main_title);
        }
        setContentView(R.layout.main);
        findViewById(id.basicFeatures).setOnClickListener(this);
        findViewById(id.imageDisplay).setOnClickListener(this);
        findViewById(id.eventHandling).setOnClickListener(this);
        findViewById(id.advancedEventHandling).setOnClickListener(this);
        findViewById(id.viewPagerGalleries).setOnClickListener(this);
        findViewById(id.animation).setOnClickListener(this);
        findViewById(id.extension).setOnClickListener(this);
        findViewById(id.configuration).setOnClickListener(this);
        findViewById(id.autozoom).setOnClickListener(this);
        findViewById(id.github).setOnClickListener(this);
        findViewById(id.self).setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case id.basicFeatures: startActivity(BasicFeaturesActivity.class); break;
            case id.imageDisplay: startActivity(ImageDisplayActivity.class); break;
            case id.eventHandling: startActivity(EventHandlingActivity.class); break;
            case id.advancedEventHandling: startActivity(AdvancedEventHandlingActivity.class); break;
            case id.viewPagerGalleries: startActivity(ViewPagerActivity.class); break;
            case id.animation: startActivity(AnimationActivity.class); break;
            case id.extension: startActivity(ExtensionActivity.class); break;
            case id.configuration: startActivity(ConfigurationActivity.class); break;
            case id.autozoom: startActivity(AutoZoomActivity.class); break;
            case id.github: openGitHub(); break;
            case id.self: openSelf(); break;
        }
    }

    private void startActivity(Class<? extends Activity> activity) {
        Intent intent = new Intent(this, activity);
        startActivity(intent);
    }

    private void openGitHub() {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse("https://github.com/davemorrissey/subsampling-scale-image-view"));
        startActivity(i);
    }

    private void openSelf() {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse("http://www.davemorrissey.com"));
        startActivity(i);
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }
}

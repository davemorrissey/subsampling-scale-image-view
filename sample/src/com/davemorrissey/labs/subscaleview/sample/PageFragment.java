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

import android.graphics.PointF;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.Toast;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.davemorrissey.labs.subscaleview.sample.R.id;

public class PageFragment extends Fragment implements OnClickListener, OnLongClickListener {

    private static final String STATE_SCALE = "state-scale";
    private static final String STATE_CENTER_X = "state-center-x";
    private static final String STATE_CENTER_Y = "state-center-y";
    private static final String STATE_ORIENTATION = "state-orientation";
    private static final String STATE_ASSET = "state-asset";

    private int orientation = 0;

    private String asset;

    public PageFragment() {
    }

    public PageFragment(String asset) {
        this.asset = asset;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.page, container, false);

        if (savedInstanceState != null) {
            if (asset == null && savedInstanceState.containsKey(STATE_ASSET)) {
                asset = savedInstanceState.getString(STATE_ASSET);
            }
            if (savedInstanceState.containsKey(STATE_ORIENTATION)) {
                orientation = savedInstanceState.getInt(STATE_ORIENTATION);
            }
        }

        rootView.findViewById(id.rotate).setOnClickListener(this);
        if (asset != null) {
            SubsamplingScaleImageView imageView = (SubsamplingScaleImageView)rootView.findViewById(id.imageView);
            imageView.setOrientation(orientation);
            imageView.setImageAsset(asset);
            imageView.setOnClickListener(this);
            imageView.setOnLongClickListener(this);

            if (savedInstanceState != null &&
                    savedInstanceState.containsKey(STATE_SCALE) &&
                    savedInstanceState.containsKey(STATE_CENTER_X) &&
                    savedInstanceState.containsKey(STATE_CENTER_Y)) {
                imageView.setScaleAndCenter(savedInstanceState.getFloat(STATE_SCALE), new PointF(savedInstanceState.getFloat(STATE_CENTER_X), savedInstanceState.getFloat(STATE_CENTER_Y)));
            }
        }

        return rootView;
    }

    @Override
    public void onClick(View view) {
        View rootView = getView();
        if (view.getId() == id.rotate && rootView != null) {
            orientation = (orientation + 90) % 360;
            SubsamplingScaleImageView imageView = (SubsamplingScaleImageView)rootView.findViewById(id.imageView);
            imageView.setOrientation(orientation);
        } else if (view.getId() == id.imageView) {
            Toast.makeText(getActivity(), "Clicked", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onLongClick(View view) {
        if (view.getId() == id.imageView) {
            Toast.makeText(getActivity(), "Long clicked", Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        View rootView = getView();
        if (rootView != null) {
            SubsamplingScaleImageView imageView = (SubsamplingScaleImageView)rootView.findViewById(id.imageView);
            outState.putFloat(STATE_SCALE, imageView.getScale());
            PointF center = imageView.getCenter();
            if (center != null) {
                outState.putFloat(STATE_CENTER_X, center.x);
                outState.putFloat(STATE_CENTER_Y, center.y);
            }
            outState.putString(STATE_ASSET, asset);
            outState.putInt(STATE_ORIENTATION, orientation);
        }
    }


}

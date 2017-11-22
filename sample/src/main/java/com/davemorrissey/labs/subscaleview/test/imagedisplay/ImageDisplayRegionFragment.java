package com.davemorrissey.labs.subscaleview.test.imagedisplay;

import android.graphics.Rect;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.davemorrissey.labs.subscaleview.test.R.id;
import com.davemorrissey.labs.subscaleview.test.R.layout;

public class ImageDisplayRegionFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(layout.imagedisplay_region_fragment, container, false);
        final SubsamplingScaleImageView imageView = rootView.findViewById(id.imageView);
        imageView.setOrientation(SubsamplingScaleImageView.ORIENTATION_90);
        imageView.setImage(ImageSource.asset("card.png").region(new Rect(0, 0, 3778, 2834)));
        rootView.findViewById(id.previous).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { ((ImageDisplayActivity) ImageDisplayRegionFragment.this.getActivity()).previous(); }
        });
        rootView.findViewById(id.rotate).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { imageView.setOrientation((imageView.getOrientation() + 90) % 360); }
        });
        return rootView;
    }

}

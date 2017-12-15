package com.davemorrissey.labs.subscaleview.test.imagedisplay;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.davemorrissey.labs.subscaleview.test.R.id;
import com.davemorrissey.labs.subscaleview.test.R.layout;

public class ImageDisplayLargeFragment extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(layout.imagedisplay_large_fragment, container, false);
        rootView.findViewById(id.next).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { ((ImageDisplayActivity) ImageDisplayLargeFragment.this.getActivity()).next(); }
        });
        SubsamplingScaleImageView imageView = rootView.findViewById(id.imageView);
        imageView.setImage(ImageSource.asset("card.png"));
        return rootView;
    }

}

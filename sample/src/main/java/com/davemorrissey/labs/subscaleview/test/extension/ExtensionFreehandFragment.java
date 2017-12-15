package com.davemorrissey.labs.subscaleview.test.extension;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.test.R.id;
import com.davemorrissey.labs.subscaleview.test.R.layout;
import com.davemorrissey.labs.subscaleview.test.extension.views.FreehandView;

public class ExtensionFreehandFragment extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(layout.extension_freehand_fragment, container, false);
        final ExtensionActivity activity = (ExtensionActivity)getActivity();
        if (activity != null) {
            rootView.findViewById(id.previous).setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { activity.previous(); }
            });
        }
        final FreehandView imageView = rootView.findViewById(id.imageView);
        imageView.setImage(ImageSource.asset("sanmartino.jpg"));
        rootView.findViewById(id.reset).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { imageView.reset(); }
        });
        return rootView;
    }

}

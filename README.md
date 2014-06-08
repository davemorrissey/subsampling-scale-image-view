Subsampling Zoom Image View
===========================

A custom ImageView for Android with pinch to zoom and subsampled tiles to support large images. While zooming in, the
low resolution, full size base layer is overlaid with smaller tiles at least as high resolution as the screen, and
tiles are loaded and discarded during panning to avoid holding too much bitmap data in memory.

Ideal for use in image gallery apps where the size of the images may be large enough to require subsampling, and where
pinch to zoom is required to view the high resolution detail.

*This view doesn't extend `ImageView` and isn't intended as a general replacement for it. It's specialized for the display of much larger images than `ImageView` can display, so it's perfect for image galleries and displaying images from the camera.*

#### Image display
* Display huge images, larger than can be loaded into memory
* Show high resolution detail on zooming in
* Tested up to 20,000x13,000px, though larger images are slower
* Display images from assets or the file system
* Automatically rotate images from the file system (e.g. the camera or gallery) according to EXIF
* Manually rotate images in 90° increments
* Swap images at runtime

#### Gesture detection
* One finger pan
* Two finger pinch to zoom
* Pan while zooming
* Seamless switch between pan and zoom
* Fling momentum after panning
* Double tap to zoom in and out
* Options to disable pan and/or zoom gestures

#### Animation
* Public methods for animating the scale and center
* Customisable duration and easing
* Optional uninterruptible animations

#### Overridable event detection
* Supports `OnClickListener` and `OnLongClickListener`
* Supports interception of events using `GestureDetector` and `OnTouchListener`
* Extend to add your own gestures

#### Easy integration
* Use within a `ViewPager` to create a photo gallery
* Easily restore scale, center and orientation after screen rotation
* Can be extended to add overlay graphics that move and scale with the image
* Handles view resizing and `wrap_content` layout

#### Coming soon...
* Demonstration app

#### Limitations
* Requires SDK 10 (Gingerbread).
* No support for decoding an image from resources - the image file needs to be in assets or external storage.
* This view does not extend ImageView so attributes including android:tint, android:scaleType and android:src are not supported.
* Images stored in assets cannot be rotated based on EXIF, you'll need to do it manually. You probably know the orientation of your own assets :-)

## Quality notes

Images are decoded as dithered RGB_565 bitmaps by default, because this requires half as much memory as ARGB_8888. For most
JPGs you won't notice the difference in quality. If you are displaying large PNGs with alpha channels, Android will probably
decode them as ARGB_8888, and this may cause `OutOfMemoryError`s. **If possible, remove the alpha channel from PNGs larger than about 2,000x2,000.**
This allows them to be decoded as RGB_565.

## Basic setup

Checkout the project and import the library project as a module in your app. Alternatively you can just copy the classes in `com.davemorrissey.labs.subscaleview` to your project.

Add the view to your layout XML as shown below. Normally you should set width and height to `match_parent`.

    <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
        android:layout_width="match_parent"
        android:layout_height="match_parent" >

        <com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
            android:id="@+id/imageView"
            android:layout_width="match_parent"
            android:layout_height="match_parent"/>

    </RelativeLayout>

Now, in your fragment or activity, set the image asset name or file path.

    SubsamplingScaleImageView imageView = (SubsamplingScaleImageView)findViewById(id.imageView);
    imageView.setImageAsset("map.png");
    // ... or ...
    imageView.setImageFile("/sdcard/DCIM/DSCM00123.JPG");

That's it! Keep reading for some more options.

## Define asset name in XML

For a zero code approach to showing an image from your assets, you need to define the custom namespace in your layout.

    <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:ssiv="http://schemas.android.com/apk/res-auto"
        android:layout_width="match_parent"
        android:layout_height="match_parent" >

        <com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
            ssiv:assetName="map.png"
            android:layout_width="match_parent"
            android:layout_height="match_parent"/>
            
    </RelativeLayout>

**This method doesn't support restoring state after a screen orientation change.**

## Handle screen orientation changes

If you want the current scale, center and orientation to be preserved when the screen is rotated, you can request it from the view's `getState` method, and restore it after rotation, by passing it to the view along with the image asset name or file path. Here's a simple example of how you might do this in a fragment.

    private static final String BUNDLE_STATE = "ImageViewState";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.my_fragment, container, false);
        
        ImageViewState imageViewState = null;
        if (savedInstanceState != null && savedInstanceState.containsKey(BUNDLE_STATE)) {
            imageViewState = (ImageViewState)savedInstanceState.getSerializable(BUNDLE_STATE);
        }
        SubsamplingScaleImageView imageView = (SubsamplingScaleImageView)rootView.findViewById(id.imageView);
        imageView.setImageAsset("map.png", imageViewState);
        
        return rootView;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        View rootView = getView();
        if (rootView != null) {
            SubsamplingScaleImageView imageView = (SubsamplingScaleImageView)rootView.findViewById(id.imageView);
            ImageViewState state = imageView.getState();
            if (state != null) {
                outState.putSerializable(BUNDLE_STATE, imageView.getState());
            }
        }
    }

## Extending functionality

Take a look at the sample app for a simple implementation showing the view in a `ViewPager` with an `OnClickListener` and an `OnLongClickListener`. I'll add more examples to this soon.

## About

Copyright 2014 David Morrissey, and licensed under the Apache License, Version 2.0. No attribution is necessary but it's very much appreciated. Star this project to show your gratitude.

This project started life as a way of showing very large images (e.g. a large building floor plan) with gestures to pan and zoom, with support for extensions that showed overlays (location pins, annotations) aligned with the image. It's grown massively, but for the moment I am keeping everything in one class to prevent subclasses and extensions breaking the assumptions (or violating invariants) on which the class depends.
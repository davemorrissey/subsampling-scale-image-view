Subsampling Scale Image View
===========================

Custom image views for Android, designed for photo galleries and displaying huge images (e.g. maps and building plans) without `OutOfMemoryError`s. Includes pinch to zoom, panning, rotation and animation support, and allows easy extension so you can add your own overlays and touch event detection.

This library includes two view classes. `SubsamplingScaleImageView` uses subsampling and tiles to support large images - as you zoom in, the low resolution initial image is overlaid with smaller high resolution tiles to avoid holding too much data in memory. It's ideal for displaying large images while allowing you to zoom in to the high resolution details. `ScaleImageView` doesn't subsample images but can display `Bitmap` objects and supports all the same features. To decide which is best for you, see below.

#### Download the sample app

[![Get it on Google Play](https://developer.android.com/images/brand/en_generic_rgb_wo_60.png)](https://play.google.com/store/apps/details?id=com.davemorrissey.labs.subscaleview.sample)

#### Hall of fame

**Are you using this library in your app? Let me know and I'll add it to this list.**

| [![Fourth Mate](https://lh3.ggpht.com/2ALnL-05ILKLwP9U8Dfy7n4iI54OlXeZG-rHf31FP5l8Bup9wws9wnSlyX56ShgzlQ=w100)](https://play.google.com/store/apps/details?id=com.sleetworks.serenity.android) | [![Sync for reddit](https://lh5.ggpht.com/eOcmQUHHFCXM5uiajTkTsak5sIB5eTLKXaKSGGGWi8TJ3edYtqz8EtvjlOto5eFYvoLb=w100)](https://play.google.com/store/apps/details?id=com.laurencedawson.reddit_sync) | [![Journey](https://lh3.ggpht.com/Mz6YqxKsLfVbjYVHj_3nfUxLe5Yvl9W4KO2sKnwud6hZl5mnGitm55PnILT2jx4Hafv6=w100)](https://play.google.com/store/apps/details?id=com.journey.app)  |
|---|---|---|
| **Fourth Mate** | **Sync for reddit** | **Journal** |
| [![Clover](https://lh5.ggpht.com/Q8vw6LLyj3AjRev4ID3uvFUxnMp4ca4eBEaPlkupcK7cNn2xtVg-wIxVsKSJ-IIFaUM=w100)](https://play.google.com/store/apps/details?id=org.floens.chan)  | [![Tag Gallery](https://lh5.ggpht.com/mKch3_fgPYswBPmZ-qEvp91_fPKdbvN2UubCvUTDqy1sAaLJBzfFYETb-sJgPfCvDg=w100)](https://play.google.com/store/apps/details?id=me.snapdiary.us.taggallery)  | [![nycTrans.it](https://lh5.ggpht.com/eDe_bnb2KVXd6fwjJDroWYfEs7Qy-ity93s4LnOwei3S8AGZIeJy8wwmjllt1TKciD4=w100)](https://play.google.com/store/apps/details?id=com.nyctrans.it)  |
| **Clover** | **Tag Gallery** | **nycTrans.it** |

## Features

#### Image display

* Display images from assets, resources or the file system
* Automatically rotate images from the file system (e.g. the camera or gallery) according to EXIF
* Manually rotate images in 90° increments
* Swap images at runtime
* Use a custom bitmap decoder

*`SubsamplingScaleImageView` only:*

* Display huge images, larger than can be loaded into memory
* Show high resolution detail on zooming in
* Tested up to 20,000x13,000px, though larger images are slower

*These views don't extend `ImageView` and aren't intended as a general purpose replacement for it. They're specialised for the display of photos and other large images, not the display of 9-patches, shapes and the other types of drawable that ImageView supports.*

#### Gesture detection
* One finger pan
* Two finger pinch to zoom
* Quick scale (one finger zoom)
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

#### Limitations
* `SubsamplingScaleImageView` requires SDK 10 (Gingerbread).
* `SubsamplingScaleImageView` cannot display a `Bitmap` object - the image file needs to be in assets, resources or external storage.
* `SubsamplingScaleImageView` cannot display grayscale PNGs on Android Lollipop, due to bugs in the skia library and/or BitmapRegionDecoder. Earlier versions of Android also have issues displaying some grayscale PNGs, but not all. I have reported these bugs to Google. For a workaround, see the section on custom bitmap decoders below.
* These views do not extend ImageView so attributes including android:tint, android:scaleType and android:src are not supported.
* Images stored in resources and assets cannot be rotated based on EXIF, you'll need to do it manually. You probably know the orientation of your own files :-)

## Which view is best?

Use `SubsamplingScaleImageView` if:

* You want to zoom into very large images without losing detail.
* You need to display images of unknown size e.g. from the camera or gallery.
* You don't know if the images may be too large to fit in memory on some devices.
* You need to display images larger than 2048px.
* You don't need to support devices older than SDK 10.

Use `ScaleImageView` if:

* You know the size of the images you're displaying.
* You know the images are small enough to fit in memory on all your target devices.
* Your images are no larger than 2048px, or you are able to scale them down.
* You need to support devices older than SDK 10.

## Installation

Add the library to your app using one of these methods:

* Add `com.davemorrissey.labs:subsampling-scale-image-view:2.4.0` as a dependency in your build.gradle file
* *or* download the library aar file from the releases page and add to your app manually
* *or* clone the project and import the library subproject as a module in your app
* *or* clone the project and copy the resources and classes from `com.davemorrissey.labs.subscaleview` into your project

Add the view to your layout XML as shown below. Normally you should set width and height to `match_parent`.

    <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
        android:layout_width="match_parent"
        android:layout_height="match_parent" >

        <com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
            android:id="@+id/imageView"
            android:layout_width="match_parent"
            android:layout_height="match_parent"/>

    </RelativeLayout>

Now, in your fragment or activity, set the image resource, asset name or file path.

    SubsamplingScaleImageView imageView = (SubsamplingScaleImageView)findViewById(id.imageView);
    imageView.setImageResource(R.drawable.monkey);
    // ... or ...
    imageView.setImageAsset("map.png");
    // ... or ...
    imageView.setImageUri("/sdcard/DCIM/DSCM00123.JPG");

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

## Custom bitmap decoders

Android's `BitmapRegionDecoder` class is based on the Skia library. Some users have reported problems with this library, particularly when displaying grayscale JPGs. Unfortunately it seems to be less reliable in recent versions of Android. If you don't control the format of the images displayed in your app (for example, they are user generated content) you may require a more reliable decoder.

To use your own decoder based on a different library, implement the `ImageRegionDecoder` class (do not include a constructor) and enable it using this call:

    imageView.setDecoderClass(MyImageRegionDecoder.class);
    
As an example, see the [`RapidImageRegionDecoder`](https://github.com/davemorrissey/subsampling-scale-image-view/blob/master/sample/src/com/davemorrissey/labs/subscaleview/sample/imagedisplay/decoders/RapidImageRegionDecoder.java) class, which is based on [RapidDecoder](https://github.com/suckgamony/RapidDecoder). This library is better at decoding grayscale JPG images but does not handle large images as well as `BitmapRegionDecoder` - it is significantly slower and more likely to throw out of memory errors. It appears to be very fast and reliable for PNG images. If you can detect the size and type of an image before displaying it, you can use different decoders for different images to get the best results.

Whenever possible, convert your images to a format Android's Skia library can support, and test with a variety of devices.

## Quality notes

Images are decoded as dithered RGB_565 bitmaps by default, because this requires half as much memory as ARGB_8888. For most
JPGs you won't notice the difference in quality. If you are displaying large PNGs with alpha channels, Android will probably
decode them as ARGB_8888, and this may cause `OutOfMemoryError`s. **If possible, remove the alpha channel from PNGs larger than about 2,000x2,000.**
This allows them to be decoded as RGB_565.

## Extending functionality

Take a look at the sample app for examples of classes that overlay graphics on top of the image so that they move and scale with it. `FreehandView` adds event detection, capturing only the touch events it needs so pan and zoom still work normally.

## About

Copyright 2014 David Morrissey, and licensed under the Apache License, Version 2.0. No attribution is necessary but it's very much appreciated. Star this project if you like it, and send a link to your project on GitHub or app in Google Play if you'd like me to add it to this page.

Subsampling Scale Image View
===========================

A custom image view for Android, designed for photo galleries and displaying huge images (e.g. maps and building plans) without `OutOfMemoryError`s. Includes pinch to zoom, panning, rotation and animation support, and allows easy extension so you can add your own overlays and touch event detection.

The view optionally uses subsampling and tiles to support very large images - a low resolution base layer is loaded and as you zoom in, it is overlaid with smaller high resolution tiles for the visible area. This avoids holding too much data in memory. It's ideal for displaying large images while allowing you to zoom in to the high resolution details. You can disable tiling for smaller images and when displaying a bitmap object. There are some advantages and disadvantages to disabling tiling so to decide which is best, see [the wiki](https://github.com/davemorrissey/subsampling-scale-image-view/wiki/02.-Displaying-images).

#### Guides

* [Releases & downloads](https://github.com/davemorrissey/subsampling-scale-image-view/releases)
* [Installation and setup](https://github.com/davemorrissey/subsampling-scale-image-view/wiki/01.-Setup)
* [Image display notes & limitations](https://github.com/davemorrissey/subsampling-scale-image-view/wiki/02.-Displaying-images)
* [Using preview images](https://github.com/davemorrissey/subsampling-scale-image-view/wiki/03.-Preview-images)
* [Handling orientation changes](https://github.com/davemorrissey/subsampling-scale-image-view/wiki/05.-Orientation-changes)
* [Advanced configuration](https://github.com/davemorrissey/subsampling-scale-image-view/wiki/07.-Configuration)
* [Event handling](https://github.com/davemorrissey/subsampling-scale-image-view/wiki/09.-Events)
* [Animation](https://github.com/davemorrissey/subsampling-scale-image-view/wiki/08.-Animation)
* [Extension](https://github.com/davemorrissey/subsampling-scale-image-view/wiki/10.-Extension)

#### 2.x.x to 3.x.x migration

Version 3.x.x includes breaking changes. Please view the [migration guide](https://github.com/davemorrissey/subsampling-scale-image-view/wiki/X.--2.x.x-to-3.x.x-migration).

#### Download the sample app

[![Get it on Google Play](https://developer.android.com/images/brand/en_generic_rgb_wo_60.png)](https://play.google.com/store/apps/details?id=com.davemorrissey.labs.subscaleview.sample)

#### Hall of fame

**Are you using this library in your app? Let me know and I'll add it to this list.**

| [![Fourth Mate](https://lh3.ggpht.com/2ALnL-05ILKLwP9U8Dfy7n4iI54OlXeZG-rHf31FP5l8Bup9wws9wnSlyX56ShgzlQ=w100)](https://play.google.com/store/apps/details?id=com.sleetworks.serenity.android) | [![Sync for reddit](https://lh5.ggpht.com/eOcmQUHHFCXM5uiajTkTsak5sIB5eTLKXaKSGGGWi8TJ3edYtqz8EtvjlOto5eFYvoLb=w100)](https://play.google.com/store/apps/details?id=com.laurencedawson.reddit_sync) | [![Journey](https://lh3.ggpht.com/Mz6YqxKsLfVbjYVHj_3nfUxLe5Yvl9W4KO2sKnwud6hZl5mnGitm55PnILT2jx4Hafv6=w100)](https://play.google.com/store/apps/details?id=com.journey.app) | [![Clover](https://lh5.ggpht.com/Q8vw6LLyj3AjRev4ID3uvFUxnMp4ca4eBEaPlkupcK7cNn2xtVg-wIxVsKSJ-IIFaUM=w100)](https://play.google.com/store/apps/details?id=org.floens.chan) | [![Tag Gallery](https://lh5.ggpht.com/mKch3_fgPYswBPmZ-qEvp91_fPKdbvN2UubCvUTDqy1sAaLJBzfFYETb-sJgPfCvDg=w100)](https://play.google.com/store/apps/details?id=me.snapdiary.us.taggallery) |
|---|---|---|---|---|
| **Fourth Mate** | **Sync for reddit** | **Journal** | **Clover** | **Tag Gallery** |
| [![nycTrans.it](https://lh5.ggpht.com/eDe_bnb2KVXd6fwjJDroWYfEs7Qy-ity93s4LnOwei3S8AGZIeJy8wwmjllt1TKciD4=w100)](https://play.google.com/store/apps/details?id=com.nyctrans.it) | [![RR File Locker](https://lh4.ggpht.com/taUucj91wLM_uLq-XMLo-9Urk4SeQYW1WO4oquJ_ynyJPrX7S0j0xoQ8k6q66ZElFg=w100)](https://play.google.com/store/apps/details?id=com.redrabbitsw.android.locker) | [![TransitMe NYC](https://lh3.googleusercontent.com/aErQHPHTn6TeqmGRHybIZoEE7uF1MwTXTN4upuIsMpJurwWGuF_9tAhaxspMvTBdGaqG=w100)](https://play.google.com/store/apps/details?id=com.transitme.app) | [![Mr Whipped Comics](https://lh3.googleusercontent.com/1Wtvc4dM8ZBeUWB1dFqjrn_mrwq12xVbsux9QMXQrPL3VdZi2v6OuFQ7N0k5iT7tQw=w100)](https://play.google.com/store/apps/details?id=com.pst.mrwhipped) | [![Ornithopedia](https://lh3.ggpht.com/Y20EJCUVUJYNa6JcBuhl1bdSlcnoi4VlNcZcKnyR0Ip7WsfZ4rJxjAJII5D8bKuQeTRc=w100)](https://play.google.com/store/apps/details?id=org.ornithopedia.Europe) |
| **nycTrans.it** | **RR File Locker** | **TransitMe NYC** | **Mr Whipped Comics** | **Ornithopedia** |

## Features

#### Image display

* Display images from assets, resources, the file system or bitmaps
* Automatically rotate images from the file system (e.g. the camera or gallery) according to EXIF
* Manually rotate images in 90° increments
* Display a region of the source image
* Use a preview image while large images load
* Swap images at runtime
* Use a custom bitmap decoder

*With tiling enabled:*

* Display huge images, larger than can be loaded into memory
* Show high resolution detail on zooming in
* Tested up to 20,000x20,000px, though larger images are slower

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

## Quick start

**1)** Add `com.davemorrissey.labs:subsampling-scale-image-view:3.5.0` as a dependency in your build.gradle file.

**2)** Add the view to your layout XML.

    <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
        android:layout_width="match_parent"
        android:layout_height="match_parent" >

        <com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
            android:id="@+id/imageView"
            android:layout_width="match_parent"
            android:layout_height="match_parent"/>

    </LinearLayout>

**3)** Now, in your fragment or activity, set the image resource, asset name or file path.

    SubsamplingScaleImageView imageView = (SubsamplingScaleImageView)findViewById(id.imageView);
    imageView.setImage(ImageSource.resource(R.drawable.monkey));
    // ... or ...
    imageView.setImage(ImageSource.asset("map.png"))
    // ... or ...
    imageView.setImage(ImageSource.uri("/sdcard/DCIM/DSCM00123.JPG"));

## About

Copyright 2015 David Morrissey, and licensed under the Apache License, Version 2.0. No attribution is necessary but it's very much appreciated. Star this project if you like it, and send a link to your project on GitHub or app in Google Play if you'd like me to add it to this page.

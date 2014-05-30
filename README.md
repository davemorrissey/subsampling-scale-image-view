subsampling-scale-image-view
============================

A custom ImageView for Android with pinch to zoom and subsampled tiles to support large images. While zooming in, the
low resolution, full size base layer is overlaid with smaller tiles in the best resolution for the current scale, and
tiles are loaded and discarded during panning to avoid holding too much bitmap data in memory.

Ideal for use in image gallery apps where the size of the images may be large enough to require subsampling, and where
pinch to zoom is required to view the high resolution detail.

Tested with images up to 20000x13000px, but such large images are unusably slow to render.

Supports:
* Display of images of any size
* Loading from assets or external files
* Pinch to zoom
* Panning while zooming
* One finger pan with momentum
* Dynamically swapping image
* Dynamically rotating image (0, 90, 180, 270)
* Optional automatic rotation of image based on EXIF
* Can be easily extended to add overlays
* Compatible with OnClickListener and OnLongClickListener
* Works within a ViewPager to create a photo gallery
* Designed for extension
* Respects maximum bitmap size (in SDK14+ this is Canvas.getMaximumBitmapWidth/Height, for older versions 2048px)

Limitations:
* Requires SDK 10 (Gingerbread).
* BitmapRegionDecoder does not support decoding an image from resources - the image file needs to be in assets or external storage.
* Very wide or tall images may still cause out of memory errors because each tile has same w:h ratio as the source image. Fixing this should be fairly easy if required.
* This view does not extend ImageView so attributes including android:tint, android:scaleType and android:src are not supported.
* Images stored in assets cannot be rotated based on EXIF. This shouldn't be a problem as you'll know the orientation of your files.

subsampling-scale-image-view
============================

A custom ImageView for Android with pinch to zoom and subsampled tiles to support large images. While zooming in, the
low resolution base layer is overlaid with tiles in the best resolution for the current scale, and tiles are loaded and
discarded during panning to avoid loading too much image data.

Unfortunately it's difficult to know how much image data it's safe to load but the view appears stable on tested devices.

Supports:
* Display of images of any size
* Pinch to zoom
* Panning while zooming
* One finger pan with momentum
* Can be easily extended to add overlays
* Tiles over 2048px are avoided to support hardware acceleration

Limitations:
* Requires SDK 10 (Gingerbread).
* BitmapRegionDecoder does not support decoding an image from resources - the image file needs to be in assets or external storage.
* Very wide or tall images may still cause out of memory errors because each tile has same w:h ratio as the source image. Fixing this should be fairly easy if required.
* Does not support changing the image after one is loaded. This is another fairly simple change.

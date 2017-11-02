package com.davemorrissey.labs.subscaleview.decoder;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Compatibility factory to instantiate decoders with empty public constructors.
 * @param <T> The base type of the decoder this factory will produce.
 */
public class CompatDecoderFactory<T> implements DecoderFactory<T> {
  private Class<? extends T> clazz;
  private Bitmap.Config bitmapConfig;

  public CompatDecoderFactory(@NonNull Class<? extends T> clazz) {
    this(clazz, null);
  }

  public CompatDecoderFactory(@NonNull Class<? extends T> clazz, Bitmap.Config bitmapConfig) {
    this.clazz = clazz;
    this.bitmapConfig = bitmapConfig;
  }

  @Override
  public T make() throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
    if (bitmapConfig == null) {
      return clazz.newInstance();
    }
    else {
      Constructor<? extends T> ctor = clazz.getConstructor(Bitmap.Config.class);
      return ctor.newInstance(bitmapConfig);
    }
  }
}

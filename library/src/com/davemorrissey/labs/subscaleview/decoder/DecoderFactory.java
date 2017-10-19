package com.davemorrissey.labs.subscaleview.decoder;

import java.lang.reflect.InvocationTargetException;

/**
 * Interface for decoder (and region decoder) factories.
 * @param <T> the class of decoder that will be produced.
 */
public interface DecoderFactory<T> {
  /**
   * Produce a new instance of a decoder with type {@link T}.
   * @return a new instance of your decoder.
   */
  T make() throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException;
}

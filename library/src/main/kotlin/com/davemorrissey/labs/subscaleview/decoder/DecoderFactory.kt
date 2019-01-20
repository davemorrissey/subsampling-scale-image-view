package com.davemorrissey.labs.subscaleview.decoder

interface DecoderFactory<T> {
    fun make(): T
}

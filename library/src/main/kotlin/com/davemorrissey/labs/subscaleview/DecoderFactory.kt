package com.davemorrissey.labs.subscaleview

interface DecoderFactory<T> {
    fun make(): T
}

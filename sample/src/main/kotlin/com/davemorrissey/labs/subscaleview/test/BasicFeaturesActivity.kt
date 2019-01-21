package com.davemorrissey.labs.subscaleview.test

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.davemorrissey.labs.subscaleview.ImageSource
import kotlinx.android.synthetic.main.pages_activity.*

class BasicFeaturesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pages_activity)
        actionBar?.title = getString(R.string.basic_title)
        imageView.setImage(ImageSource.uri("file:///android_asset/sanmartino.jpg"))
    }
}

package com.azrael.pixivdumpsync

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView

class LoginActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
                addView(TextView(this@LoginActivity).apply {
                    text = "Pixiv authenticated transport is isolated from the repository build."
                    textSize = 18f
                })
            }
        )
    }
}

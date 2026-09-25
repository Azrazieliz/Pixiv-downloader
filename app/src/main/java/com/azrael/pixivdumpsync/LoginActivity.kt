package com.azrael.pixivdumpsync

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class LoginActivity : Activity() {
    private lateinit var input: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
        }

        root.addView(TextView(this).apply {
            text = "Pixiv cookies"
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = "Paste the Cookie header from your own logged-in Pixiv session. It must contain PHPSESSID. It stays in this app's private storage."
            setPadding(0, 12, 0, 12)
        })

        status = TextView(this)
        root.addView(status)

        input = EditText(this).apply {
            hint = "PHPSESSID=...; other_cookie=..."
            gravity = Gravity.TOP
            minLines = 6
            maxLines = 12
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        root.addView(
            input,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(Button(this).apply {
            text = "Save / replace cookies"
            setOnClickListener { save() }
        })

        root.addView(Button(this).apply {
            text = "Clear saved cookies"
            setOnClickListener {
                SessionStore.clearCookie(this@LoginActivity)
                input.setText("")
                refreshStatus()
                Toast.makeText(
                    this@LoginActivity,
                    "Saved cookies cleared",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        setContentView(root)
        refreshStatus()
    }

    private fun save() {
        val normalized = SessionStore.normalizeCookieInput(input.text.toString())
        if (!normalized.contains("PHPSESSID=")) {
            Toast.makeText(
                this,
                "PHPSESSID was not found in what you pasted",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        SessionStore.saveCookie(this, normalized)
        NetworkCookies.install(this)
        Toast.makeText(this, "Pixiv cookies saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun refreshStatus() {
        status.text = if (SessionStore.isLoggedIn(this)) {
            "Saved session: ready"
        } else {
            "Saved session: none"
        }
    }
}

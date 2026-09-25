package com.azrael.pixivdumpsync

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class LoginActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private var captured = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        status = TextView(this).apply {
            text = "Log in to Pixiv below. The app will save its own session automatically."
            textSize = 16f
            setPadding(0, 0, 0, 12)
        }
        root.addView(status)

        root.addView(Button(this).apply {
            text = "I finished logging in"
            setOnClickListener {
                if (!captureSession()) {
                    Toast.makeText(
                        this@LoginActivity,
                        "No Pixiv login session detected yet.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })

        root.addView(Button(this).apply {
            text = "Clear saved Pixiv session"
            setOnClickListener {
                SessionStore.clearCookie(this@LoginActivity)
                CookieManager.getInstance().removeAllCookies {
                    CookieManager.getInstance().flush()
                    captured = false
                    status.text = "Session cleared. Log in to Pixiv below."
                    webView.loadUrl(LOGIN_URL)
                }
            }
        })

        webView = WebView(this)
        root.addView(
            webView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(webView, true)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                captureSession()
            }
        }

        if (SessionStore.isLoggedIn(this)) {
            status.text = "A Pixiv session is already saved. You can close this screen or log in again."
        }

        webView.loadUrl(LOGIN_URL)
    }

    private fun captureSession(): Boolean {
        if (captured) return true

        val manager = CookieManager.getInstance()
        val candidates = listOf(
            manager.getCookie("https://www.pixiv.net"),
            manager.getCookie("https://accounts.pixiv.net")
        )

        val session = candidates
            .filterNotNull()
            .flatMap { it.split(';') }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("; ")

        if (!session.contains("PHPSESSID=")) {
            return false
        }

        manager.flush()
        SessionStore.saveCookie(this, session)
        NetworkCookies.install(this)
        captured = true

        status.text = "Pixiv login detected. Session saved."
        Toast.makeText(this, "Pixiv login saved", Toast.LENGTH_SHORT).show()
        webView.postDelayed({ finish() }, 500L)
        return true
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val LOGIN_URL = "https://accounts.pixiv.net/login?lang=en"
    }
}

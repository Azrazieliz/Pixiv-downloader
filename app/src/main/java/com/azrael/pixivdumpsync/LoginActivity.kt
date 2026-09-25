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
    private lateinit var confirmButton: Button
    private var checking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        status = TextView(this).apply {
            text = "Log in to Pixiv below. When you can see your Pixiv account, tap the button."
            textSize = 16f
            setPadding(0, 0, 0, 12)
        }
        root.addView(status)

        confirmButton = Button(this).apply {
            text = "I finished logging in"
            setOnClickListener { verifyAndSaveSession() }
        }
        root.addView(confirmButton)

        root.addView(Button(this).apply {
            text = "Clear saved Pixiv session"
            setOnClickListener {
                SessionStore.clearCookie(this@LoginActivity)
                CookieManager.getInstance().removeAllCookies {
                    CookieManager.getInstance().flush()
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
        webView.webViewClient = WebViewClient()

        webView.loadUrl(LOGIN_URL)
    }

    private fun verifyAndSaveSession() {
        if (checking) return

        val manager = CookieManager.getInstance()
        val session = listOf(
            manager.getCookie("https://www.pixiv.net"),
            manager.getCookie("https://accounts.pixiv.net")
        )
            .filterNotNull()
            .flatMap { it.split(';') }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("; ")

        if (!session.contains("PHPSESSID=")) {
            Toast.makeText(
                this,
                "Pixiv login is not complete yet.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        checking = true
        confirmButton.isEnabled = false
        status.text = "Checking your Pixiv login…"

        manager.flush()
        SessionStore.saveCookie(this, session, verified = false)
        NetworkCookies.install(this)

        Thread {
            val verified = try {
                PixivApi(applicationContext).verifyAuthenticatedSession()
            } catch (_: Throwable) {
                false
            }

            runOnUiThread {
                checking = false
                confirmButton.isEnabled = true

                if (verified) {
                    SessionStore.markVerified(this@LoginActivity)
                    status.text = "Pixiv login verified."
                    Toast.makeText(
                        this@LoginActivity,
                        "Pixiv login verified",
                        Toast.LENGTH_SHORT
                    ).show()
                    webView.postDelayed({ finish() }, 400L)
                } else {
                    SessionStore.clearCookie(this@LoginActivity)
                    status.text = "Pixiv did not confirm a logged-in account. Finish logging in, then try again."
                    Toast.makeText(
                        this@LoginActivity,
                        "Login not verified yet",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
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

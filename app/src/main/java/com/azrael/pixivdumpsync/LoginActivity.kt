package com.azrael.pixivdumpsync

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
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
    private var verifyWhenPixivLoads = false

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
            setOnClickListener { verifyInsideWebView() }
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
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(LoginBridge(), "PixivDumpLogin")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (
                    verifyWhenPixivLoads &&
                    url?.startsWith("https://www.pixiv.net/") == true
                ) {
                    verifyWhenPixivLoads = false
                    runAuthenticatedCheck()
                }
            }
        }

        webView.loadUrl(LOGIN_URL)
    }

    private fun verifyInsideWebView() {
        if (checking) return

        checking = true
        confirmButton.isEnabled = false
        status.text = "Checking your Pixiv login…"

        val url = webView.url.orEmpty()
        if (!url.startsWith("https://www.pixiv.net/")) {
            verifyWhenPixivLoads = true
            webView.loadUrl(HOME_URL)
            return
        }

        runAuthenticatedCheck()
    }

    private fun runAuthenticatedCheck() {
        val script = """
            (function() {
              fetch('/ajax/settings/self?lang=en', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Accept': 'application/json' }
              })
              .then(function(r) { return r.json(); })
              .then(function(j) {
                PixivDumpLogin.onResult(String(Boolean(j && j.error === false && j.body)));
              })
              .catch(function() {
                PixivDumpLogin.onResult('false');
              });
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }

    private fun handleVerificationResult(raw: String?) {
        runOnUiThread {
            if (!PixivWebLoginResult.isAuthenticated(raw)) {
                checking = false
                confirmButton.isEnabled = true
                status.text = "Pixiv did not confirm the login yet. Stay on Pixiv, then tap the button again."
                Toast.makeText(
                    this,
                    "Login not verified yet",
                    Toast.LENGTH_LONG
                ).show()
                return@runOnUiThread
            }

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
                checking = false
                confirmButton.isEnabled = true
                status.text = "Pixiv is logged in, but the session cookie was not available yet. Tap the button again."
                return@runOnUiThread
            }

            manager.flush()
            SessionStore.saveCookie(this, session, verified = true)
            NetworkCookies.install(this)

            checking = false
            status.text = "Pixiv login verified."
            Toast.makeText(this, "Pixiv login verified", Toast.LENGTH_SHORT).show()
            webView.postDelayed({ finish() }, 400L)
        }
    }

    private inner class LoginBridge {
        @JavascriptInterface
        fun onResult(value: String?) {
            handleVerificationResult(value)
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("PixivDumpLogin")
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val LOGIN_URL = "https://accounts.pixiv.net/login?lang=en"
        private const val HOME_URL = "https://www.pixiv.net/"
    }
}

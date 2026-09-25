package com.azrael.pixivdumpsync

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class LoginActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var confirmButton: Button
    private var checking = false
    private var verifyWhenPixivLoads = false

    private fun dp(value: Int) = UiKit.dp(this, value)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = UiKit.bg
        window.navigationBarColor = UiKit.bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(UiKit.bg)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val close = Button(this).apply {
            text = "‹"
            textSize = 28f
            setTextColor(UiKit.text)
            background = UiKit.ripple(
                this@LoginActivity,
                UiKit.surface,
                UiKit.surfaceAlt,
                radius = 14,
                stroke = UiKit.line
            )
            minHeight = dp(46)
            setPadding(dp(12), 0, dp(12), dp(2))
            setOnClickListener { finish() }
        }
        top.addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        heading.addView(TextView(this).apply {
            text = "Connect Pixiv"
            UiKit.title(this, 23f)
        })
        heading.addView(TextView(this).apply {
            text = "Sign in securely inside the app"
            UiKit.body(this, 13f)
            setPadding(0, dp(2), 0, 0)
        })
        top.addView(
            heading,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )
        root.addView(top)

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = UiKit.rounded(
                this@LoginActivity,
                UiKit.surface,
                16,
                UiKit.line
            )
        }

        statusCard.addView(TextView(this).apply {
            text = "LOGIN STATUS"
            UiKit.sectionLabel(this)
        })

        status = TextView(this).apply {
            text = "Log in below. When you can see your Pixiv account, tap Verify login."
            UiKit.body(this, 13.5f)
            setTextColor(UiKit.text)
            setPadding(0, dp(7), 0, 0)
        }
        statusCard.addView(status)

        root.addView(
            statusCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(18) }
        )

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        confirmButton = Button(this).apply {
            text = "Verify login"
            UiKit.stylePrimaryButton(this@LoginActivity, this)
            setOnClickListener { verifyInsideWebView() }
        }
        actions.addView(
            confirmButton,
            LinearLayout.LayoutParams(
                0,
                dp(54),
                1f
            )
        )

        val clearButton = Button(this).apply {
            text = "Clear"
            UiKit.styleDangerButton(this@LoginActivity, this)
            setOnClickListener {
                SessionStore.clearCookie(this@LoginActivity)
                CookieManager.getInstance().removeAllCookies {
                    CookieManager.getInstance().flush()
                    status.text = "Session cleared. Log in to Pixiv below."
                    status.setTextColor(UiKit.text)
                    webView.loadUrl(LOGIN_URL)
                }
            }
        }
        actions.addView(
            clearButton,
            LinearLayout.LayoutParams(
                dp(96),
                dp(54)
            ).apply { leftMargin = dp(10) }
        )

        root.addView(
            actions,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        )

        val browserLabel = TextView(this).apply {
            text = "PIXIV"
            UiKit.sectionLabel(this)
        }
        root.addView(
            browserLabel,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(20) }
        )

        val webContainer = FrameLayout(this).apply {
            setPadding(dp(1), dp(1), dp(1), dp(1))
            background = UiKit.rounded(
                this@LoginActivity,
                UiKit.line,
                18
            )
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
        }
        webContainer.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(
            webContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply { topMargin = dp(10) }
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
        status.text = "Checking your Pixiv session…"
        status.setTextColor(UiKit.muted)

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
                status.text = "Login not confirmed yet. Stay signed in on Pixiv, then tap Verify login again."
                status.setTextColor(UiKit.danger)
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
                status.text = "Pixiv is signed in, but the session cookie is still loading. Try Verify login once more."
                status.setTextColor(UiKit.danger)
                return@runOnUiThread
            }

            manager.flush()
            SessionStore.saveCookie(this, session, verified = true)
            NetworkCookies.install(this)

            checking = false
            status.text = "Connected. Pixiv session verified."
            status.setTextColor(UiKit.success)
            Toast.makeText(this, "Pixiv connected", Toast.LENGTH_SHORT).show()
            webView.postDelayed({ finish() }, 450L)
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

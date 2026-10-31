package com.azrael.galleryflow

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.azrael.pixivdumpsync.NocturneDesign
import com.azrael.pixivdumpsync.SearchRanking

/**
 * A source-owned browser session. No passwords or raw cookies are copied into
 * settings or app databases; the WebView cookie jar owns its session data.
 */
object SiteSession {
    private const val PREFS="asterion_site_connections"
    fun validLoginUrl(url:String):Boolean =
        SearchRanking.validUrl(url) && Uri.parse(url).scheme.equals("https",true)
    fun cookie(url:String):String? {
        if(!validLoginUrl(url))return null
        return runCatching{CookieManager.getInstance().getCookie(url)}
            .getOrNull()?.takeIf{it.isNotBlank()}
    }
    private fun host(url:String):String = Uri.parse(url).host.orEmpty()
        .lowercase(java.util.Locale.ROOT).removePrefix("www.")
    fun bound(context:Context,url:String):Boolean{
        if(!validLoginUrl(url))return false
        val key=host(url)
        if(!context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getBoolean(key,false))return false
        val stored=cookie(url)?:return false
        return if(key=="x.com"||key=="twitter.com")
            stored.split(';').any{it.trim().startsWith("auth_token=")}
        else stored.isNotBlank()
    }
    fun save(context:Context,url:String){
        require(validLoginUrl(url))
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putBoolean(host(url),true).apply()
    }
    fun disconnect(context:Context,url:String) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .remove(host(url)).apply()
    }
}

class SiteLoginActivity:Activity() {
    companion object { const val EXTRA_URL="site_login_url" }
    private var browser:WebView?=null
    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        val address=intent.getStringExtra(EXTRA_URL).orEmpty()
        if(!SiteSession.validLoginUrl(address)){
            Toast.makeText(this,"Choose a valid HTTPS site to sign in",Toast.LENGTH_LONG).show()
            finish();return
        }
        val ui=NocturneDesign
        val shell=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(ui.background)
        }
        val title=ui.text(this,"Connect website account",19f,ui.white,true)
        title.setPadding(ui.px(this,15),ui.px(this,14),ui.px(this,15),ui.px(this,4))
        shell.addView(title)
        shell.addView(ui.text(this,
            "Log in on the website. Cookies stay in Android's browser storage; sign-in is not shared with other sites.",
            12f,ui.secondary).apply{
            setPadding(ui.px(this@SiteLoginActivity,15),0,
                ui.px(this@SiteLoginActivity,15),ui.px(this@SiteLoginActivity,10))
        })
        val web=WebView(this)
        browser=web
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false)
        web.settings.javaScriptEnabled=true
        web.settings.domStorageEnabled=true
        web.settings.allowFileAccess=false
        web.settings.allowContentAccess=false
        web.webViewClient=WebViewClient()
        web.webChromeClient=WebChromeClient()
        shell.addView(web,LinearLayout.LayoutParams(-1,0,1f))
        val actions=ui.row(this)
        actions.addView(ui.action(this,"BACK"){
            if(web.canGoBack())web.goBack() else finish()
        },LinearLayout.LayoutParams(0,-2,1f))
        actions.addView(ui.action(this,"SAVE CONNECTION",true){
            CookieManager.getInstance().flush()
            val cookie=SiteSession.cookie(address)
            val xSite=Uri.parse(address).host.orEmpty().removePrefix("www.") in
                setOf("x.com","twitter.com")
            val authenticated=cookie!=null && (!xSite ||
                cookie.split(';').any{it.trim().startsWith("auth_token=")})
            if(authenticated)SiteSession.save(this,address)
            Toast.makeText(this,if(authenticated)
                "Account session saved on this device."
                else "Sign-in not confirmed. Finish logging in before saving.",
                Toast.LENGTH_LONG).show()
            setResult(if(authenticated)RESULT_OK else RESULT_CANCELED,
                Intent().putExtra(EXTRA_URL,address))
            finish()
        },LinearLayout.LayoutParams(0,-2,1f))
        actions.setPadding(ui.px(this,12),ui.px(this,8),ui.px(this,12),ui.px(this,8))
        shell.addView(actions)
        setContentView(shell)
        web.loadUrl(address)
    }
    override fun onDestroy(){
        browser?.apply{stopLoading();destroy()}
        browser=null
        super.onDestroy()
    }
    @Suppress("DEPRECATION")
    override fun onBackPressed(){
        val w=browser
        if(w!=null&&w.canGoBack())w.goBack() else super.onBackPressed()
    }
}

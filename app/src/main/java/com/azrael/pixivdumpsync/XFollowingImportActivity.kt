package com.azrael.pixivdumpsync

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.azrael.galleryflow.SiteSession
import org.json.JSONArray

/**
 * Uses the account's own rendered X Following list, rather than an unauthenticated
 * third-party API. Scans virtualized cells while the user visits /following.
 * No account cookies, profile identifiers or passwords leave app storage.
 */
class XFollowingImportActivity:Activity(){
    private val ui=NocturneDesign
    private val handler=Handler(Looper.getMainLooper())
    private val discovered=linkedSetOf<String>()
    private lateinit var browser:WebView
    private lateinit var label:TextView
    private lateinit var importButton:TextView
    @Volatile private var scanning=true
    private val reserved=setOf("home","explore","search","notifications","messages",
        "settings","compose","i","login","signup","privacy","tos","intent","hashtag",
        "following","followers","lists","bookmarks","premium","help","account","media")
    private fun validHandle(s:String)=s.matches(Regex("[A-Za-z0-9_]{1,15}"))&&
        s.lowercase() !in reserved
    private fun allowed(link:String)=runCatching{
        val host=Uri.parse(link).host.orEmpty().removePrefix("www.").lowercase()
        host=="x.com"||host=="twitter.com"
    }.getOrDefault(false)
    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        val layout=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(ui.background)
            setPadding(ui.px(this@XFollowingImportActivity,10),ui.px(this@XFollowingImportActivity,10),
                ui.px(this@XFollowingImportActivity,10),ui.px(this@XFollowingImportActivity,10))
        }
        layout.addView(ui.text(this,"X · My Following",21f,ui.white,true))
        label=ui.text(this,"Open your X profile, tap Following. Visible accounts are collected as you scroll.",
            13f,ui.secondary)
        layout.addView(label,ui.lp(this,8))
        browser=WebView(this).apply{
            settings.javaScriptEnabled=true
            settings.domStorageEnabled=true
            settings.allowFileAccess=false
            settings.allowContentAccess=false
            android.webkit.CookieManager.getInstance().setAcceptCookie(true)
            addJavascriptInterface(Bridge(),"AsterionFollowing")
            webViewClient=object:WebViewClient(){
                override fun shouldOverrideUrlLoading(v:WebView,request:WebResourceRequest):Boolean{
                    val u=request.url.toString()
                    if(allowed(u))return false
                    runCatching{startActivity(Intent(Intent.ACTION_VIEW,request.url))}
                    return true
                }
            }
        }
        layout.addView(browser,LinearLayout.LayoutParams(-1,0,1f).apply{weight=1f})
        importButton=ui.action(this,"IMPORT FOLLOWED ACCOUNTS",true){
            if(discovered.isEmpty()){
                Toast.makeText(this,"No accounts detected. Open the Following list.",Toast.LENGTH_LONG).show()
                return@action
            }
            var added=0
            XPhotoQueue(this).use{db->
                discovered.forEach{handle->
                    if(db.addSource("https://x.com/$handle"))added++
                }
            }
            XPhotoScheduler.ensure(this)
            if(added>0)XPhotoEngine.start(this)
            Toast.makeText(this,"Imported $added followed accounts. Existing entries retained.",
                Toast.LENGTH_LONG).show()
            setResult(RESULT_OK)
            finish()
        }
        layout.addView(importButton,ui.lp(this,8))
        layout.addView(ui.action(this,"DONE / RETURN",false){finish()},ui.lp(this,6))
        setContentView(layout)
        browser.loadUrl("https://x.com/home")
    }
    inner class Bridge {
        @JavascriptInterface
        fun found(raw:String){
            val handles=runCatching{
                val arr=JSONArray(raw)
                (0 until minOf(arr.length(),200)).map{arr.optString(it)}
            }.getOrDefault(emptyList())
            runOnUiThread{
                for(handle in handles)if(validHandle(handle))discovered.add(handle)
                label.text="${discovered.size} accounts found. Continue scrolling to collect more, then import."
            }
        }
    }
    private val scanner=object:Runnable {
        override fun run(){
            if(!scanning)return
            if(::browser.isInitialized){
                val url=browser.url.orEmpty()
                if(allowed(url) && Uri.parse(url).path.orEmpty()
                        .trimEnd('/').endsWith("/following")){
                    browser.evaluateJavascript(
                        """(function(){
                            const found = new Set();
                            document.querySelectorAll('[data-testid="UserCell"]').forEach(cell=>{
                                cell.querySelectorAll('a[href]').forEach(a=>{
                                    const m = a.getAttribute('href').match(/^\/([A-Za-z0-9_]{1,15})\/?$/);
                                    if(m) found.add(m[1]);
                                });
                            });
                            AsterionFollowing.found(JSON.stringify(Array.from(found)));
                            window.scrollBy(0,Math.max(200,Math.floor(window.innerHeight*0.60)));
                        })()""",null)
                }
            }
            handler.postDelayed(this,1500L)
        }
    }
    override fun onResume(){super.onResume();scanning=true;handler.post(scanner)}
    override fun onPause(){scanning=false;handler.removeCallbacks(scanner);super.onPause()}
    override fun onDestroy(){handler.removeCallbacks(scanner);browser.removeJavascriptInterface("AsterionFollowing");browser.destroy();super.onDestroy()}
}
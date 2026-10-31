package com.azrael.pixivdumpsync

import android.app.Activity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray

/** Authenticated browser fallback when external X metadata providers are unavailable. */
class XMediaCaptureActivity:Activity() {
    companion object { const val EXTRA_HANDLE="x_handle" }
    private lateinit var browser:WebView
    private lateinit var status:TextView
    private var handle=""
    private val observed=linkedSetOf<String>()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        handle=intent.getStringExtra(EXTRA_HANDLE).orEmpty()
        if(!handle.matches(Regex("[A-Za-z0-9_]{1,15}"))) { finish();return }
        val ui=NocturneDesign
        val outer=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(ui.background)
            setPadding(ui.px(this@XMediaCaptureActivity,10),ui.px(this@XMediaCaptureActivity,10),
                ui.px(this@XMediaCaptureActivity,10),ui.px(this@XMediaCaptureActivity,10))
        }
        outer.addView(ui.text(this,"X · @"+handle+" media",20f,ui.white,true))
        status=ui.text(this,"Scroll through the media tab. Capture visible image links below.",13f,ui.secondary)
        outer.addView(status,ui.lp(this,8))
        browser=WebView(this).apply{
            settings.javaScriptEnabled=true
            settings.domStorageEnabled=true
            settings.allowFileAccess=false
            settings.allowContentAccess=false
            android.webkit.CookieManager.getInstance().setAcceptCookie(true)
            addJavascriptInterface(Bridge(),"AsterionMedia")
            webViewClient=WebViewClient()
        }
        outer.addView(browser,LinearLayout.LayoutParams(-1,0,1f))
        outer.addView(ui.action(this,"CAPTURE VISIBLE PHOTOS",true){capture()},ui.lp(this,8))
        outer.addView(ui.action(this,"DOWNLOAD CAPTURED PHOTOS",false){
            if(observed.isEmpty()) {
                Toast.makeText(this,"Scroll until photo posts load, then capture.",Toast.LENGTH_LONG).show()
                return@action
            }
            val src="https://x.com/"+handle
            var added=0
            XPhotoQueue(this).use{queue->
                for(url in observed)
                    if(queue.addPhoto(src,url.hashCode().toString(),url)) added++
            }
            XPhotoEngine.start(this)
            Toast.makeText(this,"Queued "+added+" new photos out of "+observed.size+" captured.",Toast.LENGTH_LONG).show()
        },ui.lp(this,8))
        setContentView(outer)
        browser.loadUrl("https://x.com/"+handle+"/media")
    }
    private fun capture(){
        browser.evaluateJavascript("""(function(){
          const found=new Set();
          document.querySelectorAll('article img[src*="pbs.twimg.com/media/"], [data-testid="tweetPhoto"] img').forEach(img=>{
             const u=img.getAttribute('src')||'';
             if(u.startsWith('https://pbs.twimg.com/media/'))found.add(u.replace(/([?&])name=[^&]*/,'$1name=large'));
          });
          AsterionMedia.found(JSON.stringify(Array.from(found)));
          return found.size;
        })()""",null)
    }
    inner class Bridge {
        @JavascriptInterface fun found(json:String){
            val urls=runCatching{
                val items=JSONArray(json)
                (0 until minOf(500,items.length())).map{items.optString(it)}
            }.getOrDefault(emptyList())
            runOnUiThread {
                urls.filter{it.startsWith("https://pbs.twimg.com/media/")}.forEach{observed.add(it)}
                status.text=observed.size.toString()+" images captured. Scroll further and capture older posts."
            }
        }
    }
    override fun onDestroy(){
        browser.removeJavascriptInterface("AsterionMedia")
        browser.destroy()
        super.onDestroy()
    }
}

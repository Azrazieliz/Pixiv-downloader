package com.azrael.pixivdumpsync

import android.content.Context
import com.asterion.videoflow.WebDiscovery
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder

/** Federated keyword metadata search with evidence-bearing result URLs. */
object KeywordSources {
    private const val PREFS="asterion_keyword_sources"
    fun endpoint(context:Context):String=context.getSharedPreferences(PREFS,0)
        .getString("searxng","").orEmpty()
    fun setEndpoint(context:Context,address:String){
        context.getSharedPreferences(PREFS,0).edit().putString("searxng",address.trim()).apply()
    }
    fun search(context:Context,keyword:String):List<Pair<String,String>>{
        val endpoint=endpoint(context).trimEnd('/')
        if(endpoint.isEmpty())return emptyList()
        require(WebDiscovery.isUrl(endpoint)){"Invalid search service URL"}
        val q=URLEncoder.encode(keyword.trim(),"UTF-8")
        val api="$endpoint/search?q=$q&categories=videos,general&format=json&safesearch=0"
        val response=Jsoup.connect(api).ignoreContentType(true)
            .timeout(30000).maxBodySize(4_000_000).execute()
        require(response.statusCode() in 200..299){"Search provider unavailable"}
        val items=JSONObject(response.body()).optJSONArray("results")
            ?:error("Search provider did not return JSON results")
        val found=linkedMapOf<String,Pair<String,String>>()
        for(i in 0 until items.length()){
            val row=items.optJSONObject(i)?:continue
            val url=row.optString("url")
            if(!WebDiscovery.isUrl(url))continue
            // URL is the actual provider result; the original title and link are preserved.
            val host=runCatching{URI(url).host.orEmpty()}.getOrDefault("")
            val looksLikeVideo=WebDiscovery.isVideoPage(url) ||
                listOf("youtube.com","youtu.be","vimeo.com","dailymotion.com",
                    "tiktok.com","twitch.tv").any{host==it||host.endsWith(".$it")}
            if(!looksLikeVideo)continue
            val title=row.optString("title",url).take(210)
            found.putIfAbsent(url,title to url)
        }
        return found.values.take(150).toList()
    }
}

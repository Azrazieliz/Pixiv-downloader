package com.azrael.pixivdumpsync

import android.content.Context
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.net.URLEncoder

/**
 * Multi-source video discovery. Providers may index ordinary websites, adult sites,
 * mirrors, and Tor-reachable sources. No preconfigured site-category blacklist.
 * Discovery is metadata-based; it does not bypass protected streams or DRM.
 */
object KeywordSources {
    private const val PREFS="asterion_keyword_sources"
    private fun preferences(context:Context)=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    fun endpoint(context:Context):String=preferences(context).getString("searxng","").orEmpty()
    fun setEndpoint(context:Context,address:String){
        preferences(context).edit().putString("searxng",address.trim()).apply()
    }
    fun sources(context:Context):String {
        val configured=preferences(context).getString("providers",null)
        if(configured!=null)return configured
        val legacy=endpoint(context)
        return if(legacy.isNotBlank()) "searxng|$legacy" else
            "html|https://www.bing.com/videos/search?q={query}|Bing Videos\\n" +
            "html|https://search.brave.com/search?q={query}|Brave Search"
    }
    fun setSources(context:Context,lines:String){
        SearchRanking.parseProviders(lines) // Reject invalid values before persistence.
        preferences(context).edit().putString("providers",lines.trim()).apply()
    }
    private fun proxy(context:Context):Proxy? {
        val settings=context.getSharedPreferences("video_options",Context.MODE_PRIVATE)
        if(!settings.getBoolean("tor_proxy",false))return null
        return Proxy(Proxy.Type.SOCKS,InetSocketAddress.createUnresolved(
            "127.0.0.1",settings.getInt("tor_port",9050).coerceIn(1,65535)))
    }
    private fun connection(context:Context,url:String):org.jsoup.Connection {
        require(SearchRanking.validUrl(url)){"Invalid search source address"}
        val host=URI(url).host.orEmpty().lowercase()
        val route=proxy(context)
        require(!host.endsWith(".onion")||route!=null){
            "Enable a running local SOCKS5/Tor proxy for .onion search sources"
        }
        val call=Jsoup.connect(url).timeout(25000).maxBodySize(4_000_000)
            .followRedirects(true).userAgent("Mozilla/5.0 (Linux; Android 16; Mobile)")
        // Do not silently fall back to an unproxied request when the proxy is enabled.
        if(route!=null)call.proxy(route)
        return call
    }
    private fun index(context:Context,provider:SearchProvider,keyword:String):List<RankedHit> {
        val escaped=URLEncoder.encode(keyword,"UTF-8")
        val address=provider.url.trimEnd('/')+
            "/search?q=$escaped&format=json&categories=videos,general&safesearch=0"
        val response=connection(context,address).ignoreContentType(true).execute()
        require(response.statusCode() in 200..299){
            "Search provider returned HTTP ${response.statusCode()}"
        }
        val found=JSONObject(response.body()).optJSONArray("results")
            ?:error("Search index did not return JSON results")
        val results=ArrayList<RankedHit>()
        for(i in 0 until minOf(found.length(),250)){
            val row=found.optJSONObject(i)?:continue
            val url=row.optString("url")
            if(!SearchRanking.validUrl(url))continue
            val title=row.optString("title",url).take(220)
            val snippet=row.optString("content").take(500)
            results.add(RankedHit(title,url,provider.label,snippet))
        }
        return results
    }
    private fun html(context:Context,provider:SearchProvider,keyword:String):List<RankedHit> {
        val encoded=URLEncoder.encode(keyword,"UTF-8")
        val url=provider.url.replace("{query}",encoded).replace("{name}",encoded)
        val doc=connection(context,url).get()
        val found=ArrayList<RankedHit>()
        // Honor ordinary and adult site search result layouts without a domain catalog.
        val links=doc.select("article a[href], .search-result a[href], .result a[href], "+
            ".video a[href], .thumb a[href], .item a[href], a[href]")
        for(node in links.take(1400)) {
            val absolute=runCatching {
                URI(doc.location()).resolve(node.attr("href")).toString()
            }.getOrNull()?:continue
            if(!SearchRanking.validUrl(absolute))continue
            val title=node.attr("title").ifBlank{node.text().trim()}
                .ifBlank{node.selectFirst("img[alt]")?.attr("alt").orEmpty()}
            if(title.length<3)continue
            if(URI(absolute).path.orEmpty()==URI(doc.location()).path.orEmpty())continue
            val container=node.closest("article, .search-result, .result, .video, .item")
            val snippet=container?.text().orEmpty().take(450)
            val resemblesMedia=Regex("(?i)(video|watch|movie|film|clip|scene|view|post|stream)")
                .containsMatchIn(URI(absolute).path.orEmpty())||
                Regex("(?i)\\.(mp4|webm|mov|m3u8|mpd)([?#]|$)").containsMatchIn(absolute)
            // Retain title-relevant links even when a site's URL lacks video words.
            if(!resemblesMedia && SearchRanking.relevance(keyword,title,snippet)==0)continue
            found.add(RankedHit(title.take(220),absolute,provider.label,snippet))
        }
        return found.take(250)
    }
    fun searchDetailed(context:Context,keyword:String):SearchOutcome {
        require(keyword.trim().isNotBlank()){"Enter a search term"}
        val providers=SearchRanking.parseProviders(sources(context))
        val collected=ArrayList<RankedHit>()
        val errors=ArrayList<String>()
        for(provider in providers){
            try {
                val hits=when(provider.kind){
                    SearchProviderKind.SEARXNG->index(context,provider,keyword.trim())
                    SearchProviderKind.HTML->html(context,provider,keyword.trim())
                }
                collected.addAll(hits)
            }catch(t:Exception){
                errors.add("${provider.label}: ${t.message?:t.javaClass.simpleName}")
            }
        }
        return SearchOutcome(SearchRanking.deduplicate(keyword,collected),
            providers.size,errors)
    }
    /** Search one explicitly supplied site, including HTML sites with unusual page URLs. */
    fun searchCustom(context:Context,keyword:String,address:String):SearchOutcome {
        require(SearchRanking.validUrl(
            address.replace("{query}","sample").replace("{name}","sample"))) {
            "Invalid user-supplied source URL"
        }
        val provider=SearchProvider(SearchProviderKind.HTML,address,"Custom site")
        return try {
            SearchOutcome(SearchRanking.deduplicate(keyword,
                html(context,provider,keyword)),1,emptyList())
        }catch(t:Exception) {
            SearchOutcome(emptyList(),1,listOf(
                "Custom site: ${t.message?:t.javaClass.simpleName}"))
        }
    }
    /** Compatibility entry point for older UI and stored creator watch records. */
    fun search(context:Context,keyword:String):List<Pair<String,String>> =
        searchDetailed(context,keyword).hits.map{it.title to it.url}
}

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
enum class VideoSearchArea { ALL, GENERAL, ADULT, SAVED, HIDDEN }

object KeywordSources {
    private const val PREFS="asterion_keyword_sources"
    private fun preferences(context:Context)=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    fun endpoint(context:Context):String=preferences(context).getString("searxng","").orEmpty()
    fun setEndpoint(context:Context,address:String){
        preferences(context).edit().putString("searxng",address.trim()).apply()
    }
    /** Search video websites directly; never substitute a search-engine results page. */
    private val builtInSites=listOf(
        "html|https://www.pornhub.com/video/search?search={query}|Pornhub",
        "html|https://www.xvideos.com/?k={query}|XVideos",
        "html|https://xhamster.com/search/{query}|xHamster",
        "html|https://www.dailymotion.com/search/{query}/videos|Dailymotion",
        "html|https://www.youtube.com/results?search_query={query}|YouTube",
        "html|https://www.xnxx.com/search/{query}|XNXX",
        "html|https://www.eporner.com/search/{query}/|Eporner",
        "html|https://spankbang.com/s/{query}/|SpankBang",
        "html|https://www.redtube.com/?search={query}|RedTube"
    )
    private fun isOldBrowser(line:String)=
        line.contains("www.bing.com/videos/search",true) ||
        line.contains("search.brave.com/search",true)
    fun sources(context:Context):String {
        // Search does not depend on choosing or configuring a search provider.
        return builtInSites.joinToString("\n")
    }
    private fun searchProviders(context:Context):List<SearchProvider> {
        val direct=SearchRanking.parseProviders(builtInSites.joinToString("\n"))
        // Automatically include user-saved video sources when their host has an
        // actual search endpoint we know how to parse. No user-facing providers.
        val dynamic=runCatching { com.asterion.videoflow.VideoDb(context).use { db->
            db.listWatches().filter{it.enabled}.mapNotNull { watch->
                val host=runCatching{URI(watch.address).host.orEmpty().lowercase()}
                    .getOrDefault("").removePrefix("www.")
                val url=when {
                    host=="pornhub.com" -> "https://www.pornhub.com/video/search?search={query}"
                    host=="xvideos.com" -> "https://www.xvideos.com/?k={query}"
                    host=="xhamster.com" -> "https://xhamster.com/search/{query}"
                    host=="youtube.com" || host=="m.youtube.com" ->
                        "https://www.youtube.com/results?search_query={query}"
                    host=="dailymotion.com" -> "https://www.dailymotion.com/search/{query}/videos"
                    else->null
                }
                url?.let { SearchProvider(SearchProviderKind.HTML,it,watch.label) }
            }
        }}.getOrDefault(emptyList())
        val hidden=runCatching { com.asterion.videoflow.VideoDb(context).use { db->
            db.listWatches().filter { it.enabled && it.address.contains(".onion") &&
                it.address.contains("{query}") }.map { watch->
                SearchProvider(SearchProviderKind.HTML, watch.address, watch.label)
            }
        }}.getOrDefault(emptyList())
        return (direct+dynamic+hidden).distinctBy{it.url}
    }
    fun setSources(context:Context,lines:String){
        SearchRanking.parseProviders(lines) // Reject invalid values before persistence.
        preferences(context).edit().putString("providers",lines.trim()).apply()
    }
    private fun proxy(context:Context,url:String):Proxy? {
        if(!URI(url).host.orEmpty().endsWith(".onion",true))return null
        val settings=context.getSharedPreferences("video_options",Context.MODE_PRIVATE)
        if(!settings.getBoolean("tor_proxy",false))return null
        return Proxy(Proxy.Type.SOCKS,InetSocketAddress.createUnresolved(
            "127.0.0.1",settings.getInt("tor_port",9050).coerceIn(1,65535)))
    }
    private fun connection(context:Context,url:String):org.jsoup.Connection {
        require(SearchRanking.validUrl(url)){"Invalid search source address"}
        val host=URI(url).host.orEmpty().lowercase()
        val route=proxy(context,url)
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
            if(!SearchRanking.validUrl(url) || !SearchRanking.isVideoUrl(url))continue
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
        val selectors="li.pcVideoListItem a[href], .videoBox a[href], "+
            ".thumb-block a[href], [class*=video-thumb] a[href], "+
            "[class*=video-card] a[href], [class*=thumb] a[href], "+
            "[class*=video] a[href], article a[href], a[href]"
        for(node in doc.select(selectors).take(5000)){
            if(node.closest("header,footer,nav,aside,.menu,.sidebar,.navigation")!=null)continue
            val absolute=runCatching{
                URI(doc.location()).resolve(node.attr("href")).toString()
            }.getOrNull()?:continue
            if(!SearchRanking.validUrl(absolute) ||
                !SearchRanking.isVideoUrl(absolute))continue
            val resultHost=runCatching{URI(absolute).host.orEmpty().removePrefix("www.")}.getOrDefault("")
            val searchHost=URI(url).host.orEmpty().removePrefix("www.")
            if(resultHost!=searchHost && !resultHost.endsWith("."+searchHost))continue
            val container=node.closest(
                "li.pcVideoListItem, .videoBox, .thumb-block, "+
                "[class*=video-thumb], [class*=video-card], article, "+
                "[class*=thumb], [class*=video]")
            val image=(node.selectFirst("img")?:container?.selectFirst("img"))
            val description=container?.text().orEmpty().take(500)
            val titles=listOf(
                container?.selectFirst("h3,h2,[class*=title]")?.text().orEmpty(),
                node.attr("title"),node.attr("aria-label"),
                image?.attr("alt").orEmpty(),node.text(),
                container?.attr("title").orEmpty()
            ).map{it.trim().take(220)}.filter{it.length>=5}
            val title=titles.maxByOrNull{
                SearchRanking.relevance(keyword,it,description)
            }?:continue
            if(SearchRanking.relevance(keyword,title,description)==0)continue
            val thumb=image?.let{img->
                listOf("data-thumb","data-thumb_url","data-preview",
                    "data-original","data-src","data-lazy-src","src")
                    .map{img.attr(it)}.firstOrNull{it.startsWith("http")||
                        it.startsWith("//")||it.startsWith("/")}
                    ?.let{runCatching{URI(doc.location()).resolve(it).toString()}.getOrNull()}
            }?.takeIf{SearchRanking.validUrl(it)}
            found.add(RankedHit(title,absolute,provider.label,description,
                thumbnail=thumb))
            if(found.size>=500)break
        }
        return found
    }
    fun searchDetailed(context:Context,keyword:String,
                       area:VideoSearchArea=VideoSearchArea.ALL):SearchOutcome {
        require(keyword.trim().isNotBlank()){"Enter a search term"}
        val adult=setOf("pornhub.com","xvideos.com","xhamster.com",
            "xnxx.com","eporner.com","spankbang.com","redtube.com")
        val general=setOf("youtube.com","dailymotion.com")
        val savedHosts=runCatching{com.asterion.videoflow.VideoDb(context).use{db->
            db.listWatches().filter{it.enabled}.mapNotNull { watch->
                runCatching{URI(watch.address).host?.lowercase()?.removePrefix("www.")}
                    .getOrNull()
            }.toSet()
        }}.getOrDefault(emptySet())
        val providers=searchProviders(context).filter { provider->
            val host=runCatching{
                URI(provider.url.replace("{query}","x")).host.orEmpty()
                    .lowercase().removePrefix("www.")
            }.getOrDefault("")
            when(area){
                VideoSearchArea.ALL -> !host.endsWith(".onion")
                VideoSearchArea.GENERAL -> host in general
                VideoSearchArea.ADULT -> host in adult
                VideoSearchArea.SAVED -> host in savedHosts
                VideoSearchArea.HIDDEN -> host.endsWith(".onion")
            }
        }
        if(providers.isEmpty())return SearchOutcome(emptyList(),0,
            listOf("No accessible sites for this search area. Add a saved video site or configure Tor for hidden services."))
        val pool=java.util.concurrent.Executors.newFixedThreadPool(minOf(6,providers.size))
        val completion=java.util.concurrent.ExecutorCompletionService<
            Triple<String,List<RankedHit>,String?>>(pool)
        val collected=ArrayList<RankedHit>()
        val errors=ArrayList<String>()
        try {
            for(provider in providers)completion.submit(java.util.concurrent.Callable {
                try {
                    val hits=when(provider.kind){
                        SearchProviderKind.SEARXNG->index(context,provider,keyword.trim())
                        SearchProviderKind.HTML->html(context,provider,keyword.trim())
                    }
                    Triple(provider.label,hits,null)
                }catch(t:Exception){
                    Triple(provider.label,emptyList(),
                        t.message?:t.javaClass.simpleName)
                }
            })
            // Execute independent providers concurrently. A broken site cannot hold
            // the whole search queue hostage.
            val deadline=System.nanoTime()+
                java.util.concurrent.TimeUnit.SECONDS.toNanos(90)
            var finished=0
            while(finished<providers.size){
                val remaining=deadline-System.nanoTime()
                if(remaining<=0L)break
                val result=completion.poll(remaining,
                    java.util.concurrent.TimeUnit.NANOSECONDS)?:break
                val (label,hits,error)=result.get()
                finished++
                collected.addAll(hits)
                if(error!=null)errors.add("$label: $error")
            }
            if(finished<providers.size)
                errors.add("${providers.size-finished} search source(s) timed out")
        }finally{pool.shutdownNow()}
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

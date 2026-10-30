package com.azrael.pixivdumpsync

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/** Parsing and ranking are independent of Android and never remove user query terms. */
enum class SearchProviderKind { SEARXNG, HTML }
data class SearchProvider(val kind:SearchProviderKind,val url:String,val label:String)
data class RankedHit(val title:String,val url:String,val source:String,
    val description:String="",val score:Int=0)
data class SearchOutcome(val hits:List<RankedHit>,val checked:Int,val failures:List<String>)

object SearchRanking {
    private val tracking=setOf("fbclid","gclid","ref","ref_src","source","mc_cid","mc_eid")
    fun validUrl(value:String):Boolean = try {
        val uri=URI(value.trim())
        val host=uri.host?.lowercase(Locale.ROOT)?:return false
        if(uri.scheme !in setOf("http","https") || uri.userInfo!=null)return false
        if(host=="localhost"||host.endsWith(".localhost")||host.endsWith(".local")||
            host.endsWith(".internal")||host=="0.0.0.0"||host=="::1"||
            host.startsWith("127.")||host.startsWith("10.")||
            host.startsWith("192.168.")||host.startsWith("169.254.")||
            (host.startsWith("172.")&&host.split('.').getOrNull(1)?.toIntOrNull() in 16..31))
            return false
        true
    } catch(_:Exception){false}
    fun parseProviders(lines:String):List<SearchProvider> {
        val parsed=ArrayList<SearchProvider>()
        for(raw in lines.lineSequence()){
            val line=raw.trim()
            if(line.isEmpty()||line.startsWith("#"))continue
            val parts=line.split('|',limit=3)
            require(parts.size>=2){"Source format: searxng|URL or html|URL-containing-{query}"}
            val kind=when(parts[0].trim().lowercase(Locale.ROOT)){
                "searxng","index"->SearchProviderKind.SEARXNG
                "html","site"->SearchProviderKind.HTML
                else->throw IllegalArgumentException("Unknown source type: ${parts[0].trim()}")
            }
            val address=parts[1].trim().trimEnd('/')
            val checked=address.replace("{query}","sample").replace("{name}","sample")
            require(validUrl(checked)){"Invalid source URL: $address"}
            if(kind==SearchProviderKind.HTML)
                require("{query}" in address||"{name}" in address){
                    "HTML search URL must contain {query}"}
            val label=parts.getOrNull(2)?.trim()?.take(60)
                ?.takeIf(String::isNotBlank)?:URI(checked).host.orEmpty()
            parsed.add(SearchProvider(kind,address,label))
        }
        require(parsed.size<=30){"Limit the configuration to 30 search sources"}
        return parsed.distinctBy{it.kind to it.url}
    }
    fun canonicalKey(raw:String):String = try {
        val uri=URI(raw.trim())
        val host=uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.")?:return raw.trim()
        val path=uri.rawPath.orEmpty().trimEnd('/').ifEmpty{"/"}
        // Don't rewrite the actual URL sent to the downloader. This is only a deduplication key.
        val filtered=uri.rawQuery.orEmpty().split('&').filter{it.isNotBlank()}.filterNot{
            val key=it.substringBefore('=').lowercase(Locale.ROOT)
            key.startsWith("utm_")||key in tracking
        }.sorted().joinToString("&")
        "${uri.scheme?.lowercase(Locale.ROOT)}://$host$path"+
            if(filtered.isNotBlank())"?$filtered" else ""
    }catch(_:Exception){raw.trim()}
    fun relevance(query:String,title:String,description:String):Int {
        val q=query.trim().lowercase(Locale.ROOT)
        if(q.isEmpty())return 0
        val words=q.split(Regex("\\s+")).filter{it.isNotBlank()}
        val head=title.lowercase(Locale.ROOT)
        val body=description.lowercase(Locale.ROOT)
        var score=if(head.contains(q))100 else 0
        score+=if(body.contains(q))30 else 0
        score+=words.count{head.contains(it)}*17
        score+=words.count{body.contains(it)}*5
        return score
    }
    fun deduplicate(query:String,hits:List<RankedHit>):List<RankedHit> {
        val unique=linkedMapOf<String,RankedHit>()
        for(hit in hits){
            if(!validUrl(hit.url))continue
            val normalized=hit.copy(score=relevance(query,hit.title,hit.description))
            val key=canonicalKey(hit.url)
            val previous=unique[key]
            if(previous==null || normalized.score>previous.score)unique[key]=normalized
        }
        return unique.values.sortedWith(compareByDescending<RankedHit>{it.score}
            .thenBy{it.title.lowercase(Locale.ROOT)}).toList()
    }
}

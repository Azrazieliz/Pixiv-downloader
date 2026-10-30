package com.azrael.pixivdumpsync

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI

data class ChapterLink(val url:String,val title:String)
object ChapterScraper {
    private val number=Regex("(\\d+(?:\\.\\d+)?)")
    private val chapter=Regex("(?i)(chapter|chapitre|episode|chap[-_ /]?[0-9]+)")
    fun naturalKey(text:String):Double=number.findAll(text).lastOrNull()
        ?.value?.toDoubleOrNull()?:Double.MAX_VALUE
    fun sort(items:List<ChapterLink>):List<ChapterLink> =
        items.distinctBy{it.url}.sortedWith(compareBy<ChapterLink>{naturalKey(it.title)}
            .thenBy{it.title.lowercase()}.thenBy{it.url})
    private fun read(url:String):Document=Jsoup.connect(url)
        .userAgent("Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 Chrome/131.0")
        .timeout(30000).maxBodySize(4_000_000).followRedirects(true).get()
    private fun allowed(raw:String):Boolean=runCatching {
        val u=URI(raw)
        val h=u.host.orEmpty().lowercase()
        (u.scheme=="http"||u.scheme=="https")&&u.userInfo==null&&h.isNotEmpty()&&
            h!="localhost"&&!h.endsWith(".local")&&!h.startsWith("127.")&&
            !h.startsWith("10.")&&!h.startsWith("192.168.")
    }.getOrDefault(false)
    fun chapters(seriesUrl:String):List<ChapterLink> {
        require(allowed(seriesUrl)){"Provide an HTTP(S) chapter or series URL"}
        val collected=linkedMapOf<String,ChapterLink>()
        var next:String?=seriesUrl
        val visited=mutableSetOf<String>()
        while(next!=null) {
            if(!visited.add(next))break
            require(visited.size<=100){"Series index has over 100 pages; export not complete"}
            val doc=read(next)
            val root=URI(doc.location()).host?.removePrefix("www.")
            for(a in doc.select("a[href]")){
                val link=runCatching{URI(doc.location()).resolve(a.attr("href")).toString()}
                    .getOrNull()?:continue
                if(!allowed(link))continue
                if(URI(link).host?.removePrefix("www.")!=root)continue
                val label=a.text().trim().ifEmpty{a.attr("title")}
                if(chapter.containsMatchIn(label)||chapter.containsMatchIn(URI(link).path)) {
                    if(!label.contains("next",true)&&!label.contains("prev",true))
                        collected.putIfAbsent(link.substringBefore('#'),
                            ChapterLink(link.substringBefore('#'),label.ifBlank{"Chapter"}))
                }
            }
            val nextTag=doc.selectFirst(
                ".pagination a.next[href], a.page-numbers.next[href], a[rel=next][href]")
            val found=nextTag?.let{
                runCatching{URI(doc.location()).resolve(it.attr("href")).toString()}.getOrNull()
            }
            next=found?.takeIf{allowed(it)&&URI(it).host?.removePrefix("www.")==root&&
                it !in visited}
        }
        return if(collected.isEmpty()) listOf(ChapterLink(seriesUrl,"Chapter 1"))
            else sort(collected.values.toList())
    }
    fun pageImages(chapterUrl:String):List<String> {
        val doc=read(chapterUrl)
        val nodes=doc.select(".reading-content img, .chapter-content img, #reader img, "+
            ".page-chapter img, .container-chapter-reader img, img.wp-manga-chapter-img, "+
            "img[data-original], img[data-src]")
        val fallback=if(nodes.isEmpty())doc.select("article img, main img")else nodes
        val urls=linkedSetOf<String>()
        for(node in fallback) {
            val raw=listOf("data-original","data-src","data-lazy-src","src")
                .map{node.attr(it)}.firstOrNull{it.isNotBlank()&&!it.startsWith("data:")}
                ?:continue
            val u=runCatching{URI(doc.location()).resolve(raw.trim()).toString()}
                .getOrNull()?:continue
            if(allowed(u)&&!Regex("(?i)(avatar|icon|emoji|logo|loading)").containsMatchIn(u))
                urls.add(u)
        }
        require(urls.isNotEmpty()){"No chapter images found at $chapterUrl"}
        return urls.toList()
    }
}

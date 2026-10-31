package com.azrael.pixivdumpsync

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.util.Locale

data class ChapterLink(val url:String,val title:String)

/** Chapter lists and readers: preserve chapter numbers, fractions and original page order. */
object ChapterScraper {
    private val chapterNumber=Regex(
        "(?i)(?:chapter|chapitre|episode|ch[.-]?|ep[.-]?)[\\s_/-]*(\\d+(?:[._]\\d+)?)"
    )
    private val pathChapter=Regex("(?i)/(?:chapter|chapitre|chap|ch|episode)[-_]?(\\d+(?:[._]\\d+)?)")
    private fun numberFrom(text:String):Double? =
        chapterNumber.find(text)?.groupValues?.get(1)?.replace('_','.')
            ?.toDoubleOrNull()
    fun naturalKey(text:String):Double=
        numberFrom(text)?:pathChapter.find(text)?.groupValues?.get(1)
            ?.replace('_','.')?.toDoubleOrNull()?:Double.MAX_VALUE
    fun sort(items:List<ChapterLink>):List<ChapterLink> =
        items.distinctBy{it.url.trimEnd('/')}.sortedWith(
            compareBy<ChapterLink>{
                val titleNumber=naturalKey(it.title)
                if(titleNumber!=Double.MAX_VALUE)titleNumber else naturalKey(it.url)
            }.thenBy{it.title.lowercase(Locale.ROOT)}.thenBy{it.url})
    fun isSingleChapter(url:String):Boolean =
        pathChapter.containsMatchIn(URI(url).path.orEmpty())
    private fun read(url:String):Document{
        val connection=Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 Chrome/131.0")
            .timeout(30000).maxBodySize(12_000_000).followRedirects(true)
            .ignoreHttpErrors(true)
        com.azrael.galleryflow.SiteSession.cookie(url)?.let { requestCookie ->
            connection.header("Cookie", requestCookie)
        }
        val result=connection.execute()
        val doc=result.parse()
        require(result.statusCode() in 200..299 &&
            !doc.title().contains("Just a moment",true) &&
            !doc.title().contains("Attention required",true) &&
            !doc.text().contains("Enable JavaScript and cookies to continue",true)){
            if(result.statusCode()==403 || doc.title().contains("Just a moment",true))
                "This website requires a browser verification challenge; chapter images cannot be accessed yet (HTTP ${result.statusCode()})."
            else "Series page returned HTTP ${result.statusCode()}"
        }
        return doc
    }
    private fun allowed(raw:String):Boolean=SearchRanking.validUrl(raw)
    private fun clean(u:String):String=u.substringBefore('#').trimEnd('/')+"/"
    private fun chapterTitle(anchor:org.jsoup.nodes.Element,url:String):String{
        val label=anchor.text().trim().ifBlank{anchor.attr("title").trim()}
        val num=numberFrom(label) ?: pathChapter.find(URI(url).path.orEmpty())
            ?.groupValues?.get(1)?.replace('_','.')?.toDoubleOrNull()
        return if(num!=null) "Chapter "+num.toString().removeSuffix(".0") else label.ifBlank{"Chapter"}
    }
    fun chapters(seriesUrl:String):List<ChapterLink>{
        require(allowed(seriesUrl)){"Provide an HTTP(S) chapter or series URL"}
        if(isSingleChapter(seriesUrl))
            return listOf(ChapterLink(seriesUrl,"Chapter "+
                (pathChapter.find(URI(seriesUrl).path.orEmpty())
                    ?.groupValues?.get(1)?.replace('_','.')?:"1")))
        val base=URI(seriesUrl)
        val prefix=base.path.orEmpty().trimEnd('/')+"/"
        val root=base.host?.removePrefix("www.")
        val collected=linkedMapOf<String,ChapterLink>()
        var next:String?=seriesUrl
        val visited=mutableSetOf<String>()
        while(next!=null){
            if(!visited.add(clean(next)))break
            require(visited.size<=150){"Over 150 index pages; cannot establish full chapter list"}
            val doc=read(next)
            for(a in doc.select("a[href], option[value]")){
                val href=if(a.tagName()=="option")a.attr("value")else a.attr("href")
                val link=runCatching{URI(doc.location()).resolve(href).toString()}
                    .getOrNull()?:continue
                if(!allowed(link))continue
                val uri=URI(link)
                if(uri.host?.removePrefix("www.")!=root)continue
                if(!uri.path.orEmpty().startsWith(prefix) ||
                    !pathChapter.containsMatchIn(uri.path.orEmpty()))continue
                val canonical=clean(link)
                collected.putIfAbsent(canonical,
                    ChapterLink(canonical,chapterTitle(a,link)))
            }
            val nextTag=doc.selectFirst(
                ".pagination a.next[href], a.page-numbers.next[href], a[rel=next][href]"
            )
            val found=nextTag?.let{
                runCatching{URI(doc.location()).resolve(it.attr("href")).toString()}.getOrNull()
            }
            next=found?.takeIf{allowed(it)&&URI(it).host?.removePrefix("www.")==root &&
                !visited.contains(clean(it))}
        }
        require(collected.isNotEmpty()){
            "No chapter links found on the series page; refusing to create an incomplete ZIP."
        }
        return sort(collected.values.toList())
    }
    fun parsePageImages(doc:Document):List<String>{
        val nodes=doc.select(".reading-content img, .chapter-content img, #reader img, "+
            ".page-chapter img, .container-chapter-reader img, img.wp-manga-chapter-img, "+
            "img[data-original], img[data-src], img[data-lazy-src]")
        val fallback=if(nodes.isEmpty())doc.select("article img, main img")else nodes
        val urls=linkedSetOf<String>()
        for(node in fallback){
            val raw=listOf("data-original","data-src","data-lazy-src","data-full","src")
                .map{node.attr(it)}.firstOrNull{
                    it.isNotBlank() && !it.startsWith("data:") && !it.startsWith("blob:")
                }?:node.attr("srcset").split(',').lastOrNull()?.trim()?.substringBefore(' ')
                ?:continue
            val resolved=runCatching{URI(doc.location()).resolve(raw.trim()).toString()}
                .getOrNull()?:continue
            if(allowed(resolved) &&
                !Regex("(?i)(avatar|icon|emoji|logo|loading|spinner)").containsMatchIn(resolved))
                urls.add(resolved)
        }
        return urls.toList()
    }
    fun pageImages(chapterUrl:String):List<String>{
        val images=parsePageImages(read(chapterUrl))
        require(images.isNotEmpty()){"No readable chapter images at $chapterUrl"}
        return images
    }
}

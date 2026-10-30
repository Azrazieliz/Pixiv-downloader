package com.azrael.pixivdumpsync

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.asterion.videoflow.VideoDb
import com.asterion.videoflow.WebDiscovery
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder

data class NameWatch(val id:Long,val name:String,val source:String,val enabled:Boolean,
    val lastCheck:Long,val lastError:String?)
class NameWatchDb(context:Context):SQLiteOpenHelper(context,"asterion_name_watches.sqlite",null,1){
    override fun onCreate(db:SQLiteDatabase){
        db.execSQL("""CREATE TABLE name_watches(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL, source TEXT NOT NULL DEFAULT '',
            enabled INTEGER NOT NULL DEFAULT 1, last_check INTEGER NOT NULL DEFAULT 0,
            last_error TEXT, UNIQUE(name,source))""")
    }
    override fun onUpgrade(db:SQLiteDatabase,a:Int,b:Int){}
    fun add(name:String,source:String=""):Boolean {
        if(name.trim().length<2)return false
        val v=ContentValues().apply{put("name",name.trim());put("source",source.trim())}
        return writableDatabase.insertWithOnConflict(
            "name_watches",null,v,SQLiteDatabase.CONFLICT_IGNORE)>0L
    }
    fun list():List<NameWatch>{
        val out=ArrayList<NameWatch>()
        readableDatabase.rawQuery("""SELECT id,name,source,enabled,last_check,last_error
            FROM name_watches ORDER BY name COLLATE NOCASE""",null).use{c->
            while(c.moveToNext())out.add(NameWatch(c.getLong(0),c.getString(1),
                c.getString(2),c.getInt(3)==1,c.getLong(4),c.getString(5)))}
        return out
    }
    fun setEnabled(id:Long,value:Boolean){
        writableDatabase.execSQL("UPDATE name_watches SET enabled=? WHERE id=?",
            arrayOf(if(value)1 else 0,id))
    }
    fun setResult(id:Long,error:String?){
        val v=ContentValues().apply{put("last_check",System.currentTimeMillis())
            if(error==null)putNull("last_error") else put("last_error",error.take(300))}
        writableDatabase.update("name_watches",v,"id=?",arrayOf("$id"))
    }
    fun delete(id:Long){writableDatabase.delete("name_watches","id=?",arrayOf("$id"))}
}
/** Searches explicit source pages and public metadata; dedupe is enforced by VideoDb. */
object NameCrawler {
    fun scan(context:Context,onStatus:(String)->Unit={}){
        NameWatchDb(context).use{watches->
            VideoDb(context).use{videos->
                for(watch in watches.list().filter{it.enabled}){
                    try{
                        onStatus("Discovering videos: ${watch.name}")
                        val hits=if(watch.source.isBlank()){
                            val archive=runCatching{publicArchive(watch.name)}
                                .getOrElse{onStatus("Archive search unavailable: ${it.message}");emptyList()}
                            val indexed=runCatching{KeywordSources.search(context,watch.name)}
                                .getOrElse{onStatus("Keyword index unavailable: ${it.message}");emptyList()}
                            (archive+indexed).distinctBy{it.second}
                        }else namedSource(watch.name,watch.source)
                        for((title,url) in hits.take(100))videos.addVideo(url,title)
                        watches.setResult(watch.id,null)
                    }catch(t:Exception){watches.setResult(watch.id,
                        t.message?:"Discovery failed")}
                }
            }
        }
    }
    fun publicArchive(name:String):List<Pair<String,String>> {
        val q=URLEncoder.encode("mediatype:movies AND title:(\"$name\")","UTF-8")
        val api="https://archive.org/advancedsearch.php?q=$q&fl%5B%5D=identifier&fl%5B%5D=title&rows=100&page=1&output=json"
        val doc=Jsoup.connect(api).ignoreContentType(true).timeout(30000)
            .maxBodySize(3500000).execute().body()
        val hits=JSONObject(doc).getJSONObject("response").getJSONArray("docs")
        val out=ArrayList<Pair<String,String>>()
        for(i in 0 until hits.length()){
            val item=hits.getJSONObject(i)
            val id=item.optString("identifier")
            val title=item.optString("title",id)
            if(id.isNotBlank()&&title.contains(name,true))
                out.add(title to "https://archive.org/details/$id")
        }
        return out
    }
    fun namedSource(name:String,template:String):List<Pair<String,String>> {
        val link=if(template.contains("{name}"))
            template.replace("{name}",URLEncoder.encode(name,"UTF-8")) else template
        require(WebDiscovery.isUrl(link)){"Invalid source search address"}
        // Source-page monitoring preserves source locality; it never pretends to search the entire web.
        return WebDiscovery.findOnPage(link,50)
            .filter{it.title.contains(name,true)||it.url.contains(name,true)}
            .map{it.title to it.url}
    }
}

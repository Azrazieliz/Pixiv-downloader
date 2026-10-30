package com.azrael.pixivdumpsync

import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.asterion.videoflow.VideoDb
import com.asterion.videoflow.VideoEngine
import com.asterion.videoflow.WebDiscovery
import com.azrael.galleryflow.AdapterRegistry
import com.azrael.galleryflow.GalleryDb
import com.azrael.galleryflow.SyncMode
import java.net.URI

/** One top-level application for all sources; legacy specialists remain advanced tools. */
class AsterionHomeActivity:Activity(){
    private val theme=NocturneDesign
    private lateinit var root:LinearLayout
    private lateinit var body:LinearLayout
    private lateinit var scroller:ScrollView
    private var page="Add"
    private val pages=listOf("Add","Queue","Library","Sources","Settings")
    private fun dp(v:Int)=theme.px(this,v)
    private fun text(v:String,size:Float=14f,tint:Int=theme.white,bold:Boolean=false)=
        theme.text(this,v,size,tint,bold)
    private fun button(label:String,action:()->Unit)=theme.action(this,label,false,action)
    private fun section(title:String,subtitle:String=""){
        body.addView(text(title,23f,theme.white,true),theme.lp(this,20,4))
        if(subtitle.isNotBlank())body.addView(text(subtitle,12f,theme.secondary))
    }
    private fun card():LinearLayout=theme.card(this)
    private fun put(parent:LinearLayout,v:View,top:Int=9){
        parent.addView(v,theme.lp(this,top))
    }
    private fun edit(hint:String):EditText=EditText(this).apply{
        this.hint=hint;setSingleLine(true);textSize=15f
        setTextColor(theme.white);setHintTextColor(theme.subdued)
        background=theme.shape(this@AsterionHomeActivity,theme.raised,12)
        setPadding(dp(12),dp(9),dp(12),dp(9))
    }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.statusBarColor=theme.background
        window.navigationBarColor=theme.background
        ChapterRecovery.ensure(this)
        XPhotoScheduler.ensure(this)
        root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(theme.background)}
        val head=theme.row(this).apply{setPadding(dp(14),dp(12),dp(14),dp(12))}
        head.addView(theme.brandMark(this,47),LinearLayout.LayoutParams(dp(47),dp(47)))
        val label=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(11),0,0,0)}
        label.addView(text("ASTERION",19f,theme.white,true))
        label.addView(text("DOWNLOADER  •  ALL MEDIA",10f,theme.secondary,true))
        head.addView(label,LinearLayout.LayoutParams(0,-2,1f))
        root.addView(head)
        scroller=ScrollView(this).apply{isFillViewport=true}
        body=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(17),dp(5),dp(17),dp(34))
        }
        scroller.addView(body)
        root.addView(scroller,LinearLayout.LayoutParams(-1,0,1f))
        val tabs=theme.row(this).apply{setPadding(dp(5),dp(7),dp(5),dp(7));setBackgroundColor(theme.surface)}
        pages.forEach{tab->
            val t=text(tab,12f,if(tab==page)theme.primary else theme.secondary,tab==page).apply{
                gravity=Gravity.CENTER;minHeight=dp(50)
                isClickable=true;isFocusable=true
                setOnClickListener{page=tab;render();scroller.scrollTo(0,0)}
            }
            tabs.addView(t,LinearLayout.LayoutParams(0,-2,1f))
        }
        root.addView(tabs)
        setContentView(root)
        when(intent?.action){
            Intent.ACTION_SEND->intent.getStringExtra(Intent.EXTRA_TEXT)?.let{submitInput(it)}
        }
        render()
    }
    override fun onResume(){super.onResume();if(::body.isInitialized)render()}
    private fun render(){
        body.removeAllViews()
        when(page){
            "Add"->renderAdd()
            "Queue"->renderQueue()
            "Library"->renderLibrary()
            "Sources"->renderSources()
            else->renderSettings()
        }
    }
    private fun renderAdd(){
        section("Download anything","One URL, automatic routing; videos, X posts, Pixiv, images and chapter archives.")
        val c=card()
        val input=edit("Paste video, X, Pixiv, gallery or series URL")
        put(c,input,0)
        put(c,theme.action(this,"ADD TO DOWNLOADS",true){
            submitInput(input.text.toString().trim())
        })
        put(c,text("Recognized URL types are handled by their corresponding internal engine.",12f,theme.secondary))
        put(body,c,16)
        val names=card()
        put(names,text("FOLLOW A NAME",15f,theme.white,true),0)
        put(names,text("Discover public videos matching a title or creator. Add a specific site URL or a search URL with {name} to monitor that source.",12f,theme.secondary))
        val query=edit("Person, creator, title or subject")
        val source=edit("Optional source or search URL")
        put(names,query)
        put(names,source)
        put(names,button("ADD AUTOMATIC SEARCH"){
            val name=query.text.toString().trim()
            val src=source.text.toString().trim()
            if(name.length<2){toast("Enter a name");return@button}
            if(src.isNotBlank()&&!WebDiscovery.isUrl(src.replace("{name}","test"))){
                toast("Enter an HTTP(S) source URL");return@button
            }
            val ok=NameWatchDb(this).use{it.add(name,src)}
            if(ok){toast("Name search saved");Thread{ NameCrawler.scan(applicationContext) }.start()}
            else toast("This name and source are already monitored")
            page="Sources";render()
        })
        put(body,names,13)
        val quick=card()
        put(quick,text("DOWNLOAD OPTIONS",14f,theme.white,true),0)
        put(quick,button("Run all pending videos"){VideoEngine.launch(this,false)})
        put(quick,button("Resume pending chapter archives"){ChapterArchives.start(this)})
        put(body,quick,13)
    }
    private fun submitInput(raw:String){
        val value=raw.trim()
        if(!WebDiscovery.isUrl(value)){
            toast("Enter a valid HTTP(S) link");return
        }
        when(SourceRouter.classify(value)){
            DownloadKind.PIXIV->{
                val id=SourceRouter.pixivArtistId(value)
                if(id==null){toast("Add a Pixiv artist profile URL");return}
                AppDb(this).use{it.addArtist(id)}
                val sync=Intent(this,SyncForegroundService::class.java).apply{
                    action=SyncForegroundService.ACTION_START
                    putExtra(SyncForegroundService.EXTRA_MODE,"BACKFILL")
                    putExtra(SyncForegroundService.EXTRA_TARGET_ARTIST_ID,id)
                }
                startForegroundService(sync)
                toast("Pixiv artist added")
            }
            DownloadKind.X->{
                val ok=XPhotoQueue(this).use{it.addSource(value)}
                if(ok)XPhotoEngine.start(this)
                toast(if(ok)"X images queued" else "X account/post already saved")
            }
            DownloadKind.COMIC->{
                val ok=ChapterQueue(this).use{it.enqueue(value,URI(value).path.takeLast(70))}
                if(ok)ChapterArchives.start(this)
                toast(if(ok)"Chapter ZIP queued" else "Archive already in queue")
            }
            DownloadKind.VIDEO->{
                val ok=VideoDb(this).use{it.addVideo(value,value)}
                if(ok)VideoEngine.launch(this,false)
                toast(if(ok)"Video queued" else "Already in video history")
            }
            DownloadKind.GALLERY->{
                toast("Resolving gallery…")
                Thread{
                    val result=runCatching{
                        val adapter=AdapterRegistry.forUrl(value)
                        val entity=adapter.resolveEntity(value)
                        GalleryDb(this).use{it.addEntity(entity)}
                        val service=Intent(this,com.azrael.galleryflow.SyncForegroundService::class.java)
                            .setAction(com.azrael.galleryflow.SyncForegroundService.ACTION_START)
                            .putExtra(com.azrael.galleryflow.SyncForegroundService.EXTRA_MODE,SyncMode.BACKFILL.name)
                            .putExtra(com.azrael.galleryflow.SyncForegroundService.EXTRA_SITE_ID,entity.stableId)
                        startForegroundService(service)
                    }
                    runOnUiThread{toast(if(result.isSuccess)"Gallery queued" else
                        "Gallery error: ${result.exceptionOrNull()?.message}")}
                }.start()
            }
        }
        page="Queue";render()
    }
    private fun renderQueue(){
        section("Transfer queue","Persistent history across supported sources.")
        val videos=VideoDb(this).use{it.listVideos(200)}
        val comics=ChapterQueue(this).use{it.list()}
        val xphotos=XPhotoQueue(this).use{it.recent(100)}
        val artists=AppDb(this).use{it.listArtists()}
        val galleries=GalleryDb(this).use{it.listEntities()}
        put(body,text("${videos.count{it.status=="running"||it.status=="queued"}+
            comics.count{it.state=="working"||it.state=="queued"}+
            xphotos.count{it.state=="running"||it.state=="queued"}} pending   •   "+
            "${videos.count{it.status=="error"}+comics.count{it.state=="error"}+xphotos.count{it.state=="error"}} failed",
            14f,theme.primary,true))
        val actions=theme.row(this)
        actions.addView(button("RESUME VIDEOS"){VideoEngine.launch(this,false)},
            LinearLayout.LayoutParams(0,-2,1f))
        actions.addView(button("RESUME ZIP"){ChapterArchives.start(this)},
            LinearLayout.LayoutParams(0,-2,1f).apply{leftMargin=dp(8)})
        put(body,actions)
        put(body,button("RESUME X IMAGE DOWNLOADS"){XPhotoEngine.start(this)})
        xphotos.take(35).forEach{photo->
            val c=card()
            put(c,text("X IMAGE · ${photo.state.uppercase()}",13f,theme.primary,true),0)
            put(c,text(photo.photo,11f,theme.secondary))
            if(photo.error!=null)put(c,text(photo.error,11f,theme.danger))
            if(photo.state=="error")put(c,button("RETRY"){
                XPhotoQueue(this).use{it.retry(photo.id)}
                XPhotoEngine.start(this);render()
            })
            if(photo.saved!=null)put(c,button("OPEN"){openFile(photo.saved,"image/*")})
            put(body,c,9)
        }
        videos.take(70).forEach{v->
            val c=card()
            put(c,text("VIDEO  ·  ${v.status.uppercase()}  ·  ${v.progress}%",13f,theme.primary,true),0)
            put(c,text(v.title.take(120),13f))
            if(v.error!=null)put(c,text(v.error.take(150),11f,theme.danger))
            if(v.status=="error")put(c,button("RETRY"){
                VideoDb(this).use{it.retry(v.id)}
                VideoEngine.launch(this,false);render()
            })
            if(v.savedUri!=null)put(c,button("OPEN"){
                openFile(v.savedUri,"video/*")
            })
            put(body,c,9)
        }
        comics.take(70).forEach{v->
            val c=card()
            put(c,text("CHAPTER ZIP  ·  ${v.state.uppercase()}  ·  ${v.progress}%",13f,theme.violet,true),0)
            put(c,text(v.title,13f))
            if(v.error!=null)put(c,text(v.error.take(150),11f,theme.danger))
            if(v.state=="error")put(c,button("RETRY"){
                ChapterQueue(this).use{it.retry(v.id)}
                ChapterArchives.start(this);render()
            })
            if(v.savedUri!=null)put(c,button("OPEN"){openFile(v.savedUri,"application/zip")})
            put(body,c,9)
        }
        put(body,text("${artists.size} Pixiv artists  ·  ${galleries.size} image sources",13f,theme.secondary))
        artists.take(20).forEach{a->put(body,text("PIXIV  ·  ${a.label?:a.userId}  ·  ${a.archiveStatus}",12f))}
        galleries.take(20).forEach{g->put(body,text("IMAGES  ·  ${g.displayName}",12f))}
    }
    private fun openFile(uri:String,mime:String){
        runCatching{startActivity(Intent(Intent.ACTION_VIEW).apply{
            setDataAndType(Uri.parse(uri),mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })}.onFailure{toast("No app is available to open this file")}
    }
    private fun renderLibrary(){
        section("Saved media","Files are retained in their original download folders.")
        val projection=arrayOf(MediaStore.Downloads._ID,MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.RELATIVE_PATH,MediaStore.Downloads.SIZE)
        runCatching{
            contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,projection,
                null,null,"${MediaStore.Downloads.DATE_ADDED} DESC")?.use{c->
                var count=0
                while(c.moveToNext()&&count<100){
                    val dir=c.getString(2).orEmpty()
                    if(!listOf("VideoFlow","Kuroha","NocturnePixiv","GalleryFlow",
                            "NocturneGallery","AsterionDownloader").any{dir.contains(it,true)})
                        continue
                    val item=card()
                    val name=c.getString(1).orEmpty()
                    val id=c.getLong(0)
                    val uri=ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,id)
                    put(item,text(name,14f,theme.white,true),0)
                    put(item,text("${dir} · ${c.getLong(3)/1024} KB",11f,theme.secondary))
                    put(item,button("OPEN"){
                        openFile(uri.toString(),if(name.endsWith(".zip",true))
                            "application/zip" else if(name.endsWith(".mp4",true))
                            "video/*" else "image/*")
                    })
                    put(body,item,8);count++
                }
                if(count==0)put(body,text("No visible downloaded media yet.",13f,theme.secondary))
            }
        }.onFailure{put(body,text("Library could not be read: ${it.message}",12f,theme.danger))}
    }
    private fun renderSources(){
        section("Monitored sources","Pixiv artists, image sites, video accounts and name searches.")
        val watches=VideoDb(this).use{it.listWatches()}
        for(w in watches){
            val c=card()
            put(c,text(w.label,14f,theme.white,true),0)
            put(c,text(w.address,11f,theme.secondary))
            put(c,button(if(w.enabled)"PAUSE SOURCE" else "ENABLE SOURCE"){
                VideoDb(this).use{it.enableWatch(w.id,!w.enabled)};render()
            })
            put(body,c,9)
        }
        NameWatchDb(this).use{db->
            for(w in db.list()){
                val c=card()
                put(c,text("NAME · ${w.name}",14f,theme.white,true),0)
                put(c,text(if(w.source.isBlank())"Internet Archive public search"
                    else w.source,11f,theme.secondary))
                if(w.lastError!=null)put(c,text(w.lastError,11f,theme.danger))
                put(c,button(if(w.enabled)"PAUSE SEARCH" else "ENABLE SEARCH"){
                    db.setEnabled(w.id,!w.enabled);render()
                })
                put(body,c,9)
            }
        }
        put(body,button("SCAN ENABLED SOURCES"){VideoEngine.launch(this,true)})
    }
    private fun renderSettings(){
        section("Settings","Account, downloads, storage and cloud settings.")
        val prefs=getSharedPreferences("video_options",MODE_PRIVATE)
        val quality=card()
        put(quality,text("VIDEO QUALITY",14f,theme.white,true),0)
        val choices=listOf("Best MP4","Best available","1080p max","720p max","Audio only")
        val values=listOf("best[ext=mp4]/best","best",
            "bestvideo[height<=1080]+bestaudio/best[height<=1080]",
            "bestvideo[height<=720]+bestaudio/best[height<=720]","bestaudio/best")
        val spinner=Spinner(this)
        spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,choices)
        spinner.setSelection(values.indexOf(prefs.getString("quality",values[0])).coerceAtLeast(0))
        put(quality,spinner)
        put(quality,button("SAVE QUALITY"){
            prefs.edit().putString("quality",values[spinner.selectedItemPosition]).apply()
            toast("Quality saved")
        })
        put(body,quality)
        val settings=card()
        put(settings,text("ADVANCED SOURCE SETTINGS",14f,theme.white,true),0)
        put(settings,button("Pixiv login, following feed and filters"){
            startActivity(Intent(this,MainActivity::class.java))
        })
        put(settings,button("Gallery sites, tag filters and backfill"){
            startActivity(Intent(this,com.azrael.galleryflow.MainActivity::class.java))
        })
        put(settings,button("Video cookies, schedule and Round Sync"){
            startActivity(Intent(this,com.asterion.videoflow.MainActivity::class.java))
        })
        put(body,settings,12)
        put(body,text("Cloud uploads are not marked verified without confirmation from the destination. Originals are retained.",12f,theme.secondary))
    }
}

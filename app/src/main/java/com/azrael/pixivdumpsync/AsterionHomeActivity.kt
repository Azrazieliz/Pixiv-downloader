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
    private var page="Home"
    private val pages=listOf("Home","Queue","Library")
    private val navButtons=linkedMapOf<String,TextView>()
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
        label.addView(text("ASTERION DOWNLOADER",19f,theme.white,true))
        label.addView(text("Creator archives · Sync · Backfill",11f,theme.secondary))
        head.addView(label,LinearLayout.LayoutParams(0,-2,1f))
        root.addView(head)
        val settings=text("SETTINGS",12f,theme.secondary,true).apply{
            gravity=Gravity.CENTER
            isClickable=true
            isFocusable=true
            contentDescription="Open settings"
            setPadding(dp(12),dp(10),dp(12),dp(10))
            setOnClickListener{open("Settings")}
        }
        head.addView(settings)
        scroller=ScrollView(this).apply{isFillViewport=true}
        body=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(17),dp(5),dp(17),dp(34))
        }
        scroller.addView(body)
        root.addView(scroller,LinearLayout.LayoutParams(-1,0,1f))
        val tabs=theme.row(this).apply{setPadding(dp(5),dp(7),dp(5),dp(7));setBackgroundColor(theme.surface)}
        pages.forEach{tab->
            val title=when(tab){"Home"->"SOURCES";"Queue"->"DOWNLOADS";else->"FILES"}
            val t=text(title,14f,if(tab==page)theme.primary else theme.secondary,tab==page).apply{
                gravity=Gravity.CENTER;minHeight=dp(50)
                isClickable=true;isFocusable=true
                setOnClickListener{open(tab)}
            }
            navButtons[tab]=t
            tabs.addView(t,LinearLayout.LayoutParams(0,-2,1f))
        }
        root.addView(tabs)
        // Android 15+ enforces edge-to-edge: keep the status/navigation bars clear.
        if(android.os.Build.VERSION.SDK_INT>=30){
            root.setOnApplyWindowInsetsListener{_,insets->
                val status=insets.getInsets(android.view.WindowInsets.Type.statusBars())
                val navigation=insets.getInsets(android.view.WindowInsets.Type.navigationBars())
                head.setPadding(dp(14),dp(12)+status.top,dp(14),dp(12))
                tabs.setPadding(dp(5),dp(7),dp(5),dp(7)+navigation.bottom)
                insets
            }
        }
        setContentView(root)
        window.decorView.systemUiVisibility=0
        when(intent?.action){
            Intent.ACTION_SEND->intent.getStringExtra(Intent.EXTRA_TEXT)?.let{submitInput(it)}
        }
        render()
    }
    override fun onResume(){super.onResume();if(::body.isInitialized)render()}
    @Suppress("DEPRECATION")
    override fun onBackPressed(){
        if(page!="Home")open("Home") else super.onBackPressed()
    }
    private fun open(target:String){
        page=target
        if(::body.isInitialized){render();scroller.scrollTo(0,0)}
    }
    private fun render(){
        navButtons.forEach{(tab,view)->
            view.setTextColor(if(tab==page)theme.primary else theme.secondary)
            view.setTypeface(null,if(tab==page)android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
        body.removeAllViews()
        when(page){
            "Home"->renderHome()
            "Pixiv"->AsterionAccountPanels.pixiv(this,body){render()}
            "Toonily"->renderToonily()
            "X"->AsterionAccountPanels.x(this,body){render()}
            "Site"->AsterionAccountPanels.cosplay(this,body){render()}
            "Videos"->renderAdd()
            "Queue"->renderQueue()
            "Library"->renderLibrary()
            "Sources"->renderSources()
            else->renderSettings()
        }
        if(page!="Home"){
            put(body,button("BACK TO SOURCE HOME"){open("Home")},24)
        }
    }
    private fun sourceTile(label:String,subtitle:String,icon:Int,tint:Int,
                           target:String):LinearLayout=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL
        gravity=Gravity.CENTER_HORIZONTAL
        minimumHeight=dp(180)
        setPadding(dp(13),dp(19),dp(13),dp(17))
        background=theme.touch(this@AsterionHomeActivity,theme.surface,19)
        isClickable=true
        isFocusable=true
        contentDescription="$label. $subtitle"
        setOnClickListener{
            when(target){
                "Pixiv"->startActivity(Intent(this@AsterionHomeActivity,MainActivity::class.java))
                "Kyora"->startActivity(Intent(this@AsterionHomeActivity,com.azrael.galleryflow.MainActivity::class.java))
                else->open(target)
            }
        }
        val symbol=ImageView(this@AsterionHomeActivity).apply{
            setImageResource(icon)
            imageTintList=android.content.res.ColorStateList.valueOf(tint)
            scaleType=ImageView.ScaleType.FIT_CENTER
            setPadding(dp(11),dp(11),dp(11),dp(11))
            background=theme.shape(this@AsterionHomeActivity,theme.raised,18)
            contentDescription="$label icon"
        }
        addView(symbol,LinearLayout.LayoutParams(dp(68),dp(68)))
        addView(text(label,16f,theme.white,true).apply{
            gravity=Gravity.CENTER
            setPadding(0,dp(10),0,0)
            maxLines=2
        },LinearLayout.LayoutParams(-1,-2))
        addView(text(subtitle,11f,theme.secondary).apply{
            gravity=Gravity.CENTER
            setPadding(0,dp(6),0,0)
            maxLines=3
        },LinearLayout.LayoutParams(-1,-2))
    }
    private fun tileRow(left:View,right:View){
        val row=theme.row(this)
        row.gravity=Gravity.TOP
        row.addView(left,LinearLayout.LayoutParams(0,-2,1f))
        row.addView(right,LinearLayout.LayoutParams(0,-2,1f).apply{leftMargin=dp(11)})
        put(body,row,12)
    }
    private fun renderHome(){
        section("Choose a source","Connect accounts and websites, synchronize new posts or backfill older content.")
        tileRow(
            sourceTile("Pixiv","Artists · Account sync · Backfill",
                R.drawable.ic_source_pixiv,android.graphics.Color.rgb(65,161,255),"Pixiv"),
            sourceTile("Toonily","Manhwa series · Complete ZIP",
                R.drawable.ic_source_toonily,theme.violet,"Toonily")
        )
        tileRow(
            sourceTile("X","Follow accounts · Photo sync · Backfill",
                R.drawable.ic_source_x,theme.white,"X"),
            sourceTile("Kyora","Cosplay creators · Sync · Backfill",
                R.drawable.ic_source_kyora,theme.violet,"Kyora")
        )
        tileRow(
            sourceTile("Add Site","Bind a new website · Sign in · Backfill",
                R.drawable.ic_source_site,theme.primary,"Site"),
            sourceTile("Video Search","Direct sites · Videos & previews",
                R.drawable.ic_source_video,theme.primary,"Videos")
        )
        val counts=card()
        val pixivCount=AppDb(this).use{it.listArtists().size}
        val siteCount=GalleryDb(this).use{it.listEntities().size}
        val xCount=XPhotoQueue(this).use{it.allSources().size}
        put(counts,text("YOUR ARCHIVE",12f,theme.secondary,true),0)
        put(counts,text("$pixivCount Pixiv artists  ·  $siteCount sites  ·  $xCount X accounts",
            14f,theme.white,true),5)
        put(body,counts,18)
        val tools=card()
        put(tools,text("Other download tools",15f,theme.white,true),0)
        put(tools,text("Video links, keyword searches and optional external sources.",
            12f,theme.secondary))
        put(tools,button("VIDEO URL DOWNLOADS & SEARCH"){open("Videos")})
        put(body,tools,16)
    }
    private fun renderToonily(){
        section("Toonily · Manhwa to ZIP",
            "Download every accessible chapter and image as one archive in reading order.")
        val inputCard=card()
        put(inputCard,text("Full series download",18f,theme.white,true),0)
        put(inputCard,text("Paste a Toonily series link or supported manhwa website. All discovered chapters are archived in ascending order, including fractional chapters.",
            12f,theme.secondary))
        val input=edit("https://toonily.com/serie/...")
        put(inputCard,input)
        put(inputCard,theme.action(this,"SAVE ALL CHAPTERS AS ZIP",true){
            val url=input.text.toString().trim()
            if(!WebDiscovery.isUrl(url)){toast("Enter a valid series or chapter URL");return@action}
            val slug=runCatching{
                val pieces=URI(url).path.trim('/').split('/')
                if(pieces.firstOrNull() in listOf("serie","series","manga","manhwa"))
                    pieces.getOrNull(1) else pieces.lastOrNull()
            }.getOrNull().orEmpty().ifBlank{"Manhwa series"}
            val added=ChapterQueue(this).use{it.enqueue(url,slug)}
            if(added)ChapterArchives.start(this)
            toast(if(added)"Series ZIP queued" else "Series is already in downloads")
            render()
        })
        put(body,inputCard,12)
        section("Series downloads","Progress and site-specific access errors are shown here.")
        val jobs=ChapterQueue(this).use{it.list().take(25)}
        if(jobs.isEmpty())put(body,text("No manhwa series queued yet.",13f,theme.secondary),10)
        for(job in jobs){
            val c=card()
            put(c,text(job.title,16f,theme.white,true),0)
            put(c,text(job.state.uppercase()+" · "+job.progress+"% · "+
                job.attempts+" attempt(s)",12f,theme.secondary))
            if(!job.error.isNullOrBlank())
                put(c,text(job.error.take(330),12f,theme.danger))
            if(job.state=="error")put(c,button("RETRY ZIP"){
                ChapterQueue(this).use{it.retry(job.id)}
                ChapterArchives.start(this)
                render()
            })
            if(job.state=="done" && !job.savedUri.isNullOrBlank())
                put(c,text("ZIP saved to Downloads / AsterionDownloader / Comics",
                    12f,theme.primary))
            put(body,c,10)
        }
    }
    private fun renderAdd(){
        section("Video tools","Secondary tools for individual video URLs and keyword searches.")
        val byLink=card()
        put(byLink,text("Download a video URL",18f,theme.white,true),0)
        put(byLink,text("Video sites and direct files. Other supported links are routed automatically.",
            12f,theme.secondary))
        val input=edit("Paste a video URL")
        val clip=getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        put(byLink,input)
        put(byLink,theme.action(this,"DOWNLOAD LINK",true){
            submitInput(input.text.toString().trim())
        })
        put(byLink,button("PASTE FROM CLIPBOARD"){
            val value=clip.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
            if(value.isNotBlank())input.setText(value) else toast("Clipboard is empty")
        })
        put(body,byLink,12)

        val byName=card()
        put(byName,text("Search videos by name",18f,theme.white,true),0)
        put(byName,text("Search all saved sources. Review the matches and choose what to download.",
            12f,theme.secondary))
        val searchPrefs=getSharedPreferences("asterion_video_search",MODE_PRIVATE)
        val query=edit("Creator, title or keywords")
        query.setText(searchPrefs.getString("last_query",""))
        put(byName,query)
        val follow=CheckBox(this).apply{
            text="Keep checking for new videos"
            setTextColor(theme.secondary)
            isChecked=false
        }
        put(byName,follow)
        put(byName,theme.action(this,"SEARCH VIDEOS",true){
            val value=query.text.toString().trim()
            if(value.isBlank()){toast("Enter a name or keywords");return@action}
            searchPrefs.edit().putString("last_query",value)
                .remove("last_error").apply()
            if(follow.isChecked)NameWatchDb(this).use{it.add(value)}
            toast("Searching your saved video sources…")
            Thread {
                val outcome=runCatching{KeywordSources.searchDetailed(applicationContext,value)}
                outcome.onSuccess { result->
                    SearchResultsDb(applicationContext).use{it.save(value,result.hits)}
                    val failures=result.failures.take(5).joinToString(" | ")
                    searchPrefs.edit().putString("last_error",failures).apply()
                    runOnUiThread{
                        toast("${result.hits.size} matches from ${result.checked} sources")
                        render()
                    }
                }.onFailure{error->
                    searchPrefs.edit().putString("last_error",error.message).apply()
                    runOnUiThread{toast("Search error: ${error.message}");render()}
                }
            }.start()
        })
        put(body,byName,12)

        val lastQuery=searchPrefs.getString("last_query","").orEmpty()
        val hits=if(lastQuery.isBlank())emptyList() else
            SearchResultsDb(this).use{it.listForQuery(lastQuery,60)}
        val sourceErrors=searchPrefs.getString("last_error","").orEmpty()
        if(lastQuery.isNotBlank()){
            section("Results for: "+lastQuery,
                "${hits.size} saved candidates · ranked by match. Results are not completed downloads.")
            if(sourceErrors.isNotBlank())put(body,
                text("Source errors: "+sourceErrors.take(350),12f,theme.danger))
            if(hits.isEmpty())put(body,
                text("No matching videos indexed yet. Check the source errors or search another name.",
                    13f,theme.secondary))
            for(hit in hits.take(25)){
                val c=card()
                val thumbnail=ImageView(this).apply{
                    minimumHeight=dp(124)
                    setBackgroundColor(theme.raised)
                    adjustViewBounds=true
                    layoutParams=LinearLayout.LayoutParams(-1,dp(160))
                }
                put(c,thumbnail,0)
                VideoSearchPreviews.display(this,thumbnail,hit.thumbnail)
                put(c,text(hit.title,16f,theme.white,true),8)
                put(c,text(hit.source+" · Video result",12f,theme.primary))
                put(c,text(hit.url.take(120),11f,theme.subdued))
                if(!hit.queued){
                    put(c,button("DOWNLOAD THIS RESULT"){
                        val added=VideoDb(this).use{it.addVideo(hit.url,hit.title)}
                        if(added){
                            SearchResultsDb(this).use{it.markQueued(hit.query,hit.url)}
                            VideoEngine.launch(this,false)
                        }
                        toast(if(added)"Added to downloads" else "Already in downloads")
                        open("Queue")
                    })
                }else put(c,text("Already added to downloads",12f,theme.primary))
                put(body,c,8)
            }
        }
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
                if(ok){XPhotoScheduler.ensure(this);XPhotoEngine.start(this)}
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
        section("Downloads","Queued, downloading, completed and failed files in one place.")
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
        actions.addView(button("RETRY VIDEOS"){VideoEngine.launch(this,false)},
            LinearLayout.LayoutParams(0,-2,1f))
        actions.addView(button("RETRY CHAPTERS"){ChapterArchives.start(this)},
            LinearLayout.LayoutParams(0,-2,1f).apply{leftMargin=dp(8)})
        put(body,actions)
        put(body,button("RETRY X IMAGES"){XPhotoEngine.start(this)})
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
        section("My files","Downloaded images, videos and chapter ZIPs.")
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
        put(body,button("SCAN X PHOTOS"){XPhotoEngine.start(this)})
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
        val discoveries=SearchResultsDb(this).use{it.list(60)}
        if(discoveries.isNotEmpty()){
            put(body,text("SEARCH RESULTS",17f,theme.white,true),21)
            put(body,text("Candidates include original source URLs. A result is not a completed download.",12f,theme.secondary))
            for(hit in discoveries){
                val c=card()
                put(c,text(hit.title,14f,theme.white,true),0)
                put(c,text("${hit.source} · ${hit.query} · relevance ${hit.score}",
                    11f,theme.secondary))
                put(c,text(hit.url,11f,theme.subdued))
                if(!hit.queued)put(c,button("QUEUE THIS VIDEO"){
                    val added=VideoDb(this).use{it.addVideo(hit.url,hit.title)}
                    if(added){
                        SearchResultsDb(this).use{it.markQueued(hit.query,hit.url)}
                        VideoEngine.launch(this,false)
                    }
                    toast(if(added)"Video queued" else "Already in video download history")
                    render()
                })
                put(body,c,9)
            }
        }
    }
    private fun renderSettings(){
        section("Settings","Search sources, Tor connection and download quality.")
        val netPrefs=getSharedPreferences("video_options",MODE_PRIVATE)
        val tor=Switch(this).apply{
            text="Use Orbot / Tor for hidden (.onion) sites"
            setTextColor(theme.white)
            isChecked=netPrefs.getBoolean("tor_proxy",false)
            setOnCheckedChangeListener{_,value->
                netPrefs.edit().putBoolean("tor_proxy",value).apply()
            }
        }
        put(body,tor)
        val prefs=getSharedPreferences("video_options",MODE_PRIVATE)
        val searchConfig=card()
        put(searchConfig,text("SEARCH PROVIDERS",14f,theme.white,true),0)
        put(searchConfig,text("One per line: searxng|https://your-index.example or html|https://website.example/search?q={query}. Adult and alternative sites are eligible. A local SOCKS proxy is needed for .onion endpoints.",12f,theme.secondary))
        val searchInput=edit("searxng|https://index.example")
        searchInput.setSingleLine(false)
        searchInput.minLines=4
        searchInput.maxLines=8
        searchInput.gravity=Gravity.TOP
        searchInput.setText(KeywordSources.sources(this))
        put(searchConfig,searchInput)
        put(searchConfig,button("SAVE SEARCH PROVIDERS"){
            val error=runCatching{
                KeywordSources.setSources(this,searchInput.text.toString())
            }.exceptionOrNull()
            toast(if(error==null)"Search providers saved"
                else "Source settings: ${error.message}")
        })
        put(body,searchConfig)
        put(body,button("MANAGE SAVED SEARCHES"){page="Sources";render()})
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

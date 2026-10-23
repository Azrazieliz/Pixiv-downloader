package com.azrael.galleryflow

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Size
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** Native gallery browser with bounded thumbnail pages, preserving the full-size archive. */
class GalleryDetailActivity : Activity() {
    companion object {
        const val EXTRA_GALLERY_ID = "gallery_id"
        const val EXTRA_SOURCE = "gallery_source"
        const val EXTRA_TITLE = "gallery_title"
    }
    private val pool = Executors.newFixedThreadPool(2)
    private lateinit var grid: GridLayout
    private lateinit var moreButton: Button
    private var shown = 0
    private var images: List<MediaRecord> = emptyList()
    private val bg = Color.rgb(14, 17, 24)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val id = intent.getStringExtra(EXTRA_GALLERY_ID) ?: ""
        val source = runCatching {
            SourceId.fromWire(intent.getStringExtra(EXTRA_SOURCE).orEmpty())
        }.getOrNull()
        if (source == null || id.isEmpty()) { finish(); return }
        images = GalleryDb(this).use { db ->
            db.galleryMedia(source, id).filter { it.contentUri != null && it.state == TransferState.COMPLETE }
        }
        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(20), dp(14), dp(26))
        }
        scroll.addView(root)
        root.addView(TextView(this).apply {
            text = intent.getStringExtra(EXTRA_TITLE) ?: "Gallery"
            textSize = 23f; setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "${images.size} locally available images"
            textSize = 13f; setTextColor(Color.LTGRAY)
            setPadding(0, dp(8), 0, dp(12))
        })
        grid = GridLayout(this).apply { columnCount = 2 }
        root.addView(grid)
        moreButton = Button(this).apply {
            text = "Load more images"
            setOnClickListener { addPage() }
        }
        root.addView(moreButton)
        setContentView(scroll)
        addPage()
    }

    private fun addPage() {
        val next = minOf(images.size, shown + 24)
        val width = (resources.displayMetrics.widthPixels - dp(44)) / 2
        for (i in shown until next) {
            val record = images[i]
            val view = ImageView(this).apply {
                setBackgroundColor(Color.rgb(32, 36, 48))
                setImageResource(android.R.drawable.ic_menu_gallery)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            grid.addView(view, GridLayout.LayoutParams().apply {
                this.width = width; height = width
                setMargins(dp(2), dp(2), dp(2), dp(2))
            })
            val uri = Uri.parse(record.contentUri)
            pool.execute {
                val bitmap = runCatching {
                    contentResolver.loadThumbnail(uri, Size(450, 450), null)
                }.getOrNull()
                if (bitmap != null) runOnUiThread {
                    if (!isFinishing && view.parent != null) view.setImageBitmap(bitmap)
                }
            }
        }
        shown = next
        moreButton.visibility = if (shown < images.size) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        pool.shutdownNow()
        super.onDestroy()
    }
}

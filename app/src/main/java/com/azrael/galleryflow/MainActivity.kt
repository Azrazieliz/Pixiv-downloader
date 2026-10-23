package com.azrael.galleryflow

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.util.Size
import java.util.concurrent.Executors
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private var siteFilter: String? = null
    private val thumbnailPool = Executors.newFixedThreadPool(2)
    private lateinit var addressInput: EditText
    private lateinit var status: TextView
    private lateinit var entityList: LinearLayout
    private val handler = Handler(Looper.getMainLooper())

    private val poll = object : Runnable {
        override fun run() {
            val snapshot = SyncControl.snapshot()
            status.text = if (snapshot.running) snapshot.message else "Idle • " + entityCount() + " entities"
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        siteFilter = intent.getStringExtra(EXTRA_SITE_ID)
        setContentView(buildUi())
        if (intent.getBooleanExtra(EXTRA_ADD_SITE, false)) {
            addressInput.requestFocus()
        }
        requestNotificationPermission()
        Scheduler.ensure(this)
    }

    override fun onResume() {
        super.onResume()
        renderEntities()
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    override fun onDestroy() {
        thumbnailPool.shutdownNow()
        super.onDestroy()
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        super.onPause()
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this).apply { setBackgroundColor(BG) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(40))
        }
        scroll.addView(root)

        root.addView(label("KYORA  /  GALLERY SOURCES", 26f, Color.WHITE))
        root.addView(label("Individual sources • flat image archive • tag filters", 13f, MUTED))
        root.addView(Button(this).apply {
            text = "← All sites"
            setOnClickListener { siteFilter = null; renderEntities() }
        }, LinearLayout.LayoutParams(-1, dp(40)))
        status = label("Idle", 14f, ACCENT).apply { setPadding(0, dp(12), 0, dp(12)) }
        root.addView(status)

        val input = EditText(this).apply {
            hint = "Add image site or gallery URL (https://…)"
            setHintTextColor(MUTED)
            setTextColor(Color.WHITE)
            setSingleLine(true)
            setBackgroundColor(SURFACE)
            setPadding(dp(12), 0, dp(12), 0)
        }
        addressInput = input
        root.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))

        root.addView(Button(this).apply {
            text = "Add image site / gallery"
            setOnClickListener {
                val button = this
                val url = input.text.toString().trim()
                if (url.isBlank()) return@setOnClickListener
                button.isEnabled = false
                status.text = "Resolving source…"
                Thread {
                    val result = runCatching {
                        val adapter = AdapterRegistry.forUrl(url)
                        if (!adapter.enabled) throw AdapterException(adapter.statusLabel)
                        adapter.resolveEntity(url).also { entity ->
                            GalleryDb(this@MainActivity).use { db -> db.addEntity(entity) }
                        }
                    }
                    runOnUiThread {
                        button.isEnabled = true
                        result.onSuccess { entity ->
                            input.text.clear()
                            status.text = "Added " + entity.displayName
                            siteFilter = entity.stableId
                            renderEntities()
                        }.onFailure { error ->
                            status.text = error.message ?: error.javaClass.simpleName
                            Toast.makeText(this@MainActivity, status.text, Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(8) })

        root.addView(Button(this).apply {
            text = "Import sites from old Kyora JSON"
            setOnClickListener {
                val pick = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json")
                startActivityForResult(pick, 708)
            }
        }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })

        val syncRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        syncRow.addView(Button(this).apply {
            text = "Live Sync"
            setOnClickListener { startSync(SyncMode.LIVE) }
        }, LinearLayout.LayoutParams(0, dp(50), 1f))
        syncRow.addView(Button(this).apply {
            text = "Backfill"
            setOnClickListener { startSync(SyncMode.BACKFILL) }
        }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { leftMargin = dp(8) })
        root.addView(syncRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })

        val transport = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        transport.addView(Button(this).apply {
            text = "Pause / Resume"
            setOnClickListener {
                val action = if (SyncControl.snapshot().paused) SyncForegroundService.ACTION_RESUME else SyncForegroundService.ACTION_PAUSE
                startService(Intent(this@MainActivity, SyncForegroundService::class.java).setAction(action))
            }
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        transport.addView(Button(this).apply {
            text = "Stop"
            setOnClickListener {
                startService(Intent(this@MainActivity, SyncForegroundService::class.java).setAction(SyncForegroundService.ACTION_STOP))
            }
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(8) })
        root.addView(transport, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        root.addView(label("DOWNLOAD FILTERS", 12f, MUTED).apply {
            setPadding(0, dp(18), 0, dp(6))
        })
        root.addView(label(
            "Blocked exact gallery tags (case/Unicode insensitive). A matching gallery is skipped before image downloads; existing files are retained.",
            12f, MUTED
        ))
        root.addView(Button(this).apply {
            text = "Edit excluded tags"
            setOnClickListener { showExcludedTagsDialog() }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })

        root.addView(label("TERABOX / ROUND SYNC", 12f, MUTED).apply {
            setPadding(0, dp(18), 0, dp(6))
        })
        root.addView(label(
            "Configure a Round Sync COPY task from Downloads/GalleryFlow to your TeraBox remote. Downloads remain on this device until transfer is confirmed in Round Sync.",
            12f, MUTED
        ))
        root.addView(Button(this).apply {
            text = "Round Sync settings"
            setOnClickListener { RoundSyncBridge.showSettings(this@MainActivity) }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        root.addView(Button(this).apply {
            text = "Run TeraBox backup now"
            setOnClickListener {
                Toast.makeText(this@MainActivity, RoundSyncBridge.trigger(this@MainActivity), Toast.LENGTH_LONG).show()
            }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })

        val autoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        autoRow.addView(label("Automatic periodic Live checks", 14f, Color.WHITE), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        autoRow.addView(Switch(this).apply {
            isChecked = Prefs.autoSync(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                Prefs.setAutoSync(this@MainActivity, checked)
                if (checked) Scheduler.ensure(this@MainActivity) else Scheduler.cancel(this@MainActivity)
            }
        })
        root.addView(autoRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })

        val exportRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        exportRow.addView(Button(this).apply {
            text = "Export JSON"
            setOnClickListener { export(true) }
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        exportRow.addView(Button(this).apply {
            text = "Export CSV"
            setOnClickListener { export(false) }
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(8) })
        root.addView(exportRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        root.addView(label("SOURCES", 12f, MUTED).apply { setPadding(0, dp(18), 0, dp(6)) })
        AdapterRegistry.adapters.forEach { adapter ->
            val text = adapter.source.wireName + ": " + if (adapter.enabled) "enabled" else adapter.statusLabel
            root.addView(label(text, 12f, if (adapter.enabled) ACCENT else MUTED))
        }

        root.addView(label("ENTITIES", 12f, MUTED).apply { setPadding(0, dp(18), 0, dp(6)) })
        entityList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(entityList)
        return scroll
    }

    private fun showExcludedTagsDialog() {
        val editor = EditText(this).apply {
            setText(Prefs.excludedTags(this@MainActivity))
            hint = "One exact gallery tag per line"
            minLines = 6
            gravity = Gravity.TOP
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
            addView(label("One tag per line. No partial matches; old downloads are not deleted.", 13f, MUTED))
            addView(editor)
        }
        AlertDialog.Builder(this).setTitle("Excluded gallery tags").setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                Prefs.setExcludedTags(this, editor.text.toString())
                Toast.makeText(this, "Tag exclusions saved", Toast.LENGTH_SHORT).show()
            }.show()
    }

    @Deprecated("Legacy file picker callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 708 || resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        Thread {
            val result = runCatching { LegacySourceImporter(this).importSites(uri) }
            runOnUiThread {
                result.onSuccess { count ->
                    siteFilter = null
                    renderEntities()
                    Toast.makeText(this, "Imported $count site entries. Old image records remain in the former app.", Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    Toast.makeText(this, error.message ?: "Import failed", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun renderEntities() {
        if (!::entityList.isInitialized) return
        val allEntities = GalleryDb(this).use { it.listEntities() }
        val entities = allEntities.filter { siteFilter == null || it.entityId == siteFilter }
        entityList.removeAllViews()
        if (entities.isEmpty()) {
            entityList.addView(label(
                if (allEntities.isEmpty()) "No sites yet. Paste a public image-site URL above."
                else "Site not found. Open All sites.", 13f, MUTED
            ))
            return
        }
        entities.forEach { entity ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(SURFACE)
            }
            card.addView(label(entity.displayName + "  •  " + entity.source.wireName, 15f, Color.WHITE))
            card.addView(label(entity.canonicalUrl, 11f, MUTED))
            card.addView(Button(this).apply {
                text = if (siteFilter == entity.entityId) "Viewing site" else "Open site"
                isEnabled = siteFilter != entity.entityId
                setOnClickListener { siteFilter = entity.entityId; renderEntities() }
            })
            if (!entity.lastError.isNullOrBlank()) card.addView(label(entity.lastError, 11f, ERROR))

            val toggles = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            toggles.addView(CheckBox(this).apply {
                text = "Selected"
                setTextColor(Color.WHITE)
                isChecked = entity.selected
                setOnCheckedChangeListener { _, checked ->
                    GalleryDb(this@MainActivity).use { it.setSelected(entity.source, entity.entityId, checked) }
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            toggles.addView(Switch(this).apply {
                text = "Live"
                setTextColor(Color.WHITE)
                isChecked = entity.liveEnabled
                setOnCheckedChangeListener { _, checked ->
                    GalleryDb(this@MainActivity).use { it.setLiveEnabled(entity.source, entity.entityId, checked) }
                }
            })
            card.addView(toggles)
            entityList.addView(
                card,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
            )
        }
        if (siteFilter != null) renderGalleryCards(entities.firstOrNull()?.entityId)
    }

    private fun renderGalleryCards(entityId: String?) {
        if (entityId == null) return
        val galleries = GalleryDb(this).use { it.galleryCards(entityId) }
        val header = label("GALLERIES / ${galleries.size} RECENT", 13f, ACCENT).apply {
            setPadding(0, dp(16), 0, dp(8))
        }
        entityList.addView(header)
        entityList.addView(Button(this).apply {
            text = "Refresh gallery list"
            setOnClickListener { renderEntities() }
        }, LinearLayout.LayoutParams(-1, dp(42)))
        if (galleries.isEmpty()) {
            entityList.addView(label("No galleries cataloged yet. Run Backfill for this site.", 13f, MUTED))
            return
        }
        for (gallery in galleries) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setBackgroundColor(SURFACE)
                isClickable = true
                isFocusable = true
            }
            val thumb = ImageView(this).apply {
                setImageResource(android.R.drawable.ic_menu_gallery)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(BG)
            }
            card.addView(thumb, LinearLayout.LayoutParams(dp(92), dp(92)))
            val details = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), 0, 0, 0)
            }
            details.addView(label(gallery.title.take(90), 14f, Color.WHITE))
            details.addView(label("${gallery.count} images / ${gallery.state}", 12f, MUTED))
            card.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            card.setOnClickListener {
                startActivity(Intent(this, GalleryDetailActivity::class.java)
                    .putExtra(GalleryDetailActivity.EXTRA_GALLERY_ID, gallery.galleryId)
                    .putExtra(GalleryDetailActivity.EXTRA_SOURCE, gallery.source.wireName)
                    .putExtra(GalleryDetailActivity.EXTRA_TITLE, gallery.title))
            }
            entityList.addView(card, LinearLayout.LayoutParams(-1, dp(108)).apply {
                bottomMargin = dp(7)
            })
            if (gallery.firstUri != null) {
                val uri = Uri.parse(gallery.firstUri)
                thumbnailPool.execute {
                    val bitmap = runCatching {
                        contentResolver.loadThumbnail(uri, Size(250, 250), null)
                    }.getOrNull()
                    if (bitmap != null) runOnUiThread {
                        if (!isFinishing && thumb.parent != null) thumb.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }

    private fun startSync(mode: SyncMode) {
        startForegroundService(
            Intent(this, SyncForegroundService::class.java)
                .setAction(SyncForegroundService.ACTION_START)
                .putExtra(SyncForegroundService.EXTRA_MODE, mode.name)
        )
    }

    private fun export(json: Boolean) {
        Thread {
            val result = runCatching {
                if (json) ExportManager(this).exportJson() else ExportManager(this).exportCsv()
            }
            runOnUiThread {
                result.onSuccess {
                    Toast.makeText(this, "Exported to Downloads/GalleryFlow/exports", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(this, it.message ?: "Export failed", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun entityCount(): Int =
        runCatching { GalleryDb(this).use { it.listEntities().size } }.getOrDefault(0)

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 30)
        }
    }

    private fun label(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_SITE_ID = "extra_site_id"
        const val EXTRA_ADD_SITE = "extra_add_site"
        private val BG = Color.rgb(17, 18, 22)
        private val SURFACE = Color.rgb(31, 33, 40)
        private val MUTED = Color.rgb(166, 171, 184)
        private val ACCENT = Color.rgb(116, 196, 255)
        private val ERROR = Color.rgb(255, 125, 125)
    }
}

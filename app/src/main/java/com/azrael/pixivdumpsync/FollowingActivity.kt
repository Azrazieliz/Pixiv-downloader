package com.azrael.pixivdumpsync

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class FollowingActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private lateinit var refreshButton: Button
    private lateinit var syncButton: Button
    private lateinit var stopButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private var loading = false
    private var lastItems: List<PixivApi.ArtworkPreview> = emptyList()
    private var lastRunning = false

    private val syncPoll = object : Runnable {
        override fun run() {
            val snapshot = SyncControl.snapshot()
            status.text = when {
                snapshot.stopping -> "Stopping…"
                snapshot.paused -> "Paused • ${snapshot.message}"
                snapshot.running -> snapshot.message
                loading -> "Loading latest works…"
                lastItems.isEmpty() -> "Latest works from followed artists"
                else -> "Latest Following works • ${lastItems.size} shown"
            }
            syncButton.isEnabled = !snapshot.running && !loading
            stopButton.isEnabled = snapshot.running && !snapshot.stopping

            if (lastRunning && !snapshot.running && lastItems.isNotEmpty()) {
                render(lastItems)
            }
            lastRunning = snapshot.running
            handler.postDelayed(this, 500L)
        }
    }

    private fun dp(value: Int) = UiKit.dp(this, value)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = UiKit.bg
        window.navigationBarColor = UiKit.bg
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(syncPoll)
        handler.post(syncPoll)
        if (lastItems.isEmpty()) loadFeed()
    }

    override fun onPause() {
        handler.removeCallbacks(syncPoll)
        super.onPause()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(UiKit.bg)
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(40))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "Kuroha"
            UiKit.title(this, 27f)
        })
        root.addView(TextView(this).apply {
            text = "Sync every new artwork from your Pixiv Following feed"
            UiKit.body(this, 13f)
            setPadding(0, dp(3), 0, 0)
        })

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, 0)
        }
        tabs.addView(Button(this).apply {
            text = "Archive"
            UiKit.styleSecondaryButton(this@FollowingActivity, this)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        tabs.addView(Button(this).apply {
            text = "New artworks"
            UiKit.stylePrimaryButton(this@FollowingActivity, this)
            isEnabled = false
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            leftMargin = dp(8)
        })
        root.addView(tabs)

        val controls = card()
        val controlHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val statusWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        statusWrap.addView(TextView(this).apply {
            text = "Following feed"
            UiKit.title(this, 16f)
        })
        status = TextView(this).apply {
            text = "Latest works from followed artists"
            UiKit.body(this, 12f)
            setPadding(0, dp(4), dp(8), 0)
        }
        statusWrap.addView(status)
        controlHeader.addView(
            statusWrap,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        refreshButton = Button(this).apply {
            text = "Refresh"
            UiKit.styleSecondaryButton(this@FollowingActivity, this)
            setOnClickListener { loadFeed(force = true) }
        }
        controlHeader.addView(refreshButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(42)
        ))
        controls.addView(controlHeader)

        val syncActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }

        syncButton = Button(this).apply {
            text = "Sync new"
            UiKit.stylePrimaryButton(this@FollowingActivity, this)
            setOnClickListener { startFeedSync() }
        }
        syncActions.addView(
            syncButton,
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        stopButton = Button(this).apply {
            text = "Stop"
            UiKit.styleDangerButton(this@FollowingActivity, this)
            isEnabled = false
            setOnClickListener {
                startService(
                    Intent(this@FollowingActivity, SyncForegroundService::class.java)
                        .setAction(SyncForegroundService.ACTION_STOP)
                )
            }
        }
        syncActions.addView(
            stopButton,
            LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                leftMargin = dp(8)
            }
        )
        controls.addView(syncActions)

        controls.addView(TextView(this).apply {
            text = "Sync new downloads every Following-feed artwork since the last completed feed sync. Already completed works are skipped; failed or incomplete works stay pending and are retried next time. Backfill is only for older artist history."
            UiKit.body(this, 11.5f)
            setPadding(0, dp(10), 0, 0)
        })

        root.addView(controls, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(18) })

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(list, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })

        setContentView(scroll)
    }

    private fun loadFeed(force: Boolean = false) {
        if (loading) return
        if (!SessionStore.isLoggedIn(this)) {
            status.text = "Connect your Pixiv account first."
            return
        }
        if (!force && lastItems.isNotEmpty()) {
            render(lastItems)
            return
        }

        loading = true
        refreshButton.isEnabled = false
        status.text = "Loading latest works…"

        Thread {
            val result = runCatching {
                val api = PixivApi(applicationContext)
                val items = linkedMapOf<String, PixivApi.ArtworkPreview>()

                for (page in 1..3) {
                    val feed = api.followingFeedPage(page)
                    for (item in feed.items) {
                        items.putIfAbsent(item.id, item)
                        if (items.size >= MAX_ITEMS) break
                    }
                    if (items.size >= MAX_ITEMS || feed.isLastPage || feed.items.isEmpty()) break
                }
                items.values.take(MAX_ITEMS)
            }

            runOnUiThread {
                loading = false
                refreshButton.isEnabled = true
                result.onSuccess { items ->
                    lastItems = items
                    if (items.isEmpty()) {
                        list.removeAllViews()
                        status.text = "No Following artworks returned."
                    } else {
                        SessionStore.setFollowingPreviewJson(
                            this,
                            ArtworkPreviewCodec.encode(items.take(10))
                        )
                        render(items)
                        status.text = "Latest Following works • ${items.size} shown"
                    }
                }.onFailure { error ->
                    status.text = "Could not load Following: ${error.message ?: error.javaClass.simpleName}"
                    Toast.makeText(
                        this,
                        status.text,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun render(items: List<PixivApi.ArtworkPreview>) {
        list.removeAllViews()
        AppDb(this).use { db ->
            for ((index, preview) in items.withIndex()) {
                val downloaded = db.isWorkDoneAndBookmarked(preview.id)
                list.addView(
                    artworkCard(preview, downloaded),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (index > 0) topMargin = dp(9)
                    }
                )
            }
        }
    }

    private fun artworkCard(
        preview: PixivApi.ArtworkPreview,
        downloaded: Boolean
    ): View {
        val card = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = UiKit.rounded(this@FollowingActivity, UiKit.surfaceAlt, 13, UiKit.line)
            clipToOutline = true
            setImageResource(R.drawable.ic_kuroha)
            contentDescription = preview.title.ifBlank { "Pixiv artwork ${preview.id}" }
        }
        RemoteImageLoader.load(
            this,
            image,
            preview.thumbnailUrl,
            "https://www.pixiv.net/artworks/${preview.id}"
        )
        row.addView(image, LinearLayout.LayoutParams(dp(86), dp(86)))

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        info.addView(TextView(this).apply {
            text = preview.title.ifBlank { "Artwork ${preview.id}" }
            UiKit.title(this, 14.5f)
            maxLines = 2
        })
        info.addView(TextView(this).apply {
            text = "Pixiv user ${preview.userId} • #${preview.id}"
            UiKit.body(this, 11.5f)
            setPadding(0, dp(3), 0, 0)
        })
        info.addView(TextView(this).apply {
            text = if (downloaded) "Downloaded" else "Not downloaded"
            setTextColor(if (downloaded) UiKit.success else UiKit.muted)
            textSize = 11.5f
            setPadding(0, dp(4), 0, 0)
        })
        row.addView(
            info,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        card.addView(row)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        actions.addView(Button(this).apply {
            text = "Open"
            UiKit.styleSecondaryButton(this@FollowingActivity, this)
            setOnClickListener { openPixiv(preview.id) }
        }, LinearLayout.LayoutParams(0, dp(44), 1f))

        actions.addView(Button(this).apply {
            text = if (downloaded) "Downloaded" else "Download"
            if (downloaded) {
                UiKit.styleSecondaryButton(this@FollowingActivity, this)
                isEnabled = false
            } else {
                UiKit.stylePrimaryButton(this@FollowingActivity, this)
                setOnClickListener { startDirectDownload(preview) }
            }
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            leftMargin = dp(8)
        })
        card.addView(actions)

        return card
    }

    private fun startFeedSync() {
        if (!SessionStore.isLoggedIn(this)) {
            Toast.makeText(this, "Connect your Pixiv account first", Toast.LENGTH_LONG).show()
            return
        }
        if (SyncControl.snapshot().running) {
            Toast.makeText(
                this,
                "A sync or download is already running.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val intent = Intent(this, SyncForegroundService::class.java)
            .setAction(SyncForegroundService.ACTION_START)
            .putExtra(SyncForegroundService.EXTRA_MODE, SyncMode.LIVE.name)
            .putExtra(SyncForegroundService.EXTRA_SELECTED_ONLY, false)
            .putExtra(SyncForegroundService.EXTRA_FEED_ONLY, true)

        startForegroundService(intent)
        Toast.makeText(
            this,
            "Syncing new Following artworks",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun startDirectDownload(preview: PixivApi.ArtworkPreview) {
        if (!SessionStore.isLoggedIn(this)) {
            Toast.makeText(this, "Connect your Pixiv account first", Toast.LENGTH_LONG).show()
            return
        }
        if (SyncControl.snapshot().running) {
            Toast.makeText(
                this,
                "A sync or download is already running. Stop it first.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val intent = Intent(this, SyncForegroundService::class.java)
            .setAction(SyncForegroundService.ACTION_START)
            .putExtra(SyncForegroundService.EXTRA_MODE, SyncMode.DIRECT.name)
            .putExtra(SyncForegroundService.EXTRA_TARGET_ARTIST_ID, preview.userId)
            .putExtra(SyncForegroundService.EXTRA_TARGET_ARTWORK_ID, preview.id)

        startForegroundService(intent)
        Toast.makeText(this, "Downloading artwork ${preview.id}", Toast.LENGTH_SHORT).show()
    }

    private fun openPixiv(id: String) {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.pixiv.net/artworks/$id"))
            )
        }.onFailure {
            Toast.makeText(this, "Could not open Pixiv link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun card(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = UiKit.rounded(
                this@FollowingActivity,
                UiKit.surface,
                16,
                UiKit.line
            )
        }

    companion object {
        private const val MAX_ITEMS = 40
    }
}

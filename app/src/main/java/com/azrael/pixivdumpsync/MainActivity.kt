package com.azrael.pixivdumpsync

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var artistList: LinearLayout
    private lateinit var authStatus: TextView
    private lateinit var lastSyncStatus: TextView
    private lateinit var runStatus: TextView
    private lateinit var artistCount: TextView
    private lateinit var pauseButton: Button
    private lateinit var stopButton: Button
    private lateinit var checkNowButton: Button
    private lateinit var backfillButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private var enrichStarted = false

    private val refreshTask = object : Runnable {
        override fun run() {
            updateSyncControls()
            renderArtists()
            handler.postDelayed(this, 1500L)
        }
    }

    private fun dp(value: Int) = UiKit.dp(this, value)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = UiKit.bg
        window.navigationBarColor = UiKit.bg
        buildUi()
        requestNotificationPermissionIfNeeded()
        Scheduler.ensure(this)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        handler.removeCallbacks(refreshTask)
        handler.post(refreshTask)
        enrichMissingArtists()
    }

    override fun onPause() {
        handler.removeCallbacks(refreshTask)
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

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_pixiflow)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        header.addView(icon, LinearLayout.LayoutParams(dp(58), dp(58)))

        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        titles.addView(TextView(this).apply {
            text = "PixiFlow"
            UiKit.title(this, 27f)
        })
        titles.addView(TextView(this).apply {
            text = "Live archiving for Pixiv artists"
            UiKit.body(this, 13.5f)
            setPadding(0, dp(2), 0, 0)
        })
        header.addView(
            titles,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        root.addView(header)

        root.addView(section("ACCOUNT"), sectionParams())

        val accountCard = card()
        val accountRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val accountText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        accountText.addView(TextView(this).apply {
            text = "Pixiv account"
            UiKit.title(this, 17f)
        })
        authStatus = TextView(this).apply {
            UiKit.body(this, 13f)
            setPadding(0, dp(4), 0, 0)
        }
        accountText.addView(authStatus)
        accountRow.addView(
            accountText,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        accountRow.addView(Button(this).apply {
            text = "Account"
            UiKit.styleSecondaryButton(this@MainActivity, this)
            minHeight = dp(42)
            setPadding(dp(15), 0, dp(15), 0)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
            }
        })
        accountCard.addView(accountRow)
        root.addView(accountCard, cardParams())

        root.addView(section("SYNC CONTROL"), sectionParams())

        val syncCard = card()

        runStatus = TextView(this).apply {
            UiKit.title(this, 16f)
        }
        syncCard.addView(runStatus)

        lastSyncStatus = TextView(this).apply {
            UiKit.body(this, 12.5f)
            setPadding(0, dp(5), 0, 0)
        }
        syncCard.addView(lastSyncStatus)

        val mainActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        checkNowButton = Button(this).apply {
            text = "Check new now"
            UiKit.stylePrimaryButton(this@MainActivity, this)
            setOnClickListener { startSync(SyncMode.LIVE) }
        }
        mainActions.addView(
            checkNowButton,
            LinearLayout.LayoutParams(0, dp(54), 1f)
        )

        backfillButton = Button(this).apply {
            text = "Backfill selected"
            UiKit.styleSecondaryButton(this@MainActivity, this)
            setOnClickListener { startSync(SyncMode.BACKFILL) }
        }
        mainActions.addView(
            backfillButton,
            LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                leftMargin = dp(9)
            }
        )
        syncCard.addView(
            mainActions,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }
        )

        val transport = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        pauseButton = Button(this).apply {
            text = "Pause"
            UiKit.styleSecondaryButton(this@MainActivity, this)
            setOnClickListener { togglePause() }
        }
        transport.addView(
            pauseButton,
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        stopButton = Button(this).apply {
            text = "Stop"
            UiKit.styleDangerButton(this@MainActivity, this)
            setOnClickListener { requestStop() }
        }
        transport.addView(
            stopButton,
            LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                leftMargin = dp(9)
            }
        )
        syncCard.addView(
            transport,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(9) }
        )

        syncCard.addView(TextView(this).apply {
            text = "Live Sync checks automatically in the background for artists with Live enabled. Backfill is manual and downloads their older archive."
            UiKit.body(this, 12f)
            setPadding(0, dp(14), 0, 0)
        })

        root.addView(syncCard, cardParams())

        root.addView(section("ARTISTS"), sectionParams())

        val artistToolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        artistCount = TextView(this).apply {
            UiKit.body(this, 13f)
        }
        artistToolbar.addView(
            artistCount,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        artistToolbar.addView(Button(this).apply {
            text = "All"
            UiKit.styleSecondaryButton(this@MainActivity, this)
            minHeight = dp(40)
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener {
                AppDb(this@MainActivity).use { it.setAllSelected(true) }
                renderArtists()
            }
        })

        artistToolbar.addView(Button(this).apply {
            text = "None"
            UiKit.styleSecondaryButton(this@MainActivity, this)
            minHeight = dp(40)
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener {
                AppDb(this@MainActivity).use { it.setAllSelected(false) }
                renderArtists()
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(42)
        ).apply { leftMargin = dp(7) })

        artistToolbar.addView(Button(this).apply {
            text = "+ Add"
            UiKit.stylePrimaryButton(this@MainActivity, this)
            minHeight = dp(40)
            setPadding(dp(14), 0, dp(14), 0)
            setOnClickListener { showAddArtistsDialog() }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(42)
        ).apply { leftMargin = dp(7) })

        root.addView(artistToolbar)

        artistList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(
            artistList,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        )

        root.addView(TextView(this).apply {
            text = "Files are saved in Downloads/PixiFlow. Completed works are bookmarked on Pixiv only after every image page exists locally."
            UiKit.body(this, 11.5f)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(24), dp(8), 0)
        })

        setContentView(scroll)
    }

    private fun refresh() {
        authStatus.text = if (SessionStore.isLoggedIn(this)) {
            "Connected • verified session"
        } else {
            "Not connected"
        }
        authStatus.setTextColor(
            if (SessionStore.isLoggedIn(this)) UiKit.success else UiKit.danger
        )
        lastSyncStatus.text = "Last run • ${SessionStore.lastSyncSummary(this)}"
        updateSyncControls()
        renderArtists()
    }

    private fun updateSyncControls() {
        val s = SyncControl.snapshot()

        runStatus.text = when {
            s.stopping -> "Stopping safely…"
            s.paused -> "Paused • ${modeName(s.mode)}"
            s.running -> if (s.pendingLive) "${s.message} • Live check queued" else s.message
            else -> "No sync running"
        }

        runStatus.setTextColor(
            when {
                s.stopping -> UiKit.danger
                s.paused -> Color.rgb(255, 184, 76)
                s.running -> UiKit.success
                else -> UiKit.text
            }
        )

        pauseButton.text = if (s.paused) "Resume" else "Pause"
        pauseButton.isEnabled = s.running && !s.stopping
        stopButton.isEnabled = s.running && !s.stopping
        checkNowButton.isEnabled = !s.running
        backfillButton.isEnabled = !s.running
    }

    private fun renderArtists() {
        if (!::artistList.isInitialized) return
        artistList.removeAllViews()

        AppDb(this).use { db ->
            val artists = db.listArtists()
            val selected = artists.count { it.selected }
            artistCount.text = "${artists.size} artists • $selected selected"

            if (artists.isEmpty()) {
                val empty = card()
                empty.gravity = Gravity.CENTER
                empty.addView(TextView(this).apply {
                    text = "No artists yet"
                    UiKit.title(this, 16f)
                    gravity = Gravity.CENTER
                })
                empty.addView(TextView(this).apply {
                    text = "Add a Pixiv user ID. PixiFlow will resolve the artist name and begin watching for future works."
                    UiKit.body(this, 12.5f)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(6), 0, 0)
                })
                artistList.addView(empty)
                return
            }

            for ((index, artist) in artists.withIndex()) {
                val progress = db.artistProgress(artist.userId, artist.knownTotal)
                artistList.addView(
                    artistCard(db, artist, progress),
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

    private fun artistCard(
        db: AppDb,
        artist: ArtistRecord,
        progress: ArtistProgress
    ): View {
        val card = card()

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val selectedBox = CheckBox(this).apply {
            isChecked = artist.selected
            buttonTintList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()
                ),
                intArrayOf(UiKit.accent, UiKit.muted)
            )
            setOnCheckedChangeListener { _, checked ->
                db.setArtistSelected(artist.userId, checked)
                updateArtistCountOnly()
            }
        }
        top.addView(selectedBox)

        val avatar = TextView(this).apply {
            text = (artist.label?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "P")
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            background = UiKit.rounded(this@MainActivity, UiKit.surfaceAlt, 14)
        }
        top.addView(
            avatar,
            LinearLayout.LayoutParams(dp(46), dp(46)).apply {
                leftMargin = dp(3)
            }
        )

        val identity = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        identity.addView(TextView(this).apply {
            text = artist.label ?: "Resolving artist…"
            UiKit.title(this, 15.5f)
            maxLines = 1
        })
        identity.addView(TextView(this).apply {
            text = "Pixiv ID ${artist.userId}"
            UiKit.body(this, 12f)
            setPadding(0, dp(3), 0, 0)
        })
        top.addView(
            identity,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val liveSwitch = Switch(this).apply {
            isChecked = artist.liveEnabled
            showText = false
            thumbTintList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()
                ),
                intArrayOf(UiKit.accent, UiKit.muted)
            )
            setOnCheckedChangeListener { _, checked ->
                AppDb(this@MainActivity).use {
                    it.setArtistLiveEnabled(artist.userId, checked)
                }
            }
        }
        top.addView(liveSwitch)
        card.addView(top)

        val status = TextView(this).apply {
            text = artistStatusText(artist, progress)
            UiKit.body(this, 12.5f)
            setTextColor(
                when {
                    !artist.lastError.isNullOrBlank() -> UiKit.danger
                    artist.archiveStatus == "COMPLETE" -> UiKit.success
                    else -> UiKit.muted
                }
            )
            setPadding(dp(38), dp(10), 0, 0)
        }
        card.addView(status)

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(38), dp(10), 0, 0)
        }

        bottom.addView(TextView(this).apply {
            text = if (artist.liveEnabled) "LIVE ON" else "LIVE OFF"
            setTextColor(if (artist.liveEnabled) UiKit.success else UiKit.muted)
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        bottom.addView(Button(this).apply {
            text = "Remove"
            UiKit.styleDangerButton(this@MainActivity, this)
            minHeight = dp(38)
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Remove artist?")
                    .setMessage("Downloaded files and Pixiv bookmarks will not be deleted.")
                    .setPositiveButton("Remove") { _, _ ->
                        AppDb(this@MainActivity).use { it.removeArtist(artist.userId) }
                        renderArtists()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        })
        card.addView(bottom)

        return card
    }

    private fun artistStatusText(
        artist: ArtistRecord,
        progress: ArtistProgress
    ): String {
        if (!artist.lastError.isNullOrBlank()) {
            return "Error • ${artist.lastError}"
        }

        val archive = when (artist.archiveStatus) {
            "COMPLETE" -> "Archive synced"
            "RUNNING" -> "Backfill running"
            "PARTIAL" -> "Archive partial"
            "ERROR" -> "Archive error"
            else -> "Archive not started"
        }

        val counts = if (artist.knownTotal > 0) {
            " • ${progress.done}/${artist.knownTotal} saved • ${progress.pending} pending"
        } else {
            ""
        }

        val live = if (artist.liveCursor.isNullOrBlank()) {
            " • Live baseline pending"
        } else {
            " • Live ready"
        }

        return archive + counts + live
    }

    private fun updateArtistCountOnly() {
        AppDb(this).use { db ->
            val artists = db.listArtists()
            artistCount.text = "${artists.size} artists • ${artists.count { it.selected }} selected"
        }
    }

    private fun showAddArtistsDialog() {
        val input = EditText(this).apply {
            hint = "Pixiv user ID or profile URL"
            setHintTextColor(UiKit.muted)
            setTextColor(UiKit.text)
            gravity = Gravity.TOP
            minLines = 4
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = UiKit.rounded(
                this@MainActivity,
                UiKit.surfaceAlt,
                12,
                UiKit.line
            )
        }

        AlertDialog.Builder(this)
            .setTitle("Add artists")
            .setMessage(
                "Paste one or several Pixiv IDs/profile URLs. New artists start with Live enabled; older works are downloaded only when you run Backfill."
            )
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val ids = InputParser.parseUserIds(input.text.toString())
                if (ids.isEmpty()) {
                    Toast.makeText(this, "No Pixiv user IDs found", Toast.LENGTH_LONG).show()
                } else {
                    addArtists(ids)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addArtists(ids: List<String>) {
        Toast.makeText(this, "Looking up ${ids.size} artist(s)…", Toast.LENGTH_SHORT).show()

        Thread {
            var resolved = 0
            AppDb(applicationContext).use { db ->
                val api = if (SessionStore.isLoggedIn(applicationContext)) {
                    runCatching { PixivApi(applicationContext) }.getOrNull()
                } else {
                    null
                }

                for (id in ids) {
                    val profile = api?.let { runCatching { it.userProfile(id) }.getOrNull() }
                    val works = api?.let { runCatching { it.userArtworkIds(id) }.getOrNull() }
                    db.addArtist(
                        userId = id,
                        label = profile?.name,
                        liveCursor = works?.firstOrNull()
                    )
                    if (profile != null) resolved++
                }
            }

            runOnUiThread {
                renderArtists()
                Toast.makeText(
                    this,
                    "Added ${ids.size} artist(s) • $resolved names resolved",
                    Toast.LENGTH_LONG
                ).show()
            }
        }.start()
    }

    private fun enrichMissingArtists() {
        if (enrichStarted || !SessionStore.isLoggedIn(this)) return
        enrichStarted = true

        Thread {
            runCatching {
                AppDb(applicationContext).use { db ->
                    val api = PixivApi(applicationContext)
                    for (artist in db.listArtists()) {
                        if (artist.label.isNullOrBlank() || artist.liveCursor.isNullOrBlank()) {
                            val profile = runCatching { api.userProfile(artist.userId) }.getOrNull()
                            val works = if (artist.liveCursor.isNullOrBlank()) {
                                runCatching { api.userArtworkIds(artist.userId) }.getOrNull()
                            } else {
                                null
                            }
                            db.updateArtistIdentity(
                                artist.userId,
                                profile?.name,
                                works?.firstOrNull()
                            )
                        }
                    }
                }
            }
            runOnUiThread { renderArtists() }
        }.start()
    }

    private fun startSync(mode: SyncMode) {
        if (!SessionStore.isLoggedIn(this)) {
            Toast.makeText(this, "Connect your Pixiv account first", Toast.LENGTH_LONG).show()
            return
        }

        AppDb(this).use { db ->
            val eligible = if (mode == SyncMode.LIVE) {
                db.listSelectedLiveArtists()
            } else {
                db.listSelectedArtists()
            }
            if (eligible.isEmpty()) {
                Toast.makeText(
                    this,
                    if (mode == SyncMode.LIVE) {
                        "Select at least one artist with Live enabled"
                    } else {
                        "Select at least one artist"
                    },
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        val intent = Intent(this, SyncForegroundService::class.java)
            .setAction(SyncForegroundService.ACTION_START)
            .putExtra(SyncForegroundService.EXTRA_MODE, mode.name)
            .putExtra(SyncForegroundService.EXTRA_SELECTED_ONLY, true)

        startForegroundService(intent)
        Toast.makeText(
            this,
            if (mode == SyncMode.LIVE) "Checking selected artists for new works" else "Backfill started",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun togglePause() {
        val s = SyncControl.snapshot()
        if (!s.running) return

        val action = if (s.paused) {
            SyncForegroundService.ACTION_RESUME
        } else {
            SyncForegroundService.ACTION_PAUSE
        }
        startService(Intent(this, SyncForegroundService::class.java).setAction(action))
        updateSyncControls()
    }

    private fun requestStop() {
        if (!SyncControl.snapshot().running) return
        startService(
            Intent(this, SyncForegroundService::class.java)
                .setAction(SyncForegroundService.ACTION_STOP)
        )
        updateSyncControls()
    }

    private fun modeName(mode: SyncMode?): String =
        when (mode) {
            SyncMode.LIVE -> "Live Sync"
            SyncMode.BACKFILL -> "Archive Backfill"
            null -> ""
        }

    private fun card(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = UiKit.rounded(
                this@MainActivity,
                UiKit.surface,
                17,
                UiKit.line
            )
        }

    private fun section(text: String) = TextView(this).apply {
        this.text = text
        UiKit.sectionLabel(this)
    }

    private fun sectionParams() =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(24) }

    private fun cardParams() =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(9) }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                10
            )
        }
    }
}

package com.azrael.pixivdumpsync

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var artistList: LinearLayout
    private lateinit var authStatus: TextView
    private lateinit var syncStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestNotificationPermissionIfNeeded()
        Scheduler.ensure(this)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "PixivDump Sync"
            textSize = 26f
        })
        root.addView(TextView(this).apply {
            text = "Everything goes to Pictures/PixivDump. A work is marked as downloaded only after every page is saved, then the app sends the Pixiv Like."
            setPadding(0, 8, 0, 20)
        })

        authStatus = TextView(this)
        root.addView(authStatus)

        root.addView(Button(this).apply {
            text = "Log in to Pixiv"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
            }
        })

        root.addView(Button(this).apply {
            text = "Add Pixiv users"
            setOnClickListener { showAddArtistsDialog() }
        })

        root.addView(Button(this).apply {
            text = "Sync now (backfill + new works)"
            setOnClickListener { startManualSync() }
        })

        root.addView(CheckBox(this).apply {
            text = "Automatic sync (about every 15 min; Android may defer it)"
            isChecked = SessionStore.autoSync(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                Scheduler.setEnabled(this@MainActivity, checked)
            }
        })

        syncStatus = TextView(this).apply { setPadding(0, 14, 0, 14) }
        root.addView(syncStatus)

        root.addView(TextView(this).apply {
            text = "Selected users"
            textSize = 20f
            setPadding(0, 12, 0, 8)
        })

        artistList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(artistList)
        setContentView(scroll)
    }

    private fun refresh() {
        authStatus.text = if (SessionStore.isLoggedIn(this)) {
            "Pixiv login: ready"
        } else {
            "Pixiv login: not connected"
        }

        syncStatus.text = "Last sync: ${SessionStore.lastSyncSummary(this)}"
        renderArtists()
    }

    private fun renderArtists() {
        artistList.removeAllViews()
        AppDb(this).use { db ->
            val artists = db.listArtists()

            if (artists.isEmpty()) {
                artistList.addView(TextView(this).apply {
                    text = "No users selected yet."
                })
                return
            }

            for (artist in artists) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                row.addView(
                    TextView(this).apply {
                        text = artist.label ?: "Pixiv user ${artist.userId}"
                    },
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                )

                row.addView(Button(this).apply {
                    text = "Remove"
                    setOnClickListener {
                        AppDb(this@MainActivity).use {
                            it.removeArtist(artist.userId)
                        }
                        renderArtists()
                    }
                })
                artistList.addView(row)
            }
        }
    }

    private fun showAddArtistsDialog() {
        val input = EditText(this).apply {
            hint = "One or many Pixiv user IDs / profile URLs"
            gravity = Gravity.TOP
            minLines = 6
        }

        AlertDialog.Builder(this)
            .setTitle("Add Pixiv users")
            .setMessage(
                "Paste IDs or profile URLs separated by spaces, commas, or new lines."
            )
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val ids = InputParser.parseUserIds(input.text.toString())
                AppDb(this).use { db ->
                    ids.forEach { db.addArtist(it) }
                }
                Toast.makeText(
                    this,
                    "Added ${ids.size} user(s)",
                    Toast.LENGTH_SHORT
                ).show()
                renderArtists()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startManualSync() {
        if (!SessionStore.isLoggedIn(this)) {
            Toast.makeText(
                this,
                "Log in to Pixiv first",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        AppDb(this).use { db ->
            if (db.listArtists().isEmpty()) {
                Toast.makeText(
                    this,
                    "Add at least one Pixiv user first",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        startForegroundService(
            Intent(this, SyncForegroundService::class.java)
        )

        Toast.makeText(
            this,
            "Sync started. Progress is shown in the notification.",
            Toast.LENGTH_LONG
        ).show()
    }

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

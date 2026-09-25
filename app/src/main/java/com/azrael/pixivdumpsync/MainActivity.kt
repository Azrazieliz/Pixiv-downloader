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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var artistList: LinearLayout
    private lateinit var authStatus: TextView
    private lateinit var syncStatus: TextView
    private lateinit var artistCount: TextView

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
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(UiKit.bg)
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(40))
        }
        scroll.addView(root)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logo = TextView(this).apply {
            text = "P"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            background = UiKit.rounded(this@MainActivity, UiKit.accent, 16)
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(54), dp(54)))

        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        titleBlock.addView(TextView(this).apply {
            text = "PixivDump Sync"
            UiKit.title(this, 26f)
        })
        titleBlock.addView(TextView(this).apply {
            text = "Automatic artwork backup"
            UiKit.body(this, 14f)
            setPadding(0, dp(2), 0, 0)
        })
        header.addView(
            titleBlock,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        root.addView(header)

        root.addView(TextView(this).apply {
            text = "ACCOUNT"
            UiKit.sectionLabel(this)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(28) })

        val accountCard = card()
        val accountTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val accountText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        accountText.addView(TextView(this).apply {
            text = "Pixiv"
            UiKit.title(this, 18f)
        })
        authStatus = TextView(this).apply {
            UiKit.body(this, 13f)
            setPadding(0, dp(4), 0, 0)
        }
        accountText.addView(authStatus)
        accountTop.addView(
            accountText,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val loginButton = Button(this).apply {
            text = "Account"
            UiKit.styleSecondaryButton(this@MainActivity, this)
            minHeight = dp(42)
            setPadding(dp(16), 0, dp(16), 0)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
            }
        }
        accountTop.addView(
            loginButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(44)
            )
        )
        accountCard.addView(accountTop)
        root.addView(accountCard, matchCardParams())

        val syncButton = Button(this).apply {
            text = "Sync now"
            UiKit.stylePrimaryButton(this@MainActivity, this)
            setOnClickListener { startManualSync() }
        }
        root.addView(syncButton, fullButtonParams(dp(18)))

        val addButton = Button(this).apply {
            text = "Add Pixiv users"
            UiKit.styleSecondaryButton(this@MainActivity, this)
            setOnClickListener { showAddArtistsDialog() }
        }
        root.addView(addButton, fullButtonParams(dp(10)))

        root.addView(TextView(this).apply {
            text = "AUTOMATION"
            UiKit.sectionLabel(this)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(28) })

        val automationCard = card()
        val automationRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val autoText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        autoText.addView(TextView(this).apply {
            text = "Automatic sync"
            UiKit.title(this, 16f)
        })
        autoText.addView(TextView(this).apply {
            text = "Checks periodically when Android allows it"
            UiKit.body(this, 12.5f)
            setPadding(0, dp(3), 0, 0)
        })
        automationRow.addView(
            autoText,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val autoSwitch = Switch(this).apply {
            isChecked = SessionStore.autoSync(this@MainActivity)
            showText = false
            thumbTintList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()
                ),
                intArrayOf(UiKit.accent, UiKit.muted)
            )
            trackTintList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()
                ),
                intArrayOf(
                    Color.rgb(0, 76, 126),
                    Color.rgb(57, 64, 74)
                )
            )
            setOnCheckedChangeListener { _, checked ->
                Scheduler.setEnabled(this@MainActivity, checked)
            }
        }
        automationRow.addView(autoSwitch)
        automationCard.addView(automationRow)

        val divider = View(this).apply {
            setBackgroundColor(UiKit.line)
        }
        automationCard.addView(
            divider,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                topMargin = dp(16)
                bottomMargin = dp(14)
            }
        )

        syncStatus = TextView(this).apply {
            UiKit.body(this, 13f)
        }
        automationCard.addView(syncStatus)
        root.addView(automationCard, matchCardParams())

        val usersHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        usersHeader.addView(TextView(this).apply {
            text = "SELECTED USERS"
            UiKit.sectionLabel(this)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        artistCount = TextView(this).apply {
            UiKit.body(this, 12f)
        }
        usersHeader.addView(artistCount)
        root.addView(usersHeader, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(28) })

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
            text = "Downloads are stored in Pictures/PixivDump. Works are bookmarked only after all image pages are saved."
            UiKit.body(this, 12f)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(24), dp(8), 0)
        })

        setContentView(scroll)
    }

    private fun card(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(17), dp(18), dp(17))
            background = UiKit.rounded(
                this@MainActivity,
                UiKit.surface,
                18,
                UiKit.line
            )
        }

    private fun matchCardParams() =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) }

    private fun fullButtonParams(top: Int) =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(56)
        ).apply { topMargin = top }

    private fun refresh() {
        if (SessionStore.isLoggedIn(this)) {
            authStatus.text = "Connected • session verified"
            authStatus.setTextColor(UiKit.success)
        } else {
            authStatus.text = "Not connected"
            authStatus.setTextColor(UiKit.danger)
        }

        syncStatus.text = "Last sync  •  ${SessionStore.lastSyncSummary(this)}"
        renderArtists()
    }

    private fun renderArtists() {
        artistList.removeAllViews()

        AppDb(this).use { db ->
            val artists = db.listArtists()
            artistCount.text = "${artists.size}"

            if (artists.isEmpty()) {
                val empty = card()
                empty.gravity = Gravity.CENTER
                empty.addView(TextView(this).apply {
                    text = "No users added yet"
                    UiKit.title(this, 16f)
                    gravity = Gravity.CENTER
                })
                empty.addView(TextView(this).apply {
                    text = "Add Pixiv profiles to start your download list."
                    UiKit.body(this, 13f)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(5), 0, 0)
                })
                artistList.addView(empty)
                return
            }

            for ((index, artist) in artists.withIndex()) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(13), dp(12), dp(13))
                    background = UiKit.rounded(
                        this@MainActivity,
                        UiKit.surface,
                        16,
                        UiKit.line
                    )
                }

                val avatar = TextView(this).apply {
                    text = "P"
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    background = UiKit.rounded(
                        this@MainActivity,
                        UiKit.surfaceAlt,
                        13
                    )
                }
                row.addView(avatar, LinearLayout.LayoutParams(dp(42), dp(42)))

                val labels = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, dp(8), 0)
                }
                labels.addView(TextView(this).apply {
                    text = artist.label ?: "Pixiv user"
                    UiKit.title(this, 15f)
                })
                labels.addView(TextView(this).apply {
                    text = "ID ${artist.userId}"
                    UiKit.body(this, 12.5f)
                    setPadding(0, dp(3), 0, 0)
                })
                row.addView(
                    labels,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )

                row.addView(Button(this).apply {
                    text = "Remove"
                    UiKit.styleDangerButton(this@MainActivity, this)
                    setPadding(dp(12), 0, dp(12), 0)
                    setOnClickListener {
                        AppDb(this@MainActivity).use {
                            it.removeArtist(artist.userId)
                        }
                        renderArtists()
                    }
                })

                artistList.addView(
                    row,
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

    private fun showAddArtistsDialog() {
        val input = EditText(this).apply {
            hint = "Pixiv user IDs or profile URLs"
            setHintTextColor(UiKit.muted)
            setTextColor(UiKit.text)
            setBackgroundColor(UiKit.surfaceAlt)
            gravity = Gravity.TOP
            minLines = 5
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        AlertDialog.Builder(
            this,
            android.R.style.Theme_Material_Dialog_Alert
        )
            .setTitle("Add Pixiv users")
            .setMessage("Paste one or many IDs/profile URLs. Separate them with spaces, commas, or new lines.")
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
            "Sync started",
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

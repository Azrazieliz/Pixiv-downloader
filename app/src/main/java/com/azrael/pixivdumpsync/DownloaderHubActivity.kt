package com.azrael.pixivdumpsync

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.azrael.galleryflow.GalleryDb
import java.net.URI

/** Asterion Downloader home: enter Kuroha, Kyora or VideoFlow in one app. */
class DownloaderHubActivity : Activity() {
    private val design get() = NocturneDesign
    private lateinit var savedSites: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = design.background
        window.navigationBarColor = design.background

        val screen = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(design.background)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(31), dp(20), dp(39))
        }
        screen.addView(root)
        val masthead = design.row(this)
        masthead.addView(design.brandMark(this, 52),
            LinearLayout.LayoutParams(dp(52), dp(52)))
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        brand.addView(design.text(this, "ASTERION", 18f, design.white, true))
        brand.addView(design.text(this, "DOWNLOADER  /  MEDIA ARCHIVE", 10f, design.secondary, true)
            .apply { letterSpacing = .1f })
        masthead.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(masthead)

        val hero = design.card(this, 22).apply {
            background = design.shape(this@DownloaderHubActivity, design.soft, 24)
            addView(design.eyebrow(this@DownloaderHubActivity, "ONE APPLICATION  /  THREE WORKSPACES"))
            addView(design.text(this@DownloaderHubActivity,
                "Your downloads.\nOne place.", 29f, design.white, true),
                design.lp(this@DownloaderHubActivity, 11))
            addView(design.text(this@DownloaderHubActivity,
                 "Pixiv artwork, cosplay galleries and videos — managed in Asterion Downloader.",
                13f, design.secondary), design.lp(this@DownloaderHubActivity, 11, 18))
            addView(design.action(this@DownloaderHubActivity, "Add a website to Kyora", true) {
                openGallery(add = true)
            })
        }
        root.addView(hero, design.lp(this, 27))

        root.addView(design.eyebrow(this, "YOUR WORKSPACES"), design.lp(this, 27, 12))
        root.addView(workspace("01", "KUROHA  /  PIXIV",
            "Artists, following feed and artwork downloads",
            design.violet) {
            startActivity(Intent(this, MainActivity::class.java))
        })
        root.addView(workspace("02", "KYORA  /  COSPLAY",
            "Cosplay creators, albums and flat photo archive",
            design.primary) { openGallery() }, design.lp(this, 11))

        root.addView(workspace("03", "VIDEOFLOW  /  VIDEO",
            "Video links, downloads, source watches and saved videos",
            design.violet) {
            startActivity(Intent(this, com.asterion.videoflow.MainActivity::class.java))
        }, design.lp(this, 11))

        val title = design.row(this)
        title.addView(design.text(this, "Kyora websites", 19f, design.white, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        title.addView(design.chip(this, "+ Add") { openGallery(add = true) })
        root.addView(title, design.lp(this, 32, 13))
        savedSites = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(savedSites)
        root.addView(design.text(this,
             "Kyora websites are managed independently from Kuroha and VideoFlow.",
            12f, design.subdued), design.lp(this, 14))
        setContentView(screen)
    }

    private fun workspace(number: String, name: String, summary: String,
                          tint: Int, onClick: () -> Unit): LinearLayout =
        design.row(this).apply {
            setPadding(dp(18), dp(23), dp(18), dp(23))
            background = design.touch(this@DownloaderHubActivity, design.surface, 22)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            val badge = design.text(this@DownloaderHubActivity, number, 16f,
                tint, true).apply {
                gravity = Gravity.CENTER
                background = design.shape(this@DownloaderHubActivity, design.soft, 15,
                    design.soft)
            }
            addView(badge, LinearLayout.LayoutParams(dp(51), dp(56)))
            val info = LinearLayout(this@DownloaderHubActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(15), 0, dp(7), 0)
            }
            info.addView(design.text(this@DownloaderHubActivity, name, 20f,
                design.white, true))
            info.addView(design.text(this@DownloaderHubActivity, summary, 12f,
                design.secondary), design.lp(this@DownloaderHubActivity, 6))
            addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            addView(design.text(this@DownloaderHubActivity, "›", 28f, tint, true))
        }

    override fun onResume() {
        super.onResume()
        if (!::savedSites.isInitialized) return
        savedSites.removeAllViews()
        val sites = runCatching { GalleryDb(this).use { it.listEntities() } }
            .getOrDefault(emptyList())
        if (sites.isEmpty()) {
            savedSites.addView(design.card(this).apply {
                addView(design.text(this@DownloaderHubActivity,
                    "No additional sources yet", 15f, design.white, true))
                addView(design.text(this@DownloaderHubActivity,
                     "Add a cosplay source or paste a gallery URL when you need it.",
                    12f, design.secondary), design.lp(this@DownloaderHubActivity, 8))
                addView(design.action(this@DownloaderHubActivity, "Add a website") {
                    openGallery(add = true)
                }, design.lp(this@DownloaderHubActivity, 12))
            })
            return
        }
        sites.take(12).forEach { site ->
            val domain = runCatching {
                URI(site.canonicalUrl).host.orEmpty().removePrefix("www.")
            }.getOrDefault(site.source.wireName)
            val row = design.row(this).apply {
                setPadding(dp(14), dp(15), dp(14), dp(15))
                background = design.touch(this@DownloaderHubActivity, design.surface, 17)
                isClickable = true
                isFocusable = true
                setOnClickListener { openGallery(siteId = site.entityId) }
            }
            val monogram = design.text(this, domain.take(1).uppercase(), 18f,
                design.primary, true).apply {
                gravity = Gravity.CENTER
                background = design.shape(this@DownloaderHubActivity, design.soft, 12)
            }
            row.addView(monogram, LinearLayout.LayoutParams(dp(43), dp(43)))
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
            }
            info.addView(design.text(this, site.displayName, 15f, design.white, true))
            info.addView(design.text(this,
                if (site.liveEnabled) domain + " · Live" else domain,
                12f, design.secondary), design.lp(this, 3))
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            savedSites.addView(row, design.lp(this, 0, 9))
        }
    }

    private fun openGallery(add: Boolean = false, siteId: String? = null) {
        val target = Intent(this, com.azrael.galleryflow.MainActivity::class.java)
            .putExtra(com.azrael.galleryflow.MainActivity.EXTRA_ADD_SITE, add)
        if (siteId != null)
            target.putExtra(com.azrael.galleryflow.MainActivity.EXTRA_SITE_ID, siteId)
        startActivity(target)
    }

    private fun dp(value: Int): Int = design.px(this, value)
}

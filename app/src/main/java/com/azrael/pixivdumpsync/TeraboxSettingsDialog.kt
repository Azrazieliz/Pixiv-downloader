package com.azrael.pixivdumpsync

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.Gravity
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Settings live on-device. No TeraBox password or session cookie is requested. */
object TeraboxSettingsDialog {
    fun show(activity: Activity) {
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 12)
        }
        val enabled = CheckBox(activity).apply {
            text = "Enable cloud upload after download"
            isChecked = TeraboxSettings.enabled(activity)
        }
        panel.addView(enabled)
        panel.addView(TextView(activity).apply {
            text = "Authorized Open Platform access token"
            setPadding(0, 16, 0, 0)
        })
        val token = EditText(activity).apply {
            hint = if (TeraboxSettings.accessToken(activity) == null)
                "Access token" else "Stored securely; enter a replacement"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        panel.addView(token)
        panel.addView(TextView(activity).apply {
            text = "App-assigned TeraBox directory"
            setPadding(0, 16, 0, 0)
        })
        val directory = EditText(activity).apply {
            hint = "/From: Other Applications/YourApp-..."
            setText(TeraboxSettings.remoteDirectory(activity))
            isSingleLine = true
        }
        panel.addView(directory)
        panel.addView(TextView(activity).apply {
            text = "Requires approved TeraBox Open Platform access. Tokens expire; automatic OAuth authorization and refresh require partner credentials. Upload retries keep local originals untouched."
            setPadding(0, 16, 0, 0)
        })
        val scroll = ScrollView(activity).apply { addView(panel) }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("TeraBox settings")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Disconnect") { _, _ ->
                TeraboxSettings.clearAccessToken(activity)
                Toast.makeText(activity, "TeraBox disconnected", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val folder = directory.text.toString().trim()
                    if (folder.isNotBlank()) TeraboxSettings.setRemoteDirectory(activity, folder)
                    val freshToken = token.text.toString().trim()
                    if (freshToken.isNotBlank()) TeraboxSettings.saveAccessToken(activity, freshToken)
                    if (enabled.isChecked && !TeraboxSettings.isConfigured(activity)) {
                        throw IllegalArgumentException("Access token and app folder required")
                    }
                    TeraboxSettings.setEnabled(activity, enabled.isChecked)
                    dialog.dismiss()
                    Toast.makeText(activity, "Cloud settings saved", Toast.LENGTH_SHORT).show()
                } catch (error: Exception) {
                    Toast.makeText(activity, error.message ?: "Invalid settings", Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }
}

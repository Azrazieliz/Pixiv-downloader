package com.azrael.pixivdumpsync

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** One phone-first TeraBox NDUS setup, shared across Pixiv and cosplay. */
object NduSettingsDialog {
    fun show(activity:Activity) {
        val ui=NocturneDesign
        val body=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(ui.px(activity,22),ui.px(activity,10),
                ui.px(activity,22),ui.px(activity,14))
        }
        fun title(t:String) { body.addView(ui.text(activity,t,14f,ui.white,true),
            ui.lp(activity,15,6)) }
        fun explanation(t:String) { body.addView(ui.text(activity,t,12f,ui.secondary),
            ui.lp(activity,5)) }
        val enable=CheckBox(activity).apply {
            text="Enable direct TeraBox uploads after downloading"
            isChecked=NduSettings.enabled(activity)
            setTextColor(ui.white)
        }
        body.addView(enable)
        title("Your NDUS cookie")
        val cookie=EditText(activity).apply {
            hint=if(NduSettings.cookie(activity)!=null)
                "Encrypted NDUS saved / enter a replacement" else "Paste ndus value"
            setSingleLine(true)
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(ui.white);setHintTextColor(ui.subdued)
        }
        body.addView(cookie)
        explanation("Keep the cookie private. It is encrypted on this phone and used only for direct requests to TeraBox.")
        title("Cloud folder for Kuroha / Pixiv")
        val kuroha=EditText(activity).apply {
            setSingleLine(true);setText(NduSettings.destination(activity,"kuroha"))
            setTextColor(ui.white)
        }
        body.addView(kuroha)
        title("Cloud folder for Kyora / Cosplay")
        val kyora=EditText(activity).apply {
            setSingleLine(true);setText(NduSettings.destination(activity,"kyora"))
            setTextColor(ui.white)
        }
        body.addView(kyora)
        title("TeraBox domain")
        val alt=CheckBox(activity).apply {
            text="Use nephobox.com instead of terabox.com (regional fallback)"
            isChecked=NduSettings.domain(activity)=="nephobox.com"
            setTextColor(ui.secondary)
        }
        body.addView(alt)
        explanation("Uploads are an unofficial web integration. Verify results in TeraBox. Original files are never deleted.")
        val state=TextView(activity).apply {
            val status=NduCloudQueue(activity).use { it.status() }
            text="Queue: "+status.pending+" waiting, "+status.completed+" uploaded."+
                (status.lastError?.let { " Last failure: $it" } ?: "")
            setTextColor(ui.primary);textSize=12f
        }
        body.addView(state,ui.lp(activity,15))
        body.addView(ui.action(activity,"Test TeraBox login") {
            val typed=cookie.text.toString().trim()
            val saved=NduSettings.cookie(activity)
            val ndus=runCatching { if(typed.isBlank()) saved else NduSettings.parseNdus(typed) }
                .getOrNull()
            if(ndus==null) {
                Toast.makeText(activity,"Enter your NDUS cookie first",Toast.LENGTH_LONG).show()
            } else {
                state.text="Checking TeraBox session..."
                Thread {
                    val outcome=runCatching {
                        val host=if(alt.isChecked) "nephobox.com" else "www.terabox.com"
                        NduTeraboxClient(ndus,host).connect()
                    }
                    activity.runOnUiThread {
                        state.text=if(outcome.isSuccess)
                            "TeraBox login validated" else
                            "TeraBox authentication failed: " +
                                (outcome.exceptionOrNull()?.message ?: "unknown")
                    }
                }.start()
            }
        },ui.lp(activity,12))
        val dialog=AlertDialog.Builder(activity)
            .setTitle("Direct TeraBox / NDUS")
            .setView(android.widget.ScrollView(activity).apply { addView(body) })
            .setNeutralButton("Disconnect",null)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Save",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val newCookie=cookie.text.toString().trim()
                    if(newCookie.isNotBlank()) NduSettings.save(activity,newCookie)
                    NduSettings.setDestination(activity,"kuroha",kuroha.text.toString())
                    NduSettings.setDestination(activity,"kyora",kyora.text.toString())
                    NduSettings.setDomain(activity,if(alt.isChecked) "nephobox.com"
                        else "www.terabox.com")
                    if(enable.isChecked && NduSettings.cookie(activity)==null)
                        throw IllegalArgumentException("Enter your NDUS cookie first")
                    NduSettings.setEnabled(activity,enable.isChecked)
                    Toast.makeText(activity,"Direct TeraBox settings saved",Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } catch(t:Exception) {
                    Toast.makeText(activity,t.message ?: "Invalid TeraBox settings",Toast.LENGTH_LONG).show()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                AlertDialog.Builder(activity)
                    .setTitle("Disconnect TeraBox?")
                    .setMessage("Erase the encrypted NDUS cookie? Downloaded images and queue records are preserved.")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Disconnect") { _,_ ->
                        NduSettings.clear(activity);dialog.dismiss()
                    }.show()
            }
        }
        dialog.show()
    }

    fun start(activity:Activity,allExisting:Boolean=false) {
        if(!NduSettings.configured(activity)) {
            Toast.makeText(activity,"Enable TeraBox and save NDUS settings first",
                Toast.LENGTH_LONG).show()
            show(activity)
            return
        }
        runCatching { NduUploadService.launch(activity,allExisting) }
            .onSuccess {
                Toast.makeText(activity,"Direct TeraBox upload requested",
                    Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(activity,it.message ?: "Upload could not start",
                    Toast.LENGTH_LONG).show()
            }
    }
}

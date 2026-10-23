package com.azrael.galleryflow

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import java.io.InputStreamReader

/** Safely imports only watched sites from a former Kyora JSON catalog.
 * MediaStore file ownership is not portable between Android app IDs. */
class LegacySourceImporter(private val context: Context) {
    fun importSites(uri: Uri): Int {
        val sources = mutableListOf<SourceEntity>()
        data class SourceState(val source: SourceEntity, val selected: Boolean, val live: Boolean)
        val states = mutableListOf<SourceState>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() != "entities") {
                        reader.skipValue()
                        continue
                    }
                    reader.beginArray()
                    while (reader.hasNext()) {
                        var source = ""; var id = ""; var name = ""; var url = ""
                        var kind = "site"; var selected = true; var live = true
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "source" -> source = readString(reader)
                                "entity_id" -> id = readString(reader)
                                "display_name" -> name = readString(reader)
                                "canonical_url" -> url = readString(reader)
                                "kind" -> kind = readString(reader)
                                "selected" -> selected = readBoolean(reader, true)
                                "live_enabled" -> live = readBoolean(reader, true)
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        val sourceId = SourceId.entries.firstOrNull { it.wireName == source }
                        if (sourceId != null && AdapterRegistry.available(sourceId) &&
                            id.isNotBlank() && url.startsWith("http") &&
                            states.size < 20000) {
                            states.add(SourceState(SourceEntity(sourceId, id,
                                name.ifBlank { url }, url, kind), selected, live))
                        }
                    }
                    reader.endArray()
                }
                reader.endObject()
            }
        } ?: throw IllegalArgumentException("Cannot open Kyora catalog JSON")
        GalleryDb(context).use { db ->
            for (state in states) {
                db.addEntity(state.source)
                db.setSelected(state.source.source, state.source.stableId, state.selected)
                db.setLiveEnabled(state.source.source, state.source.stableId, state.live)
            }
        }
        return states.size
    }

    private fun readString(reader: JsonReader): String = when (reader.peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
        else -> { reader.skipValue(); "" }
    }

    private fun readBoolean(reader: JsonReader, default: Boolean): Boolean =
        when (reader.peek()) {
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NUMBER, JsonToken.STRING -> readString(reader) !in setOf("0", "false")
            else -> { reader.skipValue(); default }
        }
}

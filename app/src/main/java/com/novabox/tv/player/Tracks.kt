package com.novabox.tv.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import com.novabox.tv.data.SettingsStore
import java.util.Locale

/** One line in the Audio or Subtitles panel. [lang] is what gets remembered for next time. */
data class TrackOption(val label: String, val selected: Boolean, val lang: String?, val group: Tracks.Group?, val index: Int)

/** The audio or subtitle tracks the stream offers. Subtitles start with an "Off" line. */
fun trackOptions(tracks: Tracks, type: Int): List<TrackOption> {
    val out = mutableListOf<TrackOption>()
    var n = 0
    for (g in tracks.groups) {
        if (g.type != type) continue
        for (i in 0 until g.length) {
            if (!g.isTrackSupported(i)) continue
            n++
            val f = g.getTrackFormat(i)
            out += TrackOption(trackLabel(f, type, n), g.isTrackSelected(i), normLang(f.language), g, i)
        }
    }
    if (type == C.TRACK_TYPE_TEXT && out.isNotEmpty()) {
        out.add(0, TrackOption("Off", out.none { it.selected }, SettingsStore.SUBS_OFF, null, -1))
    }
    return out
}

/** "English · 5.1", "Commentary", "Français (forced)", or "Track 2" when the stream names nothing. */
internal fun trackLabel(f: Format, type: Int, n: Int): String {
    val lang = normLang(f.language)?.let { code ->
        Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).takeIf { it.isNotBlank() && it != code }
            ?.replaceFirstChar { it.titlecase(Locale.getDefault()) } ?: code.uppercase()
    }
    val name = f.label?.takeIf { it.isNotBlank() }
    val main = when {
        name != null && lang != null && !name.contains(lang, ignoreCase = true) -> "$lang · $name"
        name != null -> name
        lang != null -> lang
        else -> "Track $n"
    }
    val extra = buildList {
        if (type == C.TRACK_TYPE_AUDIO) when (f.channelCount) {
            1 -> add("mono"); 2 -> add("stereo"); 6 -> add("5.1"); 8 -> add("7.1")
        }
        if (f.selectionFlags and C.SELECTION_FLAG_FORCED != 0) add("forced")
    }
    return if (extra.isEmpty()) main else "$main (${extra.joinToString(", ")})"
}

/** "und" and blank mean no language. */
internal fun normLang(code: String?): String? = code?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "und" && it != C.LANGUAGE_UNDETERMINED }

/** Switches the player to [o] for this stream. */
fun applyTrack(player: Player, type: Int, o: TrackOption) {
    val b = player.trackSelectionParameters.buildUpon().clearOverridesOfType(type)
    if (o.group == null) b.setTrackTypeDisabled(type, true)
    else b.setTrackTypeDisabled(type, false).setOverrideForType(TrackSelectionOverride(o.group.mediaTrackGroup, o.index))
    player.trackSelectionParameters = b.build()
}

/** The remembered languages, applied when a stream starts. */
fun preferredTracks(base: TrackSelectionParameters, audioLang: String?, subtitleLang: String?): TrackSelectionParameters {
    val b = base.buildUpon()
    audioLang?.let { b.setPreferredAudioLanguage(it) }
    when (subtitleLang) {
        null -> {}
        SettingsStore.SUBS_OFF -> b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        else -> b.setPreferredTextLanguage(subtitleLang).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
    }
    return b.build()
}

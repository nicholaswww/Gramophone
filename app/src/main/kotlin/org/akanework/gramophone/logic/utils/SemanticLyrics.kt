package org.akanework.gramophone.logic.utils

import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import android.util.Xml
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.text.subrip.SubripParser
import java.io.StringReader
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.map
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import org.akanework.gramophone.logic.utils.SemanticLyrics.LyricLine
import org.akanework.gramophone.logic.utils.SemanticLyrics.SyncedLyrics
import org.akanework.gramophone.logic.utils.SemanticLyrics.UnsyncedLyrics
import org.akanework.gramophone.logic.utils.SemanticLyrics.Word
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

private const val TAG = "SemanticLyrics"

/*
 * Syntactic-semantic lyric parser.
 *   First parse lrc syntax into custom objects, then parse these into usable representation
 *   for playback. This should be more testable and stable than the old parser.
 *
 * Formats we have to consider in this parser are:
 *  - Simple LRC files (ref Wikipedia) ex: [00:11.22] hello i am lyric
 *  - "compressed LRC" with >1 tag for repeating line ex: [00:11.22][00:15.33] hello i am lyric
 *  - Invalid LRC with all-zero tags [00:00.00] hello i am lyric
 *  - Lyrics that aren't synced and have no tags at all
 *  - Translations, type 1 (ex: pasting first japanese and then english lrc file into one file)
 *     - This implies multiple [offset:] tags are possible
 *  - Translations, type 2 (ex: translated line directly under previous non-translated line)
 *  - The timestamps can variate in the following ways: [00:11] [00:11:22] [00:11.22] [00:11.222] [00:11:222]
 *  - Multiline format: This technically isn't part of any listed guidelines, however is allows for
 *      reading of otherwise discarded lyrics, all the lines between sync point A and B are read as
 *      lyric text of A
 *  - Extended LRC (ref Wikipedia) ex: [00:11.22] <00:11.22> hello <00:12.85> i am <00:13.23> lyric
 *  - Walaoke gender extension (ref Wikipedia)
 *  - iTunes dual speaker extension (v1: / v2: / [bg: ])
 *  - [offset:] tag in header (ref Wikipedia)
 * We completely ignore all ID3 tags from the header as MediaStore is our source of truth.
 */

@Parcelize
enum class SpeakerEntity(val isWalaoke: Boolean, val isVoice2: Boolean = false, val isBackground: Boolean = false) : Parcelable {
    Male(true), // Walaoke
    Female(true), // Walaoke
    Duet(true), // Walaoke
    Background(false, isBackground = true), // iTunes
    Voice1(false), // iTunes
    Voice2(false, isVoice2 = true), // iTunes
    Voice2Background(false, isVoice2 = true, isBackground = true) // iTunes
}

/*
 * Syntactic lyric parser. Parses lrc syntax into custom objects.
 *
 * Formats we have to consider in this component are:
 *  - Simple LRC files (ref Wikipedia) ex: [00:11.22] hello i am lyric
 *  - Invalid LRC with all-zero tags [00:00.00] hello i am lyric
 *  - Lyrics that aren't synced and have no tags at all
 *  - The timestamps can variate in the following ways: [00:11] [00:11:22] [00:11.22] [00:11.222] [00:11:222]
 *  - Multiline format: This technically isn't part of any listed guidelines, however is allows for
 *      reading of otherwise discarded lyrics, all the lines between sync point A and B are read as
 *      lyric text of A
 *  - Extended LRC (ref Wikipedia) ex: [00:11.22] <00:11.22> hello <00:12.85> i am <00:13.23> lyric
 *  - Extended LRC without sync points ex: <00:11.22> hello <00:12.85> i am <00:13.23> lyric
 *  - Walaoke gender extension (ref Wikipedia)
 *  - iTunes dual speaker extension (v1: / v2: / [bg: ])
 *  - Metadata tags in header (ref Wikipedia)
 */
private sealed class SyntacticLrc {
    // all timestamps are in milliseconds ignoring offset
    data class SyncPoint(val timestamp: ULong) : SyntacticLrc()
    data class SpeakerTag(val speaker: SpeakerEntity) : SyntacticLrc()
    data class WordSyncPoint(val timestamp: ULong) : SyntacticLrc()
    data class Metadata(val name: String, val value: String) : SyntacticLrc()
    data class LyricText(val text: String) : SyntacticLrc()
    data class InvalidText(val text: String) : SyntacticLrc()
    open class NewLine() : SyntacticLrc() {
        class SyntheticNewLine : NewLine()
    }

    companion object {
        // also eats space if present
        val timeMarksRegex = "\\[(\\d{2}):(\\d{2})([.:]\\d+)?]".toRegex()
        val timeMarksAfterWsRegex = "([ \t]+)\\[(\\d{2}):(\\d{2})([.:]\\d+)?]".toRegex()
        val timeWordMarksRegex = "<(\\d{2}):(\\d{2})([.:]\\d+)?>".toRegex()
        val metadataRegex = "\\[([a-zA-Z#]+):([^]]*)]".toRegex()

        private fun parseTime(match: MatchResult): ULong {
            val minute = match.groupValues[1].toULong()
            val milliseconds = ((match.groupValues[2] + match.groupValues[3]
                .replace(':', '.')).toDouble() * 1000L).toULong()
            return minute * 60u * 1000u + milliseconds
        }

        fun parseLrc(text: String, multiLineEnabled: Boolean): List<SyntacticLrc>? {
            if (text.isBlank()) return null
            var pos = 0
            val out = mutableListOf<SyntacticLrc>()
            var isBgSpeaker = false
            while (pos < text.length) {
                var pendingBgNewLine = false
                if (isBgSpeaker && text[pos] == ']') {
                    pos++
                    isBgSpeaker = false
                    pendingBgNewLine = true
                }
                if (pos < text.length && pos + 1 < text.length && text.regionMatches(
                        pos,
                        "\r\n",
                        0,
                        2
                    )
                ) {
                    out.add(NewLine())
                    pos += 2
                    pendingBgNewLine = false
                    continue
                }
                if (pos < text.length && (text[pos] == '\n' || text[pos] == '\r')) {
                    out.add(NewLine())
                    pos++
                    pendingBgNewLine = false
                    continue
                }
                if (pendingBgNewLine) {
                    out.add(NewLine.SyntheticNewLine())
                    pendingBgNewLine = false
                    continue
                }
                val tmMatch = timeMarksRegex.matchAt(text, pos)
                if (tmMatch != null) {
                    // Insert synthetic newlines at places where we'd expect one. This won't ever
                    // work with word lyrics without normal sync points at all for obvious reasons,
                    // but hey, we tried. Can't do much about it.
                    // If you want to write something that looks like a timestamp into your lyrics,
                    // you'll probably have to delete the following three lines.
                    if (!(out.lastOrNull() is NewLine? || out.lastOrNull() is SyncPoint))
                        out.add(NewLine.SyntheticNewLine())
                    out.add(SyncPoint(parseTime(tmMatch)))
                    pos += tmMatch.value.length
                    continue
                }
                // Skip spaces in between of compressed lyric sync points. They really are
                // completely useless information we can and should discard.
                val tmwMatch = timeMarksAfterWsRegex.matchAt(text, pos)
                if (out.lastOrNull() is SyncPoint && pos + 7 < text.length && tmwMatch != null) {
                    pos += tmwMatch.groupValues[1].length
                    continue
                }
                // Speaker points can only appear directly after a sync point
                if (out.lastOrNull() is SyncPoint) {
                    if (pos + 2 < text.length && text.regionMatches(pos, "v1:", 0, 3)) {
                        out.add(SpeakerTag(SpeakerEntity.Voice1))
                        pos += 3
                        continue
                    }
                    if (pos + 2 < text.length && text.regionMatches(pos, "v2:", 0, 3)) {
                        out.add(SpeakerTag(SpeakerEntity.Voice2))
                        pos += 3
                        continue
                    }
                    if (pos + 1 < text.length && text.regionMatches(pos, "F:", 0, 2)) {
                        out.add(SpeakerTag(SpeakerEntity.Female))
                        pos += 2
                        continue
                    }
                    if (pos + 1 < text.length && text.regionMatches(pos, "M:", 0, 2)) {
                        out.add(SpeakerTag(SpeakerEntity.Male))
                        pos += 2
                        continue
                    }
                    if (pos + 1 < text.length && text.regionMatches(pos, "D:", 0, 2)) {
                        out.add(SpeakerTag(SpeakerEntity.Duet))
                        pos += 2
                        continue
                    }
                    if (pos + 3 < text.length && text.regionMatches(pos, " v1:", 0, 4)) {
                        out.add(SpeakerTag(SpeakerEntity.Voice1))
                        pos += 4
                        continue
                    }
                    if (pos + 3 < text.length && text.regionMatches(pos, " v2:", 0, 4)) {
                        out.add(SpeakerTag(SpeakerEntity.Voice2))
                        pos += 4
                        continue
                    }
                    if (pos + 2 < text.length && text.regionMatches(pos, " F:", 0, 3)) {
                        out.add(SpeakerTag(SpeakerEntity.Female))
                        pos += 3
                        continue
                    }
                    if (pos + 2 < text.length && text.regionMatches(pos, " M:", 0, 3)) {
                        out.add(SpeakerTag(SpeakerEntity.Male))
                        pos += 3
                        continue
                    }
                    if (pos + 2 < text.length && text.regionMatches(pos, " D:", 0, 3)) {
                        out.add(SpeakerTag(SpeakerEntity.Duet))
                        pos += 3
                        continue
                    }
                }
                // Metadata (or the bg speaker, which looks like metadata) can only appear in the
                // beginning of a file or after newlines
                if (pos + 3 < text.length && text.regionMatches(pos, "[bg:", 0, 4)) {
                    // Insert synthetic newlines at places where we'd expect one.
                    // If you want to write [bg: into your lyrics, you'll probably have to add the
                    // conditional surrounding this comment into the
                    // if (out.isEmpty() || out.last() is NewLine) below.
                    if (out.isNotEmpty() && out.last() !is NewLine)
                        out.add(NewLine.SyntheticNewLine())
                    val lastWasV2 = out.isNotEmpty() && out.subList(0, out.size - 1)
                        .indexOfLast { it is NewLine }.let { if (it < 0) null else it }?.let {
                            (out.subList(it, out.size - 1).findLast { it is SpeakerTag }
                                    as SpeakerTag?)?.speaker?.isVoice2
                    } == true
                    // TODO revisit this heuristic. iTunes bg lines are a child of the last main
                    //  line and there is no "opposite" flag for background lines, but reality does
                    //  not work that way. can main lines be empty in iTunes' lyrics?
                    out.add(SpeakerTag(if (lastWasV2) SpeakerEntity.Voice2Background else
                        SpeakerEntity.Background))
                    pos += 4
                    isBgSpeaker = true
                    continue
                }
                if (out.isEmpty() || out.last() is NewLine) {
                    val mmMatch = metadataRegex.matchAt(text, pos)
                    if (mmMatch != null) {
                        out.add(Metadata(mmMatch.groupValues[1], mmMatch.groupValues[2]))
                        pos += mmMatch.value.length
                        continue
                    }
                }
                // Word marks can be in any lyric text, and in some cases there are no sync points
                // but only word marks in a lrc file.
                val wmMatch = timeWordMarksRegex.matchAt(text, pos)
                if (wmMatch != null) {
                    out.add(WordSyncPoint(parseTime(wmMatch)))
                    pos += wmMatch.value.length
                    continue
                }
                val firstUnsafeCharPos = (text.substring(pos).indexOfFirst {
                    it == '[' ||
                            it == '<' || it == '\r' || it == '\n' || (isBgSpeaker && it == ']')
                } + pos)
                    .let { if (it == pos - 1) text.length else it }
                    .let { if (it == pos) it + 1 else it }
                val subText = text.substring(pos, firstUnsafeCharPos)
                val last = out.lastOrNull()
                // Only count lyric text as lyric text if there is at least one kind of timestamp
                // associated.
                if (out.indexOfLast { it is NewLine } <
                    out.indexOfLast { it is SyncPoint || it is WordSyncPoint }) {
                    if (last is LyricText) {
                        out[out.size - 1] = LyricText(last.text + subText)
                    } else {
                        out.add(LyricText(subText))
                    }
                } else {
                    if (last is InvalidText) {
                        out[out.size - 1] = InvalidText(last.text + subText)
                    } else {
                        out.add(InvalidText(subText))
                    }
                }
                pos = firstUnsafeCharPos
            }
            if (out.lastOrNull() is SyncPoint)
                out.add(InvalidText(""))
            if (out.isNotEmpty() && out.last() !is NewLine)
                out.add(NewLine.SyntheticNewLine())
            return out.let {
                // If there isn't a single sync point with timestamp over zero, that is probably not
                // a valid .lrc file.
                if (it.find {
                        it is SyncPoint && it.timestamp > 0u
                                || it is WordSyncPoint && it.timestamp > 0u
                    } == null)
                // Recover only text information to make the most out of this damaged file.
                    it.flatMap {
                        if (it is InvalidText)
                            listOf(it)
                        else if (it is SpeakerTag)
                            listOf(it)
                        else if (it is LyricText)
                            listOf(InvalidText(it.text))
                        else
                            listOf()
                    }
                else it
            }.let {
                if (multiLineEnabled) {
                    val a = AtomicReference<String?>(null)
                    it.flatMap {
                        val aa = a.get()
                        when {
                            it is LyricText -> {
                                if (aa == null)
                                    a.set(it.text)
                                else
                                    a.set(aa + it.text)
                                listOf()
                            }
                            // make sure InvalidText that can't be lyric text isn't saved as lyric
                            it is InvalidText && aa != null -> {
                                a.set(aa + it.text)
                                listOf()
                            }

                            it is NewLine && aa != null -> {
                                a.set(aa + "\n")
                                listOf()
                            }

                            aa != null -> {
                                a.set(null)
                                var aaa: String = aa
                                var i = 0
                                while (aaa.last() == '\n') {
                                    i++
                                    aaa = aaa.dropLast(1)
                                }
                                listOf(LyricText(aaa)).let {
                                    var aaaa: List<SyntacticLrc> = it
                                    while (i-- > 0)
                                        aaaa = aaaa + listOf(NewLine())
                                    aaaa
                                } + it
                            }

                            else -> listOf(it)
                        }
                    }.let {
                        if (a.get() != null)
                            it + if (a.get()!!.last() == '\n')
                                listOf(LyricText(a.get()!!.dropLast(1)), NewLine())
                            else
                                listOf(LyricText(a.get()!!))
                        else it
                    }
                } else it
            }
        }
    }
}

/*
 * Syntactic lyric parser. Parse custom objects into usable representation for playback.
 *
 * Formats we have to consider in this component are:
 *  - Simple LRC files (ref Wikipedia) ex: [00:11.22] hello i am lyric
 *  - "compressed LRC" with >1 tag for repeating line ex: [00:11.22][00:15.33] hello i am lyric
 *  - Lyrics that aren't synced and have no tags at all
 *  - Translations, type 1 (ex: pasting first japanese and then english lrc file into one file)
 *     - This implies multiple [offset:] tags are possible
 *  - Translations, type 2 (ex: translated line directly under previous non-translated line)
 *  - Extended LRC (ref Wikipedia) ex: [00:11.22] <00:11.22> hello <00:12.85> i am <00:13.23> lyric
 *  - Extended LRC without sync points ex: <00:11.22> hello <00:12.85> i am <00:13.23> lyric
 *  - Walaoke gender extension (ref Wikipedia)
 *  - iTunes dual speaker extension (v1: / v2: / [bg: ])
 *  - [offset:] tag in header (ref Wikipedia)
 * We completely ignore all ID3 tags from the header as MediaStore is our source of truth.
 */
sealed class SemanticLyrics : Parcelable {
    abstract val unsyncedText: List<Pair<String, SpeakerEntity?>>

    @Parcelize
    data class UnsyncedLyrics(override val unsyncedText: List<Pair<String, SpeakerEntity?>>) : SemanticLyrics()

    @Parcelize
    data class SyncedLyrics(val text: List<LyricLine>) : SemanticLyrics() {
        override val unsyncedText
            get() = text.map { it.text to it.speaker }
    }

    @Parcelize
    data class LyricLine(
        val text: String,
        val start: ULong,
        var end: ULong,
        val words: MutableList<Word>?,
        var speaker: SpeakerEntity?,
        var isTranslated: Boolean
    ) : Parcelable {
        val isClickable: Boolean
            get() = text.isNotBlank()
    }

    @Parcelize
    data class Word(
        var timeRange: @WriteWith<ULongRangeParceler>() ULongRange,
        var charRange: @WriteWith<IntRangeParceler>() IntRange,
        var isRtl: Boolean
    ) : Parcelable

    object ULongRangeParceler : Parceler<ULongRange> {
        override fun create(parcel: Parcel) =
            parcel.readLong().toULong()..parcel.readLong().toULong()

        override fun ULongRange.write(parcel: Parcel, flags: Int) {
            parcel.writeLong(first.toLong())
            parcel.writeLong(last.toLong())
        }
    }

    object IntRangeParceler : Parceler<IntRange> {
        override fun create(parcel: Parcel) = parcel.readInt()..parcel.readInt()

        override fun IntRange.write(parcel: Parcel, flags: Int) {
            parcel.writeInt(first)
            parcel.writeInt(last)
        }
    }
}

fun parseLrc(lyricText: String, trimEnabled: Boolean, multiLineEnabled: Boolean): SemanticLyrics? {
    val lyricSyntax = SyntacticLrc.parseLrc(lyricText, multiLineEnabled)
        ?: return null
    if (lyricSyntax.find { it is SyntacticLrc.SyncPoint || it is SyntacticLrc.WordSyncPoint } == null) {
        var lastSpeakerTag: SpeakerEntity? = null
        val out = mutableListOf<Pair<String, SpeakerEntity?>>()
        for (element in lyricSyntax) {
            when (element) {
                is SyntacticLrc.SpeakerTag -> {
                    lastSpeakerTag = element.speaker
                }
                is SyntacticLrc.InvalidText -> {
                    out += element.text to lastSpeakerTag
                    if (lastSpeakerTag?.isWalaoke != true)
                        lastSpeakerTag = null
                }
                else -> throw IllegalStateException("unexpected type ${element.javaClass.name}")
            }
        }
        val defaultIsWalaokeM = out.find { it.second?.isWalaoke == true } != null &&
                out.find { it.second?.isWalaoke == false } == null
        while (out.firstOrNull()?.first?.isBlank() == true)
            out.removeAt(0)
        while (out.lastOrNull()?.first?.isBlank() == true)
            out.removeAt(out.lastIndex)
        return UnsyncedLyrics(out.map { lyric ->
            if (defaultIsWalaokeM && lyric.second == null)
                lyric.copy(second = SpeakerEntity.Male)
            else lyric
        })
    }
    // Synced lyrics processing state machine starts here
    val out = mutableListOf<LyricLine>()
    var offset = 0L
    var lastSyncPoint: ULong? = null
    var lastWordSyncPoint: ULong? = null
    var speaker: SpeakerEntity? = null
    var hadLyricSinceWordSync = true
    var hadWordSyncSinceNewLine = false
    var currentLine = mutableListOf<Pair<ULong, String?>>()
    var syncPointStreak = 0
    var compressed = mutableListOf<ULong>()
    for (element in lyricSyntax) {
        if (element is SyntacticLrc.SyncPoint)
            syncPointStreak++
        else
            syncPointStreak = 0
        when {
            element is SyntacticLrc.Metadata && element.name == "offset" -> {
                // positive offset means lyric played earlier in lrc, hence multiply with -1
                offset = element.value.toLong() * -1
            }

            element is SyntacticLrc.SyncPoint -> {
                val ts = (element.timestamp.toLong() + offset).coerceAtLeast(0).toULong()
                if (syncPointStreak > 1) {
                    compressed.add(ts)
                } else {
                    if (compressed.isNotEmpty())
                        throw IllegalStateException("while parsing lrc, $compressed not empty but syncPointStreak is 1; lrc file: $lyricText")
                    lastSyncPoint = ts
                }
            }

            element is SyntacticLrc.SpeakerTag -> {
                speaker = element.speaker
            }

            element is SyntacticLrc.WordSyncPoint -> {
                if (!hadLyricSinceWordSync && lastWordSyncPoint != null)
                // add a dummy word for preserving end timing of previous word
                    currentLine.add(Pair(lastWordSyncPoint, null))
                lastWordSyncPoint = (element.timestamp.toLong() + offset).coerceAtLeast(0).toULong()
                if (lastSyncPoint == null)
                    lastSyncPoint = lastWordSyncPoint
                hadLyricSinceWordSync = false
                hadWordSyncSinceNewLine = true
            }

            element is SyntacticLrc.LyricText -> {
                hadLyricSinceWordSync = true
                currentLine.add(Pair(lastWordSyncPoint ?: lastSyncPoint!!, element.text))
            }

            element is SyntacticLrc.NewLine -> {
                var words = if (currentLine.size > 1 || hadWordSyncSinceNewLine) {
                    val wout = mutableListOf<Word>()
                    var idx = 0
                    for (i in currentLine.indices) {
                        val current = currentLine[i]
                        if (current.second == null)
                            continue // skip dummy words that only exist to provide time
                        val oIdx = idx
                        idx += current.second!!.length
                        // Make sure we do NOT include whitespace as part of the word. Whitespaces
                        // do not have a strong bi-di flag assigned and hence ICU4J/AndroidBidi will
                        // set bi-di transition point before whitespace. Rendering relies on being
                        // able to change edge treatment with trailing flag in Layout which only
                        // works on bi-di transition points. Additionally, excluding whitespace
                        // allows us to scale gradient properly based on asking ourselves if the
                        // next char is even rendered (or whitespace).
                        val textWithoutStartWhitespace = current.second!!.trimStart()
                        val startWhitespaceLength =
                            current.second!!.length - textWithoutStartWhitespace.length
                        val textWithoutWhitespaces = textWithoutStartWhitespace.trimEnd()
                        val endWhitespaceLength =
                            textWithoutStartWhitespace.length - textWithoutWhitespaces.length
                        val startIndex = oIdx + startWhitespaceLength
                        val endIndex = idx - endWhitespaceLength
                        if (startIndex == endIndex)
                            continue // word contained only whitespace
                        val endInclusive = if (i + 1 < currentLine.size) {
                            // If we have a next word (with sync point), use its sync
                            // point minus 1ms as end point of this word
                            currentLine[i + 1].first - 1uL
                        } else if (lastWordSyncPoint != null &&
                            lastWordSyncPoint > current.first
                        ) {
                            // If we have a dedicated sync point just for the last word,
                            // use it. Similar to dummy words but for the last word only
                            lastWordSyncPoint - 1uL // minus 1ms for consistency
                        } else {
                            // Estimate how long this word will take based on character
                            // to time ratio. To avoid this estimation, add a last word
                            // sync point to the line after the text :)
                            current.first + (wout.map {
                                it.timeRange.count() /
                                        it.charRange.count().toFloat()
                            }.average().let {
                                if (it.isNaN()) 100.0 else it
                            } *
                                    textWithoutWhitespaces.length).toULong()
                        }
                        if (endInclusive > current.first)
                        // isRtl is filled in later in splitBidirectionalWords
                            wout.add(
                                Word(
                                    current.first..endInclusive,
                                    startIndex..<endIndex,
                                    isRtl = false
                                )
                            )
                    }
                    wout
                } else null
                if (currentLine.isNotEmpty() || lastWordSyncPoint != null || lastSyncPoint != null) {
                    var text = currentLine.joinToString("") { it.second ?: "" }
                    if (trimEnabled) {
                        val orig = text
                        text = orig.trimStart()
                        val startDiff = orig.length - text.length
                        text = text.trimEnd()
                        val iter = words?.listIterator()
                        iter?.forEach {
                            if (it.charRange.last.toLong() - startDiff < 0
                                || it.charRange.first.toLong() - startDiff >= text.length
                            )
                                iter.remove()
                            else
                                it.charRange = (it.charRange.first - startDiff)
                                        .coerceAtLeast(0)..(it.charRange.last - startDiff)
                                        .coerceAtMost(text.length - 1)
                        }
                    }
                    val start = if (currentLine.isNotEmpty()) currentLine.first().first
                    else lastWordSyncPoint ?: lastSyncPoint!!
                    out.add(LyricLine(text, start, 0uL /* filled later */, words, speaker, false /* filled later */))
                    compressed.forEach {
                        val diff = it - start
                        out.add(out.last().copy(start = it, words = words?.map {
                            it.copy(
                                it.timeRange.start + diff..it.timeRange.last + diff
                            )
                        }?.toMutableList()))
                    }
                }
                compressed.clear()
                currentLine.clear()
                lastSyncPoint = null
                lastWordSyncPoint = null
                hadWordSyncSinceNewLine = false
                // Walaoke extension speakers stick around unless another speaker is
                // specified. (The default speaker - before one is chosen - is male.)
                if (speaker?.isWalaoke != true)
                    speaker = null
                hadLyricSinceWordSync = true
            }
        }
    }
    out.sortBy { it.start }
    var previousTimestamp = 0uL
    val defaultIsWalaokeM = out.find { it.speaker?.isWalaoke == true } != null &&
            out.find { it.speaker?.isWalaoke == false } == null
    while (out.firstOrNull()?.text?.isBlank() == true)
        out.removeAt(0)
    while (out.lastOrNull()?.text?.isBlank() == true)
        out.removeAt(out.lastIndex)
    out.forEachIndexed { i, lyric ->
        if (defaultIsWalaokeM && lyric.speaker == null)
            lyric.speaker = SpeakerEntity.Male
        lyric.end = lyric.words?.lastOrNull()?.timeRange?.last
            ?: (if (lyric.start == previousTimestamp) out.find { it.start == lyric.start }
                ?.words?.lastOrNull()?.timeRange?.last else null)
                    ?: out.find { it.start > lyric.start }?.start?.minus(1uL)
                    ?: Long.MAX_VALUE.toULong()
        lyric.isTranslated = lyric.start == previousTimestamp
        previousTimestamp = lyric.start
    }
    return SyncedLyrics(out)
}

private val tt = "http://www.w3.org/ns/ttml"
private val ttm = "http://www.w3.org/ns/ttml#metadata"
private val itunes = "http://itunes.apple.com/lyric-ttml-extensions"
private val itunesInternal = "http://music.apple.com/lyric-ttml-internal"
fun parseTtml(lyricText: String, trimEnabled: Boolean): SemanticLyrics? {
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
    parser.setInput(StringReader(lyricText))
    try {
        parser.next()
        parser.require(XmlPullParser.START_TAG, tt, "tt")
    } catch (_: XmlPullParserException) {
        return null // not ttml
    }
    val lang = parser.getAttributeValue("http://www.w3.org/XML/1998/namespace", "lang")
    val timing = parser.getAttributeValue(itunesInternal, "timing")
    while (parser.next() != XmlPullParser.END_TAG) {
    }
    return null
}

@OptIn(UnstableApi::class)
fun parseSrt(lyricText: String, trimEnabled: Boolean): SemanticLyrics? {
    if (!lyricText.startsWith("1\n") && !lyricText.startsWith("1\r")) return null // invalid SubRip
    val cues = mutableListOf<CuesWithTiming>()
    val parser = SubripParser()
    try {
        parser.parse(
            lyricText.toByteArray(),
            SubtitleParser.OutputOptions.allCues()
        ) { cues.add(it) }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse something which looks like SRT: ${Log.getStackTraceString(e)}")
        return null
    }
    var lastTs: ULong? = null
    return SyncedLyrics(cues.map {
        val ts = (it.startTimeUs / 1000).toULong()
        val l = lastTs == ts
        lastTs = ts
        LyricLine(it.cues[0].text!!.toString().let {
            if (trimEnabled)
                it.trim()
            else it
        }, ts, (it.endTimeUs / 1000).toULong(), null, null, l)
    })
}

fun SemanticLyrics?.convertForLegacy(): MutableList<MediaStoreUtils.Lyric>? {
    if (this == null) return null
    if (this is SyncedLyrics) {
        return this.text.map {
            MediaStoreUtils.Lyric(it.start.toLong(), it.text, it.isTranslated)
        }.toMutableList()
    }
    return mutableListOf(MediaStoreUtils.Lyric(null,
        this.unsyncedText.joinToString("\n") { it.first }, false))
}

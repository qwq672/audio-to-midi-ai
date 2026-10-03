package com.audiomidi.ai.pipeline.impl

import com.audiomidi.ai.pipeline.MidiComposition
import com.audiomidi.ai.pipeline.MidiWriter
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Pure Kotlin Standard MIDI File (SMF) writer.
 *
 * Outputs Format 1 (multi-track). Track 0 is a tempo conductor track;
 * each subsequent track is one instrument (program change at tick 0,
 * then note on/off events with delta-time VLQ encoding, then end-of-track).
 *
 * Time conversion: notes carry [com.audiomidi.ai.pipeline.MidiNote.startMs]
 * and [com.audiomidi.ai.pipeline.MidiNote.endMs] in milliseconds. We convert
 * to ticks using [MidiComposition.microsPerQuarter] / [MidiComposition.ticksPerQuarter].
 */
class KotlinMidiWriter : MidiWriter {

    override suspend fun write(composition: MidiComposition): ByteArray {
        val out = ByteArrayOutputStream()
        val dos = DataOutputStream(out)

        // --- MThd header ---
        dos.writeBytes("MThd")
        dos.writeInt(6)
        dos.writeShort(1) // format 1
        val totalTracks = (composition.tracks.size + 1).coerceAtMost(0xFFFF)
        dos.writeShort(totalTracks)
        dos.writeShort(composition.ticksPerQuarter)

        // --- Track 0: tempo conductor ---
        writeTrackBytes(dos, buildTempoTrackEvents(composition))

        // --- Each instrument track ---
        for (track in composition.tracks) {
            writeTrackBytes(dos, buildTrackEvents(track, composition))
        }

        return out.toByteArray()
    }

    /**
     * Track 0: tempo meta-event + end-of-track.
     */
    private fun buildTempoTrackEvents(comp: MidiComposition): List<MidiEvent> {
        return listOf(
            MidiEvent(0, MidiEventType.TEMPO, payload = longArrayOf(comp.microsPerQuarter.toLong())),
            MidiEvent(0, MidiEventType.END_OF_TRACK, payload = longArrayOf())
        )
    }

    /**
     * Per-instrument track: program change → note on/off sorted by tick → end-of-track.
     */
    private fun buildTrackEvents(track: com.audiomidi.ai.pipeline.MidiTrack, comp: MidiComposition): List<MidiEvent> {
        val events = mutableListOf<MidiEvent>()

        // Program change at tick 0
        events.add(MidiEvent(
            tick = 0,
            type = MidiEventType.PROGRAM_CHANGE,
            channel = track.channel,
            data1 = track.programNumber,
            data2 = 0
        ))

        // Convert each note to NOTE_ON + NOTE_OFF events, tick-based
        val microsPerTick = comp.microsPerQuarter.toDouble() / comp.ticksPerQuarter
        val tickFor: (Long) -> Long = { ms ->
            ((ms * 1000.0) / microsPerTick).toLong().coerceAtLeast(0)
        }

        for (note in track.notes.sortedBy { it.startMs }) {
            val startTick = tickFor(note.startMs)
            val endTick = tickFor(note.endMs).coerceAtLeast(startTick + 1)
            events.add(MidiEvent(
                tick = startTick,
                type = MidiEventType.NOTE_ON,
                channel = track.channel,
                data1 = note.pitch,
                data2 = note.velocity
            ))
            events.add(MidiEvent(
                tick = endTick,
                type = MidiEventType.NOTE_OFF,
                channel = track.channel,
                data1 = note.pitch,
                data2 = 0
            ))
        }

        // Sort: by tick; off before on at the same tick (avoids overlap artifacts)
        events.sortWith(compareBy({ it.tick }, { it.type.ordinal }))

        // Append end-of-track at last tick
        val endTick = events.lastOrNull()?.tick ?: 0
        events.add(MidiEvent(endTick, MidiEventType.END_OF_TRACK, payload = longArrayOf()))

        return events
    }

    private fun writeTrackBytes(dos: DataOutputStream, events: List<MidiEvent>) {
        val trackBytes = ByteArrayOutputStream()
        val trackDos = DataOutputStream(trackBytes)

        var lastTick = 0L
        for (event in events) {
            val delta = (event.tick - lastTick).coerceAtLeast(0)
            writeVarLen(trackDos, delta)
            lastTick = event.tick

            when (event.type) {
                MidiEventType.NOTE_ON -> {
                    trackDos.writeByte((0x90 or (event.channel and 0x0F)))
                    trackDos.writeByte(event.data1 and 0x7F)
                    trackDos.writeByte(event.data2 and 0x7F)
                }
                MidiEventType.NOTE_OFF -> {
                    trackDos.writeByte((0x80 or (event.channel and 0x0F)))
                    trackDos.writeByte(event.data1 and 0x7F)
                    trackDos.writeByte(event.data2 and 0x7F)
                }
                MidiEventType.PROGRAM_CHANGE -> {
                    trackDos.writeByte((0xC0 or (event.channel and 0x0F)))
                    trackDos.writeByte(event.data1 and 0x7F)
                }
                MidiEventType.TEMPO -> {
                    val tempo = event.payload.getOrElse(0) { 500000L }
                    trackDos.writeByte(0xFF)
                    trackDos.writeByte(0x51)
                    trackDos.writeByte(0x03)
                    trackDos.writeByte(((tempo ushr 16) and 0xFF).toInt())
                    trackDos.writeByte(((tempo ushr 8) and 0xFF).toInt())
                    trackDos.writeByte((tempo and 0xFF).toInt())
                }
                MidiEventType.END_OF_TRACK -> {
                    trackDos.writeByte(0xFF)
                    trackDos.writeByte(0x2F)
                    trackDos.writeByte(0x00)
                }
            }
        }

        val trackData = trackBytes.toByteArray()
        dos.writeBytes("MTrk")
        dos.writeInt(trackData.size)
        dos.write(trackData)
    }

    /**
     * Variable-length quantity encoding (MIDI standard).
     * Each byte: 7 data bits, MSB=1 means more bytes follow.
     */
    private fun writeVarLen(dos: DataOutputStream, value: Long) {
        if (value == 0L) {
            dos.writeByte(0)
            return
        }
        val buffer = ArrayList<Int>()
        var v = value
        buffer.add((v and 0x7F).toInt())
        v = v ushr 7
        while (v > 0) {
            buffer.add(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        for (i in buffer.indices.reversed()) {
            dos.writeByte(buffer[i])
        }
    }

    private enum class MidiEventType { NOTE_OFF, NOTE_ON, PROGRAM_CHANGE, TEMPO, END_OF_TRACK }

    private data class MidiEvent(
        val tick: Long,
        val type: MidiEventType,
        val channel: Int = 0,
        val data1: Int = 0,
        val data2: Int = 0,
        val payload: LongArray = LongArray(0)
    )
}

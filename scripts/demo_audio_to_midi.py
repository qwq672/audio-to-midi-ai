#!/usr/bin/env python3
"""End-to-end demo: generate test audio (sine wave chord progression), run
Basic Pitch ONNX inference, decode notes, write actual MIDI file.

Output: /tmp/demo_output.mid — open in any MIDI player to verify.
"""
import numpy as np
import onnxruntime as ort
import struct
import sys

MODEL_PATH = "/tmp/nmp.onnx"
OUTPUT_MIDI = "/tmp/demo_output.mid"

# Model constants
INPUT_LEN = 43844
SR = 22050
N_FRAMES = 172
N_PITCHES = 88
MIDI_OFFSET = 21  # MIDI 21 = A0 (lowest piano key)
NOTE_OUTPUT_INDEX = 1  # outputs[1] is the "note" probability (88-dim)
THRESHOLD = 0.4  # Frame probability above this = note active

def load_model():
    sess = ort.InferenceSession(MODEL_PATH, providers=['CPUExecutionProvider'])
    return sess, sess.get_inputs()[0].name

def generate_test_audio():
    """Generate a 4-chord progression: C major -> A minor -> F major -> G major.
    Each chord 2 seconds, total 8 seconds. Each chord is a 3-note triad.
    """
    chords = [
        ('C major', [60, 64, 67]),   # C4, E4, G4
        ('A minor', [57, 60, 64]),  # A3, C4, E4
        ('F major', [53, 57, 60]),  # F3, A3, C4
        ('G major', [55, 59, 62]),  # G3, B3, D4
    ]
    audio_chunks = []
    labels = []
    for name, midis in chords:
        freqs = [440 * 2**((m - 69) / 12) for m in midis]
        t = np.arange(INPUT_LEN) / SR
        wave = sum(0.25 * np.sin(2 * np.pi * f * t) for f in freqs)
        audio_chunks.append(wave.astype(np.float32))
        labels.append((name, midis))
    return np.concatenate(audio_chunks), labels

def run_inference_chunks(sess, input_name, audio):
    """Run inference on each 43844-sample chunk. Return list of (1, 172, 88) note arrays."""
    note_outputs = []
    n_chunks = (len(audio) + INPUT_LEN - 1) // INPUT_LEN
    for i in range(n_chunks):
        chunk = audio[i * INPUT_LEN : (i + 1) * INPUT_LEN]
        if len(chunk) < INPUT_LEN:
            chunk = np.pad(chunk, (0, INPUT_LEN - len(chunk)))
        audio_in = chunk.astype(np.float32).reshape(1, INPUT_LEN, 1)
        outputs = sess.run(None, {input_name: audio_in})
        note_outputs.append(outputs[NOTE_OUTPUT_INDEX][0])  # (172, 88)
    return note_outputs

def decode_notes(note_outputs):
    """Decode note events from note probability arrays.

    For each pitch, find contiguous runs of frames where prob > THRESHOLD.
    Emit a MidiNote(pitch, startMs, endMs, velocity).

    Time mapping: each chunk is 2 seconds = 172 frames, so frame_ms = 2000/172 ≈ 11.6ms.
    """
    notes = []
    frame_ms = (INPUT_LEN * 1000.0 / SR) / N_FRAMES
    for chunk_idx, note_arr in enumerate(note_outputs):
        # note_arr shape (172, 88)
        active = note_arr > THRESHOLD  # boolean (172, 88)
        for pitch_idx in range(N_PITCHES):
            # Find contiguous runs where active[:, pitch_idx] is True
            col = active[:, pitch_idx]
            i = 0
            while i < N_FRAMES:
                if col[i]:
                    j = i
                    while j < N_FRAMES and col[j]:
                        j += 1
                    # Contiguous run from frame i to j-1
                    start_ms = int((chunk_idx * N_FRAMES + i) * frame_ms)
                    end_ms = int((chunk_idx * N_FRAMES + j - 1) * frame_ms)
                    # Velocity: peak probability in this run
                    peak_prob = float(note_arr[i:j, pitch_idx].max())
                    velocity = int(60 + peak_prob * 60)  # 60-120 range
                    midi_pitch = MIDI_OFFSET + pitch_idx
                    notes.append({
                        'pitch': midi_pitch,
                        'start_ms': start_ms,
                        'end_ms': end_ms,
                        'velocity': velocity,
                    })
                    i = j
                else:
                    i += 1
    return notes

def write_midi(notes, output_path):
    """Write a Standard MIDI File (Format 0, single track) with the given notes."""
    # Constants
    TICKS_PER_QUARTER = 480
    MICROSPERQUARTER = 500000  # 120 BPM
    ms_to_tick = lambda ms: int(ms * 1000.0 / MICROSPERQUARTER * TICKS_PER_QUARTER)

    # Build event list: (tick, type, channel, data1, data2)
    events = []
    for n in notes:
        start_tick = ms_to_tick(n['start_ms'])
        end_tick = ms_to_tick(n['end_ms'])
        events.append((start_tick, 'on', 0, n['pitch'], n['velocity']))
        events.append((end_tick, 'off', 0, n['pitch'], 0))
    # Sort: off before on at same tick (avoids hanging notes)
    type_order = {'off': 0, 'on': 1, 'tempo': 2, 'eot': 3}
    events.sort(key=lambda e: (e[0], type_order[e[1]]))

    # Build track bytes
    track_bytes = bytearray()
    # Tempo meta event at tick 0
    track_bytes += bytes([0xFF, 0x51, 0x03,
                          (MICROSPERQUARTER >> 16) & 0xFF,
                          (MICROSPERQUARTER >> 8) & 0xFF,
                          MICROSPERQUARTER & 0xFF])
    last_tick = 0
    for tick, typ, channel, d1, d2 in events:
        delta = tick - last_tick
        # VLQ encode
        if delta == 0:
            track_bytes.append(0)
        else:
            chunks = []
            v = delta
            chunks.append(v & 0x7F)
            v >>= 7
            while v > 0:
                chunks.append((v & 0x7F) | 0x80)
                v >>= 7
            track_bytes += bytes(reversed(chunks))
        last_tick = tick
        if typ == 'on':
            track_bytes += bytes([0x90 | channel, d1 & 0x7F, d2 & 0x7F])
        elif typ == 'off':
            track_bytes += bytes([0x80 | channel, d1 & 0x7F, d2 & 0x7F])
    # End of track
    track_bytes += bytes([0, 0xFF, 0x2F, 0x00])

    # Build full MIDI file
    with open(output_path, 'wb') as f:
        f.write(b'MThd')
        f.write(struct.pack('>I', 6))
        f.write(struct.pack('>H', 0))  # format 0
        f.write(struct.pack('>H', 1))  # 1 track
        f.write(struct.pack('>H', TICKS_PER_QUARTER))
        f.write(b'MTrk')
        f.write(struct.pack('>I', len(track_bytes)))
        f.write(bytes(track_bytes))
    return len(notes)

def main():
    print("=== Audio -> MIDI end-to-end demo ===\n")

    sess, input_name = load_model()
    print(f"Model: {MODEL_PATH}")
    print(f"Input: shape (1, {INPUT_LEN}, 1)  @ {SR} Hz mono")
    print(f"Output: note probability (1, {N_FRAMES}, {N_PITCHES})  = {N_FRAMES} frames x 88 piano keys")
    print(f"Threshold: {THRESHOLD}")

    audio, chord_labels = generate_test_audio()
    print(f"\nGenerated test audio: {len(audio)} samples = {len(audio)/SR:.1f}s")
    print("Chord progression:")
    for name, midis in chord_labels:
        print(f"  {name}: MIDI {midis}")

    print(f"\nRunning inference on {len(audio) // INPUT_LEN + 1} chunks...")
    note_outputs = run_inference_chunks(sess, input_name, audio)
    print(f"Got {len(note_outputs)} note arrays each shape {note_outputs[0].shape}")

    print(f"\nDecoding notes (threshold={THRESHOLD})...")
    notes = decode_notes(note_outputs)
    print(f"Detected {len(notes)} notes total.")

    # Print note summary per chunk
    print("\nPer-chunk detection (expected vs detected):")
    for chunk_idx, (name, expected_midis) in enumerate(chord_labels):
        chunk_notes = [n for n in notes if chunk_idx * N_FRAMES * (INPUT_LEN * 1000 // SR // N_FRAMES) <= n['start_ms'] < (chunk_idx + 1) * N_FRAMES * (INPUT_LEN * 1000 // sr // N_FRAMES) if True]
        # Simpler: filter by chunk index using time
        chunk_ms_start = chunk_idx * (INPUT_LEN * 1000 // SR)
        chunk_ms_end = (chunk_idx + 1) * (INPUT_LEN * 1000 // SR)
        chunk_notes = [n for n in notes if chunk_ms_start <= n['start_ms'] < chunk_ms_end]
        detected_midis = sorted(set(n['pitch'] for n in chunk_notes))
        expected_str = ','.join(str(m) for m in expected_midis)
        detected_str = ','.join(str(m) for m in detected_midis)
        match = set(detected_midis) & set(expected_midis)
        print(f"  chunk {chunk_idx+1} ({name}): expected [{expected_str}]  detected [{detected_str}]  match={len(match)}/3")

    # Write MIDI file
    n_written = write_midi(notes, OUTPUT_MIDI)
    import os
    file_size = os.path.getsize(OUTPUT_MIDI)
    print(f"\nWrote MIDI file: {OUTPUT_MIDI}")
    print(f"  Notes in file: {n_written}")
    print(f"  File size: {file_size} bytes")

    # Compute detection accuracy
    expected_set = set()
    for chunk_idx, (name, midis) in enumerate(chord_labels):
        expected_set.update((chunk_idx, m) for m in midis)
    detected_set = set()
    for chunk_idx, (name, midis) in enumerate(chord_labels):
        chunk_ms_start = chunk_idx * (INPUT_LEN * 1000 // SR)
        chunk_ms_end = (chunk_idx + 1) * (INPUT_LEN * 1000 // SR)
        chunk_notes = [n for n in notes if chunk_ms_start <= n['start_ms'] < chunk_ms_end]
        detected_set.update((chunk_idx, n['pitch']) for n in chunk_notes)

    matched = expected_set & detected_set
    print(f"\n=== Detection accuracy ===")
    print(f"  Expected notes: {len(expected_set)}")
    print(f"  Detected:       {len(detected_set)}")
    print(f"  Correct:       {len(matched)}/{len(expected_set)} ({100*len(matched)/len(expected_set):.0f}%)")
    false_positives = detected_set - expected_set
    if false_positives:
        print(f"  False positives: {len(false_positives)} (notes detected but not in input)")
        for chunk_idx, midi in sorted(false_positives):
            print(f"    chunk {chunk_idx+1} MIDI {midi}")

    print(f"\n=== Conclusion ===")
    print(f"MIDI file written to {OUTPUT_MIDI}")
    print(f"Open it in any MIDI player (VLC, MuseScore, online MIDI player) to verify.")
    print(f"You should hear a 4-chord progression: C major -> A minor -> F major -> G major.")

if __name__ == "__main__":
    # Note: 'sr' is used in inner scope below, define global alias
    sr = SR
    main()

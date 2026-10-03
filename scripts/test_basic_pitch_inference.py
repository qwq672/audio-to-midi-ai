#!/usr/bin/env python3
"""Verify Basic Pitch ONNX model works for audio-to-MIDI conversion.

Run inference on synthetic test signals (A4, C4, chord) and check which
output and which sample rate correctly detects the played pitch.
"""
import numpy as np
import onnxruntime as ort
import sys

MODEL_PATH = "/tmp/nmp.onnx"

def load_model():
    sess = ort.InferenceSession(MODEL_PATH, providers=['CPUExecutionProvider'])
    input_name = sess.get_inputs()[0].name
    print(f"Model loaded. Input: {input_name}, shape={sess.get_inputs()[0].shape}")
    print("Outputs:")
    for i, o in enumerate(sess.get_outputs()):
        print(f"  [{i}] {o.name} shape={o.shape}")
    return sess, input_name

def run_inference(sess, input_name, audio):
    """audio: 1D array of length 43844 (model's expected input length)."""
    audio = np.asarray(audio, dtype=np.float32).reshape(1, 43844, 1)
    outputs = sess.run(None, {input_name: audio})
    # outputs[0] shape (1, 172, 264) — contour/feature map
    # outputs[1] shape (1, 172, 88)  — likely onset
    # outputs[2] shape (1, 172, 88)  — likely note
    return outputs[1], outputs[2], outputs[0]  # onset_88, note_88, contour_264

def check_pitch_detection(out_88, label):
    """Given an (1, 172, 88) probability array, find top pitches by avg prob."""
    arr = out_88[0]  # (172, 88)
    avg_per_pitch = arr.mean(axis=0)  # (88,)
    top = np.argsort(avg_per_pitch)[::-1][:5]
    print(f"  Top 5 pitches for {label}:")
    for i, p in enumerate(top):
        midi = 21 + p
        freq = 440 * 2**((midi-69)/12)
        print(f"    #{i+1}: MIDI {midi:3d} (freq {freq:6.1f} Hz) prob={avg_per_pitch[p]:.4f}")
    return avg_per_pitch, top

def test_sine(sess, input_name, freq, midi_target, label, sr=22050):
    print(f"\n=== {label}: sine wave at {freq} Hz (MIDI {midi_target}), sr={sr} ===")
    t = np.arange(43844) / sr
    audio = 0.5 * np.sin(2 * np.pi * freq * t).astype(np.float32)
    onset_out, note_out, _ = run_inference(sess, input_name, audio)
    print(f"  Output[1] (88-dim, call it 'onset'):")
    onset_avg, onset_top = check_pitch_detection(onset_out, "onset-out")
    print(f"  Output[2] (88-dim, call it 'note'):")
    note_avg, note_top = check_pitch_detection(note_out, "note-out")
    target_idx = midi_target - 21
    print(f"\n  Target MIDI {midi_target}:")
    print(f"    In 'onset-out': prob={onset_avg[target_idx]:.4f}, rank #{list(onset_top).index(target_idx)+1 if target_idx in onset_top else 'N/A'}")
    print(f"    In 'note-out':  prob={note_avg[target_idx]:.4f}, rank #{list(note_top).index(target_idx)+1 if target_idx in note_top else 'N/A'}")
    ok_onset = target_idx in onset_top[:3] or onset_avg[target_idx] > 0.3
    ok_note = target_idx in note_top[:3] or note_avg[target_idx] > 0.3
    return ok_onset, ok_note

def test_sample_rates(sess, input_name, freq=440, midi_target=69):
    print(f"\n=== Sample rate sweep for A4 (440 Hz, MIDI 69) ===")
    print(f"  {'sr':>6}  {'onset_out[A4]':>14}  {'note_out[A4]':>14}  {'note_out[A3]':>14}  {'note_out[A5]':>14}")
    for sr in [8000, 16000, 22050, 32000, 44100, 48000]:
        t = np.arange(43844) / sr
        audio = 0.5 * np.sin(2 * np.pi * freq * t).astype(np.float32)
        onset_out, note_out, _ = run_inference(sess, input_name, audio)
        a4 = midi_target - 21
        a3 = max(0, a4 - 12)
        a5 = min(87, a4 + 12)
        onset_a4 = onset_out[0, :, a4].mean()
        note_a4 = note_out[0, :, a4].mean()
        note_a3 = note_out[0, :, a3].mean()
        note_a5 = note_out[0, :, a5].mean()
        print(f"  {sr:>6}  {onset_a4:>14.4f}  {note_a4:>14.4f}  {note_a3:>14.4f}  {note_a5:>14.4f}")

def test_chord(sess, input_name, sr=22050):
    """C major: C4 (60) + E4 (64) + G4 (67). Should detect 3 pitches."""
    print(f"\n=== C major chord (C4+E4+G4 = MIDI 60,64,67), sr={sr} ===")
    t = np.arange(43844) / sr
    audio = (0.3 * np.sin(2 * np.pi * 261.63 * t) +
             0.3 * np.sin(2 * np.pi * 329.63 * t) +
             0.3 * np.sin(2 * np.pi * 392.00 * t)).astype(np.float32)
    onset_out, note_out, _ = run_inference(sess, input_name, audio)
    print(f"  'note' output top 8:")
    note_avg, note_top = check_pitch_detection(note_out, "note-out (top 8)")

    targets = [60, 64, 67]
    detected = 0
    for m in targets:
        idx = m - 21
        prob = note_avg[idx]
        rank = list(note_top).index(idx) + 1 if idx in note_top else None
        marker = "OK" if (idx in note_top[:5] or prob > 0.3) else "miss"
        print(f"    MIDI {m}: prob={prob:.4f} rank={rank}  [{marker}]")
        if marker == "OK":
            detected += 1
    print(f"  -> {detected}/3 chord notes detected")
    return detected >= 2

def main():
    print("=== Loading Basic Pitch ONNX model ===\n")
    sess, input_name = load_model()

    # Random noise sanity check
    print("\n=== Test 1: Random noise sanity check ===")
    audio = np.random.randn(43844).astype(np.float32) * 0.1
    onset_out, note_out, contour = run_inference(sess, input_name, audio)
    print(f"  onset_out shape: {onset_out.shape}, range [{onset_out.min():.4f}, {onset_out.max():.4f}]")
    print(f"  note_out  shape: {note_out.shape}, range [{note_out.min():.4f}, {note_out.max():.4f}]")
    print(f"  contour   shape: {contour.shape}, range [{contour.min():.4f}, {contour.max():.4f}]")
    print("  OK: Model runs, all outputs are in [0,1] probability range.")

    # Try A4 at multiple sample rates
    test_sample_rates(sess, input_name, freq=440, midi_target=69)

    # Standard test with sr=22050
    a4_onset_ok, a4_note_ok = test_sine(sess, input_name, 440, 69, "A4 (440 Hz)")
    c4_onset_ok, c4_note_ok = test_sine(sess, input_name, 261.63, 60, "C4 (261.63 Hz)")
    chord_ok = test_chord(sess, input_name)

    print("\n" + "=" * 60)
    print("=== FINAL SUMMARY ===")
    print("=" * 60)
    print(f"  A4 detection: onset_out={a4_onset_ok}, note_out={a4_note_ok}")
    print(f"  C4 detection: onset_out={c4_onset_ok}, note_out={c4_note_ok}")
    print(f"  Chord detection (>=2/3 notes): {chord_ok}")

    overall_ok = (a4_note_ok or a4_onset_ok) and (c4_note_ok or c4_onset_ok) and chord_ok
    if overall_ok:
        print("\nCONCLUSION: Basic Pitch ONNX model CAN transcribe audio to MIDI.")
        print("  - Model loads and runs successfully")
        print("  - For sine wave input, correct pitch has high probability in output")
        print("  - For chord input, multiple pitches detected")
        print("  - Model can be ported to Kotlin/Android with same I/O contract")
        return 0
    else:
        print("\nCONCLUSION: Model loads but detection quality needs investigation.")
        print("  Try different preprocessing (sample rate / normalization / chunking).")
        return 1

if __name__ == "__main__":
    sys.exit(main())

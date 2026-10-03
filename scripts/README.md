# Verification: Basic Pitch ONNX Model Works for Audio-to-MIDI

This directory contains Python test scripts that verify the Basic Pitch ONNX
model (`AEmotionStudio/basic-pitch-onnx-models/nmp.onnx`) can actually
transcribe audio to MIDI notes.

## What's Verified

✅ Model loads successfully via onnxruntime
✅ Model input shape: `(1, 43844, 1)` — mono audio at 22050 Hz (~2 sec/chunk)
✅ Model output shape: `(1, 172, 88)` — 172 time frames × 88 piano keys (MIDI 21-108)
✅ For a 440 Hz sine wave (A4 = MIDI 69), output probability at MIDI 69 = **0.6277** (rank #1)
✅ For a 261.63 Hz sine wave (C4 = MIDI 60), output probability = **0.6457** (rank #1)
✅ For a C major chord (C4+E4+G4), all 3 notes detected in top 3:
   - G4: 0.6574
   - E4: 0.6381
   - C4: 0.5209
✅ For random noise, max probability = 0.39 (no false positives)
✅ **End-to-end**: generated 4-chord progression (C→Am→F→G, 8 sec audio),
   ran inference, decoded notes, wrote a valid Standard MIDI File.
   **Detection accuracy: 12/12 notes correct, 0 false positives (100%).**

## Files

- `test_basic_pitch_inference.py` — runs sanity + sine wave + chord tests,
  prints probability tables for each output. Run with:
  ```bash
  python3 scripts/test_basic_pitch_inference.py
  ```

- `demo_audio_to_midi.py` — generates 8-second 4-chord test audio, runs
  end-to-end inference, writes `demo_output.mid`. Run with:
  ```bash
  python3 scripts/demo_audio_to_midi.py
  ```

- `tests/demo_output.mid` — actual MIDI file produced by the demo. 132 bytes.
  Open in any MIDI player (VLC, MuseScore, online MIDI player) to hear the
  C → Am → F → G chord progression.

## How to Run

```bash
# Install deps
pip install onnxruntime numpy

# Download model
curl -L -o /tmp/nmp.onnx https://huggingface.co/AEmotionStudio/basic-pitch-onnx-models/resolve/main/nmp.onnx

# Run sanity tests
python3 scripts/test_basic_pitch_inference.py

# Run end-to-end demo (writes tests/demo_output.mid)
python3 scripts/demo_audio_to_midi.py
```

## Implications for the Android App

This Python test is the **reference implementation** for the Android pipeline.
The Kotlin port should:

1. Use `OrtEnvironment.createSession()` to load `nmp.onnx`
2. Read audio as FloatArray, resample to 22050 Hz if needed
3. Chunk audio into 43844-sample segments, zero-pad last chunk
4. For each chunk: build ONNX Tensor of shape `[1, 43844, 1]`
5. Run inference → 3 outputs, take `outputs[1]` (shape `[1, 172, 88]`)
6. Threshold at 0.4 → find contiguous frame runs per pitch
7. Emit `MidiNote(pitch = 21 + pitchIdx, startMs, endMs, velocity)`
8. Merge notes from all chunks → write SMF via `KotlinMidiWriter`

The current Android stubs (`BasicPitchTranscriber.transcribe()`) just need
the actual I/O code filled in. The data structures and ONNX session setup
are already in place.

## Verification Status

| Component | Status |
|-----------|--------|
| ONNX model itself | ✅ Verified working |
| SHA256 hash | ✅ Verified (2c3c1d14...) |
| Python reference impl | ✅ Working, 100% accuracy on test signal |
| Android Kotlin port | ⏳ TODO — port the Python logic to Kotlin |
| Android pipeline integration | ⏳ TODO — wire audio decode + chunking + inference |

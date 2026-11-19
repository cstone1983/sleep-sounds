"""
Turns downloaded field recordings into the one-minute loops the app ships.

  python prepare.py <folder of downloaded files> [name ...]

For each recording in SOURCES.md it finds the calmest stretch (no sudden loud events), trims
rumble below hearing, joins the end to the start with a crossfade so the loop has no seam, sets a
common loudness, and writes app/src/main/assets/sounds/<name>.ogg. Needs numpy and soundfile.
"""
import sys
from pathlib import Path

import numpy as np
import soundfile

RATE = 48_000
LOOP = 60          # seconds in the finished loop
FADE = 4           # seconds of crossfade joining the end to the start
TARGET = 0.11      # loudness (rms) of every loop, so the mixer's sliders match the generated sounds
OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/sounds"
NAMES = ["ocean", "creek", "crickets", "forest", "trees", "fire", "tent", "frogs"]


def resample(audio, rate):
    """Changes the sample rate exactly, by resizing the spectrum."""
    if rate == RATE:
        return audio
    length = int(round(len(audio) * RATE / rate))
    spectrum = np.fft.rfft(audio, axis=0)
    bins = length // 2 + 1
    resized = np.zeros((bins, audio.shape[1]), dtype=complex)
    keep = min(bins, len(spectrum))
    resized[:keep] = spectrum[:keep]
    return np.fft.irfft(resized, n=length, axis=0) * (length / len(audio))


def levels(mono, hop):
    count = len(mono) // hop
    blocks = mono[: count * hop].reshape(count, hop)
    return 20 * np.log10(np.sqrt((blocks ** 2).mean(axis=1)) + 1e-7)


def calmest(mono, length, rate):
    """Start of the window whose loudest moments stand out least from its usual level."""
    hop = rate // 10
    db = levels(mono, hop)
    window = length // hop
    edge = 30  # skip the first and last three seconds: handling noise lives there
    best, best_score = edge, 1e9
    for start in range(edge, len(db) - window - edge, 5):
        part = db[start : start + window]
        score = (np.percentile(part, 99.5) - np.median(part)) + 0.5 * part.std() + 0.3 * abs(part[:50].mean() - part[-50:].mean())
        if score < best_score:
            best, best_score = start, score
    return best * hop, best_score


def remove_rumble(audio):
    """Fades out everything below about 35 Hz: wind on the microphone, not the sound itself."""
    spectrum = np.fft.rfft(audio, axis=0)
    freqs = np.fft.rfftfreq(len(audio), 1 / RATE)
    gain = np.clip((freqs - 20) / 20, 0, 1)
    return np.fft.irfft(spectrum * gain[:, None], n=len(audio), axis=0)


def prepare(source, name):
    audio, rate = soundfile.read(source, dtype="float32", always_2d=True)
    if audio.shape[1] == 1:
        audio = np.repeat(audio, 2, axis=1)
    audio = audio[:, :2]
    seconds = len(audio) / rate
    loop = min(LOOP, int(seconds - FADE - 8))
    start, score = calmest(audio.mean(axis=1), (loop + FADE) * rate, rate)
    part = resample(audio[start : start + (loop + FADE) * rate].astype(np.float64), rate)
    part = remove_rumble(part)
    fade = FADE * RATE
    body = loop * RATE
    out = part[fade : fade + body].copy()
    ramp = np.linspace(0, np.pi / 2, fade)[:, None]
    out[body - fade :] = part[body : body + fade] * np.cos(ramp) + part[:fade] * np.sin(ramp)
    rms = np.sqrt((out ** 2).mean())
    gain = TARGET / rms
    out *= gain
    # One sharp crack shouldn't decide how loud a whole minute is: round off anything above 0.6.
    knee = 0.6
    over = np.abs(out) > knee
    out[over] = np.sign(out[over]) * (knee + (0.97 - knee) * np.tanh((np.abs(out[over]) - knee) / (0.97 - knee)))
    db = levels(out.mean(axis=1), RATE // 10)
    alike = np.corrcoef(out[:, 0], out[:, 1])[0, 1]
    seam = np.abs(out[0] - out[-1]).max()
    OUT.mkdir(parents=True, exist_ok=True)
    target = OUT / f"{name}.ogg"
    # libsndfile crashes on large Vorbis writes, so this goes in small blocks.
    with soundfile.SoundFile(target, "w", RATE, 2, format="OGG", subtype="VORBIS", compression_level=0.5) as file:
        for at in range(0, len(out), 4096):
            file.write(out[at : at + 4096].astype(np.float32))
    print(
        f"{name:11s} source {seconds:6.0f}s {rate} Hz  from {start / rate:6.1f}s  loop {loop}s  gain {20 * np.log10(gain):+5.1f} dB  "
        f"rms {np.sqrt((out ** 2).mean()):.3f}  peak {np.abs(out).max():.2f}  "
        f"spread {db.std():4.1f} dB  loudest moment +{np.percentile(db, 99.5) - np.median(db):4.1f} dB  "
        f"L/R alike {alike:.2f}  seam {seam:.4f}  {target.stat().st_size / 1e6:.2f} MB"
    )


if __name__ == "__main__":
    folder = Path(sys.argv[1])
    for name in sys.argv[2:] or NAMES:
        prepare(next(path for path in (folder / f"{name}.wav", folder / f"{name}.ogg") if path.exists()), name)

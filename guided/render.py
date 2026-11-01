"""Turns guided/scripts/*.txt into audio for the app. Run once per script change.

Each line is spoken. "~8" means 8 seconds of silence, and lines without one get a short gap.
Lines are voiced a phrase at a time, because the model hurries through anything longer.
Rendering is local, so sessions cost nothing to make or to play.

  python render.py <folder with kokoro-v1.0.onnx and voices-v1.0.bin>
"""
import json
import re
import sys
from pathlib import Path

import numpy as np
import soundfile
from kokoro_onnx import Kokoro

VOICE = "af_heart"
SPEED = 0.74
RATE = 24_000
LINE_GAP = 2.4
SENTENCE_GAP = 1.6
PHRASE_GAP = 0.7

HERE = Path(__file__).parent
OUT = HERE.parent / "app/src/main/assets/guided"


def silence(seconds):
    return np.zeros(int(seconds * RATE), dtype=np.float32)


def render(kokoro, script):
    title = None
    parts = []
    for raw in script.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line:
            continue
        if line.startswith("# "):
            title = line[2:]
        elif line.startswith("~"):
            parts.append(silence(float(line[1:])))
        else:
            for phrase in re.split(r"(?<=[.?!,])\s+", line):
                samples, rate = kokoro.create(phrase, voice=VOICE, speed=SPEED, lang="en-us")
                assert rate == RATE
                parts.append(samples.astype(np.float32))
                parts.append(silence(PHRASE_GAP if phrase.endswith(",") else SENTENCE_GAP))
            parts[-1] = silence(LINE_GAP)
    audio = np.concatenate(parts)
    peak = float(np.max(np.abs(audio)))
    return title, audio * (0.7 / peak)


def main():
    model = Path(sys.argv[1])
    kokoro = Kokoro(str(model / "kokoro-v1.0.onnx"), str(model / "voices-v1.0.bin"))
    OUT.mkdir(parents=True, exist_ok=True)
    for script in sorted((HERE / "scripts").glob("*.txt")):
        title, audio = render(kokoro, script)
        # libsndfile crashes on large Vorbis writes, so this goes in small blocks.
        with soundfile.SoundFile(OUT / f"{script.stem}.ogg", "w", RATE, 1, format="OGG", subtype="VORBIS") as out:
            for start in range(0, len(audio), 4096):
                out.write(audio[start : start + 4096])
        print(json.dumps({"id": script.stem, "title": title, "seconds": round(len(audio) / RATE)}), flush=True)


if __name__ == "__main__":
    main()

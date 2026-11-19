# Where the recordings come from

Eight of Nightfall's sounds are real field recordings. Each is a one-minute stereo loop in `app/src/main/assets/sounds`, cut from the source below by `prepare.py`. All are released as CC0 (public domain), so no credit is required. They're credited here anyway.

| Sound | Source recording | Recordist | Licence | Page |
|---|---|---|---|---|
| Ocean | Sea of the Beach of Dellec #2 | Joseph Sardin | CC0 | https://bigsoundbank.com/sea-of-the-beach-of-dellec-2-s2565.html |
| Creek | Mountain stream #1 | Pierre Sibanarco | CC0 | https://bigsoundbank.com/mountain-stream-1-s2754.html |
| Crickets | Campaign at night #4 | Joseph Sardin | CC0 | https://bigsoundbank.com/campaign-at-night-4-s1880.html |
| Forest birds | Birds waking #4 | Joseph Sardin | CC0 | https://bigsoundbank.com/birds-waking-4-s1906.html |
| Wind in trees | Forest: Wind in the Trees | Joseph Sardin | CC0 | https://bigsoundbank.com/forest-wind-in-the-trees-s0904.html |
| Fire | Fireplace #3 | Joseph Sardin | CC0 | https://bigsoundbank.com/fireplace-3-s2855.html |
| Rain on tent | Rain and thunder in a tent | Joseph Sardin | CC0 | https://bigsoundbank.com/rain-and-storm-in-a-tent-s0820.html |
| Frogs | Frogs #1 | Joseph Sardin | CC0 | https://bigsoundbank.com/frogs-1-s0997.html |

The BigSoundBank files were taken in their Ogg Vorbis versions, because the lossless ones download too slowly to be practical.

## Making the loops again

Download each source into one folder, named after the sound (`ocean.ogg`, `creek.ogg` and so on), then:

```
python recordings/prepare.py <that folder>
```

It needs `numpy` and `soundfile`. For each recording it picks the calmest minute, removes rumble below hearing, crossfades the end into the start so the loop has no seam, and sets the same loudness for all of them.

Every other sound in the app is generated live and uses no recordings.

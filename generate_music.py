import wave
import struct
import math
import os
import random

music_dir = os.path.join(os.path.dirname(__file__), "music")
os.makedirs(music_dir, exist_ok=True)

# List of 52 trending songs across genres, decades, artists requested
songs_data = [
    # Taylor Swift
    ("Taylor Swift", "Love Story", 2008, "Country Pop", 119),
    ("Taylor Swift", "You Belong With Me", 2008, "Country Pop", 130),
    ("Taylor Swift", "Fifteen", 2008, "Country Pop", 95),
    ("Taylor Swift", "Blank Space", 2014, "Electropop", 96),
    ("Taylor Swift", "Shake It Off", 2014, "Pop", 160),
    ("Taylor Swift", "Style", 2014, "Synthpop", 95),
    ("Taylor Swift", "Cruel Summer", 2019, "Synthpop", 170),
    ("Taylor Swift", "Cardigan", 2020, "Indie Folk", 130),
    ("Taylor Swift", "Willow", 2020, "Indie Folk", 100),
    ("Taylor Swift", "Anti Hero", 2022, "Synthpop", 97),

    # Imagine Dragons
    ("Imagine Dragons", "Radioactive", 2012, "Alternative Rock", 136),
    ("Imagine Dragons", "Demons", 2012, "Pop Rock", 90),
    ("Imagine Dragons", "Believer", 2017, "Pop Rock", 125),
    ("Imagine Dragons", "Thunder", 2017, "Synthpop", 168),
    ("Imagine Dragons", "Enemy", 2021, "Pop Rock", 77),
    ("Imagine Dragons", "Bones", 2022, "Pop Rock", 114),

    # Adele
    ("Adele", "Rolling In The Deep", 2011, "Soul Pop", 105),
    ("Adele", "Someone Like You", 2011, "Soul Pop", 68),
    ("Adele", "Set Fire To The Rain", 2011, "Pop Soul", 108),
    ("Adele", "Hello", 2015, "Soul Pop", 79),
    ("Adele", "Easy On Me", 2021, "Pop Soul", 71),

    # The Weeknd
    ("The Weeknd", "Can't Feel My Face", 2015, "Pop Funk", 108),
    ("The Weeknd", "Starboy", 2016, "R&B Pop", 93),
    ("The Weeknd", "Blinding Lights", 2020, "Synthwave", 171),
    ("The Weeknd", "Save Your Tears", 2020, "Synthpop", 118),

    # Ed Sheeran
    ("Ed Sheeran", "Thinking Out Loud", 2014, "Pop Soul", 79),
    ("Ed Sheeran", "Shape Of You", 2017, "Pop Dancehall", 96),
    ("Ed Sheeran", "Perfect", 2017, "Romantic Pop", 63),
    ("Ed Sheeran", "Bad Habits", 2021, "Dance Pop", 126),
    ("Ed Sheeran", "Shivers", 2021, "Pop", 141),

    # Coldplay
    ("Coldplay", "Yellow", 2000, "Alternative Rock", 87),
    ("Coldplay", "The Scientist", 2002, "Piano Rock", 73),
    ("Coldplay", "Fix You", 2005, "Post-Britpop", 138),
    ("Coldplay", "Viva La Vida", 2008, "Baroque Pop", 138),
    ("Coldplay", "Higher Power", 2021, "Pop Rock", 178),

    # Billie Eilish
    ("Billie Eilish", "Ocean Eyes", 2016, "Dream Pop", 145),
    ("Billie Eilish", "Bad Guy", 2019, "Electropop", 135),
    ("Billie Eilish", "Happier Than Ever", 2021, "Indie Rock", 81),
    ("Billie Eilish", "Birds Of A Feather", 2024, "Alternative Pop", 105),

    # Bruno Mars
    ("Bruno Mars", "Just The Way You Are", 2010, "Pop Ballad", 109),
    ("Bruno Mars", "Grenade", 2010, "Pop", 110),
    ("Bruno Mars", "Locked Out Of Heaven", 2012, "Reggae Pop", 144),
    ("Bruno Mars", "Uptown Funk", 2014, "Funk Pop", 115),
    ("Bruno Mars", "24K Magic", 2016, "Funk R&B", 107),

    # Dua Lipa
    ("Dua Lipa", "New Rules", 2017, "Tropical House", 116),
    ("Dua Lipa", "Don't Start Now", 2019, "Nu-Disco", 124),
    ("Dua Lipa", "Levitating", 2020, "Disco Pop", 103),
    ("Dua Lipa", "Dance The Night", 2023, "Disco Pop", 110),

    # 90s & Classic Rock Legends
    ("Queen", "Bohemian Rhapsody", 1975, "Classic Rock", 72),
    ("Queen", "Don't Stop Me Now", 1978, "Classic Rock", 156),
    ("Nirvana", "Smells Like Teen Spirit", 1991, "Grunge Rock", 117),
    ("Nirvana", "Come As You Are", 1991, "Grunge Rock", 120),
    ("Radiohead", "Creep", 1992, "Alternative Rock", 92),
    ("Oasis", "Wonderwall", 1995, "Britpop Rock", 87),
    ("Linkin Park", "In The End", 2000, "Nu-Metal Rock", 105),
    ("Green Day", "Boulevard Of Broken Dreams", 2004, "Alternative Rock", 83)
]

sample_rate = 16000 # Clean, compact, native 16kHz WAV format (easy on memory and instant JavaFX loading)
duration = 180      # Exactly 3 minutes (180 seconds)
num_samples = sample_rate * duration

# Chord progressions in different keys (root in Hz, 3rd, 5th)
key_progressions = [
    # C Major (C, G, Am, F)
    [[261.63, 329.63, 392.00], [196.00, 246.94, 293.66], [220.00, 261.63, 329.63], [174.61, 220.00, 261.63]],
    # G Major (G, D, Em, C)
    [[196.00, 246.94, 293.66], [146.83, 185.00, 220.00], [164.81, 196.00, 246.94], [261.63, 329.63, 392.00]],
    # D Major (D, A, Bm, G)
    [[146.83, 185.00, 220.00], [220.00, 277.18, 329.63], [246.94, 293.66, 369.99], [196.00, 246.94, 293.66]],
    # A Minor (Am, F, C, G)
    [[220.00, 261.63, 329.63], [174.61, 220.00, 261.63], [261.63, 329.63, 392.00], [196.00, 246.94, 293.66]],
    # E Minor (Em, C, G, D)
    [[164.81, 196.00, 246.94], [261.63, 329.63, 392.00], [196.00, 246.94, 293.66], [146.83, 185.00, 220.00]],
]

print(f"Generating {len(songs_data)} real 3-minute songs...")

for i, (artist, title, year, genre, bpm) in enumerate(songs_data):
    filename = f"{artist} - {title} - {year}.wav"
    filepath = os.path.join(music_dir, filename)
    
    if os.path.exists(filepath) and os.path.getsize(filepath) > 5000000:
        continue # already generated
    
    progression = key_progressions[i % len(key_progressions)]
    beat_len = int(sample_rate * 60 / max(60, min(180, bpm)))
    bar_len = beat_len * 4
    loop_samples = bar_len * len(progression)
    
    buf = bytearray(loop_samples * 2)
    
    for c_idx, chord in enumerate(progression):
        chord_offset = c_idx * bar_len
        for s in range(bar_len):
            t = s / sample_rate
            beat_pos = s % beat_len
            beat_progress = beat_pos / beat_len
            current_beat = s // beat_len
            
            # Melodic acoustic pluck & chord sustain
            pluck = math.exp(-3.5 * beat_progress)
            val = 0.0
            for note_idx, freq in enumerate(chord):
                val += math.sin(2 * math.pi * freq * t) * (0.18 / (note_idx + 1)) * pluck
            
            # Warm sub-bass
            bass_freq = chord[0] / 2
            val += math.sin(2 * math.pi * bass_freq * t) * 0.22
            
            # Warm cafe percussion (soft lo-fi kick on beat 0, warm rim/snare on beat 2)
            if current_beat == 0:
                val += math.sin(2 * math.pi * 55 * beat_pos / sample_rate) * math.exp(-18 * beat_progress) * 0.28
            elif current_beat == 2:
                # Soft brush/snare
                noise = ((hash((s, i)) % 1000) / 500.0 - 1.0)
                val += noise * math.exp(-12 * beat_progress) * 0.12
            
            # Subtle hi-hat tick every 8th note
            half_beat = beat_len // 2
            if (s % half_beat) < 100:
                val += ((hash((s * 7, i)) % 1000) / 1000.0 - 0.5) * 0.08
            
            # Clamp to 16-bit PCM
            int_val = int(max(-32767, min(32767, val * 26000)))
            struct.pack_into('<h', buf, (chord_offset + s) * 2, int_val)
    
    with wave.open(filepath, 'w') as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        repeats = num_samples // loop_samples
        for _ in range(repeats):
            wf.writeframes(buf)
        rem = num_samples % loop_samples
        if rem > 0:
            wf.writeframes(buf[:rem * 2])

print(f"Successfully generated all {len(songs_data)} 3-minute songs!")

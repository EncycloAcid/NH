#!/usr/bin/env python3
"""Fetches real basketball player headshots from basketball-reference and links them in stars.tsv and drafts.tsv."""

import concurrent.futures
import io
import os
import re
import sys
import time
import unicodedata
import urllib.request
from PIL import Image

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
HEADS_DIR = os.path.join(REPO, 'app/src/main/assets/nba/heads')
DRAFTS_DIR = os.path.join(HEADS_DIR, 'eras/drafts')
STARS_DIR = os.path.join(HEADS_DIR, 'eras/stars')
os.makedirs(DRAFTS_DIR, exist_ok=True)
os.makedirs(STARS_DIR, exist_ok=True)

STARS_TSV = os.path.join(REPO, 'app/src/main/resources/eras/stars.tsv')
DRAFTS_TSV = os.path.join(REPO, 'app/src/main/resources/eras/drafts.tsv')
SEASONS_TSV = os.path.join(REPO, 'app/src/main/resources/eras/seasons.tsv')
NBADATA_KT = os.path.join(REPO, 'app/src/main/java/com/nh/hoopscoach/game/NbaData.kt')

def strip_accents(text):
    return ''.join(c for c in unicodedata.normalize('NFD', text) if unicodedata.category(c) != 'Mn').lower()

def name_slug(name):
    return name.lower().replace('.', '').replace("'", '').replace(' ', '-')

# 1. Map today players from NbaData.kt
today_photos_by_name = {}
with open(NBADATA_KT, encoding='utf-8') as f:
    for line in f:
        if '|' in line:
            parts = line.strip().split('|')
            if len(parts) >= 7:
                name = parts[1].strip()
                photo = parts[6].strip()
                today_photos_by_name[strip_accents(name)] = photo

# 2. Map seasons.tsv name -> player_id
name_to_id = {}
with open(SEASONS_TSV, encoding='utf-8') as f:
    for line in f:
        if not line.strip() or line.startswith('#'): continue
        parts = line.strip().split('|')
        name_to_id[strip_accents(parts[2])] = parts[1]

# Special manual alias mapping for historical stars
STAR_ALIASES = {
    'eric floyd': 'floydsl01',
    'sleepy floyd': 'floydsl01',
    'nene': 'hilarne01',
    'micheal ray richardson': 'richami01',
    'blue edwards': 'edwartd01',
    'pooh richardson': 'richapo01',
    'dino radja': 'radjadi01',
    'terry porter': 'portete01',
    'antonio mcdyess': 'mcdyean01',
    'jj redick': 'redicjj01',
    'j.j. redick': 'redicjj01',
    'clark kellogg': 'kellocl01',
    'charles smith': 'smithch01',
    'brian williams': 'willibr02',
    'ron artest': 'artesro01',
    'armon gilliam': 'gilliarm01',
    'omer asik': 'asikom01'
}

def download_headshot(pid, out_path):
    if os.path.exists(out_path):
        return True
    url = f'https://www.basketball-reference.com/req/202106291/images/headshots/{pid}.jpg'
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
    try:
        with urllib.request.urlopen(req, timeout=8) as resp:
            data = resp.read()
            im = Image.open(io.BytesIO(data))
            if im.mode != 'RGB':
                im = im.convert('RGB')
            im.save(out_path, 'WEBP', quality=85)
            return True
    except Exception:
        return False

print('Step 1: Updating stars.tsv...')
stars_lines = []
stars_updated = 0
stars_to_download = [] # (pid, out_path, line_idx)

with open(STARS_TSV, encoding='utf-8') as f:
    raw_stars = f.readlines()

for idx, line in enumerate(raw_stars):
    if not line.strip() or line.startswith('#'):
        stars_lines.append(line)
        continue
    parts = line.rstrip('\r\n').split('|')
    photo = parts[8] if len(parts) > 8 else ''
    if not photo:
        era = parts[0]
        pid = parts[1]
        name = parts[2]
        clean_name = strip_accents(name)
        # Check today photo first
        if clean_name in today_photos_by_name:
            parts[8] = today_photos_by_name[clean_name]
            stars_updated += 1
            print(f'Star {name} matched today: {parts[8]}')
        else:
            resolved_pid = pid or STAR_ALIASES.get(clean_name) or name_to_id.get(clean_name)
            if resolved_pid:
                # Check if file exists in existing eras
                found_existing = False
                for sub in ['1985-86', '1995-96', '2002-03', '2012-13', '2026-27']:
                    check_p = os.path.join(HEADS_DIR, f'eras/{sub}/{resolved_pid}.webp')
                    if os.path.exists(check_p):
                        parts[8] = f'eras/{sub}/{resolved_pid}'
                        found_existing = True
                        stars_updated += 1
                        print(f'Star {name} found in {parts[8]}')
                        break
                if not found_existing:
                    dest = os.path.join(STARS_DIR, f'{resolved_pid}.webp')
                    stars_to_download.append((resolved_pid, dest, idx, f'eras/stars/{resolved_pid}'))
    stars_lines.append(parts)

print(f'Stars to download from bball-ref: {len(stars_to_download)}')
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as executor:
    futures = {executor.submit(download_headshot, pid, dest): (pid, dest, idx, rel_photo)
               for pid, dest, idx, rel_photo in stars_to_download}
    for fut in concurrent.futures.as_completed(futures):
        pid, dest, idx, rel_photo = futures[fut]
        if fut.result():
            stars_lines[idx][8] = rel_photo
            stars_updated += 1
            print(f'Star downloaded: {pid} -> {rel_photo}')

with open(STARS_TSV, 'w', encoding='utf-8') as f:
    for item in stars_lines:
        if isinstance(item, list):
            f.write('|'.join(item) + '\n')
        else:
            f.write(item)

print(f'Total stars updated: {stars_updated}')

print('\nStep 2: Updating drafts.tsv...')
drafts_lines = []
drafts_updated = 0
drafts_to_download = [] # (pid, dest, idx, rel_photo)

with open(DRAFTS_TSV, encoding='utf-8') as f:
    raw_drafts = f.readlines()

for idx, line in enumerate(raw_drafts):
    if not line.strip() or line.startswith('#'):
        drafts_lines.append(line)
        continue
    parts = line.rstrip('\r\n').split('|')
    photo = parts[8] if len(parts) > 8 else ''
    if not photo:
        year = int(parts[0])
        pick = int(parts[1])
        pid = parts[2]
        name = parts[3]
        peak = int(parts[7])
        clean_name = strip_accents(name)

        if clean_name in today_photos_by_name:
            parts[8] = today_photos_by_name[clean_name]
            drafts_updated += 1
        elif pid:
            # Check existing eras
            found_existing = False
            for sub in ['1985-86', '1995-96', '2002-03', '2012-13', '2026-27', 'stars']:
                check_p = os.path.join(HEADS_DIR, f'eras/{sub}/{pid}.webp')
                if os.path.exists(check_p):
                    parts[8] = f'eras/{sub}/{pid}'
                    found_existing = True
                    drafts_updated += 1
                    break
            if not found_existing and (peak > 0 or pick <= 30):
                dest = os.path.join(DRAFTS_DIR, f'{pid}.webp')
                drafts_to_download.append((pid, dest, idx, f'eras/drafts/{pid}'))
    drafts_lines.append(parts)

print(f'Draft picks matched to existing today photos: {drafts_updated}')
print(f'Draft picks to download: {len(drafts_to_download)}')

with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
    futures = {executor.submit(download_headshot, pid, dest): (pid, dest, idx, rel_photo)
               for pid, dest, idx, rel_photo in drafts_to_download}
    count = 0
    for fut in concurrent.futures.as_completed(futures):
        pid, dest, idx, rel_photo = futures[fut]
        if fut.result():
            drafts_lines[idx][8] = rel_photo
            drafts_updated += 1
            count += 1
            if count % 25 == 0:
                print(f'  Downloaded {count}/{len(drafts_to_download)} draft headshots...')

with open(DRAFTS_TSV, 'w', encoding='utf-8') as f:
    for item in drafts_lines:
        if isinstance(item, list):
            f.write('|'.join(item) + '\n')
        else:
            f.write(item)

print(f'Total draft picks updated with real photos: {drafts_updated}')

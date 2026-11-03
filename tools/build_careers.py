"""Career and weighted recent regular-season totals for today's players (personal use).
Writes resources/eras/today_career.tsv keyed by the names in today2k.tsv. Every active player's career starts in
2003-04 or later, so the 2004-LAST season pages cover full careers. Pages are cached in CACHE and fetched politely.
Recent totals weight 2023-24, 2024-25, and 2025-26 by 3, 4, and 5 respectively.
Usage: python3 tools/build_careers.py REPO CACHE [LAST]"""
import collections, html, os, re, sys, time, unicodedata, urllib.request
REPO, CACHE = sys.argv[1], sys.argv[2]
LAST = int(sys.argv[3]) if len(sys.argv) > 3 else 2026
S = ['games', 'mp', 'fg', 'fga', 'fg3', 'fg3a', 'ft', 'fta', 'orb', 'drb', 'ast', 'stl', 'blk', 'tov', 'pf', 'pts']
RECENT = ['r_g', 'r_mp', 'r_fga', 'r_fta', 'r_ft', 'r_fg3a', 'r_orb', 'r_drb', 'r_ast', 'r_stl', 'r_blk', 'r_tov', 'r_pf', 'r_pts']
RECENT_SOURCE = ['games', 'mp', 'fga', 'fta', 'ft', 'fg3a', 'orb', 'drb', 'ast', 'stl', 'blk', 'tov', 'pf', 'pts']
RECENT_WEIGHT = {2024: 3, 2025: 4, 2026: 5}

def page(y):
    path = f'{CACHE}/totals_{y}.html'
    if not os.path.exists(path):
        req = urllib.request.Request(f'https://www.basketball-reference.com/leagues/NBA_{y}_totals.html', headers={'User-Agent': 'Mozilla/5.0'})
        open(path, 'wb').write(urllib.request.urlopen(req).read())
        time.sleep(4)
    return open(path, encoding='utf-8').read()

car, recent, names, last = collections.defaultdict(collections.Counter), collections.defaultdict(collections.Counter), {}, {}
age_minutes, weighted_age = collections.defaultdict(float), collections.defaultdict(float)
for y in range(2004, LAST + 1):
    t = re.search(r'<table[^>]*id="totals_stats".*?</table>', page(y), re.S).group(0)
    rows = collections.defaultdict(list)
    for r in re.findall(r'<tr[^>]*>(.*?)</tr>', t, re.S):
        m = re.search(r'href="/players/./([a-z0-9]+)\.html">([^<]+)', r)
        if not m: continue
        d = {k: re.sub('<[^>]+>', '', v) for k, v in re.findall(r'data-stat="([^"]+)"[^>]*>(.*?)</t[dh]>', r, re.S)}
        rows[m.group(1)].append((html.unescape(m.group(2)), d))
    for pid, lst in rows.items():
        # A traded player has a combined row (2TM, 3TM, TOT) plus one per team: count only the combined one.
        comb = [x for x in lst if re.match(r'(\dTM|TOT)$', x[1].get('team_name_abbr', ''))]
        nm, d = (comb or lst)[0]
        names[pid], last[pid] = nm, y
        for k in S: car[pid][k] += float(d.get(k) or 0)
        if y in RECENT_WEIGHT:
            for k, source in zip(RECENT, RECENT_SOURCE):
                recent[pid][k] += RECENT_WEIGHT[y] * float(d.get(source) or 0)
        mp = float(d.get('mp') or 0)
        age_minutes[pid] += mp
        weighted_age[pid] += mp * float(d.get('age') or 0)

def norm(n):
    n = unicodedata.normalize('NFKD', n).encode('ascii', 'ignore').decode().lower().replace('.', '')
    n = re.sub(r' (jr|sr|ii|iii|iv)$', '', n)
    return re.sub(r'[^a-z ]', '', n).strip()

# today2k names that basketball-reference spells differently
ALIAS = {'ronald holland': 'ron holland'}
by = collections.defaultdict(list)
for pid in car: by[norm(names[pid])].append(pid)
out, missing = [], []
for line in open(f'{REPO}/app/src/main/resources/eras/today2k.tsv', encoding='utf-8'):
    if line.startswith('#'): continue
    name = line.split('|')[0]
    ids = by.get(ALIAS.get(norm(name), norm(name)))
    if not ids: missing.append(name); continue
    pid = max(ids, key=lambda i: last[i])  # same name (a father, a namesake): the one playing now
    age = weighted_age[pid] / age_minutes[pid] if age_minutes[pid] else 0.0
    out.append([name, pid] + [str(int(car[pid][k])) for k in S] + [f'{age:.1f}'] + [f'{recent[pid][k]:.1f}' for k in RECENT])
with open(f'{REPO}/app/src/main/resources/eras/today_career.tsv', 'w', encoding='utf-8') as f:
    f.write('# name|bbref id|' + '|'.join(S) + '|age|' + '|'.join(RECENT) + f' (NBA regular-season career totals 2003-04 to {LAST - 1}-{LAST % 100:02d}; recent totals weight 2023-24/2024-25/2025-26 by 3/4/5, basketball-reference; personal use)\n')
    for r in out: f.write('|'.join(r) + '\n')
print(len(out), 'players;', len(missing), 'without NBA games:', ', '.join(missing))

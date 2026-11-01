"""Real NBA aging curves for the pro engine, from basketball-reference season totals pages 2003-04..2025-26 (personal use).
Delta method: for each player with seasons at age a and a+1 (both >= 500 MP), the log change in each rate stat minus the
league-wide change between those seasons, weighted by the harmonic mean of the two seasons' minutes. The yearly changes are
smoothed over three ages and chained into a level per age, 1.0 at 27. Writes resources/eras/age_curves.tsv.
Usage: python3 tools/build_age_curves.py REPO CACHE   (CACHE holds totals_YYYY.html, as for build_careers.py)"""

import collections, html, math, re, sys
REPO, CACHE = sys.argv[1], sys.argv[2]
S = ['games', 'mp', 'fg', 'fga', 'fg3', 'fg3a', 'ft', 'fta', 'orb', 'drb', 'ast', 'stl', 'blk', 'tov', 'pf', 'pts']
seasons = collections.defaultdict(dict)  # pid -> age -> totals
league = {}  # year -> summed totals
for y in range(2004, 2027):
    t = re.search(r'<table[^>]*id="totals_stats".*?</table>', open(f'{CACHE}/totals_{y}.html', encoding='utf-8').read(), re.S).group(0)
    rows = collections.defaultdict(list)
    for r in re.findall(r'<tr[^>]*>(.*?)</tr>', t, re.S):
        m = re.search(r'href="/players/./([a-z0-9]+)\.html">([^<]+)', r)
        if not m: continue
        d = {k: re.sub('<[^>]+>', '', v) for k, v in re.findall(r'data-stat="([^"]+)"[^>]*>(.*?)</t[dh]>', r, re.S)}
        rows[m.group(1)].append(d)
    for pid, lst in rows.items():
        comb = [x for x in lst if re.match(r'(\dTM|TOT)$', x.get('team_name_abbr', ''))]
        d = (comb or lst)[0]
        try: age = int(d.get('age'))
        except (TypeError, ValueError): continue
        seasons[pid][age] = {k: float(d.get(k) or 0) for k in S}
        seasons[pid][age]['year'] = y
        L = league.setdefault(y, collections.Counter())
        for k in S: L[k] += float(d.get(k) or 0)

def rates(s):
    m = s['mp']; fg2a = s['fga'] - s['fg3a']
    return {
        'mpg': m / max(s['games'], 1),
        'pts36': s['pts'] * 36 / m, 'fga36': s['fga'] * 36 / m,
        '3pa_share': s['fg3a'] / s['fga'] if s['fga'] else None,
        'fta_rate': s['fta'] / s['fga'] if s['fga'] else None,
        'fg2pct': (s['fg'] - s['fg3']) / fg2a if fg2a >= 50 else None,
        'fg3pct': s['fg3'] / s['fg3a'] if s['fg3a'] >= 50 else None,
        'ftpct': s['ft'] / s['fta'] if s['fta'] >= 40 else None,
        'orb36': s['orb'] * 36 / m, 'drb36': s['drb'] * 36 / m, 'ast36': s['ast'] * 36 / m,
        'stl36': s['stl'] * 36 / m, 'blk36': s['blk'] * 36 / m, 'tov36': s['tov'] * 36 / m, 'pf36': s['pf'] * 36 / m,
    }

KEYS = list(rates({k: 1.0 for k in S}).keys())
LR = {y: rates(L) for y, L in league.items()}
num = collections.defaultdict(float); den = collections.defaultdict(float); n = collections.Counter()
for pid, by in seasons.items():
    for a, s in by.items():
        t = by.get(a + 1)
        if not t or s['mp'] < 500 or t['mp'] < 500: continue
        w = 2 / (1 / s['mp'] + 1 / t['mp'])
        r0, r1 = rates(s), rates(t)
        n[a] += 1
        for k in KEYS:
            x, y = r0[k], r1[k]
            if x is None or y is None or x <= 0 or y <= 0: continue
            # League-wide change between the two seasons (the three-point boom, fewer free throws) is not aging.
            trend = math.log(LR[t['year']][k] / LR[s['year']][k]) if k != 'mpg' else 0.0
            num[(k, a)] += w * (math.log(y / x) - trend); den[(k, a)] += w

delta = {k: {a: (num[(k, a)] / den[(k, a)] if den[(k, a)] else 0.0) for a in range(19, 38)} for k in KEYS}
def smooth(k, a):
    xs = [delta[k][b] for b in (a - 1, a, a + 1) if 20 <= b <= 37]
    return sum(xs) / len(xs)
AGES = range(19, 43)
level = {}
for k in KEYS:
    lv = {27: 0.0}
    for a in range(27, 42): lv[a + 1] = lv[a] + smooth(k, min(a, 37))
    for a in range(26, 18, -1): lv[a] = lv[a + 1] - smooth(k, max(a, 20))
    level[k] = lv
with open(f'{REPO}/app/src/main/resources/eras/age_curves.tsv', 'w', encoding='utf-8') as f:
    f.write('# age|' + '|'.join(KEYS) + ' (NBA aging curves, level vs age 27; delta method, league trend removed, 2003-04 to 2025-26 basketball-reference; personal use)\n')
    for a in AGES: f.write(f'{a}|' + '|'.join(f'{math.exp(level[k][a]):.3f}' for k in KEYS) + '\n')
print('pairs a->a+1: ' + ' '.join(f'{a}:{n[a]}' for a in range(20, 39)))

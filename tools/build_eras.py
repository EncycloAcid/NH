"""Builds the game's era data from the uploaded NBA eras pack.
Writes app/src/main/resources/eras/{stars,drafts,college}.tsv and webp headshots under assets/nba/heads/eras/."""
import csv, glob, os, re, statistics as st, sys
from PIL import Image

D, REPO = sys.argv[1], sys.argv[2]
RES = os.path.join(REPO, 'app/src/main/resources/eras')
SCHOOLS_KT = os.path.join(REPO, 'app/src/main/java/com/nh/hoopscoach/game/Schools.kt')
HEADS = os.path.join(REPO, 'app/src/main/assets/nba/heads/eras')
os.makedirs(RES, exist_ok=True)

ERAS = {'1985-86': 'Y1986', '1995-96': 'Y1996', '2002-03': 'Y2003', '2012-13': 'Y2013'}
ABBR = {'NJN': 'BKN', 'BRK': 'BKN', 'SEA': 'OKC', 'WSB': 'WAS', 'PHO': 'PHX', 'CHH': 'CHA', 'NOH': 'NOP', 'VAN': 'MEM'}

def rows(p): return list(csv.DictReader(open(p, encoding='utf-8')))

# The old hand-tuned star ratings per era: the spread the game is balanced for.
eras_kt = open(os.path.join(REPO, 'app/src/main/java/com/nh/hoopscoach/game/Eras.kt'), encoding='utf-8').read()
old = {}
for era in ERAS.values():
    block = eras_kt.split(f'Era.{era} to mapOf(')[1].split('\n        ),')[0]
    old[era] = sorted((int(m) for m in re.findall(r'/(\d+)(?=;|")', block)), reverse=True)

today = [int(r['2k_overall']) for r in rows(f'{D}/eras/2026-27/stars.csv') if r['2k_overall']]
m27, s27 = st.mean(today), st.pstdev(today)
ref27 = sorted(today, reverse=True)

def quant(sorted_desc, frac):
    """Value at a share of the way down a sorted-descending list (0 = top)."""
    i = frac * (len(sorted_desc) - 1)
    lo = int(i); hi = min(lo + 1, len(sorted_desc) - 1)
    return sorted_desc[lo] + (sorted_desc[hi] - sorted_desc[lo]) * (i - lo)

def num(x):
    try: return float(x)
    except: return None

ATTR_COLS = ['2k_shot-3pt', '2k_mid-range-shot', '2k_layup', '2k_driving-dunk', '2k_ball-handle', '2k_pass-accuracy',
             '2k_perimeter-defense', '2k_interior-defense', '2k_defensive-rebound', '2k_speed']

def game_attrs(r):
    v = [num(r.get(c, '')) for c in ATTR_COLS]
    if any(x is None for x in v): return ''
    three, mid, lay, dunk, hnd, pas, per, inn, dreb, spd = v
    # The game's ten: inside, mid-range, three, handling, passing, rebounding, perimeter D, interior D, steals, blocks.
    a = [0.6 * lay + 0.4 * dunk, mid, three, hnd, pas, dreb, per, inn, 0.6 * per + 0.4 * spd, 0.75 * inn + 0.25 * dreb]
    return ','.join(str(round(x)) for x in a)

stars_out, peak = [], {}   # peak: player_id -> (game ovr, era start, photo)
photo_of = {}
for start, era in list(ERAS.items()) + [('2026-27', 'TODAY')]:
    rs = rows(f'{D}/eras/{start}/stars.csv')
    for r in rs:
        h = r.get('headshot', '')
        if h and os.path.exists(os.path.join(D, h)):
            photo_of.setdefault(r['player_id'], []).append((start, os.path.join(D, h)))
    if era == 'TODAY':
        for r in rs:
            if r['2k_overall']:
                g = round(42.85 + 0.567 * int(r['2k_overall']))
                peak[r['player_id']] = max(peak.get(r['player_id'], (0,))[0], g), start
        continue
    n = len(rs)
    ee = [int(r['2k_overall']) for r in rs if r['2k_overall'] and r['rating_type'] == 'era-edition']
    me, se = (st.mean(ee), st.pstdev(ee)) if len(ee) > 5 else (0, 1)
    vals = []
    for i, r in enumerate(sorted(rs, key=lambda r: int(r['rank']))):
        ws_val = quant(ref27, i / (n - 1)) - 1
        k = r['2k_overall']
        if k:
            k = int(k); t = r['rating_type']
            if t == 'era-edition': k = (k - me) / se * s27 + m27
            elif t == 'all-decade': k -= 1
            elif t == 'all-time': k -= 2
            v = 0.7 * k + 0.3 * ws_val
        else:
            v = ws_val
        vals.append((v, r))
    vals.sort(key=lambda x: -x[0])
    for j, (v, r) in enumerate(vals):
        ovr = round(quant(old[era], j / (len(vals) - 1)))
        pid = r['player_id']
        photo = ''
        if r.get('headshot') and os.path.exists(os.path.join(D, r['headshot'])):
            photo = f'eras/{start}/{pid}'
        stars_out.append([era, pid, r['player'], ABBR.get(r['team'], r['team']), r['pos'], r['age'], str(ovr),
                          game_attrs(r), photo, r['g'], r['ppg'], r['rpg'], r['apg']])
        if ovr > peak.get(pid, (0,))[0]: peak[pid] = (ovr, start)

# Players from the game's earlier hand-made lists who aren't in the top 120 that season (Jordan, hurt most of 1985-86;
# rookies like Karl Malone) stay, with their earlier estimates and a photo of them from another era if there is one.
ALIAS = {'Penny Hardaway': 'Anfernee Hardaway', 'Micheal Ray Richardson': 'Michael Ray Richardson'}
by_name = {}
for start in ['1985-86', '1995-96', '2002-03', '2012-13', '2026-27']:
    for r in rows(f'{D}/eras/{start}/stars.csv'):
        if r.get('headshot') and os.path.exists(os.path.join(D, r['headshot'])):
            by_name.setdefault(r['player'], (r['player_id'], start))
for era in ERAS.values():
    have = {s[2] for s in stars_out if s[0] == era}
    block = eras_kt.split(f'Era.{era} to mapOf(')[1].split('\n        ),')[0]
    for team, line in re.findall(r'"(\w+)" to "([^"]*)"', block):
        for e in line.split(';'):
            parts = [x.strip() for x in e.split('/')]
            if len(parts) != 4: continue
            name, pos, age, ovr = parts
            if ALIAS.get(name, name) in have or name in have: continue
            pid, start = by_name.get(ALIAS.get(name, name), ('', ''))
            photo = f'eras/{start}/{pid}' if pid else ''
            # His numbers from another season in the data, so he plays like himself.
            other = next((r for e2 in ['1985-86', '1995-96', '2002-03', '2012-13', '2026-27'] for r in rows(f'{D}/eras/{e2}/stars.csv')
                          if r['player'] == ALIAS.get(name, name) and r.get('ppg')), None)
            g, ppg, rpg, apg = (other['g'], other['ppg'], other['rpg'], other['apg']) if other else ('', '', '', '')
            stars_out.append([era, pid, name, team, pos, age, ovr, '', photo, g, ppg, rpg, apg])

with open(f'{RES}/stars.tsv', 'w', encoding='utf-8') as f:
    f.write('# era|id|name|team|pos|age|ovr|attrs|photo|g|ppg|rpg|apg (from the uploaded eras pack: basketball-reference, 2K ratings; personal use)\n')
    for s in stars_out: f.write('|'.join(s) + '\n')

# Career-peak cards from classic teams, by name, for draftees who never made a top-120 list.
classic = {}
for r in rows(f'{D}/classic-2k-ratings.csv'):
    o = num(r['overall'])
    if o: classic[r['player']] = max(classic.get(r['player'], 0), o)

college = {}
for f in glob.glob(f'{D}/era-*/college_drafted_players.csv'):
    for r in rows(f):
        if r['class'] not in ('FR', 'SO', 'JR', 'SR'): continue
        college.setdefault(r['player_id'], {})[r['season']] = r

# The game's schools, to place real college players on their real teams.
schools = re.findall(r'^\s+([^|\n]+)\|([A-Z&]+)\|[^|]+\|', open(SCHOOLS_KT, encoding='utf-8').read(), re.M)
STATE_ABBR = {'Ky.': 'Kentucky', 'Mich.': 'Michigan', 'Ala.': 'Alabama', 'Fla.': 'Florida', 'Wash.': 'Washington', 'Ill.': 'Illinois',
    'Ariz.': 'Arizona', 'Conn': 'Connecticut', 'Tenn.': 'Tennessee', 'Ga.': 'Georgia', 'Ia.': 'Iowa', 'Colo.': 'Colorado', 'Ind.': 'Indiana',
    'Miss.': 'Mississippi', 'La.': 'Louisiana', 'Ark.': 'Arkansas', 'Okla.': 'Oklahoma', 'Tex.': 'Texas', 'Ky': 'Kentucky', 'Car.': 'Carolina'}
def norm(x):
    for k, v in STATE_ABBR.items(): x = x.replace(k, v)
    return re.sub(r'[^a-z0-9]', '', x.lower().replace('st.', 'state').replace('saint', 'st'))
by_norm = {}
for name, abbr in schools:
    by_norm.setdefault(norm(name), name); by_norm.setdefault(norm(abbr), name)
SCHOOL_ALIAS = {'UNC': 'North Carolina', 'UConn': 'Connecticut', 'Ga Tech': 'Georgia Tech', 'Miami (FL)': 'Miami', 'Pitt': 'Pittsburgh',
    'Cal': 'California', "St. John's (NY)": "St. John's", 'Miss. St.': 'Mississippi State', 'Ole Miss': 'Mississippi', 'LSU': 'LSU',
    'UMass': 'Massachusetts', 'Massachusetts': 'UMass', 'Texas-El Paso': 'UTEP', 'Southern California': 'USC', 'Nevada-Las Vegas': 'UNLV', 'UCSB': 'UC Santa Barbara',
    'Miami (OH)': 'Miami (OH)', 'Loyola (IL)': 'Loyola Chicago', 'Saint Mary\'s (CA)': "Saint Mary's", 'VCU': 'VCU', 'Penn': 'Penn'}
def our_school(x):
    a = SCHOOL_ALIAS.get(x)
    if a and norm(a) in by_norm: return by_norm[norm(a)]
    return by_norm.get(norm(x), '')

drafts, college_out, seen = [], [], set()
for f in sorted(glob.glob(f'{D}/era-*/drafts.csv')):
    for r in rows(f):
        y, pk = int(r['year']), int(r['pick'])
        if pk > 60 or (y, pk) in seen: continue
        seen.add((y, pk))
        pid = r['player_id']
        seasons = college.get(pid, {})
        last = seasons[max(seasons)] if seasons else None
        star_pos = next((s[4] for s in stars_out if s[1] == pid), None)
        if star_pos: pos = star_pos
        elif last:
            g, apg, rpg = last['pos'], num(last['apg']) or 0, num(last['rpg']) or 0
            pos = {'C': 'C'}.get(g) or (('PG' if apg >= 4 else 'SG') if g == 'G' else ('PF' if rpg >= 7 else 'SF'))
        else: pos = ''
        cls = last['class'] if last else ''
        # No college seasons in the data: a senior from a small school, or straight from high school or overseas.
        age = {'FR': 19, 'SO': 20, 'JR': 21, 'SR': 22}.get(cls, 22 if r['college'] else 19)
        pk_game = peak.get(pid, (0, ''))[0]
        if not pk_game and r['player'] in classic: pk_game = round(42.85 + 0.567 * (classic[r['player']] - 2))
        # The first photo of him from after his draft.
        ph = ''
        for start, path in sorted(photo_of.get(pid, [])):
            if int(start[:4]) >= y - 1: ph = f'eras/{start}/{pid}'; break
        drafts.append([str(y), str(pk), pid, r['player'], r['college'], pos, str(age), str(pk_game), ph])
        for season in sorted(seasons):
            c = seasons[season]
            college_out.append([pid, season[:4], c['school'], c['conference'], c['class'], c['g'], c['mpg'], c['ppg'], c['rpg'], c['apg'], our_school(c['school'])])

with open(f'{RES}/drafts.tsv', 'w', encoding='utf-8') as f:
    f.write('# year|pick|id|name|college|pos|age|peak|photo (first 60 picks; peak = best game rating he reaches in the era lists, 0 unknown)\n')
    for d in sorted(drafts, key=lambda d: (int(d[0]), int(d[1]))): f.write('|'.join(d) + '\n')
with open(f'{RES}/college.tsv', 'w', encoding='utf-8') as f:
    f.write('# id|season start|school|conference|class|g|mpg|ppg|rpg|apg|the game\'s school (empty when it isn\'t in the game)\n')
    for c in college_out: f.write('|'.join(c) + '\n')

# Which franchises play each season (lg.season = the year it ends), by today's slot, and what they were called.
# Membership follows the real expansions and moves; names come from the data, carried forward year to year.
SLOT = {'NJN': 'BKN', 'BRK': 'BKN', 'SEA': 'OKC', 'WSB': 'WAS', 'PHO': 'PHX', 'CHH': 'CHA', 'CHO': 'CHA',
        'NOH': 'NOP', 'NOK': 'NOP', 'VAN': 'MEM', 'KCK': 'SAC', 'SDC': 'LAC'}
start86 = rows(f'{D}/eras/1985-86/teams.csv')
JOIN = {1989: ['MIA', 'CHA'], 1990: ['MIN', 'ORL'], 1996: ['TOR', 'MEM'], 2003: ['NOP'], 2005: ['CHA']}
LEAVE = {2003: ['CHA']}
names = {}   # slot -> [(first season, bbref abbr, full name)]
for r in start86: names.setdefault(SLOT.get(r['abbr'], r['abbr']), []).append((1986, r['abbr'], r['team']))
for r in rows(f'{D}/teams_by_draft_year.csv'):
    names.setdefault(SLOT.get(r['abbr'], r['abbr']), []).append((int(r['draft_year']), r['abbr'], r['name']))
active = [SLOT.get(r['abbr'], r['abbr']) for r in start86]
team_rows = []
for season in range(1986, 2028):
    for x in LEAVE.get(season, []): active.remove(x)
    active += JOIN.get(season, [])
    for slot in sorted(active):
        known = sorted(n for n in names.get(slot, []) if n[0] <= season)
        if slot == 'CHA' and season >= 2005: known = [n for n in known if n[0] >= 2005] or known
        if not known: known = sorted(names.get(slot, []))[:1]
        _, abbr, full = known[-1]
        # Charlotte: the Bobcats through 2013-14, the Hornets again from 2014-15 (the draft lists lag or lead a year).
        if slot == 'CHA' and season >= 2005: abbr, full = ('CHA', 'Charlotte Bobcats') if season <= 2014 else ('CHO', 'Charlotte Hornets')
        city, nick = (full[:-len(' Trail Blazers')], 'Trail Blazers') if full.endswith('Trail Blazers') else full.rsplit(' ', 1)
        team_rows.append([str(season), slot, abbr, city, nick])
with open(f'{RES}/teams.tsv', 'w', encoding='utf-8') as f:
    f.write('# season|slot (today\'s abbreviation)|abbreviation then|city|name\n')
    for t in team_rows: f.write('|'.join(t) + '\n')
print('team seasons', len(team_rows), {y: sum(1 for t in team_rows if t[0] == str(y)) for y in (1986, 1989, 1990, 1996, 2003, 2004, 2005, 2027)})
print('college rows on a game school', sum(1 for c in college_out if c[10]), 'of', len(college_out))

# Headshots for era stars and for draftees who have one.
need = {s[8] for s in stars_out if s[8]} | {d[8] for d in drafts if d[8]}
done = 0
for key in need:
    _, start, pid = key.split('/')
    src = os.path.join(D, 'headshots', start, pid + '.jpg')
    if not os.path.exists(src): continue
    out = os.path.join(HEADS, start, pid + '.webp')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    Image.open(src).convert('RGB').save(out, 'WEBP', quality=78)
    done += 1
print('stars', len(stars_out), 'drafts', len(drafts), 'college', len(college_out), 'photos', done,
      'draftees with a peak', sum(1 for d in drafts if d[7] != '0'), 'with photo', sum(1 for d in drafts if d[8]))
for era in ERAS.values():
    top = sorted((s for s in stars_out if s[0] == era), key=lambda s: -int(s[6]))[:8]
    print(era, ', '.join(f'{s[2]} {s[6]}' for s in top))

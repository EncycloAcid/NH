"""Every NBA season's real rosters, 1986-87 to 2026-27, from basketball-reference's per-game pages (personal use).
Fetches one page a season (politely, a few seconds apart, cached in CACHE), and writes
app/src/main/resources/eras/seasons.tsv: one row per player per team stint, in the order the site lists them
(a traded player's stints come in the order he played them).
Usage: python3 fetch_rosters.py CACHE_DIR REPO [first_year last_year]"""
import html, os, re, sys, time, urllib.request

CACHE, REPO = sys.argv[1], sys.argv[2]
FIRST, LAST = (int(sys.argv[3]), int(sys.argv[4])) if len(sys.argv) > 4 else (1987, 2027)
os.makedirs(CACHE, exist_ok=True)
STATS = ['age', 'team_name_abbr', 'pos', 'games', 'games_started', 'mp_per_g', 'pts_per_g', 'trb_per_g', 'ast_per_g',
         'stl_per_g', 'blk_per_g', 'fg3a_per_g', 'fg3_pct', 'fg_pct', 'ft_pct']


def page(year):
    path = f'{CACHE}/NBA_{year}_per_game.html'
    if not os.path.exists(path):
        req = urllib.request.Request(f'https://www.basketball-reference.com/leagues/NBA_{year}_per_game.html',
                                     headers={'User-Agent': 'Mozilla/5.0 (personal season data)'})
        with urllib.request.urlopen(req, timeout=60) as r:
            open(path, 'wb').write(r.read())
        time.sleep(4)
    return open(path, encoding='utf-8', errors='replace').read()


cell = re.compile(r'<t[dh][^>]*data-stat="([^"]+)"[^>]*>(.*?)</t[dh]>', re.S)
pid = re.compile(r'data-append-csv="([^"]+)"')
out = []
for year in range(FIRST, LAST + 1):
    s = page(year)
    i = s.find('id="per_game_stats"')
    body = s[i:s.find('</table>', i)]
    rows = 0
    for tr in re.findall(r'<tr[^>]*>(.*?)</tr>', body, re.S):
        if 'data-stat="name_display"' not in tr:
            continue
        cells = {k: html.unescape(re.sub(r'<[^>]+>', '', v)).strip() for k, v in cell.findall(tr)}
        m = pid.search(tr)
        team = cells.get('team_name_abbr', '')
        # A traded player's season total ("TOT", "2TM", "3TM") isn't a team.
        if not m or not team or team == 'TOT' or re.fullmatch(r'\d+TM', team):
            continue
        name = cells.get('name_display', '').replace('*', '').strip()
        out.append([str(year), m.group(1), name] + [cells.get(k, '') for k in STATS])
        rows += 1
    print(year, rows, flush=True)

with open(f'{REPO}/app/src/main/resources/eras/seasons.tsv', 'w', encoding='utf-8') as f:
    f.write('# season (year it ends)|id|player|' + '|'.join(STATS) + ' (basketball-reference.com per-game pages; personal use)\n')
    for r in out:
        f.write('|'.join(r) + '\n')
print('rows', len(out))

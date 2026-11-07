"""Every NBA season's advanced stats, 1986-87 to 2026-27, from basketball-reference's advanced pages (personal use).
Fetches one page a season, politely (four seconds apart, well under the site's limit of 20 requests a minute),
with the library's own identity, cached in CACHE, and writes app/src/main/resources/eras/advanced.tsv: one row
per player per team stint, like seasons.tsv (traded players' season totals are left out; the game adds stints up).
Usage: python3 fetch_advanced.py CACHE_DIR REPO [first_year last_year]"""
import html, os, re, sys, time, urllib.request

CACHE, REPO = sys.argv[1], sys.argv[2]
FIRST, LAST = (int(sys.argv[3]), int(sys.argv[4])) if len(sys.argv) > 4 else (1987, 2027)
os.makedirs(CACHE, exist_ok=True)
STATS = ['team_name_abbr', 'games', 'mp', 'per', 'ts_pct', 'usg_pct', 'ows', 'dws', 'ws', 'ws_per_48', 'obpm', 'dbpm', 'bpm', 'vorp']


def page(year):
    path = f'{CACHE}/NBA_{year}_advanced.html'
    if not os.path.exists(path):
        with urllib.request.urlopen(f'https://www.basketball-reference.com/leagues/NBA_{year}_advanced.html', timeout=60) as r:
            open(path, 'wb').write(r.read())
        time.sleep(4)
    return open(path, encoding='utf-8', errors='replace').read()


cell = re.compile(r'<t[dh][^>]*data-stat="([^"]+)"[^>]*>(.*?)</t[dh]>', re.S)
pid = re.compile(r'data-append-csv="([^"]+)"')
out = ['# season (year it ends)|id|' + '|'.join(STATS) + ' (basketball-reference.com advanced pages; personal use)']
for year in range(FIRST, LAST + 1):
    try:
        s = page(year)
    except Exception as e:  # a season the site doesn't have yet
        print(year, 'skipped:', e)
        continue
    i = s.find('id="advanced"')
    body = s[i:s.find('</table>', i)]
    rows = 0
    for tr in re.findall(r'<tr[^>]*>(.*?)</tr>', body, re.S):
        if 'data-stat="name_display"' not in tr:
            continue
        cells = {k: html.unescape(re.sub(r'<[^>]+>', '', v)).strip() for k, v in cell.findall(tr)}
        m = pid.search(tr)
        team = cells.get('team_name_abbr', '')
        if not m or not team or team in ('TOT', '2TM', '3TM', '4TM', '5TM'):
            continue
        out.append('|'.join([str(year), m.group(1)] + [cells.get(k, '') for k in STATS]))
        rows += 1
    print(year, rows, flush=True)
with open(f'{REPO}/app/src/main/resources/eras/advanced.tsv', 'w', encoding='utf-8') as f:
    f.write('\n'.join(out) + '\n')
print('wrote', len(out) - 1, 'rows')

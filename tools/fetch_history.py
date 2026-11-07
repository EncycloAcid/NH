"""Every NBA season's results, 1979-80 to 2025-26, from basketball-reference's season pages (personal use): each
team's record and how its season ended, the champion, the runner-up, the MVP and the Rookie of the Year. One page
a season, politely (four seconds apart, the library's own identity), cached in CACHE. Earlier champions (1947 to
1979) are added from the public record, by franchise. Writes app/src/main/resources/eras/history.tsv:
  T|season|abbreviation then|wins|losses|result      one row per team
  S|season|champion|runner-up|MVP|Rookie of the Year one row per season (champion and runner-up as abbreviations then)
  C|season|franchise slot                           champions before 1980, by today's franchise
Usage: python3 fetch_history.py CACHE_DIR REPO [first_year last_year]"""
import html, os, re, sys, time, urllib.request

CACHE, REPO = sys.argv[1], sys.argv[2]
FIRST, LAST = (int(sys.argv[3]), int(sys.argv[4])) if len(sys.argv) > 4 else (1980, 2026)
os.makedirs(CACHE, exist_ok=True)

# NBA (and BAA) champions before 1980, by today's franchise (the 1948 Baltimore Bullets folded and are left out).
EARLY = {1947: 'GSW', 1949: 'LAL', 1950: 'LAL', 1951: 'SAC', 1952: 'LAL', 1953: 'LAL', 1954: 'LAL', 1955: 'PHI',
         1956: 'GSW', 1957: 'BOS', 1958: 'ATL', 1959: 'BOS', 1960: 'BOS', 1961: 'BOS', 1962: 'BOS', 1963: 'BOS',
         1964: 'BOS', 1965: 'BOS', 1966: 'BOS', 1967: 'PHI', 1968: 'BOS', 1969: 'BOS', 1970: 'NYK', 1971: 'MIL',
         1972: 'LAL', 1973: 'NYK', 1974: 'BOS', 1975: 'GSW', 1976: 'BOS', 1977: 'POR', 1978: 'WAS', 1979: 'OKC'}


def page(year):
    path = f'{CACHE}/NBA_{year}.html'
    if not os.path.exists(path):
        with urllib.request.urlopen(f'https://www.basketball-reference.com/leagues/NBA_{year}.html', timeout=60) as r:
            open(path, 'wb').write(r.read())
        time.sleep(4)
    return open(path, encoding='utf-8', errors='replace').read()


def abbr(href):
    m = re.search(r'/teams/([A-Z]{3})/', href)
    return m.group(1) if m else ''


def award(s, label):
    m = re.search(label + r'</strong>:\s*<a[^>]*>([^<]+)</a>', s)
    return html.unescape(m.group(1)).strip() if m else ''


# Checked in this order: "Eastern Conference Finals" also contains "Finals".
ROUND = [('Conference Finals', 'Lost in the conference finals'), ('Conf Finals', 'Lost in the conference finals'),
         ('Semifinals', 'Lost in the second round'), ('Second Round', 'Lost in the second round'), ('First Round', 'Lost in the first round'),
         ('Finals', 'Lost in the Finals')]
out = ['# T|season|team then|W|L|result; S|season|champion|runner-up|MVP|Rookie of the Year; C|season|champion slot before 1980 (basketball-reference.com season pages; personal use)']
for y, slot in sorted(EARLY.items()):
    out.append(f'C|{y}|{slot}')
for year in range(FIRST, LAST + 1):
    try:
        s = page(year)
    except Exception as e:
        print(year, 'skipped:', e)
        continue
    # Standings: every team's record (division or conference tables, whichever the season has).
    rec = {}
    for tid in ('divs_standings_E', 'divs_standings_W', 'confs_standings_E', 'confs_standings_W'):
        i = s.find(f'id="{tid}"')
        if i < 0:
            continue
        for tr in re.findall(r'<tr[^>]*>(.*?)</tr>', s[i:s.find('</table>', i)], re.S):
            a = abbr(tr)
            w = re.search(r'data-stat="wins"[^>]*>(\d+)', tr)
            l = re.search(r'data-stat="losses"[^>]*>(\d+)', tr)
            if a and w and l:
                rec[a] = (int(w.group(1)), int(l.group(1)))
    # Playoffs: each series' loser, and how far the winner went. (Each series row holds a table of its games, so the
    # series are read straight from the section, up to the awards table that follows it.)
    result = {}
    champion = runner = ''
    i = s.find('id="all_playoffs"')
    if i >= 0:
        end = s.find('id="all_awards"', i)
        region = s[i:end if end > 0 else i + 300000]
        for label, win, lose in re.findall(r"<strong>([^<]+)</strong>.*?/teams/([A-Z]{3})/\d+\.html['\"]>[^<]*</a>\s*over\s*<a href=['\"]/teams/([A-Z]{3})/", region, re.S):
            label = html.unescape(label).strip()
            text = next((r for k, r in ROUND if k in label), 'Lost in the first round')
            if label == 'Finals':
                champion, runner = win, lose
                result[win] = 'Champion'
            result.setdefault(lose, text)
    for a, (w, l) in rec.items():
        out.append(f'T|{year}|{a}|{w}|{l}|{result.get(a, "Missed playoffs")}')
    out.append(f'S|{year}|{champion}|{runner}|{award(s, "Most Valuable Player")}|{award(s, "Rookie of the Year")}')
    print(year, len(rec), 'teams; champion', champion, flush=True)
with open(f'{REPO}/app/src/main/resources/eras/history.tsv', 'w', encoding='utf-8') as f:
    f.write('\n'.join(out) + '\n')
print('wrote', len(out) - 1, 'rows')

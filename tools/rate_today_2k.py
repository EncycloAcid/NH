"""Rates today's 522 real players from the user's full NBA 2K27 ratings file (personal use).
Writes resources/eras/today2k.tsv (name|overall|potential|10 game ratings, all on the 2K scale) and puts each
player's overall into game/NbaData.kt on the game's internal pro scale (the inverse of the 2K-style scale shown)."""
import csv, sys
F, REPO = sys.argv[1], sys.argv[2]
def internal(k): return 85 + (k - 77) / 1.5 if k >= 77 else 85 - (77 - k) / 1.2
POT = {'A+': 97, 'A': 93, 'A-': 90, 'B+': 87, 'B': 84, 'B-': 81, 'C+': 78, 'C': 75}
rows = list(csv.DictReader(open(F, encoding='utf-8')))
def n(r, c):
    try: return float(r[c])
    except: return None
out = {}
for r in rows:
    if not r['overall']: continue
    o = int(r['overall'])
    pot = max(o, POT.get(r['potential'], o))
    v = {c: n(r, c) for c in ['Close Shot', 'Layup', 'Driving Dunk', 'Standing Dunk', 'Mid-Range Shot', 'Three-Point Shot', 'Ball Handle', 'Speed with Ball',
         'Pass Accuracy', 'Pass Vision', 'Pass IQ', 'Defensive Rebound', 'Offensive Rebound', 'Perimeter Defense', 'Interior Defense', 'Steal', 'Block']}
    attrs = ''
    if all(x is not None for x in v.values()):
        g = [0.35 * v['Close Shot'] + 0.35 * v['Layup'] + 0.15 * v['Driving Dunk'] + 0.15 * v['Standing Dunk'], v['Mid-Range Shot'], v['Three-Point Shot'],
             0.7 * v['Ball Handle'] + 0.3 * v['Speed with Ball'], 0.5 * v['Pass Accuracy'] + 0.3 * v['Pass Vision'] + 0.2 * v['Pass IQ'],
             0.65 * v['Defensive Rebound'] + 0.35 * v['Offensive Rebound'], v['Perimeter Defense'], v['Interior Defense'], v['Steal'], v['Block']]
        attrs = ','.join(str(round(x)) for x in g)
    out[r['name']] = (o, pot, attrs)
with open(f'{REPO}/app/src/main/resources/eras/today2k.tsv', 'w', encoding='utf-8') as f:
    f.write('# name|2K27 overall|potential (from the letter grade)|inside,mid,three,handling,passing,rebounding,perimeter D,interior D,steals,blocks (NBA 2K27 via the user\'s ratings file)\n')
    for name, (o, p, a) in out.items(): f.write(f'{name}|{o}|{p}|{a}\n')
path = f'{REPO}/app/src/main/java/com/nh/hoopscoach/game/NbaData.kt'
src = open(path, encoding='utf-8').read()
head, rest = src.split('P0 = """', 1)
body, tail = rest.split('"""', 1)
lines = []
for l in body.strip().splitlines():
    f = l.split('|')
    if f[1] in out: f[4] = str(max(70, min(99, round(internal(out[f[1]][0])))))
    lines.append('|'.join(f))
open(path, 'w', encoding='utf-8').write(head + 'P0 = """\n' + '\n'.join(lines) + '\n"""' + tail)
print('rated', len(out), 'with attributes', sum(1 for v in out.values() if v[2]))

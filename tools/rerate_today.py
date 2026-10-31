"""Re-rates today's real players (game/NbaData.kt) from NBA 2K27 where the eras pack has a rating,
on the game's internal pro scale (the inverse of the 2K-style scale the screens show), and corrects
everyone else by how far players their age were overrated against 2K27."""
import csv, re, sys, statistics as st
D, REPO = sys.argv[1], sys.argv[2]
path = f'{REPO}/app/src/main/java/com/nh/hoopscoach/game/NbaData.kt'
src = open(path, encoding='utf-8').read()
head, rest = src.split('P0 = """', 1)
body, tail = rest.split('"""', 1)
rows = [l.split('|') for l in body.strip().splitlines()]

def internal(k):  # 2K-style overall -> the game's internal pro scale
    return 85 + (k - 77) / 1.5 if k >= 77 else 85 - (77 - k) / 1.2

k27 = {r['player']: int(r['2k_overall']) for r in csv.DictReader(open(f'{D}/eras/2026-27/stars.csv', encoding='utf-8')) if r['2k_overall']}
band = lambda age: 0 if age <= 21 else 1 if age <= 24 else 2 if age <= 29 else 3
res = {b: [] for b in range(4)}
for r in rows:
    if r[1] in k27: res[band(int(r[5]))].append(int(r[4]) - internal(k27[r[1]]))
corr = {b: max(0.0, st.mean(v)) if v else 0.0 for b, v in res.items()}
print('overrated by age band (internal points):', {b: round(c, 1) for b, c in corr.items()}, {b: len(v) for b, v in res.items()})
out, changed = [], 0
for r in rows:
    old = int(r[4])
    new = round(internal(k27[r[1]])) if r[1] in k27 else round(old - corr[band(int(r[5]))])
    new = max(70, min(99, new))
    changed += new != old
    r[4] = str(new)
    out.append('|'.join(r))
open(path, 'w', encoding='utf-8').write(head + 'P0 = """\n' + '\n'.join(out) + '\n"""' + tail)
o = sorted((int(r[4]) for r in rows), reverse=True)
print('changed', changed, 'of', len(rows), '; 95+:', sum(x >= 95 for x in o), '93+ (90+ on the 2K scale):', sum(x >= 93 for x in o), 'median', o[len(o)//2])

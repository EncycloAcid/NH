"""Builds the social feed's real accounts from the user's social accounts pack (personal use):
resources/social/accounts.tsv (type|name|handle|followers|verified|avatar) and small webp avatars in assets/social/."""
import csv, os, sys
from PIL import Image
SRC, REPO = sys.argv[1], sys.argv[2]
out_dir = f'{REPO}/app/src/main/assets/social'
os.makedirs(out_dir, exist_ok=True)
os.makedirs(f'{REPO}/app/src/main/resources/social', exist_ok=True)
rows = list(csv.DictReader(open(f'{SRC}/social-accounts.csv', encoding='utf-8')))
lines, n = [], 0
for r in rows:
    key = ''
    a = r['avatar_file']
    if a and os.path.exists(f'{SRC}/{a}'):
        key = os.path.splitext(os.path.basename(a))[0]
        im = Image.open(f'{SRC}/{a}').convert('RGB')
        s = min(im.size)
        im = im.crop(((im.width - s) // 2, (im.height - s) // 2, (im.width + s) // 2, (im.height + s) // 2)).resize((96, 96))
        im.save(f'{out_dir}/{key}.webp', 'WEBP', quality=80)
        n += 1
    lines.append('|'.join([r['type'], r['name'], r['x_handle'].lstrip('@'), r['x_followers'] or '0', '1' if r['x_verified'] else '0', key]))
with open(f'{REPO}/app/src/main/resources/social/accounts.tsv', 'w', encoding='utf-8') as f:
    f.write('# type|name|handle|followers|verified|avatar (the user\'s social accounts pack; personal use)\n')
    f.write('\n'.join(lines) + '\n')
print(len(lines), 'accounts,', n, 'avatars')

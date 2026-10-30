# Hoops Dynasty

A native Android college basketball coaching game, written in Kotlin with Jetpack Compose.

Pick a school, set your rotation and tactics, play or sim games, recruit high schoolers and transfers, manage NIL money, develop players, and chase national titles season after season.

You can coach any of the 364 Division I schools, in their real 2026-27 conferences (32 of them, to the best of our knowledge), with real names, nicknames and colors. Every player and coach is fictional. Team marks are drawn monograms in school colors, not official logos. To fix any school's name, nickname or colors, use **Career → Settings → Schools**.

The design is clean and neutral, with light and dark modes, and takes its accent color from the school you coach.

## Install on your phone

1. Open the repository's **Releases** page on your Android phone and download `HoopsDynasty.apk` from the newest build. Every push builds a fresh one.
2. Open the file. If Android asks, allow your browser or file manager to install unknown apps.
3. Your dynasty saves automatically on the device.

**Keep your save safe:** In **Settings → Back Up Save**, write your dynasty to a file. If Android ever refuses to install a new build over the old one ("app not installed" or a package conflict), it means the builds were signed with different keys. Back up first, uninstall, install the new build, then use **Restore Save**.

## What's in the game

- **Continue bar**: one bar above the tabs always shows what's next. Play the game live, sim it, or use the ⋮ menu to jump a week, to the conference tournament, to Selection Sunday, or to the end of the season.
- **Today**: next opponent with win odds and a head-to-head comparison, recent form, conference standings, top performers and headlines. During tournaments it shows your road to the title and the current round.
- **Team**
  - *Roster*: starting five, rotation and bench with jersey numbers, rating rings and personality traits.
  - *Lineup*: offense and defense styles, starter and backup at each position, a minutes slider, and optional usage tuning.
  - *Develop*: XP progress and a training focus (archetype) for each player.
  - *Program*: prestige, NIL budget, locker-room mood and coaching staff.
- **Recruit**: targets, high-school prospects, transfers and commits, with interest races against rival schools.
- **League**: AP Top 25 and NET, standings for all 32 conferences, stat leaders, Bubble Watch, bracketology and brackets, and history.
- **Bubble Watch**: the projected field, sorted into Locks, Should be in, On the bubble (last four byes and last four in), First four out and Next four out, plus projected automatic bids. Tap any two teams to compare their resumes side by side: record, NET, Quad 1 and Quad 2 records, SOS, non-conference SOS and Wins Above Bubble. After Selection Sunday it shows the committee's final call. The Today screen shows your own outlook from mid-season on.
- **Career**: your coach's record, honors, job security, coaching skills and history.
- **Live games**: possession-by-possession play-by-play, a live win-probability chart, speed controls and in-game tactics.
- **Season structure**: 31-game regular season with in-season tournaments, conference tournaments, a 68-team NCAA Tournament and a 32-team NIT.
  - **Selection**: 32 automatic bids and 36 at-large. The committee ranks resumes by NET and Wins Above Bubble equally, with credit for Quad 1 wins and a penalty for Quad 4 losses. Bubble Watch uses the same formula, so its projection matches the real selection.
  - **First Four**: the last four at-large teams play for two 11 seeds, and the four lowest automatic qualifiers play for two 16 seeds, before the Round of 64.
  - **NIT**: the 32 best teams that missed the field, in four brackets of eight. Higher seeds host until the semifinals. The NIT champion gets +2 to +3 prestige, 3 extra coaching points and 350 XP for every returning player.
- **Season wrap-up**: results, awards (Player of the Year, All-Americans, All-Tournament), graduations, program prestige changes, and coach development points.
- **Offseason**, step by step:
  1. **Roster & NIL**: pitch draft entrants to return, raise below-market deals, release players.
  2. **Draft Night**: 60 picks.
  3. **Career Decision**: job offers from other programs. Win more and bigger schools call. Lose too much and you can get fired.
  4. **Player Development**: summer growth for every returning player.
  5. **NIL Budget**: donor pool, conference revenue share and performance bonuses.
  6. **Recruiting**: five rounds for the recruiting class and the transfer portal, then signing day.

## Build it yourself

Requirements: JDK 17+ and the Android SDK (Android Studio works out of the box).

```sh
./gradlew :app:assembleRelease          # APK at app/build/outputs/apk/release/app-release.apk
./gradlew :app:testDebugUnitTest --tests com.nh.hoopscoach.SimTest   # simulates 3 full seasons
```

`ScreenshotTest` renders every main screen on the JVM with Robolectric and writes PNGs to `build/screenshots/`.

## Project layout

```
app/src/main/java/com/nh/hoopscoach/
  game/   pure Kotlin game logic: league generation, possession-by-possession game engine,
          schedule, rankings (Elo-based NET + AP poll), awards, recruiting, finance, offseason
  ui/     Jetpack Compose screens, theme, drawn team logos and player portraits
```

## Signing key

The signing key is never committed. Local builds read it from `keystore.properties` in the project root (git-ignored):

```properties
storeFile=/path/to/your.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

GitHub Actions uses repository secrets instead: `KEYSTORE_BASE64` (the keystore file, base64-encoded), `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. With them set, every published APK carries the same signature and installs over the last one. Without them, builds are signed with a throwaway debug key, so you'd need to back up your save, uninstall, and reinstall when switching builds.

Make a key with:

```sh
keytool -genkeypair -keystore hoops.keystore -alias hoops -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 hoops.keystore   # paste the output into the KEYSTORE_BASE64 secret
```

Fonts: [Big Shoulders Display](https://github.com/xotypeco/big_shoulders) and [Manrope](https://github.com/sharanda/manrope), both SIL Open Font License (see `licenses/`).

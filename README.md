# Hoops Dynasty

A native Android college basketball coaching game, written in Kotlin with Jetpack Compose.

Pick a school, set your rotation and tactics, play or sim games, recruit high schoolers and transfers, manage NIL money, develop players, and chase national titles season after season.

All 228 schools, conferences, players and coaches are fictional. You can rename any school, its mascot, colors and logo shape in **Settings → Edit Any School**.

## Install on your phone

1. Open the repository's **Releases** page on your Android phone and download `HoopsDynasty.apk` from the newest build. Every push builds a fresh one.
2. Open the file. If Android asks, allow your browser or file manager to install unknown apps.
3. Your dynasty saves automatically on the device.

**Keep your save safe:** In **Settings → Back Up Save**, write your dynasty to a file. If Android ever refuses to install a new build over the old one ("app not installed" or a package conflict), it means the builds were signed with different keys. Back up first, uninstall, install the new build, then use **Restore Save**.

## What's in the game

- **Home**: next game with Play (watch it possession by possession, change tactics mid-game) or Sim, recent results, team stats, your leaders and headlines. A Sim menu jumps ahead a game, a week, or to any point in the season.
- **Program**
  - *Summary*: prestige, budget, NIL spend, player happiness, and players who need attention.
  - *Players*: starters, rotation and bench, with roles, stats and personality traits.
  - *Gameplan*: offense and defense styles, starters and backups at each position, minutes split and usage sliders, or Auto-Allocate.
  - *Training*: XP and level-ups, with a training focus (archetype) for each player.
  - *Recruiting*: daily points to scout, call, visit and offer scholarships and NIL money.
- **League**: national AP Top 25 and NET rankings, conference standings, stat leaders, coach power rankings, and history (season review, awards, draft, Hall of Fame, record book).
- **Dynasty**: your coach's career record, titles, awards, job security, coaching history and skills.
- **Season structure**: 31-game regular season with in-season tournaments, conference tournaments, and a 64-team NCAA bracket.
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

Font: [Lexend](https://github.com/googlefonts/lexend) (SIL Open Font License, see `licenses/`).

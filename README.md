# Hoops Dynasty

A native Android college basketball coaching game, written in Kotlin with Jetpack Compose.

Pick a school, set your rotation and tactics, play or sim games, recruit high schoolers and transfers, manage NIL money, develop players, and chase national titles season after season.

You can coach any of the 364 Division I schools, in their real 2026-27 conferences (32 of them, to the best of our knowledge), with real names, nicknames and colors. Every player and coach is fictional. Every school shows its own logo (the logo files you supplied, in `app/src/main/assets/logos`, one per team named by its abbreviation). A school without a logo file falls back to a drawn monogram in its colors. The logos are the schools' trademarks, so keep this build for personal use and don't publish it to an app store. To fix any school's name, nickname or colors, use **Career → Settings → Schools**.

The design is clean and neutral, with light and dark modes, and takes its accent color from the school you coach.

## This branch: Road to the League (experiment)

The `road-to-the-league` branch adds a second way to play: instead of coaching a program, you play one player's college career. It builds as a separate app, **Hoops Dynasty Road** (`com.nh.hoopscoach.road`), so it installs next to the main game and never touches its save. The main game keeps going on its own branch.

- **Create your player**: name, position, height and weight, play style, hometown and state, jersey number, and prospect level (3-star sleeper, 4-star prospect or 5-star blue-chipper). Every level has a real ceiling: 96 to 99 potential.
- **Scholarship offers**: eight schools offer, matched to your level, with a lean toward home. Each offer shows your projected role, how many players at your position are already as good as you, distance from home and the NIL money the school pays.
- **Your team is coached by the AI**: the coach sets the rotation and tactics, recruits and pays the roster. You see the roster and the program; your row is marked YOU. Minutes come from being the best at your position.
- **The Me tab**: draft stock and big-board rank, your role and planned minutes, the depth chart at your position, training focus and XP, your NIL deal and career earnings, and your story season by season. Today shows a "Your player" card with your last game.
- **NIL deals**: each season pick one. A hometown dealership pays full value. A performance deal pays 60% plus 4% per point you average and a bonus for All-America honors. A national brand pays 150% but costs 15% of your development that season. Richer programs pay more.
- **The offseason** in four steps:
  1. **Decision**: return to school, declare for the draft (undrafted underclassmen go back to school), or enter the transfer portal.
  2. **Draft Night**.
  3. **Summer**: workouts, and transfer offers if you entered the portal.
  4. **Next season**: everything else (coaching changes, recruiting, tryouts) runs on its own.
- **Media and followers**: you start with a following that matches your ranking (about 150K for a top five-star, 50K to 70K for a four-star, 10K for a three-star). Big games grow it, more on big stages (NCAA Tournament x3, conference tournaments and rivalries x1.5, ranked opponents x1.3) and at big-brand schools. Thirty-point nights and game-winners go viral. Every follower adds $1 a year to your NIL value, and sponsors pay one-time bonuses at 100K, 250K, 500K, 1M, 2.5M and 5M followers.
- **Postgame press**: after notable games (a big night, a game-winner, a rivalry win, a tournament win, a tough loss, draft buzz) you get a question on Today and the Me tab with three answers. Humble answers please fans and teammates. Brash ones grow your following faster but cost locker-room goodwill. Your answers shape a persona: Fan favorite (wins lift the fan base more), Quiet pro, or Lightning rod (big games grow your following 20% faster).
- **Big moments**: in live games, when your team has the ball in the last two minutes and it's within five, the game pauses (up to three times a game). Pick the play: a pull-up jumper or three, a drive to the rim, or a kick-out to your best shooter. Each shows its chance to score from your ratings against that defense. Then the engine plays it out, with fouls, turnovers and rebounds. Simmed games play on their own. The Me tab counts how often you came through.
- **Weekly practice**: choose a plan, applied after every game.
  - Team practice: works your training focus.
  - Shooting reps: Inside, Mid-Range and 3-Point.
  - Film study: Handling, Passing and Perimeter D. Easy on the legs.
  - Weight room: Inside, Rebound and Interior D. The most tiring.
  - Rest: recovers fatigue.
  Practice work banks toward each rating (+1 per 300). Fatigue comes from practice and minutes. Past 40 it costs you up to 9% shooting in games, and past 70 practice gains are halved.
- **Spend your NIL**: the guaranteed part of your NIL deal lands in your bank when you sign. Performance bonuses arrive at season end, and sponsor payouts as you hit milestones. Spend the bank on your own team for the season (contracts end with it):
  - Personal trainer ($150K): develop 25% faster from games and practice.
  - Nutritionist ($90K): fatigue builds a quarter slower, and you recover faster.
  - Shooting coach ($120K): +3% on jump shots, +5% when you take the big shot late.
  - Agent ($80K): 15% more on NIL deals, bonuses and sponsor payouts.
  The Me tab shows your bank and staff; the career summary shows what you invested.
- **Award races**: a Player of the Year ladder on the Me tab, scored the same way as the end-of-season awards. It shows your rank, your All-America pace, a weekly up or down arrow, your conference race and the freshman race.
- **The ending**: drafted (with your pick) or, after four seasons, undrafted and overseas. You get a career summary with totals, your story, NIL earned and every school you played for.

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
  - *Program*: Prestige, Legacy, Buzz, Program Pull, Conference Exposure, Expected Wins and the resume ceiling, plus NIL budget, locker-room mood and coaching staff.
  - The **i** button opens the **Program Guide**, with tabs for Players, NIL, Gameplan, Training and Program.
- **Recruit**: a class of 1,600 high-school prospects plus the transfer portal. In season you scout and build your targets; after the season, recruiting runs in five rounds (details below). The **i** button opens the **Recruiting Guide**.
- **Program numbers**: Prestige (0-100) is the durable brand and moves once a season.
  - **How it moves**: about 15% of the way toward what the season earned (12% on the way down). A season earns credit for record, conference finish and tournament title, NCAA run, Quadrant 1 wins, last summer's draft picks and national awards. Rivalry games and an NIT title add on top.
  - **Conference resistance**: records in a low-exposure league count for less, and a high-exposure league adds a little. Below 70 records and titles drive the climb; above 70 it takes March.
  - **Resume ceiling**: above 70, the last 10 NCAA Tournaments set a soft ceiling (Round of 32 most years supports the low 80s, Sweet 16 most years the high 80s, one Elite Eight the high 80s on its own, a Final Four or title 90 and up). Past it, climbing slows to a quarter, and above 80 Prestige drifts back toward it. Years before the save began count at the level Legacy implies.
  - **Legacy**: the long-run baseline. It moves a few points a decade and starts higher for programs with big pasts (UCLA, Indiana, Georgetown and others). It cushions a fallen power, lets it recover faster, and slows a one-year spike.
  - **Buzz**: short-term heat from ranked wins, upsets, rivalry wins, conference tournament titles and March wins (Cinderella runs count 50% more). It cools every game and halves over the summer.
  - **Conference Exposure**: a league's national visibility, from its level and its members' prestige.
  - **Program Pull**: 45% Prestige, 15% Legacy and 10% each of Conference Exposure, coach recruiting, recent NCAA results and Buzz. Recruits see it as Recruiting Pull.
  - **Expected Wins**: the preseason target from your schedule and team strength.
  - The season review lists everything that moved your Prestige, including draft picks, Legacy and the resume ceiling.
- **Recruiting cycle**: five rounds in the offseason, after the NIL budget step.
  - **Budget**: one point budget for the whole cycle, from your Recruiting Pull and your coach's recruiting rating (plus 10% for an Ace Recruiter).
  - **Rounds**: before each round, rival schools set their points. You put points on prospects (they stay there until you move them), preview where you'd stand, and lock in. Points have diminishing returns, and gains taper as interest nears 100.
  - **Interest**: how much your points count depends on program strength (your Pull against what he's after), coach recruiting, location (in-state and same region), pro pipeline, playing time, roster crowding at his position, and his own preference: close to home, early playing time, winning now, a big-name program, NIL money or a path to the pros. Each prospect page rates every factor for your school.
  - **NIL offers**: an offer adds interest every round on its own, even with no points on him (about +8 a round at his ask, +13 at double, 60% more for NIL-minded prospects and Bag Chasers). Rival programs offer 60% to 120% of the ask depending on their NIL budget. All your open offers must fit next year's NIL room, and the prospect page shows the room left and exactly what your offer adds.
  - **Locking in**: interest moves exactly as the preview showed. Then some prospects commit (leaders with high interest and a clear margin, more often in later rounds), points on them come back, schools that fall far behind or run out of scholarships drop out, and new schools enter. A round report shows what happened on your board.
  - **End of cycle**: prospects with a clear favorite sign late, programs with room pick up some of the rest, and the others go unsigned. Your open spots go to walk-on tryouts.
  - **Auto-assign**: one tap plans the round for you: it steps out of races that are lost, keeps about two prospects per open scholarship, matches NIL asks while the room lasts, and puts most of your free points where the chances are best (never more than 30% of the budget on one prospect). Simming the offseason does this every round.
  - **Commitments**: once a prospect's interest is high he may decide. He picks among the schools near the top of his race: a tie is a coin flip, and a school 5 points back is about a third as likely as the leader.
- **League**: AP Top 25 and NET, standings for all 32 conferences, stat leaders, Bubble Watch, bracketology and brackets, and history.
- **Bubble Watch**: the projected field, sorted into Locks, Should be in, On the bubble (last four byes and last four in), First four out and Next four out, plus projected automatic bids. Tap any two teams to compare their resumes side by side: record, NET, Quad 1 and Quad 2 records, SOS, non-conference SOS and Wins Above Bubble. After Selection Sunday it shows the committee's final call. The Today screen shows your own outlook from mid-season on.
- **Career**: your coach's record, honors, job security, coaching skills and history.
- **Rivalry games**: 45 traditional rivalries, including Duke-North Carolina (Tobacco Road), Kentucky-Louisville (Battle of the Bluegrass), Kansas-Missouri (Border War), Indiana-Purdue, Michigan-Michigan State and Missouri-Illinois (Braggin' Rights, at a neutral site).
  - **Schedule**: conference rivals play home-and-home, with the rematch closing out conference play. Non-conference rivals meet every year, and the home team alternates.
  - **Game day**: rivalry games sell out, and the home crowd is worth an extra 25 Elo. The engine and the win odds both include it.
  - **Prestige**: the winner takes 1 prestige point from the loser, or 2 for an upset. Winning also lifts your players' mood and your job security.
  - **Headlines**: every result gets a front page in *The Hoops Dispatch*, a newspaper-style banner with headline, score, crowd, prestige swing and series record. It appears on Today and in the box score.
  - **Rivals list**: rivals and the series record show on team pages and in your Program tab.
- **Fans and crowds**: every program has a fan mood, from Apathetic to Electric.
  - Wins raise it, especially at home, against ranked teams, in rivalries and in March. Losses lower it, and it slowly settles back toward what the program's stature supports.
  - Fan mood sets attendance, so a hot team sells out and a losing one plays to empty seats.
  - It also adds up to 20% to the donor NIL pool, or takes up to 20% away. That shows as "Fan support" in the NIL budget.
- **Pro pipeline**: your best underclassmen can declare early for the NBA draft. Every player drafted from your program raises recruiting interest for the next four drafts: lottery picks count most, and 4- and 5-star recruits care most.
- **Program legends**: when a player leaves, his whole career at the school is scored on awards, titles, Final Fours, scoring and draft position.
  - The best enter the program's Hall of Fame, and the all-time greats have their jersey retired. Retired jerseys hang as banners in the rafters, and nobody on that team wears the number again.
  - Coaches with titles, Final Fours or long runs are honored when they leave.
  - Legends show on the Program tab and every team page, with a ceremony on Draft Night for your own.
- **Upset alerts and buzzer-beaters**: the engine records each game's deciding basket, and tied teams hold for the last shot.
  - **Buzzer-beaters**: a winning shot with two seconds or less left counts, and so does a shot at the horn that forces overtime.
  - **Upsets**: an NCAA upset of five or more seed lines is an upset alert.
  - **Other moments**: multi-overtime classics, Cinderella runs (a 10 seed or lower reaching the Sweet 16, a 7 seed or lower reaching the Final Four) and the national title.
  - **Where they show**: each one gets a tagged headline, and during March, Today shows the latest upset alerts. Bracket winners that pulled an upset get an UPSET tag.
  - **Highlight reel**: every moment is saved to a reel under League → History, with a filter for your team, and the season review lists the year's top March moments. Your own buzzer-beaters count in any month.
- **Hoops Dynasty Preview**: a 15-page preseason yearbook. Each new issue opens itself before your first game of the season, and you swipe through it page by page (or tap the page ticks to jump). Reopen it any time from Today or League → Polls.
  1. **Cover**: the preseason No. 1 and what's inside.
  2. **Contents and editor's letter**: the big storylines of the year.
  3. **Top 5**: a feature on each, with projected record, star, newcomer and a scouting report of strengths and worries.
  4. **Nos. 6–25**: the rest of the poll, each with a short write-up.
  5. **Preseason All-America**: three five-man teams.
  6. **Freshman class**: the top recruiting classes and the top 10 freshmen.
  7. **New faces, last calls**: impact transfers, portal winners and the best seniors.
  8. **Breakouts and sleepers**: breakout candidates, plus stars at mid-major schools.
  9. **Your conference in depth**: predicted finish and a line on every team.
  10. **Power conferences**: contenders and Player of the Year picks for the big leagues.
  11. **Mid-major roundup**: the pick and player to watch in every other league.
  12. **Team report**: your own team's outlook, starting five, bench and scouting report.
  13. **Coaches**: the top 10 coaches and the hot seat.
  14. **Games to watch**: early-season tournaments and the 10 regular-season games to circle.
  15. **Predictions**: Final Four, champion, Cinderella, sleeper, award picks and bold predictions.
  Once conference play starts, the conference pages show who's ahead of or behind the prediction.
- **Player archetypes**: 20 play styles, from Floor General and Sharpshooter to 3-and-D Wing, Shot Creator, Table Setter, Ball Hawk, Glue Guy, Rim Runner, Point Center, Stretch Five and Enforcer. Each one raises three ratings when used as a training focus. Existing players keep their style; the new ones show up in new recruits and in the Develop tab.
- **Player customization**: tap Edit on any of your own players to change his name, jersey number, position, height, weight, archetype, hometown and high school. A number already worn by a teammate or retired by the program can't be picked. Changing position shows his overall at the new spot, and the lineup updates itself.
- **Player career pages**: open any player for his full Career tab.
  - **Stats**: a season-by-season table (minutes, points, rebounds, assists, steals, blocks, turnovers and shooting) that switches between per-game and totals, with the season in progress highlighted and a career line. Plus his NCAA Tournament stats, and career highs with the game each one came in.
  - **Honors by season**: each season shows his team, its record and result (Sweet 16, NIT Champion and so on), and every honor: All-American teams, national and conference awards, All-Tournament, draft position, walk-on tryouts and program Hall of Fame.
  - **Former players**: when players leave, their career pages stay on record. That covers everyone who played for you, every draft pick and every All-American. Open them from the Former players list on team pages, the draft history, award winners and the Hall of Fame.
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
  7. **Walk-on tryouts**: five unrated students try out. Instead of ratings you see drill grades for shooting, athleticism and the scrimmage, plus your coach's gut feeling. You can take a closer look at two of them to see their overall and get sharper grades. Keep up to two walk-ons on top of your 13 scholarships, and any scholarship you leave open can go to a walk-on too. Walk-ons cost no NIL money.
     - Once in a while a walk-on is a hidden gem. He breaks out over a summer, earns the Late Bloomer trait, develops faster and can become a star. The coach's gut is your best clue.
     - Other programs find gems too, so watch the headlines for "from walk-on to star" stories.
     - When a scholarship opens, award one to a walk-on from his player card.

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

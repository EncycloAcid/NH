package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Info

enum class Guide(val title: String) { PROGRAM("Program Guide"), RECRUITING("Recruiting Guide") }

/** A round "i" button that opens a guide. */
@Composable
fun GuideButton(onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(P.surface2).clickable(onClick = onClick).semantics { contentDescription = "Guide" },
        contentAlignment = Alignment.Center,
    ) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Info, null, tint = P.sub, modifier = Modifier.size(20.dp)) }
}

private class GSection(val title: String, val lines: List<String>)

private fun sec(title: String, vararg lines: String) = GSection(title, lines.toList())

/** Lines starting with "• " are bullets; everything else is a paragraph. */
private val programTabs: List<Pair<String, List<GSection>>> = listOf(
    "Players" to listOf(
        sec(
            "The short version",
            "Every player has ten ratings from 1 to 99, an overall (OVR) and a potential (POT). Overall weighs the ratings by position, so the same player can rate higher at one spot than at another.",
        ),
        sec(
            "Ratings",
            "• Inside, Mid-Range and 3-Point: scoring from each level.",
            "• Handling and Passing: running the offense and protecting the ball.",
            "• Rebound: both ends of the floor.",
            "• Perimeter D and Interior D: guarding the ball and the paint.",
            "• Stealing and Blocking: turning defense into extra possessions.",
        ),
        sec(
            "Class and eligibility",
            "Players have four seasons. Seniors graduate after the season, and your best underclassmen may declare for the draft. Unhappy players can enter the transfer portal in the offseason.",
        ),
        sec(
            "Personality",
            "• Ambitious: wants a big role and sours fast on the bench.",
            "• Bag Chaser: takes an underpaid NIL deal hard.",
            "• Disloyal: quicker to look at the portal.",
            "• Loyal: sticks with the program through bad stretches.",
            "• Team Player: takes a smaller role in stride.",
            "• Gym Rat: develops faster.",
            "• Clutch: better with the game on the line.",
            "• Late Bloomer: earned, never rolled. A walk-on whose game took off; develops faster.",
        ),
        sec(
            "Mood",
            "Every game day, a player's mood drifts toward a target set by three things: his minutes against what his talent expects, his NIL deal against his market value, and winning. Loyal and Team Player soften the swings; Ambitious and Bag Chaser sharpen them.",
            "Happy and Content players stay put. Unhappy ones are a risk, and players marked as transfer risks often leave in the offseason.",
        ),
        sec(
            "Walk-ons and your roster",
            "Walk-ons don't use one of your 13 scholarships. Each preseason you can hold tryouts, and once in a while a walk-on turns into a real player.",
            "Tap Edit on any of your own players to change his name, number, position, size, archetype or background.",
        ),
    ),
    "NIL" to listOf(
        sec(
            "The short version",
            "NIL money pays your players and recruits. Your program gets a budget every season, and every player on the roster has a yearly deal paid from it.",
        ),
        sec(
            "Where the budget comes from",
            "• Donors: the biggest piece, and it grows faster than Prestige does.",
            "• Revenue share: set by your conference's level.",
            "• Fan support: excited fans add up to 20% to the donor money, apathetic ones take up to 20% away.",
            "• Performance bonuses: conference titles and deep March runs pay for a few seasons, up to 12% extra.",
        ),
        sec(
            "Market value",
            "Every player has a market value based on his overall, potential and class. Paying below it lowers his mood, and twice as hard for a Bag Chaser. Paying above it helps a little.",
        ),
        sec(
            "Recruits",
            "Each prospect has an NIL ask. An NIL offer adds interest every recruiting round on its own, even with no points on him: matching the ask adds about 8 a round, and double the ask about 13. Prospects who care about NIL money, and Bag Chasers, feel it 60% more.",
            "Rival programs offer NIL too while they recruit a prospect, from 60% of his ask at the smallest budgets to 120% at the richest. All your open offers together have to fit next year's NIL room.",
            "Money promised to recruits who commit counts against next year's budget.",
        ),
        sec(
            "Offseason",
            "Graduates, draft picks and transfers free up money in the Roster & NIL step. The NIL Budget step shows next year's number before recruiting starts.",
        ),
    ),
    "Gameplan" to listOf(
        sec(
            "The short version",
            "Your gameplan is how you play: an offense, a defense, a starter and a backup at each position, minutes and shot usage.",
        ),
        sec(
            "Offense",
            "• Balanced: take what the defense gives you.",
            "• Inside Attack: pound the paint and get to the line.",
            "• Perimeter: spread the floor and shoot threes.",
            "• Run & Gun: push the pace for more possessions.",
            "• Grind It Out: slow it down and work for good shots.",
        ),
        sec(
            "Defense",
            "• Man-to-Man: solid, no-frills defense.",
            "• 2-3 Zone: protects the paint, gives up threes.",
            "• Full-Court Press: forces turnovers but allows easy looks.",
            "• Pack Line: walls off the rim, forces fewer turnovers.",
        ),
        sec(
            "Rotation",
            "Each position has a starter and a backup. Starter minutes decide how the 40 minutes split, and the backup plays the rest. Usage decides who takes the shots.",
            "Auto lineup picks your best players and minutes, and fixes the lineup whenever the roster changes.",
        ),
        sec(
            "Minutes and mood",
            "Players compare their minutes with what their talent expects: your top five expect starter minutes, the next three a real role. Big gaps make players unhappy.",
        ),
    ),
    "Training" to listOf(
        sec(
            "The short version",
            "Players get better two ways: XP during the season and development over the summer.",
        ),
        sec(
            "XP and level-ups",
            "Every game earns XP from minutes and production. Your coach's Develop rating and the player's room to grow (potential minus overall) speed it up. Every 500 XP is a level-up.",
        ),
        sec(
            "Training focus",
            "Each level-up raises the three ratings of the player's training focus by 1. The focus is an archetype: 20 play styles, from Floor General to Stretch Five. Choose Manual to bank level-ups and spend them later.",
        ),
        sec(
            "Summer development",
            "In the offseason players grow toward their potential. Bigger gaps grow faster, and a strong Develop rating, Gym Rat and Late Bloomer all speed it up.",
            "Now and then a player's game takes off over the summer. Walk-ons who break out earn the Late Bloomer trait.",
        ),
    ),
    "Program" to listOf(
        sec(
            "The short version",
            "Prestige is your program's headline number, from 0 to 100. It moves once a season and slowly, so one great year nudges it and one bad year doesn't wreck it.",
            "Four numbers sit next to it. Legacy is how the sport sees your program over the long run. Buzz is short-term national heat. Conference Exposure is how visible your league is. Program Pull blends it all into one attractiveness rating.",
        ),
        sec(
            "How Prestige moves",
            "After each season, Prestige moves about 22% of the way toward what the season earned, plus 4 more for each season in a row it went up (up to three), so a program on a run climbs faster. A season well above your standing is always worth at least +2. On the way down it slides 14% of the way. A season earns credit for:",
            "• Your record.",
            "• Your conference finish, plus a conference tournament title.",
            "• Your NCAA Tournament run.",
            "• Quadrant 1 wins.",
            "• Players taken in the most recent draft.",
            "• National awards.",
            "Rivalry games move Prestige during the season, and an NIT title adds 2 or 3 on top. During the season, the Program panel shows what the season is on pace to earn and what a Sweet 16 run would add; the season review breaks down what it earned, piece by piece.",
            "Below 70, records and conference titles do most of the work. Above 70, the climb needs national proof: quality wins and deep March runs.",
            "Your conference doesn't wall you in, but it pushes back. Records in a low-exposure league count for less, and a high-exposure league adds a little on its own. A small school can become a national brand, but it gets there in March, not just by winning its league.",
            "• Round of 32 most years supports the low 80s.",
            "• Sweet 16 most years supports the high 80s.",
            "• One Elite Eight supports the high 80s on its own.",
            "• A Final Four or a title unlocks 90 and beyond.",
        ),
        sec(
            "Resume ceiling",
            "Above 70, your last 10 NCAA Tournaments set a soft ceiling on Prestige. Past it, climbing slows to 40% of the usual pace, and above 80 you drift back toward the ceiling each season. Seasons before your save began count at the level your Legacy implies.",
            "A great season can still push you up, but holding a higher tier takes the March history to back it.",
        ),
        sec(
            "Program terms",
            "• Prestige: the 0 to 100 brand. The number in team headers. It drives donor money and job offers.",
            "• Legacy: the long-run baseline. It moves a few points a decade, a little faster with Final Fours and titles. It cushions a fallen power and slows a one-year spike.",
            "• Buzz: short-term heat from ranked wins, upsets, rivalry wins, conference tournament titles and March wins (Cinderella runs count extra). It cools every game and halves over the summer.",
            "• Program Pull: 45% Prestige, 15% Legacy, and 10% each from Conference Exposure, coach recruiting, recent NCAA results and Buzz. Recruits see it as Recruiting Pull.",
            "• Conference Exposure: your league's national visibility, from its level and its members' prestige.",
            "• Expected Wins: the preseason target from your actual schedule and team strength. Your season and job security are judged against it.",
        ),
        sec(
            "Why the numbers can disagree",
            "A Cinderella run spikes Buzz, so Program Pull jumps before Prestige and Legacy catch up.",
            "A fallen power can have low Prestige but strong Legacy. Legacy pulls it back up faster after a good season.",
            "A team in a high-exposure league carries more national weight than a similar team in a quiet one, because beating it means more.",
        ),
        sec(
            "Where you see them",
            "• Team header: Prestige.",
            "• Program tab and team pages: Prestige, Legacy, Buzz, Program Pull, Conference Exposure, Expected Wins and the resume ceiling.",
            "• Recruit tab and prospect pages: Recruiting Pull.",
            "• Season review: what moved your Prestige this year.",
        ),
    ),
)

private val recruitingGuide: List<GSection> = listOf(
    sec(
        "Overview",
        "Each year a class of 1,600 high-school prospects enters the board, ranked nationally and by position, from 2 to 5 stars. In the offseason the transfer portal adds players leaving other schools.",
        "During the season you scout and build your board. Scouting a prospect costs 5 points and shows his real ratings, and scouting points refill every game day.",
        "After the season, recruiting runs over 5 rounds. Your program gets one point budget for the whole cycle, set by your Recruiting Pull and your coach's recruiting rating. Before each round, rival schools make their moves. Then you set your points, preview your position and lock in.",
    ),
    sec(
        "Recruiting Pull",
        "Recruiting strength isn't Prestige alone. Recruiting Pull is your Program Pull: Prestige blended with Legacy, conference exposure, coach recruiting, recent March results and Buzz.",
        "A tournament run creates Buzz and opens doors quickly, but the biggest long-term jumps need Prestige and Legacy to catch up.",
        "• Prestige: the durable brand.",
        "• Legacy: how lasting the sport thinks that brand is.",
        "• Buzz: short-term heat from big wins and March runs.",
        "• Conference Exposure: how visible your league makes your games.",
    ),
    sec(
        "Points",
        "Points on a prospect raise his interest every round, with diminishing returns: 100 points add twice what 25 do, not four times. Points stay where you put them until you move them, and when a prospect commits anywhere, the points on him come back to you.",
    ),
    sec(
        "Interest",
        "Interest, from 0 to 100, shows how strong your position is in his race. Early in the cycle it's momentum more than certainty. Late in the cycle it's a strong sign of where he'll go.",
        "How much your points count with a prospect depends on:",
        "• Program strength: your Recruiting Pull against what he's looking for.",
        "• Coach recruiting ability.",
        "• Location: in-state and same-region schools have an edge.",
        "• Pro pipeline: your recent draft picks.",
        "• Playing time: how many players at his position are already as good as him.",
        "• Roster crowding at his position.",
        "• His own preference.",
        "Your NIL offer works differently: it adds its own interest every round, on top of your points, even with no points on him. Matching his ask adds about 8 a round.",
        "• Competition from other schools.",
        "Open a prospect to see each one rated for your school.",
    ),
    sec(
        "Preferences",
        "Every prospect has one thing he cares about most:",
        "• Close to home: location counts double.",
        "• Early playing time: playing time counts double.",
        "• Winning now: your current record matters.",
        "• A big-name program: program strength counts extra.",
        "• NIL money: NIL offers count 60% more.",
        "• A path to the pros: draft picks count three times as much.",
    ),
    sec(
        "Locking in",
        "Interest moves exactly as the preview shows. Then, when you lock in:",
        "• Some prospects commit. He chooses among the schools near the top of his race, so the leader usually wins, but a close second has a real chance.",
        "• Some stay open for the next round.",
        "• Schools that fall far behind without putting points in drop out, and so do schools out of scholarships.",
        "• New schools enter.",
    ),
    sec(
        "End of cycle",
        "Later rounds produce more commitments. After round 5, prospects with a clear favorite sign late. Programs with open scholarships pick up some of the best unsigned players, and the rest go unsigned. Any spot you still have open goes to walk-on tryouts.",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideSheet(vm: GameViewModel, guide: Guide) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { vm.guide = null }, sheetState = state, containerColor = P.bg,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(40.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(P.line)) },
    ) { GuideContent(vm, guide, Modifier.fillMaxHeight(0.94f)) }
}

@Composable
fun GuideContent(vm: GameViewModel, guide: Guide, modifier: Modifier = Modifier) {
        Column(modifier) {
            Row(Modifier.padding(start = Gutter + 4.dp, end = Gutter, top = 6.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Info, null, tint = P.sub, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(guide.title, style = T.title.copy(fontSize = 28.sp), color = P.ink)
            }
            val sections = when (guide) {
                Guide.PROGRAM -> {
                    Segmented(programTabs.map { it.first }, vm.guideTab.coerceIn(0, programTabs.lastIndex), { vm.guideTab = it })
                    Spacer(Modifier.height(4.dp))
                    programTabs[vm.guideTab.coerceIn(0, programTabs.lastIndex)].second
                }
                Guide.RECRUITING -> recruitingGuide
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                for (s in sections) item(key = "${vm.guideTab}-${s.title}") { GuideBlock(s) }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
}

@Composable
private fun GuideBlock(s: GSection) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter + 4.dp)) {
        Spacer(Modifier.height(22.dp))
        Text(s.title.uppercase(), style = T.label, color = P.sub)
        Spacer(Modifier.height(8.dp))
        for (line in s.lines) {
            if (line.startsWith("• ")) {
                Row(Modifier.padding(vertical = 4.dp)) {
                    Box(Modifier.padding(top = 9.dp).size(6.dp).clip(CircleShape).background(P.accent))
                    Spacer(Modifier.width(12.dp))
                    Text(line.removePrefix("• "), style = T.body, color = P.ink)
                }
            } else {
                Text(line, style = T.body, color = P.ink, modifier = Modifier.padding(vertical = 5.dp))
            }
        }
    }
}

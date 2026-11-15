package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * Life away from the games:
 *  - booster requests (college coaching): the people who write the checks want something; money,
 *    job security, the fans and the locker room hang on the answer;
 *  - life off the court (player careers): endorsements, agents, family, fame, nights out and teammates;
 *    followers, leadership, legs, money and mood hang on it.
 */
object OffCourt {
    // =====================================================================================
    // Booster requests
    // =====================================================================================

    class Ask(val text: String, val cash: Int = 0, val security: Int = 0, val fans: Double = 0.0, val mood: Int = 0, val recruits: Int = 0, val reaction: String)
    class Request(val who: String, val title: String, val text: String, val asks: List<Ask>)

    fun boostersActive(lg: League) = !lg.pro && !lg.playerMode && lg.userTeamId >= 0

    private fun a(text: String, cash: Int = 0, security: Int = 0, fans: Double = 0.0, mood: Int = 0, recruits: Int = 0, reaction: String) = Ask(text, cash, security, fans, mood, recruits, reaction)

    private val BOOSTERS = listOf("Big Earl Whitcomb, car dealerships", "Margaret Thorne, real estate", "Dr. Vance Holloway, class of '81", "The Pruitt family foundation",
        "Duke Abernathy, oil and gas", "Sheila Kaminski, the steakhouse on Main", "Reggie Banks, former player turned investor", "Walter Ames III, the bank",
        "The Hartwell brothers, construction", "Connie Delgado, the furniture chain")

    private class BoosterKind(val title: String, val text: String, val asks: List<Ask>)

    private val REQUESTS: List<BoosterKind> = listOf(
        BoosterKind("Play my son", "My son walked on this year. He's working hard. A few minutes in a home game would mean the world to our family. And to my giving.", listOf(
            a("Minutes are earned. I can't promise anything.", cash = -150, security = 1, mood = 2, reaction = "The pledge shrank. The locker room respected it."),
            a("He'll get a few minutes when the game's decided.", cash = 250, mood = -1, reaction = "Garbage time, a standing ovation, and a check that cleared."),
            a("He'll start senior night. That's a promise.", cash = 400, security = -1, mood = -3, reaction = "Generous. A few players are grumbling about favoritism."))),
        BoosterKind("Hire my nephew", "My nephew needs a job. Student manager, video, anything. He's a good kid.", listOf(
            a("We'll find something for him.", cash = 200, security = -1, reaction = "He's a good kid, as promised. He's also terrible at video."),
            a("Have him apply like everyone else.", cash = -100, security = 1, reaction = "Fair, and the athletic department noticed."),
            a("I'll introduce him to a few people in town.", cash = 100, reaction = "He landed somewhere. Everybody's happy."))),
        BoosterKind("Schedule my alma mater", "I went to a small school two hours from here. Schedule them next season and I'll cover the travel, and then some.", listOf(
            a("Done. It'll be good for both programs.", cash = 300, fans = -1.0, reaction = "An easy win on the schedule, a full wallet."),
            a("Our schedule is set by what gets us into March.", cash = -100, security = 1, reaction = "Principled."),
            a("Let's do a charity exhibition instead.", cash = 150, fans = 1.0, reaction = "The exhibition raised money for the local hospital."))),
        BoosterKind("A seat on the bench", "I've given a lot to this program. I'd like to sit behind the bench for the rivalry game.", listOf(
            a("You've earned it. Behind the bench it is.", cash = 200, reaction = "He spent the whole game yelling at the officials."),
            a("I'll get you courtside, not on the bench.", cash = 100, reaction = "Close enough. He's happy."),
            a("The bench is for the team.", cash = -150, security = 1, mood = 1, reaction = "The players appreciated it more than he did."))),
        BoosterKind("A bigger donation, with strings", "I'll double my gift this year. I just want a say in who we recruit.", listOf(
            a("Thank you, but recruiting stays with the staff.", cash = -200, security = 2, reaction = "He gave anyway, a little less. The AD was proud."),
            a("You can sit in on a recruiting meeting.", cash = 500, security = -2, reaction = "He had opinions. Many opinions."),
            a("Let's talk about funding a recruiting coordinator instead.", cash = 300, recruits = 2, reaction = "Smart. The money went to the right place."))),
        BoosterKind("The new practice facility", "I'll put my name on a new practice facility. But I want it done my way: marble in the lobby.", listOf(
            a("Let's build it. Marble and all.", cash = -300, fans = 2.0, recruits = 3, reaction = "Recruits gasp when they walk in. So does the budget office."),
            a("Put the money into the weight room and nutrition.", cash = 100, security = 1, mood = 3, reaction = "The players feel the difference."),
            a("Not now. We have bigger needs.", cash = -100, reaction = "He's still thinking about it."))),
        BoosterKind("Fire the assistant", "Your top assistant embarrassed me at a booster dinner. I want him gone.", listOf(
            a("He's my guy. That's not happening.", cash = -300, security = -1, mood = 2, reaction = "Loyalty costs money. The staff noticed."),
            a("I'll have him apologize.", cash = 100, reaction = "The apology was stiff. It worked."),
            a("Let me look into it.", reaction = "A stall. He'll ask again."))),
        BoosterKind("A trip on the team plane", "My grandson's birthday is coming up. Could he fly with the team on the next road trip?", listOf(
            a("Sure. He'll have the time of his life.", cash = 150, reaction = "The players made him an honorary manager."),
            a("He can come to shootaround instead.", cash = 50, mood = 1, reaction = "The best day of his life, still."),
            a("Compliance won't allow it.", cash = -50, security = 1, reaction = "By the book."))),
        BoosterKind("The NIL collective", "The collective is ready to spend big on your next class. I just need to know you'll use it.", listOf(
            a("We'll put every dollar to work.", cash = 400, recruits = 4, security = -1, reaction = "The war chest is full. So is the NCAA's attention."),
            a("Fair market deals only.", cash = 200, recruits = 2, security = 1, reaction = "Clean and effective."),
            a("Hold it for retention. Keep the guys we have.", cash = 250, mood = 4, reaction = "The roster felt valued."))),
        BoosterKind("Bring back the old uniforms", "Wear the throwbacks from my era for one game. I'll make it worth your while.", listOf(
            a("Throwback night. Let's do it.", cash = 250, fans = 3.0, reaction = "Sold out. The jerseys are on sale everywhere."),
            a("Players pick the uniforms.", cash = -50, mood = 2, reaction = "They picked the black ones. As always."),
            a("Let's sell them in the team store instead.", cash = 150, fans = 1.0, reaction = "Best-selling item of the year."))),
        BoosterKind("Talk at my company", "Speak at my company's annual meeting. Bring a couple of players.", listOf(
            a("I'll do it, with two seniors.", cash = 200, mood = 2, fans = 1.0, reaction = "The seniors loved the free steak dinner."),
            a("I'll come alone. Players need rest.", cash = 100, reaction = "A solid speech. A solid check."),
            a("In the offseason.", cash = -50, reaction = "He's disappointed, but understanding."))),
        BoosterKind("The coach's contract", "A few of us think you deserve an extension. We'd help pay for it, if you'll commit long term.", listOf(
            a("I'm here for the long haul.", cash = 200, security = 4, fans = 2.0, reaction = "The fan base exhaled. The extension talks began."),
            a("Let's win first. Then we'll talk.", security = 2, reaction = "Confident."),
            a("I don't make promises about the future.", security = -2, reaction = "The boosters noticed."))),
        BoosterKind("Name the court", "I want my family's name on the court. The price is generous.", listOf(
            a("It's a deal.", cash = 600, fans = -2.0, reaction = "Fans hate the new name. The budget doesn't."),
            a("The court stays named for the legends.", cash = -100, fans = 2.0, reaction = "The old-timers stood a little taller."),
            a("Name the practice gym instead.", cash = 300, reaction = "Everybody wins."))),
        BoosterKind("A recruit's family", "I know the family of a top recruit. Let me help get them to campus.", listOf(
            a("Compliance has to sign off on everything.", cash = 0, security = 2, recruits = 1, reaction = "Done right, and the family visited."),
            a("Please do. Whatever it takes.", recruits = 4, security = -3, reaction = "The visit happened. So did a phone call from compliance."),
            a("Stay out of recruiting. Please.", security = 1, cash = -100, reaction = "He backed off, a little offended."))),
        BoosterKind("Cancel the press conference", "I didn't like what you said at the last press conference. Tone it down.", listOf(
            a("I'll say what I think. Always.", cash = -200, fans = 2.0, reaction = "The fans love the candor. He doesn't."),
            a("Point taken. I'll be more careful.", cash = 100, security = 1, reaction = "Diplomatic."),
            a("Come to the next one and ask the questions yourself.", cash = 50, fans = 1.0, reaction = "He laughed. He might actually do it."))),
        BoosterKind("The golf outing", "The annual golf outing is next week. The big donors expect you there.", listOf(
            a("Wouldn't miss it.", cash = 300, mood = -1, reaction = "Three hundred thousand raised. One practice missed."),
            a("I'll send my top assistant.", cash = 100, reaction = "Fine, but not the same."),
            a("I'll come for dinner after practice.", cash = 200, reaction = "The best of both worlds."))),
        BoosterKind("Fix the parking", "Season-ticket holders can't find parking. Fix it, or I'm not renewing.", listOf(
            a("I'll take it to the AD personally.", security = 1, fans = 2.0, reaction = "A new lot opened by February."),
            a("I coach basketball, not parking.", cash = -100, reaction = "Fair, but he's annoyed."),
            a("Free shuttles from downtown. On me.", cash = -100, fans = 3.0, reaction = "The shuttle has its own fan base now."))),
        BoosterKind("Our team at the tournament", "I want the whole booster club in the hotel with the team if we make the tournament.", listOf(
            a("Same hotel, different floor.", cash = 200, reaction = "The boosters loved it. Curfew got interesting."),
            a("The team stays alone. Focus first.", cash = -100, security = 1, mood = 2, reaction = "The players appreciated the quiet."),
            a("Let's throw a send-off party instead.", cash = 150, fans = 2.0, reaction = "A packed send-off, and the team left in peace."))),
    )

    private fun boosterStamp(lg: League) = lg.season * 100 + lg.day / 12

    fun booster(lg: League): Request? {
        if (!boostersActive(lg) || lg.phase != Phase.REGULAR || lg.user.gp < 2 || lg.deskBoosterStamp == boosterStamp(lg)) return null
        val rnd = kotlin.random.Random(boosterStamp(lg) * 6151L + lg.userTeamId)
        if (rnd.nextDouble() > 0.55) return null
        val k = REQUESTS[rnd.nextInt(REQUESTS.size)]
        return Request(BOOSTERS[rnd.nextInt(BOOSTERS.size)], k.title, k.text, k.asks)
    }

    fun answerBooster(lg: League, rq: Request, i: Int): String {
        val ask = rq.asks.getOrNull(i) ?: return ""
        val u = lg.user
        Business.ensure(lg, u)
        u.cash = (u.cash + ask.cash).coerceAtLeast(0L)
        lg.userCoach.security = (lg.userCoach.security + ask.security).coerceIn(0, 100)
        Fans.ensure(u)
        u.fans = (u.fans + ask.fans).coerceIn(5.0, 100.0)
        if (ask.mood != 0) for (p in lg.rosterOf(u)) p.happiness = (p.happiness + ask.mood).coerceIn(0, 100)
        if (ask.recruits > 0) for (r in lg.recruits) if (r.committed < 0 && (r.onBoard || u.id in r.interest)) r.interest[u.id] = ((r.interest[u.id] ?: 4) + ask.recruits).coerceAtMost(100)
        lg.deskBoosterStamp = boosterStamp(lg)
        return ask.reaction
    }

    fun skipBooster(lg: League) { lg.deskBoosterStamp = boosterStamp(lg) }

    // =====================================================================================
    // Life off the court (player careers)
    // =====================================================================================

    /** One way to handle it: followers (share, 0.05 = +5%), leadership, legs (fatigue), money (thousands), his mood and his teammates'. */
    class Move(val text: String, val followers: Double = 0.0, val leadership: Int = 0, val fatigue: Int = 0, val money: Int = 0, val mood: Int = 0, val mates: Int = 0, val reaction: String)
    class Life(val title: String, val text: String, val moves: List<Move>)

    fun lifeActive(lg: League) = lg.playerMode && lg.road != null && lg.hero?.teamId?.let { it >= 0 } == true

    private fun m(text: String, followers: Double = 0.0, leadership: Int = 0, fatigue: Int = 0, money: Int = 0, mood: Int = 0, mates: Int = 0, reaction: String) =
        Move(text, followers, leadership, fatigue, money, mood, mates, reaction)

    private class LifeKind(val title: String, val text: (League, Player) -> String, val moves: (League, Player) -> List<Move>, val proOnly: Boolean = false, val collegeOnly: Boolean = false)

    private val LIFE: List<LifeKind> = listOf(
        LifeKind("A shoe company calls", { lg, _ -> if (lg.pro) "A sneaker brand wants you in their next campaign. Shoot day is on an off day." else "A sneaker brand wants you in a local NIL campaign." }, { lg, _ -> listOf(
            m("Do the shoot.", followers = 0.06, fatigue = 4, money = if (lg.pro) 250 else 40, reaction = "The ad dropped. Everyone saw it."),
            m("Only if my teammates are in it too.", followers = 0.03, money = if (lg.pro) 120 else 20, mates = 5, reaction = "The whole starting five in one ad. The locker room loved it."),
            m("Not during the season.", leadership = 2, reaction = "Focused. Coach noticed.")) }),
        LifeKind("A party invitation", { _, _ -> "A big party tonight, the night before a game. Everybody's going." }, { _, _ -> listOf(
            m("Stay in. Ice bath and film.", leadership = 3, fatigue = -6, reaction = "Fresh legs tomorrow."),
            m("Go for an hour, then home.", followers = 0.02, fatigue = 2, mates = 2, reaction = "Showed face, left early. Smart."),
            m("All night.", followers = 0.05, fatigue = 10, leadership = -3, mates = 1, reaction = "Great photos. Heavy legs.")) }),
        LifeKind("Mom's birthday", { _, _ -> "It's your mom's birthday. She's three hours away, and you have practice tomorrow morning." }, { _, _ -> listOf(
            m("Drive home for dinner and back by midnight.", fatigue = 5, mood = 10, reaction = "She cried. You'll never regret it."),
            m("Video call and send flowers.", mood = 4, reaction = "She loved the flowers."),
            m("Fly her in for the next home game.", money = -5, mood = 8, followers = 0.01, reaction = "She held up a sign with your face on it. The camera found her.")) }),
        LifeKind("An agent's dinner", { lg, _ -> if (lg.pro) "A rival agency wants to take you to dinner and talk about switching." else "An agent wants to take you and your parents to dinner." }, { lg, _ -> listOf(
            m(if (lg.pro) "Hear them out." else "Tell compliance and say no.", money = if (lg.pro) 150 else 0, leadership = if (lg.pro) 0 else 2, reaction = if (lg.pro) "A better deal on the table. Your current agent noticed." else "Clean. The coaches appreciated it."),
            m("Bring my family. Let them decide.", mood = 3, reaction = "Your parents asked all the right questions."),
            m("I'm focused on basketball.", leadership = 3, reaction = "No distractions.")) }),
        LifeKind("A rookie needs help", { _, _ -> "One of the young guys is struggling. He keeps asking you questions after practice." }, { _, _ -> listOf(
            m("Stay late and work with him.", leadership = 5, fatigue = 4, mates = 4, reaction = "He's been better every day since."),
            m("Invite him to your morning workouts.", leadership = 4, fatigue = 2, mates = 3, reaction = "Two of you in the gym at 6 a.m. now."),
            m("He'll figure it out.", mates = -3, reaction = "He did, slowly. He noticed you didn't help.")) }),
        LifeKind("Viral moment", { _, _ -> "A clip of you from the last game is everywhere. Your phone won't stop buzzing." }, { _, _ -> listOf(
            m("Post the clip with a caption.", followers = 0.08, reaction = "Even more views."),
            m("Credit your teammates in the post.", followers = 0.05, mates = 4, reaction = "The team reposted it."),
            m("Phone off. Next game.", leadership = 2, reaction = "Locked in.")) }),
        LifeKind("A charity event", { _, _ -> "A children's hospital asked if you'd visit before the next home game." }, { _, _ -> listOf(
            m("Go, and bring signed shoes.", followers = 0.04, mood = 6, leadership = 2, money = -2, reaction = "The best day of your season."),
            m("Bring the whole team.", followers = 0.03, mates = 5, mood = 4, reaction = "The kids lost their minds."),
            m("After the season.", reaction = "They understood.")) }),
        LifeKind("Trash talk", { _, _ -> "A player on your next opponent has been talking about you online." }, { _, _ -> listOf(
            m("Fire back.", followers = 0.06, leadership = -2, reaction = "The internet loved it. Your coach didn't."),
            m("Say nothing. Answer on the court.", leadership = 3, reaction = "Classy. Now back it up."),
            m("Post a highlight with no caption.", followers = 0.04, reaction = "Say less.")) }),
        LifeKind("Late-night gym", { _, _ -> "The gym's empty and the lights are on. You feel like working." }, { _, _ -> listOf(
            m("Get up 500 shots.", fatigue = 6, leadership = 2, reaction = "The jumper feels good."),
            m("Light shooting, then sleep.", fatigue = 1, reaction = "Balance."),
            m("Go home. Rest is work too.", fatigue = -6, reaction = "The legs thanked you.")) }),
        LifeKind("A podcast invite", { lg, _ -> if (lg.pro) "A big basketball podcast wants an hour with you." else "A popular college hoops podcast wants you on." }, { _, _ -> listOf(
            m("Go on and be yourself.", followers = 0.06, reaction = "Great episode. You were funny."),
            m("Go on and talk about the team.", followers = 0.03, mates = 3, reaction = "Your teammates loved the shoutouts."),
            m("Pass.", reaction = "Maybe in the summer.")) }),
        LifeKind("Teammate drama", { _, _ -> "Two teammates aren't speaking to each other. It's affecting practice." }, { _, _ -> listOf(
            m("Get them both to dinner. Fix it.", leadership = 5, mates = 5, reaction = "They're laughing together again."),
            m("Tell the coaches.", leadership = 1, mates = 1, reaction = "The coaches handled it."),
            m("Stay out of it.", mates = -2, reaction = "It's still awkward.")) }),
        LifeKind("Hometown news", { _, p -> "Your old high school wants to retire your jersey." }, { _, _ -> listOf(
            m("Go back for the ceremony.", fatigue = 4, mood = 10, followers = 0.03, reaction = "The whole town showed up."),
            m("Send a video message.", mood = 4, reaction = "They played it on the big screen."),
            m("Donate new uniforms for the team.", money = -10, mood = 8, followers = 0.03, reaction = "The kids wore them with your number on the sleeve.")) }),
        LifeKind("Contract talk", { lg, _ -> "Reporters keep asking about your next contract." }, { _, _ -> listOf(
            m("I want to be here long term.", followers = 0.02, mates = 3, reaction = "The fans loved it."),
            m("My agent handles that.", reaction = "The standard answer."),
            m("I'll get paid. Let's see who pays.", followers = 0.04, mates = -2, reaction = "Every team's GM took note.")) }, proOnly = true),
        LifeKind("Class or the gym", { _, _ -> "A big exam tomorrow. Practice ran long. It's late." }, { _, _ -> listOf(
            m("Study until 2 a.m.", fatigue = 6, mood = 2, reaction = "Passed. Barely awake."),
            m("Get tutoring at lunch tomorrow.", fatigue = 1, reaction = "Passed comfortably."),
            m("Wing it.", mood = -3, reaction = "Not great. The academic advisor wants a word.")) }, collegeOnly = true),
        LifeKind("A local business", { _, _ -> "A local restaurant wants to name a sandwich after you." }, { lg, _ -> listOf(
            m("Absolutely. Make it spicy.", followers = 0.03, money = if (lg.pro) 20 else 5, reaction = "Best seller on the menu."),
            m("Only if a teammate gets one too.", followers = 0.02, mates = 3, reaction = "Now there's a whole menu."),
            m("Donate the proceeds to the food bank.", followers = 0.04, mood = 5, reaction = "The whole city loves you a little more.")) }),
        LifeKind("Bad game, bad comments", { _, _ -> "After a rough game, the comments online are brutal." }, { _, _ -> listOf(
            m("Delete the apps for a week.", mood = 4, leadership = 1, reaction = "Peace and quiet."),
            m("Reply to a few.", followers = 0.03, mood = -3, leadership = -1, reaction = "It got ugly."),
            m("Screenshot them. Fuel.", mood = 1, leadership = 2, reaction = "Every screenshot is on your locker.")) }),
        LifeKind("Recovery day", { _, _ -> "Your body is sore. The trainers recommend a full day off." }, { _, _ -> listOf(
            m("Take it. Cold tub, massage, sleep.", fatigue = -10, reaction = "Like new."),
            m("Light work only.", fatigue = -4, leadership = 1, reaction = "Smart."),
            m("No days off.", fatigue = 4, leadership = 2, reaction = "Respect. Your legs disagree.")) }),
        LifeKind("Big purchase", { lg, _ -> if (lg.pro) "Your first big check cleared. A dealer has a car with your name on it." else "Your NIL money came in. A new car is tempting." }, { lg, _ -> listOf(
            m("Buy the car.", money = if (lg.pro) -150 else -30, followers = 0.03, mood = 5, reaction = "It's beautiful. It's also expensive."),
            m("Save it. Invest it.", mood = 1, reaction = "Your future self says thanks."),
            m("Buy your mom a house first.", money = if (lg.pro) -300 else -40, mood = 10, followers = 0.04, reaction = "The video of her reaction went viral.")) }),
    )

    private fun lifeStamp(lg: League) = lg.season * 100 + lg.day / (if (lg.pro) 16 else 8)

    fun life(lg: League): Life? {
        if (!lifeActive(lg) || lg.phase != Phase.REGULAR || lg.deskLifeStamp == lifeStamp(lg)) return null
        val h = lg.hero ?: return null
        val rnd = kotlin.random.Random(lifeStamp(lg) * 4241L + h.id)
        if (rnd.nextDouble() > 0.6) return null
        val pool = LIFE.filter { !(it.proOnly && !lg.pro) && !(it.collegeOnly && lg.pro) }
        val k = pool[rnd.nextInt(pool.size)]
        return Life(k.title, k.text(lg, h), k.moves(lg, h))
    }

    fun live(lg: League, l: Life, i: Int): String {
        val mv = l.moves.getOrNull(i) ?: return ""
        val r = lg.road ?: return ""
        val h = lg.hero ?: return ""
        if (mv.followers != 0.0) r.followers = (r.followers * (1 + mv.followers) + (if (mv.followers > 0) 50 else 0)).roundToInt().coerceAtLeast(0)
        r.leadership = (r.leadership + mv.leadership).coerceIn(0, 100)
        r.fatigue = (r.fatigue + mv.fatigue).coerceIn(0, 100)
        r.earned = (r.earned + mv.money).coerceAtLeast(0)
        h.happiness = (h.happiness + mv.mood).coerceIn(0, 100)
        if (mv.mates != 0) lg.teams.getOrNull(h.teamId)?.let { t -> for (p in lg.rosterOf(t)) if (p.id != h.id) p.happiness = (p.happiness + mv.mates).coerceIn(0, 100) }
        lg.deskLifeStamp = lifeStamp(lg)
        return mv.reaction
    }

    fun skipLife(lg: League) { lg.deskLifeStamp = lifeStamp(lg) }
}

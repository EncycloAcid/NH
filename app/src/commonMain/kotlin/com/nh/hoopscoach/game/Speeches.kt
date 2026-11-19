package com.nh.hoopscoach.game

/**
 * The words for the nights a career is remembered for: title parades, awards nights and Hall of Fame
 * inductions. Every speech is a pair (what the button says, what he says at the microphone), grouped by
 * intent; each ceremony offers one pair from each intent, in the same order, so the choice's effect
 * still matches. Placeholders in braces are filled from the night: {town}, {school}, {team}, {season},
 * {coach}, {star}, {runner}, {titles}.
 */
object Speeches {
    class Line(val label: String, val speech: String)

    private fun l(label: String, speech: String) = Line(label, speech)

    /** Fills a template from [ctx]. Unknown or empty placeholders drop out gracefully. */
    fun fill(s: String, ctx: Map<String, String>): String {
        var out = s
        for ((k, v) in ctx) out = out.replace("{$k}", v)
        return out.replace(Regex("\\{[a-z]+\\}"), "").replace("  ", " ")
    }

    /** One line from each pool, in order, picked by [seed] so a ceremony keeps its words if redrawn. */
    fun pickSet(seed: Long, ctx: Map<String, String>, vararg pools: List<Line>): Pair<List<String>, List<String>> {
        val r = kotlin.random.Random(seed)
        val picked = pools.map { it[r.nextInt(it.size)] }
        return picked.map { fill(it.label, ctx) } to picked.map { fill(it.speech, ctx) }
    }

    fun pick(seed: Long, pool: List<String>, ctx: Map<String, String> = emptyMap()): String = fill(pool[kotlin.random.Random(seed).nextInt(pool.size)], ctx)

    // ---------- the parade: the next one, the city, the team ----------

    val PARADE_MORE = listOf(
        l("We're coming back for another one.", "Enjoy it tonight. Tomorrow we start chasing the next one. Nobody's taking this from us. We're coming back for another one!"),
        l("This is the first of many.", "I'm going to say something and I want you to remember it: this is the first of many. Not the last. The first."),
        l("Get used to this.", "{town}, get used to this. Get used to the confetti, get used to the parade, get used to this trophy. Because we're not done."),
        l("Save the date for next year.", "Don't throw away those signs. Don't put away those shirts. Same time next year. Same route. Same trophy."),
        l("We want two.", "One is nice. One is history. But I didn't come here for one. Say it with me: we want two!"),
        l("Nobody's knocking us off.", "Everybody in the country is going to be coming for us next year. Good. Let them come. They'll find out what we found out: this team doesn't break."),
        l("The standard just went up.", "From today, this is the standard. Not the tournament. Not the second weekend. This. Anything less, and we'll know we fell short."),
        l("Back to work Monday.", "I'm going to enjoy every second of today. And on Monday, I'm going to be back in my office watching film. That's how you get another one."),
        l("Dynasty starts today.", "They're going to write a lot about this team. I want the next word they write to be dynasty. Let's go earn it."),
        l("This is just the beginning.", "You think this is loud? Wait until we do it again. This is just the beginning, {town}."),
        l("I'm not done.", "People keep asking me how it feels to finally do it. I'll tell you how it feels: it feels like I want to do it again."),
        l("The target's on us. Good.", "Next year, every team on our schedule circles our name. Every crowd boos us. Every coach saves his best for us. I can't wait."),
        l("Bring the trophy back next year.", "We're going to give this trophy back in a few months. Only for a minute. Only so we can win it again."),
        l("Start the clock on No. 2.", "The clock on title number two starts right now. Ten. Nine. Eight. I'm kidding. Kind of. Let's go get another one!"),
        l("We'll be right back here.", "Same buses. Same streets. Same people. Same trophy. We'll be right back here, {town}. I promise you that."),
        l("Champions don't get comfortable.", "The hardest thing in sports is winning when everyone expects you to. That's the challenge now. And I love a challenge."),
        l("Let's make this a habit.", "Banners are nice. A row of them is better. Let's make this a habit, {town}."),
        l("Hungry already.", "I've been a champion for about thirty-six hours, and I'm already hungry. That's the truth. Let's run it back."),
    )

    val PARADE_CITY = listOf(
        l("This one is for you, {town}.", "You waited for this. You filled that building every night, you traveled, you never stopped believing. This trophy is yours, {town}. It belongs to you!"),
        l("To the fans who never left.", "To the ones who bought tickets in the bad years, who stayed through the losing streaks, who kept the faith when nobody else did: this is for you."),
        l("You were our sixth man.", "Every night, you were our sixth man. You were worth five points. In March, you were worth ten. This trophy has your fingerprints on it."),
        l("For every kid in {town}.", "There's a kid in this crowd with a ball under his arm who thinks this is the coolest thing he's ever seen. Kid, it is. Go get your own one day."),
        l("This city deserved this.", "This city has waited long enough. Waited through the near misses and the heartbreak. No more waiting. {town}, you're champions!"),
        l("For the ones who aren't here.", "Somewhere there's a grandfather who watched every game from his recliner and didn't get to see this one. This is for him. For all of them."),
        l("Look at all of you.", "I've been looking out at this crowd for an hour, and I can't see where it ends. Look at all of you. Thank you. Thank you. Thank you."),
        l("You carried us.", "When we were tired, you got loud. When we were down, you got louder. You carried us to this, {town}."),
        l("This is your parade.", "We're up here on the buses, but make no mistake: this is your parade. We just got the good seats."),
        l("Home of the champions.", "From now on, when people drive into {town}, they're driving into the home of the champions. Put it on the signs!"),
        l("Every road trip, every drive.", "I saw you in airports. I saw you in hotel lobbies at two in the morning. I saw you in buildings a thousand miles from home. You never missed. Neither did we."),
        l("To the people who raised me.", "This town raised me. It taught me how to work and how to lose and how to get back up. Today it taught me how to win. Thank you, {town}."),
        l("Tell your grandkids.", "One day you're going to tell your grandkids you were standing on this street on this day. Tell them it was loud. Tell them it was perfect."),
        l("The best fans in the country.", "I've coached in a lot of places. I've been in a lot of buildings. I'm telling you, there are no fans in this country like the fans in {town}."),
        l("We did it together.", "Not me. Not the players. Not the staff. All of us. Every one of you. We did it together, and nobody can take it away."),
        l("For the believers.", "Back in the fall, when people picked us to finish wherever they picked us, you believed. This one's for the believers."),
        l("Go to work late tomorrow.", "I'm officially asking every boss in {town} to let people come in late tomorrow. Tell them Coach said it was okay."),
        l("You earned this too.", "You paid for the tickets. You lost your voices. You sat in the cold for this parade. You earned this trophy as much as anybody up here."),
    )

    val PARADE_TEAM = listOf(
        l("I want to thank these guys.", "Look at these guys behind me. Every one of them gave up something for this. I love every one of them. This is their day."),
        l("Every one of them sacrificed.", "Some of these guys could've scored more somewhere else. Some could've played more. They chose to win. That's why we're standing up here."),
        l("To my assistant coaches.", "My assistants don't get their names in the paper. They watched more film than anyone, slept less than anyone, and they deserve this more than me."),
        l("To the guys at the end of the bench.", "The guys who didn't play much: they guarded our best players every day in practice. They made us. Stand up, fellas. This crowd knows."),
        l("To the seniors.", "To my seniors: four years ago you took a chance on us. You stayed when it was hard. You leave as champions. You'll never pay for a meal in this town again."),
        l("To {star}.", "{star}. People will talk about the shots, the points, the highlights. I'll remember the practices. Nobody worked harder. This is your day too."),
        l("Brothers.", "These aren't teammates. They're brothers. I've watched them fight for each other all year. Today they get to celebrate together. Forever."),
        l("To the trainers and managers.", "The trainers who taped every ankle. The managers who rebounded every shot at midnight. The people nobody sees. This ring is yours too."),
        l("I'm just lucky to coach them.", "I've been called a lot of things this week. Genius, champion, all of it. The truth is I'm just lucky to coach these men."),
        l("They never quit.", "There were nights this season when we were down and it looked bad. Not one of these guys ever quit. Not once. That's the whole story."),
        l("To the families.", "To the moms and dads who sent their sons here and trusted us with them: thank you. They're coming home champions."),
        l("This team was special.", "I've coached a lot of teams. I've never coached one that loved each other like this one. That's why we won."),
        l("Defense won this.", "Everybody talks about the points. This team won it on defense, every night, every possession. Champions guard. These guys guard."),
        l("To my family.", "To my wife and my kids, who gave up a lot of dinners and weekends for this: Dad's coming home with a trophy. And I'm sleeping for a week."),
        l("The guys who aren't here.", "There are players who helped build this who graduated or moved on before today. This trophy has their names on it too. Thank you, fellas."),
        l("Credit to {runner}.", "Before anything else: {runner} was a great opponent. It took everything we had. Respect. Now, {town}: let's celebrate!"),
        l("Ask them, not me.", "You don't want to hear from me. You want to hear from them. Fellas, come up here and grab the microphone."),
        l("Every possession mattered.", "We won games this year by one possession, by a stop, by a loose ball. These guys made every one of those plays. Every single one."),
    )

    // ---------- awards night: the team, the doubters, the family ----------

    val AWARD_TEAM = listOf(
        l("Thank the team.", "This award has my name on it, but it belongs to everybody in that locker room. I don't win anything without them."),
        l("It's a team award.", "They give this to one person. That's the only mistake they made. It's a team award, and my team won it."),
        l("For my teammates.", "My teammates made every pass, set every screen, ran every sprint. I just finished what they started."),
        l("For the coaches.", "Every coach who ever stayed late to rebound for me: this is yours. I hope you know it."),
        l("I didn't do this alone.", "If you see me alone on this stage, you're looking at it wrong. There are fifteen guys and a staff standing right next to me."),
        l("Credit to the staff.", "I want to thank the people you never see: the strength coaches, the trainers, the managers. They made me who I am."),
        l("We did this together.", "I'm holding the trophy. We earned it together. Tonight I just get to carry it."),
        l("For the guys who pushed me.", "Every day in practice, somebody on my team was trying to take my spot. That's why I'm here. Thank you for pushing me."),
        l("Share it with the room.", "Can everybody from our team stand up? Look at them. That's who won this award."),
        l("To my point guard.", "My point guard found me every time I was open, and a few times I wasn't. Half of this is his."),
        l("Thank the other finalists.", "The other finalists had seasons that would win this most years. I'm honored to be on a list with them. And honored my teammates got me here."),
        l("It's about winning.", "I'd trade this for a ring. My teammates know that. We'll go get the ring next. Thank you."),
    )

    val AWARD_DOUBTERS = listOf(
        l("To everyone who doubted me.", "There were a lot of people who said I couldn't. I heard every one of them. I kept every one of those. This is for you."),
        l("They said I was too small.", "Too small. Too slow. Too raw. I heard all of it. I'm holding the answer right here."),
        l("For the schools that passed.", "Some schools didn't think I was good enough. That's fine. I hope they're watching tonight."),
        l("I kept the list.", "I keep a list of everybody who said I'd never make it. I read it before every game. Tonight I'm putting it away. For now."),
        l("Remember the rankings?", "Remember where I was ranked coming out of high school? I do. I've never forgotten. Thank you for the motivation."),
        l("Nobody picked us.", "Nobody picked us in the preseason. Nobody picked me for this. Funny how that works."),
        l("Doubt is fuel.", "Every time somebody told me no, it gave me something to run on. So, thank you. I guess I owe you all a thank-you card."),
        l("Bet on yourself.", "To every kid who's been told he's not good enough: bet on yourself. They were wrong about me. They might be wrong about you."),
        l("The analysts were wrong.", "I saw a lot of analysts say I'd fade in February. I'd like to thank them for watching all the way to the end."),
        l("Say it to my face now.", "For two years people typed it about me. Now I'm standing up here. Anybody want to say it to my face?"),
        l("Underdog to the end.", "I've been an underdog my whole life. I'll be one tomorrow too. That's fine. Underdogs bite."),
        l("I heard everything.", "I don't read the comments, but my friends do, and they send me every single one. Thanks for the motivation, internet."),
    )

    val AWARD_FAMILY = listOf(
        l("For my family.", "Mom, Dad: every early morning, every long drive, every game you sat through. This is yours."),
        l("For my mom.", "My mom worked two jobs and still made every game she could. Mom, I see you. This trophy goes on your mantel."),
        l("For my grandmother.", "My grandmother used to tell me to keep my elbow in. She's not here to see this, but I kept my elbow in, Grandma."),
        l("For my little brother.", "My little brother is watching at home. Bro, you're better than I was at your age. Keep going. I'll be watching you."),
        l("For my wife and kids.", "To my wife: you held everything together so I could chase this. To my kids: Dad's going to be home more now. Maybe."),
        l("For my hometown.", "Back home, everybody is crammed into the gym watching this on a projector. This is for every one of you."),
        l("For my first coach.", "My first coach drove me to tournaments in his own car and never took a dime. Coach, wherever you are, this is yours."),
        l("For the people who raised me.", "I was raised by a lot of people: aunts, uncles, neighbors, coaches. Every one of you is on this trophy."),
        l("For my dad.", "My dad rebounded for me every night in the driveway until his knees gave out. Dad, I owe you a thousand rebounds."),
        l("For the ones I lost.", "There's someone I lost along the way who believed in me first. I think about him every game. This is for him."),
        l("For my sisters.", "My sisters kept me humble. They still do. They're probably texting each other about my suit right now."),
        l("For the dinner table.", "We didn't have much growing up, but we had a dinner table and a lot of love around it. This is for that table."),
    )

    // ---------- the Hall of Fame: thanks, roots, old scores ----------

    val HOF_THANKS = listOf(
        l("Thank everyone who got me here.", "Nobody gets here alone. My coaches, my teammates, every trainer who taped an ankle at six in the morning, and my family, who sat through every one of them. This is yours."),
        l("A room full of people to thank.", "If I thanked everyone who deserves it, we'd be here until Tuesday. So to everyone who ever helped me: you know who you are, and you know I mean it."),
        l("To my teammates, every one.", "Every great moment I had, a teammate made possible. A screen, a pass, a stop. They're all in this jacket with me."),
        l("To the people behind the scenes.", "The equipment managers. The bus drivers. The academic advisors. The people who never got a cheer. Tonight I'm cheering for you."),
        l("To my coaches.", "Every coach I ever had taught me something. Some of it was basketball. Most of it was life. Thank you."),
        l("To my family.", "My family gave up more for this than I ever did. Every missed birthday, every holiday on the road. This jacket belongs to them."),
        l("To the fans.", "The fans made every night feel like it mattered. They made the hard nights bearable and the good nights unforgettable."),
        l("To the ones who came before.", "I stood on the shoulders of everyone in this room. I'm honored just to be standing next to you."),
        l("To my opponents.", "I want to thank everyone who ever tried to beat me. You made me better. I didn't like you at the time. I like you now."),
        l("Grateful, that's all.", "I don't have a fancy speech. I've just got a lot of gratitude and a jacket that's a little too warm. Thank you."),
    )

    val HOF_ROOTS = listOf(
        l("Talk about where it all started.", "There was a gym and a kid who wouldn't go home. If you're that kid tonight: stay a little longer. It's worth it."),
        l("A hoop on a garage.", "It started with a hoop nailed to a garage, a net that was mostly gone, and a driveway that sloped to the left. I still shoot better going left."),
        l("The playground.", "I learned this game on a playground where if you lost, you sat for an hour. I hated sitting. So I learned to win."),
        l("A borrowed pair of shoes.", "My first pair of real basketball shoes were borrowed from my cousin. Two sizes too big. I stuffed them with socks and never took them off."),
        l("Where I'm from.", "Where I'm from, people don't end up in places like this. I want every kid back home to know: you can."),
        l("My first team.", "My first team didn't win a game all year. We had the best time. I've been chasing that feeling ever since."),
        l("The night gym.", "There was a janitor at my high school who let me in the gym at night. He never told anybody. Mr. Hollis, this is for you."),
        l("The long bus rides.", "I spent more of my childhood on bus rides to tournaments than in my own bed. Every one of those miles brought me here."),
        l("A coach who saw something.", "Somebody saw something in me before I did. One phone call changed my whole life. Never underestimate one phone call."),
        l("Back to the beginning.", "When I close my eyes, I'm still that kid in the gym at nine at night, shooting free throws until I make ten in a row. I never stopped being him."),
    )

    val HOF_SCORES = listOf(
        l("Settle a few old scores.", "A lot of people said I'd never be here. I wrote every one of their names down. I don't need the list anymore. But I kept it."),
        l("About that one call…", "I've waited twenty years to say this in front of a microphone: that was not a foul. I'm still right. Thank you."),
        l("To the recruiters who passed.", "There were coaches who didn't return my calls. Some of them are in this room. Gentlemen, I'm returning your calls now."),
        l("For the critics.", "To every critic who said I'd never win the big one: I hope you've got a good seat. You're going to want to see this jacket up close."),
        l("Still mad about one game.", "I've won a lot of games. I'm going to be honest: I'm still mad about one I lost. I'm putting it to rest tonight. Maybe."),
        l("Proving them wrong, one last time.", "My whole career was about proving somebody wrong. This is the last time. And it feels as good as the first."),
        l("To my rivals.", "To my rivals: you made me miserable and you made me great. I'd do it all again. And I'd still beat you."),
        l("Nobody believed.", "They told me I'd last a year. Then two. Then they stopped telling me anything. Here we are."),
        l("The scouting report was wrong.", "My old scouting report said I couldn't go left, couldn't shoot, and couldn't lead. Two out of three were wrong. I'll let you guess."),
        l("The last word.", "I've been interviewed a thousand times and never got the last word. Tonight I do. Thank you, and good night."),
    )

    // ---------- the parade day: openers, closers and voices ----------

    val PARADE_OPEN_PRO = listOf(
        "The buses rolled out at ten. By noon, downtown {town} was a wall of {team} colors, an estimated {crowd} people deep, confetti in the trees and on the rooftops.",
        "They started lining the route the night before. When the trophy finally came around the corner on the lead bus, {town} lost its mind: an estimated {crowd} people, the biggest crowd the city has seen in years.",
        "Schools let out early. Offices emptied by eleven. By the time the first bus turned onto the parade route, an estimated {crowd} people had packed downtown {town}.",
        "The fire department brought out the ladder trucks and sprayed arcs of water over the route. Under them, {crowd} people chanted the same two words for three hours.",
        "Some of them camped overnight in folding chairs. Some climbed light poles. By noon an estimated {crowd} fans filled every block from the arena to the river.",
        "Duck boats, flatbeds and open-top buses rolled through a blizzard of confetti. City officials put the crowd at {crowd}. It felt like more.",
        "The route was three miles long. It took four hours. Every foot of it was lined with fans, an estimated {crowd} of them, waving towels and holding up kids.",
        "Grown men cried on street corners. Strangers hugged. Somebody's grandmother danced on a mailbox. {town} had waited a long time, and an estimated {crowd} people let it out.",
    )

    val PARADE_OPEN_COLLEGE = listOf(
        "The team buses crawled across campus and into town behind a marching band, students on every balcony and an estimated {crowd} fans along the way.",
        "Back home, {school} turned out: an estimated {crowd} fans, the band, the cheerleaders and the trophy on the hood of the lead bus.",
        "Classes were canceled. Nobody would have gone anyway. An estimated {crowd} people packed the quad, the streets and the steps of every building on the route.",
        "The bell in the old tower rang for an hour straight. Below it, {crowd} fans in {school} colors waited for the buses to come over the hill.",
        "Students climbed trees. Alumni drove in from three states away. An estimated {crowd} people crowded the route from the arena to the main gate.",
        "Main Street was painted in school colors overnight. By noon an estimated {crowd} fans stood shoulder to shoulder from the courthouse to campus.",
        "The pep band played the fight song so many times the trumpets gave out. Nobody minded. An estimated {crowd} fans sang it for them.",
        "Local diners closed for the day and hung signs in the window: GONE TO THE PARADE. So did an estimated {crowd} other people.",
    )

    val PARADE_FIRST = listOf(
        "The first one is the one you never forget.",
        "There will be others, maybe. There will never be another first.",
        "For years the answer to the trivia question was nobody. Not anymore.",
        "Generations of fans waited for this. Most of them were on the route today.",
        "Nobody who was there will ever have to wonder what this feels like again.",
    )

    val AD_LINES_COLLEGE = listOf(
        "This is what we built this program for. Thank you to everyone who made it happen.",
        "I've never been prouder of a group of young men. They represented this university the right way.",
        "When we hired Coach {coach}, we said we wanted to compete for championships. We meant it. They delivered.",
        "This trophy will sit in our lobby for every recruit, every student and every alum to see. It belongs to all of them.",
        "Champions in the classroom, champions in the community and now champions on the court.",
        "I told our donors this day would come. I'm glad I didn't have to wait any longer.",
    )

    val OWNER_LINES = listOf(
        "We promised this city a champion. These men delivered.",
        "I bought this team to win a championship. Today, this city owns one.",
        "Every decision, every late night, every trade: it was all for this moment.",
        "The trophy belongs to the fans. I'm just holding it for them.",
        "We'll celebrate tonight. Tomorrow, we start building the next one.",
        "{town}, you've been patient. Today you're champions.",
    )

    val MAYOR_LINES = listOf(
        "Today, {town} is the capital of basketball. The key to the city is waiting.",
        "I'm declaring today an official holiday. Effective immediately. Don't tell the council.",
        "I've been mayor a long time. I've never seen the city this happy.",
        "The fountains downtown are running in team colors for the rest of the week.",
        "On behalf of {town}: thank you for the best day this city has had in years.",
        "We're renaming a street after this team. We're still arguing about which one.",
    )

    val STAR_LINES = listOf(
        "Coach {coach} believed in us before anybody. This ring is for him too.",
        "We've been through everything together. I wouldn't want to win it with anybody else.",
        "I dreamed about this when I was ten. It's better than the dream.",
        "To the fans: we heard you every night. Every single night.",
        "I'm going to sleep with this trophy tonight. Don't tell anybody.",
        "Coach pushed us harder than anyone. Now I know why.",
    )

    val RUNNER_LINES = listOf(
        "Congratulations to them. They were the better team. We'll be back.",
        "Hard to swallow, but they earned it. Great team, great coach.",
        "They made one more play than us. That's a championship. Hats off.",
        "We'll use this. We'll be back in this game. Congrats to them.",
        "Tip of the cap. They played like champions all year.",
    )

    val FAN_PARADE = listOf(
        "I'll be telling my grandkids about this day.", "Called in sick. Worth it. CHAMPS!", "Nobody believed. We did.",
        "Been waiting my whole life for this. Cried twice already.", "My voice is gone. I regret nothing.", "Brought my dad. He's 84. He's been waiting since he was 10.",
        "Confetti in my hair, in my car, in my soul.", "Best day of my life and I've been married twice.", "Somebody tell my boss I'm not coming in tomorrow either.",
        "THEY DID IT. THEY ACTUALLY DID IT.", "I'm framing my ticket stub from every playoff game.", "Got the trophy on my lock screen already.",
        "Walked six miles to get here. Would walk sixty.", "I've had season tickets for 22 years. Every losing season was worth it for today.", "Saw the bus go by for four seconds. Best four seconds ever.",
        "My kid's first parade. Hopefully not the last.", "Hugged a total stranger for five minutes. We're best friends now.", "Somewhere a rival fan is crying and I love that for me.",
    )

    // ---------- ring night ----------

    val RING_CLOSERS = listOf(
        "Then the hard part: playing a game after that.",
        "The visitors watched the whole thing from their bench. They'll be getting everybody's best shot all year, too.",
        "Now everyone's gunning for them.",
        "The banner's up. Now the hunt starts over.",
        "The rings came off for warmups. The chase came back on.",
        "Nobody gets a banner for last year in this league. They know it.",
        "The crowd stayed standing for the opening tip. The champions got a reminder that nothing carries over.",
        "Great night. Short memory. That's the game.",
    )

    val RING_PLAYER = listOf(
        "Seeing that banner go up... I got chills. Now let's go get another one.",
        "Every time I look up at that, I'm going to remember what it took.",
        "Ring fits perfect. Time to go to work.",
        "That banner will hang there forever. Now we need a neighbor for it.",
        "I'll wear this ring to every practice. Reminder.",
    )
}

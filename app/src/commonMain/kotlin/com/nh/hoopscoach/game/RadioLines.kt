package com.nh.hoopscoach.game

/**
 * The radio show's material: who calls in, and the any-week topics they bring up when nothing urgent is
 * going on. Each topic has a few ways of being asked and three answers (measured, bold, deflecting) with
 * what each does. Kept apart from [RadioShow] so the show's logic stays readable.
 */
internal object RadioLines {
    val CALLERS = listOf(
        "Marcus from the east side", "Big Tony in the truck", "Linda, season-ticket holder since '88", "Dev, a student in section 112",
        "Coach Ray, retired", "Keisha on her lunch break", "Earl, longtime caller", "Jamal from the barbershop",
        "Rosa, class of '97", "Pete on the loading dock", "Dr. Okafor between surgeries", "Shonda from the alumni chapter",
        "Walt, who hasn't missed a home game since '79", "Tasha in the carpool line", "Mike at the hardware store", "Gus, first-time caller",
        "Nate from the student paper", "Old Man Harlan", "Bri, the walk-on's cousin", "Ty on the night shift",
        "Carla, who drove four hours to the last road game", "Deacon Fields", "Javi from the pickup run at the rec", "Ms. Whitfield, retired teacher",
        "Lou in the parking garage", "Andre, a booster", "Sam, calling from the tailgate", "Renee, band alum",
        "Chip, who has a take", "Mo from the food truck", "Hank on the tractor", "Priya, a grad student",
        "Darnell, youth league coach", "Bev, who keeps her own stats", "Curtis in the upper deck", "Frankie from across the river",
        "Zeke, long-haul driver", "Monique at the salon", "T.J., a former manager", "Opal, 91 years young",
        "Rick, who played here in the '80s", "Ana, a freshman", "Dale, calling about defense again", "Imani from the pep band",
        "Stu at the diner", "Kev, two beers in", "Lorraine, who knits during timeouts", "Marcus's dad",
        "Trey from the intramural league", "Pastor Dan", "Gina, a rival's fan (don't tell)", "Cole in the dorms",
        "Wes, a season-ticket holder in row 3", "Nia, who called it in October", "Bobby from the body shop", "Yusuf, an exchange student",
        "Deb, who rings the cowbell", "Leon, a high school ref", "Hattie on the porch", "Vince, who bet his brother-in-law",
    )

    class Topic(val questions: List<String>, val answers: List<RadioShow.Answer>)

    private fun a(text: String, fans: Double = 0.0, security: Int = 0, mood: Int = 0, heat: Int = 0, reaction: String) =
        RadioShow.Answer(text, fans = fans, security = security, heat = heat, mood = mood, reaction = reaction)

    private fun t(vararg q: String, answers: List<RadioShow.Answer>) = Topic(q.toList(), answers)

    /** Topics for any week. {school} and {mascot} are filled in. */
    val GENERAL: List<Topic> = listOf(
        t("What's the one thing this team has to get better at?", "If you could fix one thing by Saturday, what is it?", "Where does this team have to grow?", answers = listOf(
            a("Defense. It wins in March.", fans = 1.0, reaction = "The old-school callers approve."),
            a("Taking care of the ball.", fans = 1.0, reaction = "Fair."),
            a("Honestly? Everything. We're not close to our ceiling.", fans = 2.0, security = 1, reaction = "Ambitious, and the AD likes ambitious."))),
        t("Coach, why don't we press more?", "Any chance we see full-court pressure this year?", answers = listOf(
            a("We'll pick our spots. It has to fit the roster.", fans = 1.0, reaction = "A sensible answer."),
            a("You want pressure? Come Saturday. You'll get pressure.", fans = 3.0, reaction = "The phones lit up."),
            a("I coach the team. You enjoy the games.", fans = -2.0, reaction = "That one landed badly."))),
        t("How's practice been this week?", "What's the mood in the gym right now?", answers = listOf(
            a("Sharp. Guys are competing for minutes.", fans = 1.0, mood = 2, reaction = "The players liked hearing it."),
            a("Not good enough. We had to start one over.", fans = 1.0, security = 1, mood = -3, reaction = "Honest, and the roster felt it."),
            a("Practice stays in the gym.", reaction = "A closed door."))),
        t("Who's the hardest worker on this team?", "Who's the first one in the gym every morning?", answers = listOf(
            a("I could name five. That's the best thing about this group.", fans = 1.0, mood = 3, reaction = "The locker room appreciated that."),
            a("The guys know who it is. And they know who it isn't.", mood = -3, security = 1, reaction = "A message sent."),
            a("The managers. Nobody outworks our managers.", fans = 2.0, reaction = "A laugh, and the managers got a round of applause at practice."))),
        t("Are we deep enough for a tournament run?", "Do you trust your bench in March?", answers = listOf(
            a("Our bench has won us games already. I trust them.", fans = 1.0, mood = 3, reaction = "The reserves heard it."),
            a("We'll play the guys who earn it. Right now that's seven.", security = 1, mood = -2, reaction = "Blunt. The end of the bench noticed."),
            a("Depth is a March question. Ask me in March.", fans = 1.0, reaction = "Fair enough."))),
        t("Coach, the student section has been great. Do you hear them?", "Do the players notice when the building is loud?", answers = listOf(
            a("They're worth five points a night. Keep coming.", fans = 4.0, reaction = "The students will be louder Saturday."),
            a("We hear them. We'd hear them better if it were full.", fans = -1.0, security = 1, reaction = "A challenge. Some took it the wrong way."),
            a("I'm locked in on the game. But the guys love it.", fans = 2.0, reaction = "Good enough."))),
        t("What do you say to fans who think we schedule too soft?", "Why don't we play anybody in November?", answers = listOf(
            a("The schedule is built to get us ready for league play.", security = 1, reaction = "The athletic director nodded along."),
            a("Tell me who to call. I'll play anybody, anywhere.", fans = 3.0, reaction = "The base loved it."),
            a("Scheduling is harder than it looks from your couch.", fans = -2.0, reaction = "That stung a few callers."))),
        t("Free throws, coach. What's going on?", "We leave too many points at the line. Is that fixable?", answers = listOf(
            a("We shoot a hundred a day. They'll fall.", fans = 1.0, reaction = "Callers will be counting."),
            a("It's between the ears. We're working on it.", security = 1, reaction = "Honest."),
            a("If you can make them, I've got a jersey for you.", fans = 2.0, reaction = "A laugh on the phones."))),
        t("Do you watch the rankings?", "Does the poll matter in your locker room?", answers = listOf(
            a("Not until March. They're for you, not for us.", fans = 1.0, reaction = "The standard answer, well delivered."),
            a("Every week. We deserve to be higher.", fans = 3.0, heat = 1, reaction = "The fans agree, loudly."),
            a("I don't have a vote and I don't want one.", reaction = "A shrug."))),
        t("What's your message to recruits listening tonight?", "If a recruit is in the car with his dad right now, what should he know?", answers = listOf(
            a("Come here and you'll get better every day.", fans = 1.0, security = 1, reaction = "A clean pitch."),
            a("Come here and you'll play in front of the best fans in the country.", fans = 4.0, reaction = "The fan base stood a little taller."),
            a("We don't beg. If you want to work, call me.", fans = 2.0, security = -1, reaction = "Some loved it. The AD winced."))),
        t("Who's the best team you've seen this year?", "Who's the toughest team on our schedule?", answers = listOf(
            a("Whoever we play next. That's how we treat it.", fans = 1.0, reaction = "Predictable, and correct."),
            a("We are. I'd take my guys against anybody.", fans = 3.0, heat = 1, reaction = "Confidence sells."),
            a("There are four or five teams that can win it all. We want to be one.", security = 1, reaction = "Measured."))),
        t("How do you keep guys fresh this time of year?", "Are legs a concern in February?", answers = listOf(
            a("Shorter practices, more film. We've done this before.", security = 1, reaction = "A veteran answer."),
            a("They're young. They'll sleep in April.", fans = 2.0, mood = -2, reaction = "The fans laughed. The trainers didn't."),
            a("We listen to the trainers. Always.", mood = 2, reaction = "The players appreciated it."))),
        t("What's your favorite thing about coaching at {school}?", "Why {school}, coach?", answers = listOf(
            a("The people. This place cares.", fans = 3.0, reaction = "Straight to the heart of the fan base."),
            a("The expectations. I want to be somewhere it matters.", fans = 2.0, security = 1, reaction = "The boosters liked that."),
            a("The barbecue. Don't tell my doctor.", fans = 2.0, reaction = "A light moment."))),
        t("Are we shooting too many threes?", "Live by the three, die by the three. Agree?", answers = listOf(
            a("We take the shots the defense gives us.", reaction = "Reasonable."),
            a("We're not shooting enough of them.", fans = 2.0, reaction = "The analytics crowd cheered."),
            a("I want the ball inside first. Always have.", fans = 1.0, reaction = "The old guard approves."))),
        t("What did you tell the team after the last game?", "What was the locker room like afterward?", answers = listOf(
            a("That I'm proud of how they compete.", fans = 1.0, mood = 3, reaction = "The players heard it."),
            a("That we're not as good as we think we are.", security = 1, mood = -2, reaction = "A dose of reality."),
            a("What's said in there stays in there.", reaction = "A closed door."))),
        t("Coach, any superstitions?", "Do you have a game-day routine?", answers = listOf(
            a("Same tie until we lose. My wife hates it.", fans = 2.0, reaction = "The fans will be watching the tie."),
            a("Preparation is my superstition.", security = 1, reaction = "On brand."),
            a("If I tell you, it stops working.", fans = 1.0, reaction = "A grin you could hear."))),
        t("What do you make of the transfer portal?", "Is the portal good for college basketball?", answers = listOf(
            a("It's the world we live in. We'll use it well.", security = 1, reaction = "Pragmatic."),
            a("I want guys who want to be here for four years.", fans = 2.0, mood = 2, reaction = "The roster liked that one."),
            a("If it gets us better players, I love it.", fans = 1.0, mood = -2, reaction = "A few players wondered about their spots."))),
        t("How important is the conference tournament to you?", "Do you coach to win the league tournament or rest guys?", answers = listOf(
            a("We play to win every game we're in.", fans = 2.0, reaction = "No argument from the callers."),
            a("It's a tune-up. The real one starts a week later.", fans = -1.0, security = -1, reaction = "The league office won't love that."),
            a("Depends where we're seeded. Ask me then.", reaction = "Honest."))),
        t("Who's the funniest guy on the team?", "Who keeps the locker room loose?", answers = listOf(
            a("There are a couple. This group likes each other.", fans = 1.0, mood = 3, reaction = "Good for chemistry."),
            a("None of them are as funny as they think.", fans = 2.0, reaction = "The players are already arguing about it."),
            a("We're not here to tell jokes.", fans = -1.0, mood = -1, reaction = "A flat moment."))),
        t("What's your take on NIL, coach?", "Is the money changing the locker room?", answers = listOf(
            a("Players deserve it. We help them handle it right.", mood = 2, security = 1, reaction = "A modern answer."),
            a("I miss when it was about the name on the front.", fans = 2.0, mood = -2, reaction = "Older fans cheered. Players rolled their eyes."),
            a("It's above my pay grade. I coach basketball.", reaction = "A dodge."))),
        t("What does this team do better than anybody?", "What's our identity?", answers = listOf(
            a("We guard. Every night, no matter what.", fans = 2.0, reaction = "That's the answer they wanted."),
            a("We share the ball. Nobody cares who scores.", fans = 1.0, mood = 3, reaction = "The players loved it."),
            a("We're still finding out.", fans = -1.0, security = -1, reaction = "Not what anyone hoped to hear this late."))),
        t("Do you sleep after a loss?", "How long does a loss stay with you?", answers = listOf(
            a("Not much. I watch the film twice before sunrise.", fans = 1.0, security = 1, reaction = "They believe it."),
            a("Until the next tip. Then it's gone.", reaction = "Healthy."),
            a("I'm still mad about one from six years ago.", fans = 2.0, reaction = "Relatable."))),
        t("What's the best advice you ever got as a coach?", "Who taught you the most about this job?", answers = listOf(
            a("Coach the person first. The player follows.", fans = 1.0, mood = 2, reaction = "A thoughtful moment."),
            a("Recruit players better than you are a coach.", fans = 2.0, reaction = "A laugh, and some truth."),
            a("Never read the message boards.", fans = 1.0, reaction = "The message boards disagree."))),
        t("Coach, would you rather win pretty or win ugly?", "Style points: do they matter?", answers = listOf(
            a("A win is a win. I've never given one back.", fans = 1.0, reaction = "Agreed."),
            a("Ugly. Ugly travels in March.", fans = 2.0, reaction = "The defense-first callers cheered."),
            a("I want people to enjoy watching us.", fans = 2.0, security = 1, reaction = "Ticket sales like that answer."))),
        t("How do you handle a player who's in a slump?", "What do you say to a shooter who's gone cold?", answers = listOf(
            a("Keep shooting. I'll never pull a guy for missing good shots.", mood = 4, reaction = "The shooters heard that."),
            a("If the shot's not falling, defend and rebound.", security = 1, reaction = "Coaching 101."),
            a("Minutes are earned. Slump or not.", mood = -3, security = 1, reaction = "A hard line."))),
        t("Any update on the new practice facility?", "When do we get an upgrade to the building?", answers = listOf(
            a("That's a question for the people with the checkbooks.", security = -1, fans = 1.0, reaction = "The boosters shifted in their seats."),
            a("We win with what we have. Then we ask.", security = 2, reaction = "The athletic director sent a thank-you text."),
            a("If you're listening and you've got a spare ten million, call me.", fans = 3.0, reaction = "The phone lines lit up with fake pledges."))),
        t("Who was the best player you ever coached?", "Best player you've ever had?", answers = listOf(
            a("I've been lucky. Too many to name one.", reaction = "Diplomatic."),
            a("He might be on this roster right now.", fans = 3.0, mood = 3, reaction = "Every player thinks it's him."),
            a("The best ones were never the most talented.", fans = 1.0, reaction = "A coach's answer."))),
        t("What's the toughest road trip in this league?", "Which building do you hate playing in?", answers = listOf(
            a("They're all hard. That's the league.", reaction = "Diplomatic."),
            a("I won't name it, but they know. And we're coming.", fans = 2.0, heat = 1, reaction = "Everybody thinks he means them."),
            a("Ours, for the other team. That's the idea.", fans = 3.0, reaction = "The home crowd took the compliment."))),
        t("Do you let the players call their own plays late?", "How much freedom do your guards have?", answers = listOf(
            a("I trust my point guard. He's earned it.", mood = 4, reaction = "The point guard walked taller at practice."),
            a("They run what I call. That's why I'm paid.", security = 1, mood = -2, reaction = "Old school."),
            a("Depends on the night, and on the guard.", reaction = "Fair."))),
        t("What did you learn about this team in the last month?", "Has this group surprised you?", answers = listOf(
            a("They're tougher than I thought.", fans = 2.0, mood = 3, reaction = "High praise."),
            a("We have a ways to go, and less time than we think.", security = 1, reaction = "Sobering."),
            a("They surprise me every day. Not always the good kind.", fans = 1.0, reaction = "A dry laugh."))),
        t("Coach, zone or man?", "Why don't we play more zone?", answers = listOf(
            a("Man to man. It's who we are.", fans = 1.0, reaction = "No surprise."),
            a("Whatever wins. I'm not married to anything.", security = 1, reaction = "Flexible."),
            a("You'll see some things soon you haven't seen yet.", fans = 2.0, heat = 1, reaction = "Opposing staffs took notes."))),
        t("How's the freshman class coming along?", "Are the young guys ready?", answers = listOf(
            a("Ahead of schedule. They want it.", fans = 2.0, mood = 2, reaction = "Good news for the future."),
            a("They're freshmen. Some days it shows.", mood = -1, reaction = "True of every freshman ever."),
            a("They'll play when they've earned it.", security = 1, reaction = "A firm hand."))),
        t("Is this the best league in the country?", "How does our conference stack up?", answers = listOf(
            a("Top to bottom, yes. No nights off.", fans = 2.0, reaction = "League pride."),
            a("We'll find out in March when we play the others.", fans = 1.0, reaction = "Fair."),
            a("I only worry about one team in it.", reaction = "Tunnel vision."))),
        t("What's your relationship with the officials like?", "Do the refs get a fair shake from you?", answers = listOf(
            a("It's a hard job. I let them do it.", security = 1, reaction = "The league office approves."),
            a("They know how I feel. Loudly.", fans = 3.0, security = -1, reaction = "The fans love a coach who fights."),
            a("No comment. I like my paycheck.", fans = 2.0, reaction = "Everybody understood."))),
        t("Does this team have a leader?", "Who's the voice in the huddle?", answers = listOf(
            a("We have a couple. The captains have been great.", mood = 3, reaction = "The captains appreciated it."),
            a("Leadership is earned. Somebody needs to take it.", mood = -3, security = 1, reaction = "A challenge to the room."),
            a("I'm the voice. They're the legs.", fans = 1.0, reaction = "Old school."))),
        t("What would a successful season look like?", "How do you define success this year?", answers = listOf(
            a("Playing our best basketball in March.", security = 1, reaction = "Sensible."),
            a("A banner. Anything else is practice.", fans = 4.0, security = -1, reaction = "A big promise. They'll remember it."),
            a("Getting better every week. The rest follows.", reaction = "Coach-speak, but true."))),
        t("Coach, what are you reading these days?", "What do you do to get away from basketball?", answers = listOf(
            a("Scouting reports. Very dry.", fans = 1.0, reaction = "A chuckle."),
            a("My kids' homework. I'm no help.", fans = 2.0, reaction = "Humanizing."),
            a("There's no getting away. Not this time of year.", security = 1, reaction = "The grind."))),
        t("Would you ever schedule {school}'s biggest rival twice?", "Should the rivalry game be home-and-home every year?", answers = listOf(
            a("Every year, their place and ours. That's what fans want.", fans = 4.0, heat = 1, reaction = "A popular idea."),
            a("Once is plenty for my blood pressure.", fans = 1.0, reaction = "A laugh."),
            a("That's up to the league office.", reaction = "Passing the buck."))),
        t("What's the hardest part of your job nobody sees?", "What would surprise fans about coaching?", answers = listOf(
            a("Telling a kid he's not playing. It never gets easier.", fans = 1.0, mood = 2, reaction = "A quiet, honest moment."),
            a("The recruiting miles. I know every airport in the country.", security = 1, reaction = "The grind."),
            a("Listening to this show.", fans = 3.0, reaction = "The host laughed the loudest."))),
        t("How do you handle playing time complaints?", "What do you tell a parent who thinks his son should start?", answers = listOf(
            a("My door is open to players. Always.", mood = 3, reaction = "The roster knows it's true."),
            a("The film doesn't lie, and I'll show it to anyone.", security = 1, mood = -1, reaction = "Firm."),
            a("Parents coach from row twelve. I coach from the bench.", fans = 2.0, mood = -2, reaction = "A few parents were listening."))),
        t("Do you still get nervous before games?", "Butterflies, coach? After all these years?", answers = listOf(
            a("Every single one. The day I don't, I'll retire.", fans = 2.0, reaction = "They liked that."),
            a("No. We prepare so we don't have to be.", security = 1, reaction = "Calm."),
            a("Only when you people call in.", fans = 2.0, reaction = "A good-natured jab."))),
        t("What's one rule you'd change in college basketball?", "If you were commissioner for a day?", answers = listOf(
            a("Fewer timeouts. Let them play.", fans = 2.0, reaction = "Fans everywhere agreed."),
            a("Move the line back. Reward real shooters.", fans = 1.0, reaction = "A purist's answer."),
            a("Nothing. The game is in a good place.", reaction = "Safe."))),
        t("How much film do the players watch?", "Do kids today still study the game?", answers = listOf(
            a("More than any team I've had. They want to learn.", mood = 3, fans = 1.0, reaction = "Praise the players earned."),
            a("Not enough. Phones are winning.", fans = 1.0, mood = -3, reaction = "The players put their phones down for a day."),
            a("Enough to know where to be.", reaction = "Fine."))),
        t("Are you happy with our rebounding?", "We're getting beat on the glass. Why?", answers = listOf(
            a("It's effort. We'll fix effort.", security = 1, mood = -2, reaction = "The bigs heard it."),
            a("Our guards have to help. It's five guys.", reaction = "A fair point."),
            a("We'll out-rebound the next one. Write it down.", fans = 2.0, reaction = "Callers will be checking the box score."))),
        t("Coach, do you ever think about the NBA?", "Would you ever leave for the pros?", answers = listOf(
            a("I'm a college coach. This is home.", fans = 4.0, security = 1, reaction = "The fan base exhaled."),
            a("Never say never. But not today.", fans = -2.0, security = -1, reaction = "That will be on the message boards tonight."),
            a("They haven't called. Should I be offended?", fans = 2.0, reaction = "A laugh."))),
        t("What makes a great {mascot} player?", "What do you look for in a recruit?", answers = listOf(
            a("Toughness. I can teach the rest.", fans = 2.0, reaction = "The answer they wanted."),
            a("Guys who love the gym more than the spotlight.", mood = 2, fans = 1.0, reaction = "The gym rats nodded."),
            a("Talent. Don't let anyone tell you otherwise.", fans = 1.0, reaction = "Honest."))),
        t("How do you prepare for a team you've never seen?", "What goes into a scouting report?", answers = listOf(
            a("My staff is the best in the business. They don't sleep.", security = 1, reaction = "The assistants appreciated the credit."),
            a("We worry about us. They have to guard us too.", fans = 1.0, reaction = "Confidence."),
            a("Film, film, and then more film.", reaction = "Exactly what you'd expect."))),
        t("Are the players handling school all right?", "How are the grades, coach?", answers = listOf(
            a("Best team GPA I've had. I'm proud of that.", fans = 2.0, security = 2, reaction = "The administration loved hearing it."),
            a("They know: no class, no court.", security = 1, reaction = "A firm line."),
            a("They're students first. I leave the rest to the advisors.", reaction = "Fair."))),
        t("What's the loudest you've ever heard our building?", "Best atmosphere you've coached in here?", answers = listOf(
            a("It hasn't happened yet. Saturday could be it.", fans = 4.0, reaction = "A challenge to the crowd, gladly accepted."),
            a("Any night we beat a rival. Nothing like it.", fans = 3.0, reaction = "Goosebumps on the phones."),
            a("I couldn't tell you. I had to coach.", fans = 1.0, reaction = "A grin."))),
        t("Do you believe in moral victories?", "Is there such a thing as a good loss?", answers = listOf(
            a("No. We keep score for a reason.", fans = 2.0, reaction = "The competitive answer."),
            a("If we learn from it, yes.", security = 1, reaction = "A teacher's answer."),
            a("Ask the selection committee.", fans = 1.0, reaction = "A knowing laugh."))),
    )
}

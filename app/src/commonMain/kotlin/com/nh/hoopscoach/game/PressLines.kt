package com.nh.hoopscoach.game

/**
 * More ways to answer the postgame questions, by topic and by tone: humble (team first), brash
 * (confidence that turns heads) and even (the steady, quotable answer). [Press] swaps one in for the
 * written answer about half the time, so the same moment doesn't always get the same three replies.
 */
internal object PressLines {
    private class Tones(val humble: List<String>, val brash: List<String>, val even: List<String>)

    private fun t(humble: List<String>, brash: List<String>, even: List<String>) = Tones(humble, brash, even)

    private val BY_TOPIC: Map<String, Tones> = mapOf(
        "Game-winner" to t(
            listOf("My teammates got me the ball in a spot I like. They did the hard part.", "I was the fourth option on that play. The other three got covered. That's on the play call, not me.",
                "Honestly, it's the screen that made it. Go ask the big man about it.", "That's the kind of shot we practice every day. Everybody in that huddle believed."),
            listOf("I want the last shot. Every night. Put it in my hands.", "When it's close, I'm not thinking. I'm just waiting for the ball.",
                "Big moments, big players. That's all I'll say.", "I knew it was good when it left my hand. I was already turning around."),
            listOf("Read the defense, took what was there.", "Clock was running down, I had space, I shot it.", "Same shot as the first half. This one counted more.", "Happy we won. That's the main thing."),
        ),
        "Career high" to t(
            listOf("My guys kept finding me. When you get open looks like that, you have to knock them down.", "Points are nice. The win is nicer. My teammates made it easy.",
                "Coach put me in good spots tonight. I just tried not to waste them.", "I'll give the game ball to whoever set all those screens."),
            listOf("I've been waiting for a night like this. It's not the last one.", "Mark it down. You'll be writing about my next career high soon.",
                "That's what happens when they leave me open. Do it again.", "I'm just getting started. Honestly."),
            listOf("The rim looked big tonight. I'll take it.", "Some nights it falls. Tonight it fell.", "Felt good early and stayed with it.", "I'll enjoy it tonight and get back to work tomorrow."),
        ),
        "Rival" to t(
            listOf("He's a great player. Every time we play, he makes me better.", "Nothing but respect for him. It's always a battle.", "We came up together. I'm proud of him, even when he's on the other side."),
            listOf("There's a best player in our class. It isn't him.", "He knows. I know. Everybody watching knows.", "Ask him how it felt. I'm good."),
            listOf("It's one game. There'll be more.", "I don't play him. I play the team in front of me.", "Good matchup. Good night for the fans."),
        ),
        "Old team" to t(
            listOf("I've got a lot of love for that place. It was good to see some old faces.", "They gave me my start. I'll never forget that.", "It's strange looking at that bench from the other side. Good people over there."),
            listOf("They made their choice. Tonight I made mine.", "Every time I play them, I'm going to remind them.", "I hope they enjoyed watching."),
            listOf("It's a game on the schedule. We needed it.", "Emotions were there for a minute. Then it was basketball.", "Business is business."),
        ),
        "Hot streak" to t(
            listOf("The team is playing great, and I'm benefiting from it.", "I'm getting good looks because everybody else is a threat too.", "Credit the coaches. They found the spots where I'm best."),
            listOf("This is who I am. Get used to it.", "I don't feel like I can miss right now. That's a scary feeling for defenses.", "Hot? I'm just playing my game."),
            listOf("Trying to stay in the moment.", "Same routine every day. It's working.", "It'll cool off eventually. I'll keep shooting."),
        ),
        "Slump" to t(
            listOf("I have to be better for my teammates. They've been carrying me.", "It's on me. I'll keep working.", "My guys keep telling me to shoot it. That means a lot."),
            listOf("I'm not worried. Shooters shoot.", "Slumps end. Mine ends next game.", "Keep doubting me. It's working out great for me historically."),
            listOf("The shots are good looks. They'll fall.", "Back to the gym tomorrow.", "It's a long season. One stretch doesn't define it."),
        ),
        "Win streak" to t(
            listOf("Everybody's bought in. That's the whole secret.", "We're just trying to get better every day, and the wins are coming.", "Credit the guys who don't play much. They make practice hard."),
            listOf("We expect to win every night. Nobody should be surprised.", "Who's going to stop us? I'm asking honestly.", "We're not even at our best yet."),
            listOf("One at a time. Next game is the only one that matters.", "Streaks are fun. They don't mean anything in March.", "We'll keep stacking."),
        ),
        "Losing streak" to t(
            listOf("We're going to stick together. That's the only way out.", "I have to lead better. That starts with me.", "We owe the fans more than this."),
            listOf("We're better than this, and we're going to show it.", "Nobody's panicking in that locker room. We know who we are.", "Write us off. Please."),
            listOf("We'll watch the film and fix it.", "It's a long season.", "One win changes the mood. Let's go get one."),
        ),
        "Tough loss" to t(
            listOf("That one stings. We'll learn from it.", "They made the plays at the end. Credit to them.", "I have to make one more play for my team."),
            listOf("We'll see them again. They won't like how that one goes.", "They got lucky and they know it.", "Circle the rematch."),
            listOf("Close games go both ways. This one went theirs.", "We'll move on.", "Lot of good things in there. Not enough."),
        ),
        "Big night" to t(
            listOf("Coach drew up some great stuff and the guys trusted me.", "That's a team win. I just had the ball a lot.", "My teammates made the hard plays. I made the easy ones."),
            listOf("Nights like this are why I play.", "I felt like I could do anything out there.", "Nobody in this league guards me one-on-one."),
            listOf("Took what the defense gave me.", "Good rhythm early, kept it going.", "It was one of those nights."),
        ),
        "Rivalry" to t(
            listOf("That's a great program over there. It's always a war.", "The crowd made it special. Credit to both sides.", "Rivalry games bring out the best in everybody."),
            listOf("This is our house. It always will be.", "They know who runs this rivalry.", "We heard all their talk. We answered."),
            listOf("It means a lot to the fans. We wanted it for them.", "It's one win, but it's a good one.", "Glad to get this one."),
        ),
        "Playoffs" to t(
            listOf("Everybody is locked in. Nobody cares who scores.", "We're playing for each other right now.", "The staff has had us ready every single night."),
            listOf("We're the better team, and we'll keep proving it.", "We're not done. Not close.", "Bring on whoever's next."),
            listOf("Series isn't over. Next game.", "We'll rest up and get ready.", "Trying not to get too high or too low."),
        ),
        "March" to t(
            listOf("Survive and advance. My teammates made sure we did.", "This group plays for each other. That's why we're still here.", "Every guy on that bench contributed."),
            listOf("We came to win the whole thing. Nothing less.", "Nobody wants to see us in their bracket.", "We're the team nobody wants to play."),
            listOf("One game at a time. That's all March is.", "Enjoy it tonight, prepare tomorrow.", "Still dancing. That's what matters."),
        ),
        "Season over" to t(
            listOf("I'm proud of this group. We gave everything.", "It hurts because we cared. I wouldn't trade these guys.", "I'll remember this team for a long time."),
            listOf("We'll be back. And we'll be better.", "Remember this feeling. It's fuel for next year.", "Nobody's getting us out early again."),
            listOf("Tough way to end it. We'll reflect and get better.", "It's over, and that's hard to say.", "Credit to them. They beat us."),
        ),
        "Draft stock" to t(
            listOf("I just want to win here. The rest takes care of itself.", "My teammates are the reason anybody's talking about me.", "I'm focused on this team. That's it."),
            listOf("I know where I belong. Top of the board.", "The scouts are here for a reason.", "Every game is an audition. I'm acing it."),
            listOf("That's a spring conversation.", "I don't read the mock drafts.", "One day at a time."),
        ),
        "Minutes" to t(
            listOf("Coach plays whoever helps us win. I trust that.", "My job is to be ready when my name is called.", "Whatever the team needs."),
            listOf("Give me more minutes and I'll show you.", "I can play with anybody in this league.", "I earned more than I'm getting. I'll keep earning."),
            listOf("I'll keep working.", "Minutes come when you earn them.", "Ready when called."),
        ),
        "Freshman year" to t(
            listOf("The older guys have helped me so much. I'm just following them.", "Still learning every day.", "I'm lucky to be in this locker room."),
            listOf("Freshman? I don't feel like one.", "I came here to play right away, and I'm playing.", "I'm not waiting my turn."),
            listOf("College is different. I'm adjusting.", "It's a big jump. I'm handling it.", "Every game is a lesson."),
        ),
        "Pressure" to t(
            listOf("The pressure's on all of us together. That makes it lighter.", "My teammates take the pressure off.", "I'm grateful to be in a spot where people expect things."),
            listOf("Pressure is a privilege. I love it.", "I live for this.", "More pressure, please."),
            listOf("I don't think about it.", "Same game, bigger crowd.", "Focus on the next play."),
        ),
    )

    /** A fresh answer in the same tone, about half the time; otherwise the written one. */
    fun vary(topic: String, a: PressAnswer): PressAnswer {
        val tones = BY_TOPIC[topic] ?: return a
        if (!Rng.chance(0.5)) return a
        val pool = when { a.persona < 0 -> tones.humble; a.persona > 0 -> tones.brash; else -> tones.even }
        return PressAnswer(Rng.pick(pool), a.followers, a.fans, a.teammates, a.persona)
    }
}

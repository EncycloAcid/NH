package com.nh.hoopscoach.game

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Real dates for a college season's game days, laid out the way the season really runs: non-conference
 * games through November and December, conference play twice a week from January to early March,
 * conference tournaments the week before Selection Sunday, and the NCAA tournament from the First Four
 * to the title game on the first Monday of April. [League.season] is the spring year (2027 = 2026-27).
 */
object SeasonCalendar {
    /** A dated moment on the calendar that isn't a game. */
    class Marker(val date: LocalDate, val label: String, val kind: String)

    private fun titleGame(season: Int): LocalDate =
        LocalDate.of(season, 4, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY))

    fun selectionSunday(season: Int): LocalDate = dateOf(season, Schedule.NCAA_START).minusDays(2)

    /** The date of game day [day] (1 to [Schedule.LAST_DAY]). */
    fun dateOf(season: Int, day: Int): LocalDate {
        val title = titleGame(season)
        val f4 = title.minusDays(2)
        val e8 = f4.minusDays(7)
        val s16 = e8.minusDays(2)
        val r32 = s16.minusDays(5)
        val r64 = r32.minusDays(2)
        val firstFour = r64.minusDays(2)
        val sel = firstFour.minusDays(2)
        return when {
            day >= Schedule.LAST_DAY -> title
            day >= Schedule.NCAA_START -> when (day - Schedule.NCAA_START) {
                0 -> firstFour; 1 -> r64; 2 -> r32; 3 -> s16; 4 -> e8; else -> f4
            }
            day >= Schedule.CT_START -> sel.minusDays((Schedule.NCAA_START - day).toLong())      // Wed to Sat before Selection Sunday
            day > Schedule.NC_DAYS -> confDay(sel, day)
            else -> {
                val start = LocalDate.of(season - 1, 11, 3).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
                val end = confDay(sel, Schedule.NC_DAYS + 1).minusDays(5)
                val span = java.time.temporal.ChronoUnit.DAYS.between(start, end)
                start.plusDays(Math.round(span * (day - 1) / (Schedule.NC_DAYS - 1).toDouble()))
            }
        }
    }

    /** Conference play: Saturdays and Tuesdays, counting back from the last regular-season Saturday. */
    private fun confDay(sel: LocalDate, day: Int): LocalDate {
        var d = sel.minusDays(8)                       // the Saturday a week before Selection Sunday
        var k = Schedule.REG_DAYS
        var four = true
        while (k > day) {
            d = d.minusDays(if (four) 4 else 3)
            four = !four
            k--
        }
        return d
    }

    /** Game day on a date, or null when no games are played that day. */
    fun dayOn(season: Int, date: LocalDate): Int? = (1..Schedule.LAST_DAY).firstOrNull { dateOf(season, it) == date }

    fun markers(season: Int): List<Marker> {
        val title = titleGame(season)
        return listOf(
            Marker(dateOf(season, Schedule.CT_START), "Conference tournaments", "tourney"),
            Marker(selectionSunday(season), "Selection Sunday", "selection"),
            Marker(dateOf(season, Schedule.NCAA_START), "First Four", "ncaa"),
            Marker(dateOf(season, Schedule.NCAA_START + 5), "Final Four", "ncaa"),
            Marker(title, "National championship", "ncaa"),
            Marker(title.plusDays(1), "Transfer portal opens", "portal"),
            Marker(LocalDate.of(season, 4, 15), "Spring signing day", "signing"),
            Marker(LocalDate.of(season, 6, 1).with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY)), "NBA Draft", "draft"),
        )
    }

    /** The months the calendar shows: November through June. */
    fun months(season: Int): List<java.time.YearMonth> =
        (0..7).map { java.time.YearMonth.of(season - 1, 11).plusMonths(it.toLong()) }

    /** The name of a game day's round, for tournament days. */
    fun roundName(day: Int): String? = when {
        day in Schedule.CT_START until Schedule.NCAA_START -> "Conference tournament"
        day == Schedule.NCAA_START -> "First Four"
        day > Schedule.NCAA_START && day <= Schedule.LAST_DAY -> Season.NCAA_ROUNDS.getOrNull(day - Schedule.NCAA_START - 1)
        else -> null
    }
}

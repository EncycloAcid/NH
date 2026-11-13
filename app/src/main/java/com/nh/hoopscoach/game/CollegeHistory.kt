package com.nh.hoopscoach.game

/**
 * College history: the real national champions (1939 to 2025, by the year the season ended, under the
 * game's school names; CCNY's 1950 title belongs to a school that isn't in Division I anymore, and there
 * was no tournament in 2020) up to a league's first season, then the league's own. The previews use it
 * for blue bloods, droughts, first-title chases and, in a league's first season, last year's champion.
 */
object CollegeHistory {
    private val CHAMPIONS: Map<Int, String> = mapOf(
        1939 to "Oregon", 1940 to "Indiana", 1941 to "Wisconsin", 1942 to "Stanford", 1943 to "Wyoming", 1944 to "Utah",
        1945 to "Oklahoma State", 1946 to "Oklahoma State", 1947 to "Holy Cross", 1948 to "Kentucky", 1949 to "Kentucky",
        1951 to "Kentucky", 1952 to "Kansas", 1953 to "Indiana", 1954 to "La Salle", 1955 to "San Francisco", 1956 to "San Francisco",
        1957 to "North Carolina", 1958 to "Kentucky", 1959 to "California", 1960 to "Ohio State", 1961 to "Cincinnati", 1962 to "Cincinnati",
        1963 to "Loyola Chicago", 1964 to "UCLA", 1965 to "UCLA", 1966 to "UTEP", 1967 to "UCLA", 1968 to "UCLA", 1969 to "UCLA",
        1970 to "UCLA", 1971 to "UCLA", 1972 to "UCLA", 1973 to "UCLA", 1974 to "NC State", 1975 to "UCLA", 1976 to "Indiana",
        1977 to "Marquette", 1978 to "Kentucky", 1979 to "Michigan State", 1980 to "Louisville", 1981 to "Indiana", 1982 to "North Carolina",
        1983 to "NC State", 1984 to "Georgetown", 1985 to "Villanova", 1986 to "Louisville", 1987 to "Indiana", 1988 to "Kansas",
        1989 to "Michigan", 1990 to "UNLV", 1991 to "Duke", 1992 to "Duke", 1993 to "North Carolina", 1994 to "Arkansas", 1995 to "UCLA",
        1996 to "Kentucky", 1997 to "Arizona", 1998 to "Kentucky", 1999 to "UConn", 2000 to "Michigan State", 2001 to "Duke",
        2002 to "Maryland", 2003 to "Syracuse", 2004 to "UConn", 2005 to "North Carolina", 2006 to "Florida", 2007 to "Florida",
        2008 to "Kansas", 2009 to "North Carolina", 2010 to "Duke", 2011 to "UConn", 2012 to "Kentucky", 2013 to "Louisville",
        2014 to "UConn", 2015 to "Duke", 2016 to "Villanova", 2017 to "North Carolina", 2018 to "Villanova", 2019 to "Virginia",
        2021 to "Baylor", 2022 to "Kansas", 2023 to "UConn", 2024 to "UConn", 2025 to "Florida",
    )

    /** This league's first season of its own; the real (or alternate) titles count up to it. */
    fun firstSeason(lg: League): Int = lg.history.minOfOrNull { it.season } ?: lg.teams.flatMap { it.history }.minOfOrNull { it.season } ?: lg.season

    /** Pre-league champions for this save: alternate universe map, or the real record book. */
    fun champions(lg: League): Map<Int, String> = if (lg.altCollegeTitles.isNotEmpty()) lg.altCollegeTitles else CHAMPIONS

    /** Every national title [t] has won, oldest first: the past before this league, then this league's. */
    fun titles(lg: League, t: Team): List<Int> {
        val first = firstSeason(lg)
        return champions(lg).filter { (y, s) -> y < first && s == t.school }.keys.sorted() +
            t.history.filter { it.result == "National Champion" }.map { it.season }
    }

    /** Last season's national champion: this league's, or the past record in a league's first season. */
    fun lastChampion(lg: League): Team? {
        lg.history.firstOrNull { it.season == lg.season - 1 }?.champion?.takeIf { it >= 0 }?.let { return lg.teams.getOrNull(it) }
        if (lg.season - 1 >= firstSeason(lg)) return null
        val school = champions(lg)[lg.season - 1] ?: return null
        return lg.teams.firstOrNull { it.school == school }
    }

    fun count(n: Int) = when (n) { 0 -> "no national titles"; 1 -> "one national title"; 2 -> "two national titles"; 3 -> "three national titles"; 4 -> "four national titles"; 5 -> "five national titles"; 6 -> "six national titles"; 7 -> "seven national titles"; 8 -> "eight national titles"; else -> "$n national titles" }

    /** A line on a program's titles: "Eight national titles, the last in 2012." */
    fun line(lg: League, t: Team): String {
        val ts = titles(lg, t)
        return when {
            ts.isEmpty() -> "${t.school} has never won a national title."
            ts.size == 1 -> "${t.school}'s one national title came in ${ts[0]}."
            else -> "${t.school} owns ${count(ts.size)}, the last in ${ts.last()}."
        }
    }
}

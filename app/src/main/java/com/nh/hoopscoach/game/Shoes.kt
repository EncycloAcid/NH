package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** A signature shoe deal: the brand, when it started and for how long, the guarantee a year (thousands), the royalty, the current model, and each season's sales (pairs). */
@Serializable
class ShoeDeal(
    val brand: String,
    val start: Int,
    val years: Int,
    val perYear: Int,
    val royalty: Double,
    var model: Int = 1,
    val sales: MutableList<Int> = mutableListOf(),
    val paid: MutableList<Int> = mutableListOf(),
) {
    val ends get() = start + years - 1
}

/**
 * Your own shoe. Once your player is a star (an All-Star, or a million followers), brands come calling at
 * season's end with a guarantee, a royalty and how long; you pick one. Every season after, the new model
 * drops ("The Apex Carter 2") and its sales follow you: scoring, All-Star nods, your jersey's spot and your
 * following. The guarantee and the royalties go to your bank. When the deal runs out, the brands bid again.
 */
object Shoes {
    val BRANDS = listOf("Apex", "Vanta", "Strive", "Kinetic", "Rally")
    private val PITCH = mapOf(
        "Apex" to "We build legends. You're next.",
        "Vanta" to "The darkest, fastest shoe in the league, with your name on it.",
        "Strive" to "We sign people, not stats. We want your story on every pair.",
        "Kinetic" to "Our lab, your game. We'll design it around how you move.",
        "Rally" to "The biggest marketing push we've ever done. Billboards in every city.",
    )

    fun shoeName(lg: League, d: ShoeDeal): String = "${d.brand} ${lg.hero?.last ?: ""} ${d.model}".trim()

    /** How hot he is with buyers: 0.4 a quiet year to 2.5 a superstar's. */
    private fun heat(lg: League, h: Player): Double {
        val r = Jerseys.rank(lg, h)
        val stars = h.awards.count { it == "${lg.season} All-Star" }
        val f = (lg.road?.followers ?: 0) / 1_000_000.0
        return (0.4 + h.stats.ppg / 25.0 + stars * 0.4 + (if (r > 0) (16 - r) * 0.05 else 0.0) + f.coerceAtMost(8.0) * 0.08).coerceIn(0.4, 2.5)
    }

    private fun eligible(lg: League, h: Player): Boolean =
        (lg.road?.followers ?: 0) >= 1_000_000 || h.awards.any { it.endsWith("All-Star") }

    /** At season's end, in a player career: pay the year, drop next year's model, and bring offers when he's due. */
    fun seasonEnd(lg: League) {
        val r = lg.road ?: return
        val h = lg.hero ?: return
        if (!lg.pro) return
        val money = Eras.of(lg).money
        r.shoe?.let { d ->
            if (lg.season in d.start..d.ends) {
                val pairs = (180_000 * heat(lg, h) * (0.85 + Rng.d() * 0.3)).roundToInt()
                val royalties = (pairs * 140.0 * d.royalty / 1000 * money).roundToInt()
                val pay = (d.perYear * money).roundToInt() + royalties
                d.sales += pairs; d.paid += pay
                r.earned += pay
                lg.news += News(lg.season, lg.day, "The ${shoeName(lg, d)} sells ${Road.count(pairs)} pairs", true, -1, "shoes",
                    "${h.name}'s signature shoe paid ${Road.dollars(pay)} this season: the ${Road.dollars((d.perYear * money).roundToInt())} guarantee and ${Road.dollars(royalties)} in royalties.", d.brand, h.teamId, h.id)
                if (lg.season >= d.ends) {
                    lg.news += News(lg.season, lg.day, "${h.name}'s deal with ${d.brand} is up", true, -1, "shoes",
                        "${d.years} years, ${d.model} models, ${Road.count(d.sales.sum())} pairs. The brands will be calling.", d.brand, h.teamId, h.id)
                    r.shoe = null
                } else {
                    d.model++
                    lg.news += News(lg.season, lg.day, "The ${shoeName(lg, d)} is coming", true, -1, "shoes", "${d.brand} shows off next season's model.", d.brand, h.teamId, h.id)
                }
            }
        }
        if (r.shoe == null && r.shoeOffered != lg.season && eligible(lg, h)) offer(lg, h)
    }

    private fun offer(lg: League, h: Player) {
        val r = lg.road ?: return
        r.shoeOffered = lg.season
        val f = (r.followers / 1_000_000.0).coerceAtMost(20.0)
        val stars = h.awards.count { it.endsWith("All-Star") }
        val base = 1_500 + f * 600 + stars * 900 + h.stats.ppg * 60
        val brands = BRANDS.shuffled(Rng.r).take(3)
        r.shoeOffers.clear()
        for (b in brands) {
            val years = Rng.range(3, 5)
            val per = ((base * (0.8 + Rng.d() * 0.4)) / 10).roundToInt() * 10
            val roy = (3 + Rng.int(0, 4)) / 100.0
            r.shoeOffers += ShoeDeal(b, lg.season + 1, years, per, roy)
        }
        fun line(d: ShoeDeal) = "${d.brand}: ${d.years} years, ${Road.dollars(d.perYear)} a year, ${(d.royalty * 100).roundToInt()}% royalty"
        Farewell.show(lg, Ceremony(
            kind = "shoes", kicker = "SIGNATURE SHOE", title = "Your own shoe", sub = "${r.shoeOffers.size} brands want ${h.name} on a signature line, starting next season.",
            teamId = h.teamId, number = h.number,
            statLabels = r.shoeOffers.map { it.brand }, statValues = r.shoeOffers.map { Road.dollars(it.perYear) + "/yr" },
            choices = r.shoeOffers.map { line(it) },
            choiceQuotes = r.shoeOffers.map { "${it.brand}: \"${PITCH[it.brand] ?: "We want you."}\"" },
            choiceReactions = r.shoeOffers.map { d -> listOf(Tribute("Sneaker Insider", "Sneakers", "The ${d.brand} ${h.last} 1 is going to fly off the shelves.")) },
            choiceTitle = "Pick your brand", choicePrompt = "The guarantee is paid every season; the royalty is your cut of every pair sold.",
            effects = r.shoeOffers.map { "${it.years} years · ${Road.dollars(it.perYear * it.years)} guaranteed · ${(it.royalty * 100).roundToInt()}% of sales" },
            badge = "${lg.season} offseason",
        ))
    }

    /** He picks a brand. */
    fun sign(lg: League, choice: Int) {
        val r = lg.road ?: return
        val h = lg.hero ?: return
        val d = r.shoeOffers.getOrNull(choice) ?: return
        r.shoe = d
        r.shoeOffers.clear()
        lg.news += News(lg.season, lg.day, "${h.name} signs a signature shoe deal with ${d.brand}", true, -1, "shoes",
            "${d.years} years, ${Road.dollars(d.perYear)} a year guaranteed and ${(d.royalty * 100).roundToInt()}% of every pair. The ${shoeName(lg, d)} drops next season.", d.brand, h.teamId, h.id)
        Media.post(lg, h.name, h.name.replace(" ", "").replace(".", "").lowercase(), "self", "Dream come true. ${d.brand} family. The ${shoeName(lg, d)} is coming.", Media.likes(lg, 0.05))
    }
}

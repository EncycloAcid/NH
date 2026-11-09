package com.nh.hoopscoach.game

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Real footwear / apparel companies for school kit deals and personal star endorsements.
 * Names are flavor only — no logos or licensed likenesses.
 */
enum class ShoeCo(
    val label: String,
    val short: String,
    /** Share of the donor NIL pool paid as apparel money. */
    val apparelPct: Double,
    /** Minimum school apparel money ($K / season). */
    val apparelFloor: Int,
    /** Recruiting Pull bump while under contract. */
    val pullBonus: Int,
    /** How selective the brand is when picking schools (higher = bigger programs). */
    val prestigeNeed: Int,
    /** Multiplier on personal endorsement offers. */
    val dealMult: Double,
    val blurb: String,
) {
    NIKE("Nike", "NKE", 0.085, 90, 3, 58, 1.15, "The biggest college apparel deal in the game"),
    JORDAN("Jordan Brand", "JOR", 0.095, 110, 4, 72, 1.25, "Exclusive Jumpman programs and star signature energy"),
    ADIDAS("adidas", "ADI", 0.075, 70, 2, 48, 1.05, "Three stripes on campus and in the portal"),
    UNDER_ARMOUR("Under Armour", "UA", 0.065, 55, 2, 40, 1.0, "Performance kit with a hungry mid-major footprint"),
    PUMA("Puma", "PMA", 0.045, 35, 1, 28, 0.9, "Growing college presence and flashy colorways"),
    NEW_BALANCE("New Balance", "NB", 0.05, 40, 1, 32, 0.95, "Freshman-class energy and made-in-USA flex"),
    CONVERSE("Converse", "CNV", 0.035, 25, 1, 22, 0.85, "Classic Chuck heritage for the mid-majors"),
    REEBOK("Reebok", "RBK", 0.04, 28, 1, 24, 0.88, "Comeback brand hunting hungry programs"),
}

object Shoes {
    fun byId(id: String): ShoeCo? = ShoeCo.entries.firstOrNull { it.name == id }

    fun of(t: Team): ShoeCo? = byId(t.apparel)

    fun ofPlayer(p: Player): ShoeCo? = byId(p.shoe)

    fun apparelMoney(t: Team): Int {
        val co = of(t) ?: return 0
        val donor = Finance.donor(t.prestige)
        return max(co.apparelFloor, (donor * co.apparelPct).roundToInt() / 5 * 5)
    }

    fun pullBonus(t: Team): Int = of(t)?.pullBonus ?: 0

    /** Assign or renew a school's apparel partner from prestige. */
    fun assignApparel(t: Team, force: Boolean = false) {
        if (!force && t.apparel.isNotEmpty() && t.apparelLeft > 0) return
        val co = pickSchoolBrand(t.prestige)
        t.apparel = co.name
        t.apparelLeft = Rng.range(3, 6)
    }

    fun ensure(lg: League) {
        for (t in lg.teams) if (t.apparel.isEmpty() || byId(t.apparel) == null) assignApparel(t, force = true)
    }

    fun pickSchoolBrand(prestige: Int): ShoeCo {
        val weights = ShoeCo.entries.associateWith { co ->
            val fit = 1.0 - kotlin.math.abs(prestige - co.prestigeNeed) / 55.0
            val tier = when {
                prestige >= 80 && co in listOf(ShoeCo.NIKE, ShoeCo.JORDAN, ShoeCo.ADIDAS) -> 1.6
                prestige >= 65 && co in listOf(ShoeCo.NIKE, ShoeCo.ADIDAS, ShoeCo.UNDER_ARMOUR, ShoeCo.JORDAN) -> 1.35
                prestige < 40 && co in listOf(ShoeCo.PUMA, ShoeCo.NEW_BALANCE, ShoeCo.CONVERSE, ShoeCo.REEBOK, ShoeCo.UNDER_ARMOUR) -> 1.4
                else -> 1.0
            }
            max(0.08, fit.coerceIn(0.05, 1.2) * tier)
        }
        return Rng.weighted(ShoeCo.entries.toList()) { weights.getValue(it) }
    }

    fun eligible(p: Player): Boolean {
        if (p.walkOn || p.year <= 0) return false
        return p.ovr >= 76 || p.stars >= 4 || p.pot >= 84 || p.ovr + p.pot >= 155
    }

    /** Personal endorsement pay in $K / season. */
    fun dealValue(p: Player, co: ShoeCo): Int {
        val base = 5.0 + max(0, p.ovr - 72) * 2.4 + max(0, p.pot - p.ovr) * 0.8 + (p.stars - 2) * 4.0
        var v = base * co.dealMult
        if (p.has(Trait.BAG_CHASER)) v *= 1.12
        if (p.awards.any { it.contains("AA") || it.contains("MVP") }) v *= 1.2
        return (v / 5).roundToInt().coerceAtLeast(5) * 5
    }

    fun pickPlayerBrand(p: Player, school: Team?): ShoeCo {
        val schoolCo = school?.let { of(it) }
        val pool = ShoeCo.entries.toList()
        return Rng.weighted(pool) { co ->
            var w = when {
                p.ovr >= 88 && co in listOf(ShoeCo.NIKE, ShoeCo.JORDAN, ShoeCo.ADIDAS) -> 3.0
                p.ovr >= 80 -> 1.0 + co.dealMult
                else -> 1.2 - (co.prestigeNeed / 120.0)
            }
            if (schoolCo != null && co == schoolCo) w *= 2.4
            if (co == ShoeCo.JORDAN && p.ovr < 82) w *= 0.35
            max(0.05, w)
        }
    }

    fun clearDeal(p: Player) {
        p.shoe = ""; p.shoePay = 0; p.shoeLeft = 0
    }

    fun clearOffer(p: Player) {
        p.shoeOffer = ""; p.shoeOfferPay = 0; p.shoeOfferYears = 0
    }

    fun applyDeal(p: Player, co: ShoeCo, pay: Int, years: Int) {
        p.shoe = co.name
        p.shoePay = pay
        p.shoeLeft = years
        clearOffer(p)
        p.happiness = (p.happiness + if (p.has(Trait.BAG_CHASER)) 12 else 8).coerceAtMost(100)
    }

    fun acceptOffer(p: Player): Boolean {
        val co = byId(p.shoeOffer) ?: return false
        if (p.shoeOfferPay <= 0) return false
        applyDeal(p, co, p.shoeOfferPay, p.shoeOfferYears.coerceIn(1, 4))
        return true
    }

    fun declineOffer(p: Player) { clearOffer(p) }

    /** Total cash a player feels (school NIL + shoe). */
    fun totalPay(p: Player) = p.nil + p.shoePay

    fun marketPay(p: Player) = Ratings.expectedNil(p) + if (eligible(p)) (dealValue(p, ShoeCo.NIKE) * 0.55).roundToInt() / 5 * 5 else 0

    /**
     * End-of-summer shoe cycle: tick apparel + player deals, generate offers,
     * auto-accept for AI schools. User stars keep pending offers.
     */
    fun runOffseason(lg: League, os: OffseasonState) {
        ensure(lg)
        for (t in lg.teams) {
            if (t.apparelLeft > 0) t.apparelLeft--
            if (t.apparelLeft <= 0) assignApparel(t, force = true)
        }
        for (t in lg.teams) {
            for (pid in t.roster) {
                val p = lg.player(pid)
                if (p.shoeLeft > 0) {
                    p.shoeLeft--
                    if (p.shoeLeft <= 0) clearDeal(p)
                }
                clearOffer(p)
                if (!eligible(p)) continue
                val current = ofPlayer(p)
                val co = pickPlayerBrand(p, t)
                val pay = dealValue(p, co)
                val years = Rng.range(1, 3)
                if (current != null) {
                    val betterPay = pay > p.shoePay + 10
                    val betterBrand = current != co && pay >= p.shoePay
                    if (!betterPay && !betterBrand) continue
                }
                if (t.id == lg.userTeamId) {
                    p.shoeOffer = co.name
                    p.shoeOfferPay = pay
                    p.shoeOfferYears = years
                    os.shoeOffers += p.id
                } else {
                    applyDeal(p, co, pay, years)
                }
            }
        }
        // Seed a few deals on opening rosters so year one isn't empty for AI/user stars.
    }

    /** Stamp deals onto a freshly generated roster (league start). */
    fun seedRoster(lg: League, t: Team) {
        if (t.apparel.isEmpty()) assignApparel(t, force = true)
        for (pid in t.roster) {
            val p = lg.player(pid)
            if (!eligible(p)) continue
            if (!Rng.chance(0.55 + (p.ovr - 76) * 0.03)) continue
            val co = pickPlayerBrand(p, t)
            applyDeal(p, co, dealValue(p, co), Rng.range(1, 3))
            // Don't bump happiness at generation.
            p.happiness = p.happiness.coerceIn(55, 85)
        }
    }

    fun label(p: Player): String {
        val co = ofPlayer(p) ?: return ""
        return "${co.label} · ${moneyK(p.shoePay)}/yr"
    }

    fun offerLabel(p: Player): String {
        val co = byId(p.shoeOffer) ?: return ""
        return "${co.label} · ${moneyK(p.shoeOfferPay)}/yr · ${p.shoeOfferYears} yr"
    }

    /** NBA rookie shoe deal: lottery gets signature-tier money; second round stays modest. */
    fun nbaDeal(pick: Int, p: Player): Pair<ShoeCo, Int> {
        val co = when {
            pick <= 5 -> Rng.weighted(listOf(ShoeCo.JORDAN, ShoeCo.NIKE, ShoeCo.ADIDAS)) {
                when (it) { ShoeCo.JORDAN -> 1.4; ShoeCo.NIKE -> 1.3; else -> 0.7 }
            }
            pick <= 14 -> Rng.weighted(listOf(ShoeCo.NIKE, ShoeCo.JORDAN, ShoeCo.ADIDAS, ShoeCo.PUMA)) {
                when (it) { ShoeCo.NIKE -> 1.4; ShoeCo.JORDAN -> 1.0; ShoeCo.ADIDAS -> 1.1; else -> 0.6 }
            }
            pick <= 30 -> Rng.weighted(listOf(ShoeCo.NIKE, ShoeCo.ADIDAS, ShoeCo.UNDER_ARMOUR, ShoeCo.PUMA, ShoeCo.NEW_BALANCE)) { 1.0 + it.dealMult }
            else -> Rng.weighted(ShoeCo.entries.toList()) { 1.4 - it.prestigeNeed / 100.0 }
        }
        val tier = when {
            pick <= 3 -> 2200.0
            pick <= 10 -> 1200.0
            pick <= 14 -> 700.0
            pick <= 30 -> 280.0
            pick <= 45 -> 90.0
            else -> 35.0
        }
        var pay = tier * co.dealMult * (0.85 + (p.ovr - 70).coerceIn(0, 25) * 0.02)
        if (p.awards.any { it.contains("AA") || it.contains("MVP") }) pay *= 1.15
        return co to (pay / 5).roundToInt().coerceAtLeast(10) * 5
    }

    /** Stamp an NBA shoe deal onto a drafted player and return the brand + pay. */
    fun signNba(p: Player, pick: Int): Pair<ShoeCo, Int> {
        val (co, pay) = nbaDeal(pick, p)
        applyDeal(p, co, pay, years = if (pick <= 14) 5 else 3)
        return co to pay
    }

    fun draftShoeLine(d: DraftPick): String {
        val co = byId(d.shoe) ?: return ""
        return "${co.label} · ${moneyK(d.shoePay)}/yr"
    }

    fun moneyK(v: Int) = if (v >= 1000) "$%.1fM".format(v / 1000.0) else "$${v}K"
}

/** Real NBA clubs used as draft destinations (flavor only). */
object Nba {
    val teams = listOf(
        "Atlanta Hawks", "Boston Celtics", "Brooklyn Nets", "Charlotte Hornets", "Chicago Bulls",
        "Cleveland Cavaliers", "Dallas Mavericks", "Denver Nuggets", "Detroit Pistons", "Golden State Warriors",
        "Houston Rockets", "Indiana Pacers", "LA Clippers", "Los Angeles Lakers", "Memphis Grizzlies",
        "Miami Heat", "Milwaukee Bucks", "Minnesota Timberwolves", "New Orleans Pelicans", "New York Knicks",
        "Oklahoma City Thunder", "Orlando Magic", "Philadelphia 76ers", "Phoenix Suns", "Portland Trail Blazers",
        "Sacramento Kings", "San Antonio Spurs", "Toronto Raptors", "Utah Jazz", "Washington Wizards",
    )

    /** One shuffled order for the night; each club picks once per round (picks 1–30 and 31–60). */
    fun order(): List<String> = teams.shuffled(Rng.r)

    fun forPick(order: List<String>, pick: Int): String {
        val slot = (pick - 1) % order.size
        return order[slot]
    }

    fun short(name: String) = when {
        name.startsWith("LA ") -> "Clippers"
        name.startsWith("Los Angeles ") -> "Lakers"
        name.startsWith("Golden State ") -> "Warriors"
        name.startsWith("Oklahoma City ") -> "Thunder"
        name.startsWith("San Antonio ") -> "Spurs"
        name.startsWith("Portland ") -> "Blazers"
        name.startsWith("New Orleans ") -> "Pelicans"
        name.startsWith("New York ") -> "Knicks"
        name.startsWith("Philadelphia ") -> "76ers"
        name.startsWith("Minnesota ") -> "Wolves"
        else -> name.substringAfterLast(' ')
    }
}

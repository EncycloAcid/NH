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

    private fun moneyK(v: Int) = if (v >= 1000) "$%.1fM".format(v / 1000.0) else "$${v}K"
}

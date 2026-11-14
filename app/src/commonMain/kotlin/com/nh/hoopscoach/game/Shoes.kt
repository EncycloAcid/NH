package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.max
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
 * Real footwear / apparel companies for school kit deals, personal endorsements,
 * and pro signature lines. Names are flavor only — no logos or licensed likenesses.
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
    /** Multiplier on personal endorsement / signature offers. */
    val dealMult: Double,
    /** True when the brand bids on pro signature shoes. */
    val signature: Boolean,
    val pitch: String,
    val blurb: String,
) {
    NIKE(
        "Nike", "NKE", 0.085, 90, 3, 58, 1.18, true,
        "Swoosh worldwide. Your silhouette on the biggest launch calendar in the game.",
        "The biggest college apparel deal in the game",
    ),
    JORDAN(
        "Jordan Brand", "JOR", 0.095, 110, 4, 72, 1.28, true,
        "Jumpman only signs a few. We want your name next to the 23 legacy.",
        "Exclusive Jumpman programs and star signature energy",
    ),
    ADIDAS(
        "adidas", "ADI", 0.075, 70, 2, 48, 1.08, true,
        "Three stripes, global football crossover, and a signature built for your footwork.",
        "Three stripes on campus and in the portal",
    ),
    UNDER_ARMOUR(
        "Under Armour", "UA", 0.065, 55, 2, 40, 1.02, true,
        "Performance first. We'll put your game on every court that wears UA.",
        "Performance kit with a hungry mid-major footprint",
    ),
    PUMA(
        "Puma", "PMA", 0.05, 38, 1, 30, 0.98, true,
        "Flashy colorways and a rising hoop roster. Your model drops loud.",
        "Growing college presence and flashy colorways",
    ),
    NEW_BALANCE(
        "New Balance", "NB", 0.055, 42, 1, 34, 1.0, true,
        "Made-for-hoops energy and a cult following. We build around how you move.",
        "Freshman-class energy and made-in-USA flex",
    ),
    CONVERSE(
        "Converse", "CNV", 0.035, 25, 1, 22, 0.88, true,
        "Chuck heritage meets modern court tech. Classic silhouette, your story.",
        "Classic Chuck heritage for the mid-majors",
    ),
    REEBOK(
        "Reebok", "RBK", 0.04, 28, 1, 24, 0.9, true,
        "The comeback brand. Shaq energy, Answer vibes — your chapter next.",
        "Comeback brand hunting hungry programs",
    ),
    LI_NING(
        "Li-Ning", "LN", 0.045, 32, 1, 36, 1.05, true,
        "Global stage, Way of Wade DNA. We want your signature in every market.",
        "China's flagship hoop brand with worldwide reach",
    ),
    ANTA(
        "Anta", "ANT", 0.04, 30, 1, 34, 0.97, true,
        "KT line heat and a massive Asia footprint. Your shoe, their factories.",
        "Rising global apparel power with KT-line energy",
    ),
    PEAK(
        "Peak", "PK", 0.035, 24, 1, 26, 0.92, true,
        "Dwight Howard / Tony Parker DNA. Big overseas push with your name on it.",
        "Overseas hoop staple building U.S. name recognition",
    ),
    SKECHERS(
        "Skechers", "SKX", 0.038, 28, 1, 28, 0.94, true,
        "Comfort tech that sells. We'll put your face in every big-box aisle.",
        "Mass-market comfort brand expanding hard into hoops",
    ),
    FILA(
        "Fila", "FIL", 0.032, 22, 1, 20, 0.86, false,
        "Heritage stripes and street heat for campus kit.",
        "Retro brand energy for mid-majors and fashion-forward programs",
    ),
    AND1(
        "AND1", "AND", 0.03, 20, 1, 18, 0.84, true,
        "Streetball roots. Mixtape culture. Your model belongs on the blacktop.",
        "Streetball legend brand still chasing mixtape kids",
    ),
    ASICS(
        "ASICS", "ASI", 0.034, 26, 1, 27, 0.91, false,
        "Court stability from a running pedigree — quiet quality on campus.",
        "Performance footwear expanding into college basketball kit",
    ),
    CHAMPION(
        "Champion", "CHP", 0.03, 22, 1, 18, 0.82, false,
        "Practice gear and campus lifestyle — the C logo everywhere.",
        "Campus lifestyle apparel more than signature sneakers",
    ),
}

/**
 * College apparel partners + personal star endorsements, plus (in a pro player career)
 * your own signature shoe once brands come calling.
 */
object Shoes {
    /** Brands that bid on pro signature lines (real companies only). */
    val SIGNATURE_BRANDS: List<ShoeCo> = ShoeCo.entries.filter { it.signature }

    /** @deprecated Prefer [SIGNATURE_BRANDS]; kept for older call sites / tests. */
    val BRANDS: List<String> = SIGNATURE_BRANDS.map { it.label }

    fun byLabel(label: String): ShoeCo? =
        ShoeCo.entries.firstOrNull { it.label.equals(label, ignoreCase = true) || it.name.equals(label, ignoreCase = true) }

    fun shoeName(lg: League, d: ShoeDeal): String {
        val last = lg.hero?.last?.ifBlank { null } ?: "Sig"
        val co = byLabel(d.brand)
        val line = when (co) {
            ShoeCo.JORDAN -> "Jumpman $last"
            ShoeCo.NIKE -> "Nike $last"
            ShoeCo.ADIDAS -> "adidas $last"
            ShoeCo.LI_NING -> "Li-Ning $last"
            ShoeCo.AND1 -> "AND1 $last"
            else -> "${d.brand} $last"
        }
        return "$line ${d.model}".trim()
    }

    /** How hot he is with buyers: 0.4 a quiet year to 2.5 a superstar's. */
    private fun heat(lg: League, h: Player): Double {
        val r = Jerseys.rank(lg, h)
        val stars = h.awards.count { it == "${lg.season} All-Star" }
        val f = (lg.road?.followers ?: 0) / 1_000_000.0
        return (0.4 + h.stats.ppg / 25.0 + stars * 0.4 + (if (r > 0) (16 - r) * 0.05 else 0.0) + f.coerceAtMost(8.0) * 0.08).coerceIn(0.4, 2.5)
    }

    private fun signatureEligible(lg: League, h: Player): Boolean =
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
        if (r.shoe == null && r.shoeOffered != lg.season && signatureEligible(lg, h)) offerSignature(lg, h)
    }

    private fun offerSignature(lg: League, h: Player) {
        val r = lg.road ?: return
        r.shoeOffered = lg.season
        val f = (r.followers / 1_000_000.0).coerceAtMost(20.0)
        val stars = h.awards.count { it.endsWith("All-Star") }
        val base = 1_500 + f * 600 + stars * 900 + h.stats.ppg * 60
        // Three competing real brands; stars pull Nike / Jordan / adidas more often.
        val brands = pickSignatureBidders(h, stars).take(3)
        r.shoeOffers.clear()
        for (co in brands) {
            val years = when {
                co == ShoeCo.JORDAN || co == ShoeCo.NIKE -> Rng.range(4, 6)
                co.dealMult >= 1.05 -> Rng.range(3, 5)
                else -> Rng.range(2, 4)
            }
            val per = ((base * co.dealMult * (0.82 + Rng.d() * 0.36)) / 10).roundToInt() * 10
            val roy = ((3 + Rng.int(0, 3) + if (co.dealMult >= 1.15) 1 else 0) / 100.0).coerceIn(0.03, 0.08)
            r.shoeOffers += ShoeDeal(co.label, lg.season + 1, years, per, roy)
        }
        fun line(d: ShoeDeal) = "${d.brand}: ${d.years} years, ${Road.dollars(d.perYear)} a year, ${(d.royalty * 100).roundToInt()}% royalty"
        Farewell.show(lg, Ceremony(
            kind = "shoes", kicker = "SIGNATURE SHOE", title = "Your own shoe",
            sub = "${r.shoeOffers.size} real brands want ${h.name} on a signature line, starting next season.",
            teamId = h.teamId, number = h.number,
            statLabels = r.shoeOffers.map { it.brand },
            statValues = r.shoeOffers.map { Road.dollars(it.perYear) + "/yr" },
            choices = r.shoeOffers.map { line(it) },
            choiceQuotes = r.shoeOffers.map { d ->
                val co = byLabel(d.brand)
                "${d.brand}: \"${co?.pitch ?: "We want you on a signature line."}\""
            },
            choiceReactions = r.shoeOffers.map { d ->
                val firstDrop = ShoeDeal(d.brand, d.start, d.years, d.perYear, d.royalty, model = 1)
                listOf(Tribute("Sneaker Insider", "Sneakers", "The ${shoeName(lg, firstDrop)} is going to fly off the shelves."))
            },
            choiceTitle = "Pick your brand",
            choicePrompt = "The guarantee is paid every season; the royalty is your cut of every pair sold.",
            effects = r.shoeOffers.map { "${it.years} years · ${Road.dollars(it.perYear * it.years)} guaranteed · ${(it.royalty * 100).roundToInt()}% of sales" },
            badge = "${lg.season} offseason",
        ))
    }

    /** Weighted shortlist of signature bidders based on star power. */
    fun pickSignatureBidders(h: Player, allStars: Int = 0): List<ShoeCo> {
        val elite = allStars >= 2 || h.stats.ppg >= 24 || h.ovr >= 90
        val weights = SIGNATURE_BRANDS.associateWith { co ->
            var w = co.dealMult
            when (co) {
                ShoeCo.NIKE, ShoeCo.JORDAN, ShoeCo.ADIDAS -> w *= if (elite) 2.4 else 1.5
                ShoeCo.LI_NING, ShoeCo.NEW_BALANCE, ShoeCo.UNDER_ARMOUR, ShoeCo.PUMA -> w *= if (elite) 1.1 else 1.35
                ShoeCo.ANTA, ShoeCo.PEAK, ShoeCo.SKECHERS -> w *= if (elite) 0.7 else 1.2
                ShoeCo.AND1, ShoeCo.REEBOK, ShoeCo.CONVERSE -> w *= if (elite) 0.45 else 1.0
                else -> Unit
            }
            if (co == ShoeCo.JORDAN && !elite && h.ovr < 86) w *= 0.4
            max(0.08, w)
        }
        val picked = mutableListOf<ShoeCo>()
        val pool = SIGNATURE_BRANDS.toMutableList()
        repeat(minOf(3, pool.size)) {
            val next = Rng.weighted(pool) { weights.getValue(it) }
            picked += next
            pool.remove(next)
        }
        return picked
    }

    /** He picks a brand (pro signature ceremony). */
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

    // --- College apparel partners + personal endorsements ---

    fun byId(id: String): ShoeCo? = ShoeCo.entries.firstOrNull { it.name == id }

    fun of(t: Team): ShoeCo? = byId(t.apparel)

    fun ofPlayer(p: Player): ShoeCo? = byId(p.shoe)

    fun apparelMoney(t: Team): Int {
        val co = of(t) ?: return 0
        if (t.apparelPay > 0) return t.apparelPay
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

    private val POWER_KIT = setOf(ShoeCo.NIKE, ShoeCo.JORDAN, ShoeCo.ADIDAS)
    private val MID_KIT = setOf(ShoeCo.NIKE, ShoeCo.ADIDAS, ShoeCo.UNDER_ARMOUR, ShoeCo.JORDAN, ShoeCo.NEW_BALANCE, ShoeCo.PUMA)
    private val HUNGRY_KIT = setOf(
        ShoeCo.PUMA, ShoeCo.NEW_BALANCE, ShoeCo.CONVERSE, ShoeCo.REEBOK, ShoeCo.UNDER_ARMOUR,
        ShoeCo.FILA, ShoeCo.AND1, ShoeCo.CHAMPION, ShoeCo.ASICS, ShoeCo.PEAK, ShoeCo.SKECHERS,
    )

    fun pickSchoolBrand(prestige: Int): ShoeCo {
        val weights = ShoeCo.entries.associateWith { co ->
            val fit = 1.0 - kotlin.math.abs(prestige - co.prestigeNeed) / 55.0
            val tier = when {
                prestige >= 80 && co in POWER_KIT -> 1.7
                prestige >= 65 && co in MID_KIT -> 1.4
                prestige < 40 && co in HUNGRY_KIT -> 1.45
                prestige in 40..64 && co in listOf(ShoeCo.LI_NING, ShoeCo.ANTA, ShoeCo.UNDER_ARMOUR, ShoeCo.PUMA) -> 1.25
                else -> 1.0
            }
            // Champion / Fila / ASICS are apparel-leaning — fine for schools, rare for stars.
            val apparelLean = if (!co.signature) 1.15 else 1.0
            max(0.06, fit.coerceIn(0.05, 1.2) * tier * apparelLean)
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
        // Overseas brands pay up for stars who can sell abroad.
        if (co in listOf(ShoeCo.LI_NING, ShoeCo.ANTA, ShoeCo.PEAK) && p.ovr >= 84) v *= 1.18
        return (v / 5).roundToInt().coerceAtLeast(5) * 5
    }

    fun pickPlayerBrand(p: Player, school: Team?): ShoeCo {
        val schoolCo = school?.let { of(it) }
        // Personal deals prefer signature-capable brands.
        val pool = SIGNATURE_BRANDS
        return Rng.weighted(pool) { co ->
            var w = when {
                p.ovr >= 88 && co in POWER_KIT -> 3.2
                p.ovr >= 84 && co in listOf(ShoeCo.LI_NING, ShoeCo.NEW_BALANCE, ShoeCo.UNDER_ARMOUR) -> 2.0
                p.ovr >= 80 -> 1.0 + co.dealMult
                else -> 1.25 - (co.prestigeNeed / 120.0)
            }
            if (schoolCo != null && co == schoolCo) w *= 2.5
            if (co == ShoeCo.JORDAN && p.ovr < 82) w *= 0.32
            if (co == ShoeCo.AND1 && p.ovr >= 86) w *= 0.5
            max(0.05, w)
        }
    }

    fun clearDeal(p: Player) {
        p.shoe = ""; p.shoePay = 0; p.shoeLeft = 0
    }

    fun clearOffer(p: Player) {
        p.shoeOffer = ""; p.shoeOfferPay = 0; p.shoeOfferYears = 0
        p.shoeBids.clear()
    }

    // ---------- bidding ----------

    /** A bid as (brand, pay, years). */
    fun parseBid(s: String): Triple<ShoeCo, Int, Int>? {
        val p = s.split('|')
        val co = byId(p.getOrNull(0) ?: return null) ?: return null
        return Triple(co, p.getOrNull(1)?.toIntOrNull() ?: return null, p.getOrNull(2)?.toIntOrNull() ?: 1)
    }

    /** Two or three brands bidding for one of the user's players; the school's kit brand pays a little more to keep its look. */
    fun playerBids(lg: League, p: Player, t: Team): List<String> {
        val school = of(t)
        val brands = LinkedHashSet<ShoeCo>()
        repeat(8) { if (brands.size < 3) brands += pickPlayerBrand(p, t) }
        if (school != null && school.signature && Rng.chance(0.7)) brands += school
        return brands.take(3).map { co ->
            var pay = dealValue(p, co) * (0.9 + Rng.d() * 0.25)
            if (co == school) pay *= 1.08
            "${co.name}|${(pay / 5).roundToInt() * 5}|${Rng.range(1, 3)}"
        }
    }

    /** Signs one of a player's bids. A deal with the school's own brand pleases him a little more (no clash on game day). */
    fun signBid(lg: League, p: Player, bid: String): Boolean {
        val (co, pay, years) = parseBid(bid) ?: return false
        applyDeal(p, co, pay, years)
        val t = lg.teams.getOrNull(p.teamId)
        if (t != null && of(t) == co) p.happiness = (p.happiness + 3).coerceAtMost(100)
        return true
    }

    /** What brands bid for the school's apparel deal: money a season, years, and a Final Four bonus. */
    fun apparelBids(lg: League, t: Team): List<String> {
        val brands = LinkedHashSet<ShoeCo>()
        repeat(12) { if (brands.size < 3) brands += pickSchoolBrand(t.prestige) }
        val donor = Finance.donor(t.prestige)
        // Deep March runs make a school worth more to a brand.
        val march = t.history.takeLast(3).count { Brand.roundOf(it.result) >= 3 }
        return brands.map { co ->
            val base = max(co.apparelFloor, (donor * co.apparelPct).roundToInt()) * (0.9 + Rng.d() * 0.3 + march * 0.05)
            val pay = (base / 5).roundToInt() * 5
            val bonus = (pay * (0.15 + Rng.d() * 0.2) / 5).roundToInt() * 5
            "${co.name}|$pay|${Rng.range(3, 6)}|$bonus"
        }
    }

    /** Signs a school apparel bid. */
    fun signApparel(lg: League, t: Team, bid: String) {
        val p = bid.split('|')
        val co = byId(p.getOrNull(0) ?: return) ?: return
        t.apparel = co.name
        t.apparelPay = p.getOrNull(1)?.toIntOrNull() ?: 0
        t.apparelLeft = p.getOrNull(2)?.toIntOrNull() ?: 4
        t.apparelBonus = p.getOrNull(3)?.toIntOrNull() ?: 0
        t.apparelBids.clear()
        lg.news += News(lg.season, lg.day, "${t.school} signs a ${t.apparelLeft}-year apparel deal with ${co.label}", t.id == lg.userTeamId, kind = "move", teamId = t.id,
            body = "${moneyK(t.apparelPay)} a season" + (if (t.apparelBonus > 0) ", plus ${moneyK(t.apparelBonus)} for a Final Four" else "") + ". ${co.blurb}.", source = "Hoops Wire")
    }

    /** If the user never chose, the best-paying bid is signed. */
    fun settleApparel(lg: League) {
        val t = lg.teams.getOrNull(lg.userTeamId) ?: return
        val best = t.apparelBids.maxByOrNull { it.split('|').getOrNull(1)?.toIntOrNull() ?: 0 } ?: return
        signApparel(lg, t, best)
    }

    /** Season's end: brands pay their Final Four bonuses. */
    fun marchBonuses(lg: League) {
        if (lg.pro) return
        for (t in lg.teams) {
            if (t.apparelBonus <= 0 || Awards.tourneyRound(lg, t.id) < 5) continue
            Business.ensure(lg, t)
            t.cash += t.apparelBonus
            if (t.id == lg.userTeamId) lg.news += News(lg.season, lg.day, "${of(t)?.label ?: "Your apparel partner"} pays a ${moneyK(t.apparelBonus)} Final Four bonus", true,
                kind = "move", teamId = t.id, body = "The bonus in the apparel deal goes into the upgrade budget.", source = "Hoops Wire")
        }
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
            if (t.apparelLeft > 0) continue
            // The user's deal is up: brands bid and he chooses (see [apparelBids]); everyone else renews on the spot.
            if (t.id == lg.userTeamId && !lg.pro && !lg.playerMode) t.apparelBids = apparelBids(lg, t).toMutableList()
            else { assignApparel(t, force = true); t.apparelPay = 0; t.apparelBonus = 0 }
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
                    // A bidding war for his own stars: two or three brands, the school's kit brand among them when it wants him.
                    p.shoeBids = playerBids(lg, p, t).toMutableList()
                    val best = p.shoeBids.mapNotNull { parseBid(it) }.maxByOrNull { it.second } ?: Triple(co, pay, years)
                    p.shoeOffer = best.first.name
                    p.shoeOfferPay = best.second
                    p.shoeOfferYears = best.third
                    os.shoeOffers += p.id
                } else {
                    applyDeal(p, co, pay, years)
                }
            }
        }
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

    fun blurb(p: Player): String = ofPlayer(p)?.blurb ?: byId(p.shoeOffer)?.blurb ?: ""

    /** NBA rookie shoe deal: lottery gets signature-tier money; second round stays modest. */
    fun nbaDeal(pick: Int, p: Player): Pair<ShoeCo, Int> {
        val co = when {
            pick <= 5 -> Rng.weighted(listOf(ShoeCo.JORDAN, ShoeCo.NIKE, ShoeCo.ADIDAS, ShoeCo.LI_NING)) {
                when (it) {
                    ShoeCo.JORDAN -> 1.5
                    ShoeCo.NIKE -> 1.35
                    ShoeCo.ADIDAS -> 0.85
                    else -> 0.55
                }
            }
            pick <= 14 -> Rng.weighted(
                listOf(ShoeCo.NIKE, ShoeCo.JORDAN, ShoeCo.ADIDAS, ShoeCo.PUMA, ShoeCo.NEW_BALANCE, ShoeCo.UNDER_ARMOUR, ShoeCo.LI_NING),
            ) {
                when (it) {
                    ShoeCo.NIKE -> 1.4
                    ShoeCo.JORDAN -> 1.05
                    ShoeCo.ADIDAS -> 1.1
                    ShoeCo.LI_NING -> 0.75
                    else -> 0.7
                }
            }
            pick <= 30 -> Rng.weighted(
                listOf(
                    ShoeCo.NIKE, ShoeCo.ADIDAS, ShoeCo.UNDER_ARMOUR, ShoeCo.PUMA, ShoeCo.NEW_BALANCE,
                    ShoeCo.ANTA, ShoeCo.SKECHERS, ShoeCo.REEBOK,
                ),
            ) { 1.0 + it.dealMult }
            else -> Rng.weighted(SIGNATURE_BRANDS) { 1.45 - it.prestigeNeed / 100.0 }
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

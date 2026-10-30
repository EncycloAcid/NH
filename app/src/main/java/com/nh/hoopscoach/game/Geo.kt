package com.nh.hoopscoach.game

/** Where schools and recruits are from, for recruiting's location fit. */
object Geo {
    private val schoolStates: Map<String, String> = """
        BC:MA CAL:CA CLEM:SC DUKE:NC FSU:FL GT:GA LOU:KY MIA:FL NCST:NC UNC:NC ND:IN PITT:PA SMU:TX STAN:CA SYR:NY UVA:VA VT:VA WAKE:NC
        ILL:IL IND:IN IOWA:IA MD:MD MICH:MI MSU:MI MINN:MN NEB:NE NW:IL OSU:OH ORE:OR PSU:PA PUR:IN RUTG:NJ UCLA:CA USC:CA WASH:WA WIS:WI
        ARIZ:AZ ASU:AZ BAY:TX BYU:UT CIN:OH COLO:CO HOU:TX ISU:IA KU:KS KSU:KS OKST:OK TCU:TX TTU:TX UCF:FL UTAH:UT WVU:WV
        BAMA:AL ARK:AR AUB:AL FLA:FL UGA:GA UK:KY LSU:LA MSST:MS MIZ:MO OU:OK MISS:MS SC:SC TENN:TN TEX:TX TAMU:TX VAN:TN
        BUT:IN CREI:NE DEP:IL GTWN:DC MARQ:WI PROV:RI HALL:NJ SJU:NY CONN:CT NOVA:PA XAV:OH
        BSU:ID CSU:CO FRES:CA GONZ:WA ORST:OR SDSU:CA TXST:TX USU:UT WSU:WA
        AF:CO GCU:AZ HAW:HI NEV:NV UNM:NM SJSU:CA UNLV:NV UTEP:TX WYO:WY
        LMU:CA PAC:CA PEPP:CA PORT:OR SMC:CA USD:CA SF:CA SCU:CA SEA:WA
        DAV:NC DAY:OH DUQ:PA FOR:NY GMU:VA GW:DC LAS:PA LUC:IL URI:RI RICH:VA JOES:PA SLU:MO SBU:NY VCU:VA
        CLT:NC ECU:NC FAU:FL MEM:TN UNT:TX RICE:TX USF:FL TEM:PA TUL:LA TLSA:OK UAB:AL UTSA:TX WICH:KS
        BEL:TN BRAD:IL DRKE:IA EVAN:IN ILST:IL INST:IN MUR:KY UNI:IA SIU:IL UIC:IL VALP:IN
        DEL:DE FIU:FL JVST:AL KENN:GA LIB:VA LT:LA MTSU:TN MOST:MO NMSU:NM SHSU:TX WKU:KY
        APP:NC ARST:AR CCU:SC GASO:GA GAST:GA JMU:VA ULL:LA ULM:LA MRSH:WV ODU:VA USA:AL USM:MS TROY:AL
        AKR:OH BALL:IN BGSU:OH BUFF:NY CMU:MI EMU:MI KENT:OH MOH:OH OHIO:OH TOL:OH MASS:MA WMU:MI
        CLEV:OH DET:MI GB:WI IUI:IN MILW:WI NIU:IL NKU:KY OAK:MI PFW:IN RMU:PA WRST:OH YSU:OH
        ACU:TX CBU:CA SUU:UT TARL:TX UTA:TX UTU:UT UVU:UT
        CP:CA CSUB:CA CSUF:CA CSUN:CA LBSU:CA SAC:CA UCD:CA UCI:CA UCR:CA UCSD:CA UCSB:CA
        EWU:WA IDHO:ID IDST:ID MONT:MT MTST:MT NAU:AZ UNCO:CO PRST:OR WEB:UT
        CHSO:SC GWEB:NC HPU:NC LONG:VA PRES:SC RAD:VA UNCA:NC UPST:SC WIN:SC
        CAM:NC COFC:SC DREX:PA ELON:NC HAMP:VA HOF:NY MONM:NJ NCAT:NC NE:MA STBK:NY TOW:MD WM:VA
        APSU:TN BELL:KY UCA:AR EKU:KY FGCU:FL JAX:FL LIP:TN UNA:AL UNF:FL QUC:NC STET:FL UWG:GA
        UTC:TN ETSU:TN FUR:SC MER:GA SAM:AL CIT:SC UNCG:NC VMI:VA WCU:NC WOF:SC
        ETAM:TX HCU:TX UIW:TX LAM:TX MCN:LA UNO:LA NICH:LA NWST:LA SELA:LA SFA:TX AMCC:TX RGV:TX
        DEN:CO KC:MO UND:ND NDSU:ND OMA:NE ORU:OK SDAK:SD SDST:SD STMN:MN
        EIU:IL LIN:MO UALR:AR MORE:KY SIUE:IL SEMO:MO USI:IN TNST:TN TNTC:TN UTM:TN WIU:IL
        AMER:DC ARMY:NY BU:MA BUCK:PA COLG:NY HC:MA LAF:PA LEH:PA LMD:MD NAVY:MD
        BRWN:RI CLMB:NY COR:NY DART:NH HARV:MA PENN:PA PRIN:NJ YALE:CT
        CAN:NY FAIR:CT IONA:NY MANH:NY MRST:NY MRMK:MA MSM:MD NIAG:NY QU:CT RID:NJ SHU:CT SPU:NJ SIEN:NY
        ALB:NY BING:NY BRY:RI ME:ME UNH:NH NJIT:NJ UML:MA UMBC:MD UVM:VT
        CCSU:CT CHST:IL FDU:NJ LEM:NY LIU:NY MERC:PA UNHV:CT SFPA:PA STON:MA WAG:NY
        COPP:MD DSU:DE HOW:DC UMES:MD MORG:MD NORF:VA NCCU:NC SCST:SC
        AAMU:AL ALST:AL ALCN:MS UAPB:AR BCU:FL FAMU:FL GRAM:LA JKST:MS MVSU:MS PVAM:TX SOU:LA TXSO:TX
    """.trim().split(Regex("\\s+")).associate { it.substringBefore(':') to it.substringAfter(':') }

    private val regions = mapOf(
        "Northeast" to "ME NH VT MA RI CT NY NJ PA",
        "Mid-Atlantic" to "DE MD DC VA WV",
        "Southeast" to "NC SC GA FL AL MS TN KY",
        "South Central" to "TX LA AR OK",
        "Midwest" to "OH MI IN IL WI MN IA MO KS NE ND SD",
        "Mountain" to "CO UT NV AZ NM WY ID MT",
        "West Coast" to "CA OR WA HI AK",
    ).flatMap { (r, s) -> s.split(' ').map { it to r } }.toMap()

    fun stateOf(t: Team): String? = schoolStates[t.abbr]

    /** A recruit's home state, from "City, ST". Players from abroad have none. */
    fun stateOf(p: Player): String? = p.hometown.substringAfterLast(", ", "").takeIf { it.length == 2 && it in regions }

    fun region(state: String?): String? = state?.let { regions[it] }

    /** 2 = same state, 1 = same region, 0 = farther away (or from abroad). */
    fun closeness(t: Team, p: Player): Int {
        val a = stateOf(t) ?: return 0
        val b = stateOf(p) ?: return 0
        return when { a == b -> 2; region(a) == region(b) -> 1; else -> 0 }
    }

    fun known(abbr: String) = abbr in schoolStates
}

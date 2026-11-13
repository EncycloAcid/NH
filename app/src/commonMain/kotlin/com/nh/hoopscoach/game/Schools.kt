package com.nh.hoopscoach.game

/**
 * Division I programs by conference (2026-27 alignment, to the best of our knowledge).
 * Each school line: Name|Abbr|Nickname|Primary|Secondary|Prestige
 * Schools and conferences are real; every player and coach in the game is fictional.
 */
object Schools {
    class Conf(val name: String, val abbr: String, val tier: Int, val schools: String)

    val conferences = listOf(
        Conf("Atlantic Coast", "ACC", 0, """
            Boston College|BC|Eagles|8A100B|B29D6C|60
            California|CAL|Golden Bears|003262|FDB515|58
            Clemson|CLEM|Tigers|F56600|522D80|74
            Duke|DUKE|Blue Devils|003087|FFFFFF|97
            Florida State|FSU|Seminoles|782F40|CEB888|70
            Georgia Tech|GT|Yellow Jackets|B3A369|003057|58
            Louisville|LOU|Cardinals|AD0000|000000|82
            Miami|MIA|Hurricanes|F47321|005030|70
            NC State|NCST|Wolfpack|CC0000|000000|72
            North Carolina|UNC|Tar Heels|7BAFD4|13294B|95
            Notre Dame|ND|Fighting Irish|0C2340|C99700|64
            Pittsburgh|PITT|Panthers|003594|FFB81C|66
            SMU|SMU|Mustangs|0033A0|C8102E|64
            Stanford|STAN|Cardinal|8C1515|FFFFFF|60
            Syracuse|SYR|Orange|F76900|000E54|76
            Virginia|UVA|Cavaliers|232D4B|F84C1E|78
            Virginia Tech|VT|Hokies|630031|CF4420|64
            Wake Forest|WAKE|Demon Deacons|9E7E38|000000|62
        """),
        Conf("Big Ten", "B1G", 0, """
            Illinois|ILL|Fighting Illini|13294B|E84A27|82
            Indiana|IND|Hoosiers|990000|EEEDEB|80
            Iowa|IOWA|Hawkeyes|FFCD00|000000|72
            Maryland|MD|Terrapins|E03A3E|FFD520|76
            Michigan|MICH|Wolverines|00274C|FFCB05|86
            Michigan State|MSU|Spartans|18453B|FFFFFF|88
            Minnesota|MINN|Golden Gophers|7A0019|FFCC33|56
            Nebraska|NEB|Cornhuskers|E41C38|FFFFFF|62
            Northwestern|NW|Wildcats|4E2A84|FFFFFF|60
            Ohio State|OSU|Buckeyes|BB0000|666666|76
            Oregon|ORE|Ducks|154733|FEE123|76
            Penn State|PSU|Nittany Lions|041E42|FFFFFF|56
            Purdue|PUR|Boilermakers|CEB888|000000|88
            Rutgers|RUTG|Scarlet Knights|CC0033|000000|60
            UCLA|UCLA|Bruins|2D68C4|F2A900|86
            USC|USC|Trojans|990000|FFC72C|72
            Washington|WASH|Huskies|4B2E83|B7A57A|62
            Wisconsin|WIS|Badgers|C5050C|FFFFFF|78
        """),
        Conf("Big 12", "B12", 0, """
            Arizona|ARIZ|Wildcats|CC0033|003366|90
            Arizona State|ASU|Sun Devils|8C1D40|FFC627|62
            Baylor|BAY|Bears|154734|FFB81C|82
            BYU|BYU|Cougars|002E5D|FFFFFF|76
            Cincinnati|CIN|Bearcats|E00122|000000|70
            Colorado|COLO|Buffaloes|CFB87C|000000|62
            Houston|HOU|Cougars|C8102E|FFFFFF|92
            Iowa State|ISU|Cyclones|C8102E|F1BE48|84
            Kansas|KU|Jayhawks|0051BA|E8000D|96
            Kansas State|KSU|Wildcats|512888|FFFFFF|70
            Oklahoma State|OKST|Cowboys|FF7300|000000|64
            TCU|TCU|Horned Frogs|4D1979|A3A9AC|68
            Texas Tech|TTU|Red Raiders|CC0000|000000|84
            UCF|UCF|Knights|000000|BA9B37|64
            Utah|UTAH|Utes|CC0000|FFFFFF|60
            West Virginia|WVU|Mountaineers|002855|EAAA00|70
        """),
        Conf("Southeastern", "SEC", 0, """
            Alabama|BAMA|Crimson Tide|9E1B32|FFFFFF|88
            Arkansas|ARK|Razorbacks|9D2235|FFFFFF|82
            Auburn|AUB|Tigers|0C2340|E87722|88
            Florida|FLA|Gators|0021A5|FA4616|92
            Georgia|UGA|Bulldogs|BA0C2F|000000|64
            Kentucky|UK|Wildcats|0033A0|FFFFFF|96
            LSU|LSU|Tigers|461D7C|FDD023|68
            Mississippi State|MSST|Bulldogs|660000|FFFFFF|68
            Missouri|MIZ|Tigers|F1B82D|000000|68
            Oklahoma|OU|Sooners|841617|FDF9D8|68
            Ole Miss|MISS|Rebels|CE1126|14213D|70
            South Carolina|SC|Gamecocks|73000A|000000|62
            Tennessee|TENN|Volunteers|FF8200|FFFFFF|90
            Texas|TEX|Longhorns|BF5700|FFFFFF|78
            Texas A&M|TAMU|Aggies|500000|FFFFFF|76
            Vanderbilt|VAN|Commodores|866D4B|000000|64
        """),
        Conf("Big East", "BE", 0, """
            Butler|BUT|Bulldogs|13294B|FFFFFF|64
            Creighton|CREI|Bluejays|005CA9|FFFFFF|80
            DePaul|DEP|Blue Demons|005EB8|E4002B|50
            Georgetown|GTWN|Hoyas|041E42|8D817B|62
            Marquette|MARQ|Golden Eagles|003366|FFCC00|82
            Providence|PROV|Friars|000000|A2AAAD|66
            Seton Hall|HALL|Pirates|004488|FFFFFF|62
            St. John's|SJU|Red Storm|BA0C2F|FFFFFF|84
            UConn|CONN|Huskies|000E2F|FFFFFF|95
            Villanova|NOVA|Wildcats|00205B|13B5EA|80
            Xavier|XAV|Musketeers|0C2340|9EA2A2|74
        """),
        Conf("Pac-12", "P12", 1, """
            Boise State|BSU|Broncos|0033A0|D64309|62
            Colorado State|CSU|Rams|1E4D2B|C8C372|60
            Fresno State|FRES|Bulldogs|DB0032|002E6D|46
            Gonzaga|GONZ|Bulldogs|002967|C8102E|90
            Oregon State|ORST|Beavers|DC4405|000000|50
            San Diego State|SDSU|Aztecs|A6192E|000000|74
            Texas State|TXST|Bobcats|501214|8D734A|44
            Utah State|USU|Aggies|0F2439|FFFFFF|66
            Washington State|WSU|Cougars|981E32|5E6A71|52
        """),
        Conf("Mountain West", "MWC", 1, """
            Air Force|AF|Falcons|003087|8A8D8F|36
            Grand Canyon|GCU|Antelopes|522398|FFFFFF|58
            Hawai'i|HAW|Rainbow Warriors|024731|FFFFFF|44
            Nevada|NEV|Wolf Pack|003366|807F84|56
            New Mexico|UNM|Lobos|BA0C2F|A7A8AA|62
            San Jose State|SJSU|Spartans|0055A2|E5A823|36
            UNLV|UNLV|Rebels|CF0A2C|666666|56
            UTEP|UTEP|Miners|041E42|FF8200|46
            Wyoming|WYO|Cowboys|492F24|FFC425|46
        """),
        Conf("West Coast", "WCC", 1, """
            Loyola Marymount|LMU|Lions|8A2432|00347A|42
            Pacific|PAC|Tigers|F47920|000000|32
            Pepperdine|PEPP|Waves|00205C|EE7623|38
            Portland|PORT|Pilots|5E2A84|FFFFFF|34
            Saint Mary's|SMC|Gaels|06315B|D80024|72
            San Diego|USD|Toreros|002868|75BEE9|38
            San Francisco|SF|Dons|00543C|FDBB30|54
            Santa Clara|SCU|Broncos|862633|FFFFFF|56
            Seattle U|SEA|Redhawks|AA0000|000000|42
        """),
        Conf("Atlantic 10", "A10", 1, """
            Davidson|DAV|Wildcats|AC1A2F|000000|52
            Dayton|DAY|Flyers|CE1141|004B8D|72
            Duquesne|DUQ|Dukes|002A5C|BA0C2F|48
            Fordham|FOR|Rams|860038|FFFFFF|36
            George Mason|GMU|Patriots|006633|FFCC33|56
            George Washington|GW|Revolutionaries|033C5A|AA9868|46
            La Salle|LAS|Explorers|00205B|FFC72C|36
            Loyola Chicago|LUC|Ramblers|7A1F32|FFB81C|56
            Rhode Island|URI|Rams|002147|75B2DD|46
            Richmond|RICH|Spiders|990000|000066|48
            Saint Joseph's|JOES|Hawks|9E1B32|A2AAAD|48
            Saint Louis|SLU|Billikens|003DA5|FFFFFF|58
            St. Bonaventure|SBU|Bonnies|70261D|FFFFFF|46
            VCU|VCU|Rams|000000|FFB300|68
        """),
        Conf("American", "AAC", 1, """
            Charlotte|CLT|49ers|005035|A49665|40
            East Carolina|ECU|Pirates|592A8A|FDC82F|36
            Florida Atlantic|FAU|Owls|003366|CC0000|58
            Memphis|MEM|Tigers|003087|898D8D|74
            North Texas|UNT|Mean Green|00853E|FFFFFF|56
            Rice|RICE|Owls|00205B|7C7E7F|36
            South Florida|USF|Bulls|006747|CFC493|44
            Temple|TEM|Owls|9D2235|FFFFFF|44
            Tulane|TUL|Green Wave|006747|418FDE|44
            Tulsa|TLSA|Golden Hurricane|002D72|C8102E|40
            UAB|UAB|Blazers|1E6B52|F4C300|58
            UTSA|UTSA|Roadrunners|0C2340|F15A22|34
            Wichita State|WICH|Shockers|FFC217|000000|58
        """),
        Conf("Missouri Valley", "MVC", 1, """
            Belmont|BEL|Bruins|002469|C8102E|50
            Bradley|BRAD|Braves|A50000|FFFFFF|52
            Drake|DRKE|Bulldogs|004477|FFFFFF|58
            Evansville|EVAN|Purple Aces|4F2683|FF6F00|30
            Illinois State|ILST|Redbirds|CE1126|F9DD16|42
            Indiana State|INST|Sycamores|00669A|FFFFFF|46
            Murray State|MUR|Racers|002148|ECAC00|46
            Northern Iowa|UNI|Panthers|4B116F|FFCC00|50
            Southern Illinois|SIU|Salukis|720000|000000|44
            UIC|UIC|Flames|001E62|D50032|38
            Valparaiso|VALP|Beacons|613318|FFCC00|32
        """),
        Conf("Conference USA", "CUSA", 2, """
            Delaware|DEL|Blue Hens|00539F|FFD200|36
            FIU|FIU|Panthers|081E3F|B6862C|30
            Jacksonville State|JVST|Gamecocks|CC0000|000000|40
            Kennesaw State|KENN|Owls|FDBB30|0B1315|40
            Liberty|LIB|Flames|002D62|C8102E|54
            Louisiana Tech|LT|Bulldogs|002F8B|E31B23|48
            Middle Tennessee|MTSU|Blue Raiders|0066CC|FFFFFF|46
            Missouri State|MOST|Bears|5E0009|FFFFFF|42
            New Mexico State|NMSU|Aggies|861F41|FFFFFF|44
            Sam Houston|SHSU|Bearkats|F47321|FFFFFF|44
            Western Kentucky|WKU|Hilltoppers|C60C30|FFFFFF|50
        """),
        Conf("Sun Belt", "SBC", 2, """
            Appalachian State|APP|Mountaineers|222222|FFCC00|42
            Arkansas State|ARST|Red Wolves|CC092F|000000|46
            Coastal Carolina|CCU|Chanticleers|006F71|A27752|32
            Georgia Southern|GASO|Eagles|041E42|A3AAAE|36
            Georgia State|GAST|Panthers|0039A6|FFFFFF|40
            James Madison|JMU|Dukes|450084|CBB677|52
            Louisiana|ULL|Ragin' Cajuns|CE181E|FFFFFF|44
            Louisiana-Monroe|ULM|Warhawks|840029|BD955A|24
            Marshall|MRSH|Thundering Herd|00B140|FFFFFF|40
            Old Dominion|ODU|Monarchs|003057|7C878E|42
            South Alabama|USA|Jaguars|00205B|BF0D3E|38
            Southern Miss|USM|Golden Eagles|000000|FFAB00|34
            Troy|TROY|Trojans|8A2432|B3B5B8|38
        """),
        Conf("Mid-American", "MAC", 2, """
            Akron|AKR|Zips|041E42|A89968|52
            Ball State|BALL|Cardinals|BA0C2F|FFFFFF|38
            Bowling Green|BGSU|Falcons|FE5000|4F2C1D|34
            Buffalo|BUFF|Bulls|005BBB|FFFFFF|38
            Central Michigan|CMU|Chippewas|6A0032|FFC82E|34
            Eastern Michigan|EMU|Eagles|046A38|FFFFFF|28
            Kent State|KENT|Golden Flashes|002664|EAAB00|46
            Miami (OH)|MOH|RedHawks|C3142D|FFFFFF|40
            Ohio|OHIO|Bobcats|00694E|FFFFFF|44
            Toledo|TOL|Rockets|15397F|FFDA00|44
            UMass|MASS|Minutemen|881C1C|FFFFFF|42
            Western Michigan|WMU|Broncos|6C4023|B5A167|30
        """),
        Conf("Horizon League", "HZN", 2, """
            Cleveland State|CLEV|Vikings|006A4D|FFFFFF|36
            Detroit Mercy|DET|Titans|A50D2D|002D72|28
            Green Bay|GB|Phoenix|006F44|FFFFFF|30
            IU Indianapolis|IUI|Jaguars|990000|CFB53B|22
            Milwaukee|MILW|Panthers|000000|FFBD00|36
            Northern Illinois|NIU|Huskies|BA0C2F|000000|30
            Northern Kentucky|NKU|Norse|000000|FFC72C|38
            Oakland|OAK|Golden Grizzlies|000000|B59A57|42
            Purdue Fort Wayne|PFW|Mastodons|000000|CFB991|34
            Robert Morris|RMU|Colonials|14234B|C8102E|38
            Wright State|WRST|Raiders|007A33|CBA052|40
            Youngstown State|YSU|Penguins|C8102E|FFFFFF|38
        """),
        Conf("Western Athletic", "WAC", 2, """
            Abilene Christian|ACU|Wildcats|4F2170|FFFFFF|36
            California Baptist|CBU|Lancers|002554|A27D32|36
            Southern Utah|SUU|Thunderbirds|BA0C2F|FFFFFF|32
            Tarleton State|TARL|Texans|4F2D7F|FFFFFF|32
            UT Arlington|UTA|Mavericks|0064B1|F58025|36
            Utah Tech|UTU|Trailblazers|BA1C21|003058|26
            Utah Valley|UVU|Wolverines|275D38|FFFFFF|42
        """),
        Conf("Big West", "BWC", 2, """
            Cal Poly|CP|Mustangs|154734|BD8B13|28
            Cal State Bakersfield|CSUB|Roadrunners|003594|FFC72C|28
            Cal State Fullerton|CSUF|Titans|00274C|FF7900|32
            Cal State Northridge|CSUN|Matadors|D22030|000000|34
            Long Beach State|LBSU|Beach|000000|F6C40C|38
            Sacramento State|SAC|Hornets|043927|C4B581|30
            UC Davis|UCD|Aggies|002855|DAAA00|34
            UC Irvine|UCI|Anteaters|0064A4|FFD200|54
            UC Riverside|UCR|Highlanders|2D6CC0|F1AB00|34
            UC San Diego|UCSD|Tritons|182B49|C69214|50
            UC Santa Barbara|UCSB|Gauchos|003660|FEBC11|46
        """),
        Conf("Big Sky", "BSKY", 2, """
            Eastern Washington|EWU|Eagles|A10022|000000|38
            Idaho|IDHO|Vandals|000000|B3A369|30
            Idaho State|IDST|Bengals|F47920|000000|30
            Montana|MONT|Grizzlies|70003C|999999|42
            Montana State|MTST|Bobcats|00205B|B9975B|42
            Northern Arizona|NAU|Lumberjacks|003466|FFD200|32
            Northern Colorado|UNCO|Bears|013C65|F6B000|36
            Portland State|PRST|Vikings|154734|FFFFFF|32
            Weber State|WEB|Wildcats|492365|FFFFFF|38
        """),
        Conf("Big South", "BSO", 2, """
            Charleston Southern|CHSO|Buccaneers|002D62|A89968|24
            Gardner-Webb|GWEB|Runnin' Bulldogs|BB0000|000000|28
            High Point|HPU|Panthers|330072|FFFFFF|50
            Longwood|LONG|Lancers|003865|A2AAAD|36
            Presbyterian|PRES|Blue Hose|00205B|C8102E|24
            Radford|RAD|Highlanders|C2011B|004B8D|32
            UNC Asheville|UNCA|Bulldogs|003DA5|FFFFFF|36
            USC Upstate|UPST|Spartans|00703C|000000|24
            Winthrop|WIN|Eagles|872434|FFC72C|38
        """),
        Conf("Coastal Athletic", "CAA", 2, """
            Campbell|CAM|Fighting Camels|FF6600|000000|28
            Charleston|COFC|Cougars|800000|C6A866|52
            Drexel|DREX|Dragons|07294D|FFC600|38
            Elon|ELON|Phoenix|73000A|B59A57|32
            Hampton|HAMP|Pirates|0067AC|FFFFFF|26
            Hofstra|HOF|Pride|00338D|FFC72C|44
            Monmouth|MONM|Hawks|041E42|FFFFFF|32
            North Carolina A&T|NCAT|Aggies|004684|FDB927|28
            Northeastern|NE|Huskies|C8102E|000000|38
            Stony Brook|STBK|Seawolves|990000|16243E|32
            Towson|TOW|Tigers|FFBB00|000000|42
            William & Mary|WM|Tribe|115740|B9975B|32
        """),
        Conf("ASUN", "ASUN", 2, """
            Austin Peay|APSU|Governors|C41E3A|FFFFFF|32
            Bellarmine|BELL|Knights|BA0C2F|A2AAAD|28
            Central Arkansas|UCA|Bears|4F2D7F|A7A8AA|26
            Eastern Kentucky|EKU|Colonels|861F41|FFFFFF|32
            Florida Gulf Coast|FGCU|Eagles|00885A|002D72|38
            Jacksonville|JAX|Dolphins|004D44|FFFFFF|28
            Lipscomb|LIP|Bisons|331E54|F4AA00|42
            North Alabama|UNA|Lions|46166B|DB9F11|32
            North Florida|UNF|Ospreys|00246B|A2AAAD|32
            Queens|QUC|Royals|00205B|BF9B30|28
            Stetson|STET|Hatters|006747|FFFFFF|28
            West Georgia|UWG|Wolves|0033A1|E31837|22
        """),
        Conf("Southern", "SOCON", 2, """
            Chattanooga|UTC|Mocs|00386B|E0AA0F|40
            East Tennessee State|ETSU|Buccaneers|041E42|FFC72C|44
            Furman|FUR|Paladins|582C83|FFFFFF|46
            Mercer|MER|Bears|F76800|000000|34
            Samford|SAM|Bulldogs|002D72|C8102E|46
            The Citadel|CIT|Bulldogs|3975B7|002855|22
            UNC Greensboro|UNCG|Spartans|0F2044|FFB71B|40
            VMI|VMI|Keydets|AE122A|FFD619|22
            Western Carolina|WCU|Catamounts|592C88|C1A875|30
            Wofford|WOF|Terriers|886E4C|000000|38
        """),
        Conf("Southland", "SLC", 2, """
            East Texas A&M|ETAM|Lions|002469|FFC72C|24
            Houston Christian|HCU|Huskies|00539B|F37021|24
            Incarnate Word|UIW|Cardinals|CB2C30|000000|24
            Lamar|LAM|Cardinals|E41837|000000|32
            McNeese|MCN|Cowboys|00529B|FFD204|50
            New Orleans|UNO|Privateers|003595|A7A9AC|28
            Nicholls|NICH|Colonels|C41230|7D9AAA|32
            Northwestern State|NWST|Demons|492F92|F57F28|26
            Southeastern Louisiana|SELA|Lions|006341|EAAA00|30
            Stephen F. Austin|SFA|Lumberjacks|3F2A56|FFFFFF|40
            Texas A&M-Corpus Christi|AMCC|Islanders|0067C5|007F3E|36
            UT Rio Grande Valley|RGV|Vaqueros|F05023|646469|28
        """),
        Conf("Summit League", "SUM", 2, """
            Denver|DEN|Pioneers|8B2332|8B6F4B|26
            Kansas City|KC|Roos|004B87|FFC72C|28
            North Dakota|UND|Fighting Hawks|009A44|000000|30
            North Dakota State|NDSU|Bison|0A5640|FFC82E|38
            Omaha|OMA|Mavericks|D71920|000000|36
            Oral Roberts|ORU|Golden Eagles|002855|C5B783|38
            South Dakota|SDAK|Coyotes|AD0000|FFFFFF|30
            South Dakota State|SDST|Jackrabbits|0033A0|FFD100|46
            St. Thomas|STMN|Tommies|500878|97999B|38
        """),
        Conf("Ohio Valley", "OVC", 2, """
            Eastern Illinois|EIU|Panthers|004B83|A2AAAD|24
            Lindenwood|LIN|Lions|000000|B5A36A|22
            Little Rock|UALR|Trojans|6F263D|A2AAAD|34
            Morehead State|MORE|Eagles|005EB8|FFC72C|38
            SIU Edwardsville|SIUE|Cougars|CC0000|000000|36
            Southeast Missouri|SEMO|Redhawks|C8102E|000000|32
            Southern Indiana|USI|Screaming Eagles|002D72|C8102E|26
            Tennessee State|TNST|Tigers|00539F|FFFFFF|28
            Tennessee Tech|TNTC|Golden Eagles|4F2984|FFDD00|28
            UT Martin|UTM|Skyhawks|0E2240|F79728|30
            Western Illinois|WIU|Leathernecks|663399|FFCC00|22
        """),
        Conf("Patriot League", "PAT", 2, """
            American|AMER|Eagles|C4122F|00205B|34
            Army|ARMY|Black Knights|000000|D3BC8D|28
            Boston University|BU|Terriers|CC0000|FFFFFF|36
            Bucknell|BUCK|Bison|E87722|003865|36
            Colgate|COLG|Raiders|821019|FFFFFF|44
            Holy Cross|HC|Crusaders|602D89|FFFFFF|26
            Lafayette|LAF|Leopards|98002E|FFFFFF|28
            Lehigh|LEH|Mountain Hawks|653600|FFFFFF|34
            Loyola Maryland|LMD|Greyhounds|004236|A2AAAD|28
            Navy|NAVY|Midshipmen|00205B|C5B783|28
        """),
        Conf("Ivy League", "IVY", 2, """
            Brown|BRWN|Bears|4E3629|C00404|32
            Columbia|CLMB|Lions|B9D9EB|1D4F91|28
            Cornell|COR|Big Red|B31B1B|FFFFFF|38
            Dartmouth|DART|Big Green|00693E|FFFFFF|26
            Harvard|HARV|Crimson|A51C30|000000|40
            Penn|PENN|Quakers|011F5B|990000|34
            Princeton|PRIN|Tigers|E77500|000000|46
            Yale|YALE|Bulldogs|00356B|FFFFFF|50
        """),
        Conf("Metro Atlantic", "MAAC", 2, """
            Canisius|CAN|Golden Griffins|0E2B58|FFBA00|24
            Fairfield|FAIR|Stags|C8102E|000000|30
            Iona|IONA|Gaels|6F2C91|FFB81C|42
            Manhattan|MANH|Jaspers|00704A|FFFFFF|28
            Marist|MRST|Red Foxes|C8102E|000000|32
            Merrimack|MRMK|Warriors|003768|F1C400|34
            Mount St. Mary's|MSM|Mountaineers|003591|B9975B|30
            Niagara|NIAG|Purple Eagles|582C83|FFFFFF|26
            Quinnipiac|QU|Bobcats|00235D|FFB300|34
            Rider|RID|Broncs|981E32|FFFFFF|28
            Sacred Heart|SHU|Pioneers|CE1141|808080|28
            Saint Peter's|SPU|Peacocks|004B87|FFFFFF|34
            Siena|SIEN|Saints|006B54|FFC72C|32
        """),
        Conf("America East", "AE", 2, """
            Albany|ALB|Great Danes|46166B|EEB211|30
            Binghamton|BING|Bearcats|005A43|FFFFFF|24
            Bryant|BRY|Bulldogs|000000|C3A15C|38
            Maine|ME|Black Bears|003263|B0D7FF|26
            New Hampshire|UNH|Wildcats|041E42|BBBCBC|22
            NJIT|NJIT|Highlanders|D22630|071D49|20
            UMass Lowell|UML|River Hawks|0067B1|C8102E|32
            UMBC|UMBC|Retrievers|000000|FDB515|32
            Vermont|UVM|Catamounts|154734|FFD416|46
        """),
        Conf("Northeast", "NEC", 2, """
            Central Connecticut|CCSU|Blue Devils|1B49A2|FFFFFF|30
            Chicago State|CHST|Cougars|00653A|FFFFFF|14
            Fairleigh Dickinson|FDU|Knights|002855|B1B3B3|28
            Le Moyne|LEM|Dolphins|00543C|FFC72C|22
            LIU|LIU|Sharks|0B4BA3|FFC72C|26
            Mercyhurst|MERC|Lakers|004B35|002F65|20
            New Haven|UNHV|Chargers|002F65|FFC72C|20
            Saint Francis|SFPA|Red Flash|A6192E|FFFFFF|22
            Stonehill|STON|Skyhawks|2F2A60|FFFFFF|20
            Wagner|WAG|Seahawks|00483A|FFFFFF|26
        """),
        Conf("Mid-Eastern", "MEAC", 2, """
            Coppin State|COPP|Eagles|2E3192|FFD700|18
            Delaware State|DSU|Hornets|C8102E|002855|18
            Howard|HOW|Bison|003A63|E51937|32
            Maryland Eastern Shore|UMES|Hawks|802A2D|B1B3B3|14
            Morgan State|MORG|Bears|0033A0|F47920|24
            Norfolk State|NORF|Spartans|007A53|F3D03E|32
            North Carolina Central|NCCU|Eagles|880029|A2AAAD|28
            South Carolina State|SCST|Bulldogs|841D33|00205B|22
        """),
        Conf("Southwestern", "SWAC", 2, """
            Alabama A&M|AAMU|Bulldogs|660000|FFFFFF|20
            Alabama State|ALST|Hornets|C99700|000000|24
            Alcorn State|ALCN|Braves|4B306A|E9A900|20
            Arkansas-Pine Bluff|UAPB|Golden Lions|000000|E3A400|14
            Bethune-Cookman|BCU|Wildcats|6F263D|F2A900|22
            Florida A&M|FAMU|Rattlers|F47920|00704A|20
            Grambling State|GRAM|Tigers|000000|F0AB00|26
            Jackson State|JKST|Tigers|002147|FFFFFF|26
            Mississippi Valley State|MVSU|Delta Devils|006747|C8102E|10
            Prairie View A&M|PVAM|Panthers|4F2D7F|F2A900|20
            Southern|SOU|Jaguars|00539F|F4C400|28
            Texas Southern|TXSO|Tigers|7A0029|A7A8AA|30
        """),
    )

    class School(val name: String, val abbr: String, val nickname: String, val primary: Long, val secondary: Long, val prestige: Int)

    fun parse(c: Conf): List<School> = c.schools.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
        val f = line.split('|')
        School(f[0], f[1], f[2], 0xFF000000 or f[3].toLong(16), 0xFF000000 or f[4].toLong(16), f[5].toInt())
    }
}

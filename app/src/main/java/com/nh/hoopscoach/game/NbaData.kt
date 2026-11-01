package com.nh.hoopscoach.game

/**
 * Pro rosters: 30 teams and their players (team, name, jersey, position, overall, age, photo).
 * Overalls: NBA 2K27 for every player (the user's full ratings file, tools/rate_today_2k.py; attributes and potential in resources/eras/today2k.tsv),
 * on the game's internal pro scale (screens show a 2K-style scale). Photos and logos are the user's own files.
 * Generated from players.csv. Do not edit by hand.
 */
object NbaData {
    class NbaTeam(val csv: String, val city: String, val name: String, val abbr: String, val logo: String, val conf: Int, val primary: Long, val secondary: Long, val market: Int)
    class NbaPlayer(val team: Int, val name: String, val jersey: Int, val pos: Pos, val ovr: Int, val age: Int, val photo: String)

    val TEAMS = listOf(
        NbaTeam("Atlanta Hawks", "Atlanta", "Hawks", "ATL", "atl", 0, 0xFFC8102E, 0xFFFDB927, 74),
        NbaTeam("Boston Celtics", "Boston", "Celtics", "BOS", "bos", 0, 0xFF007A33, 0xFFBA9653, 88),
        NbaTeam("Brooklyn Nets", "Brooklyn", "Nets", "BKN", "bkn", 0, 0xFF111111, 0xFFB0B7BC, 82),
        NbaTeam("Charlotte Hornets", "Charlotte", "Hornets", "CHA", "cha", 0, 0xFF1D1160, 0xFF00788C, 62),
        NbaTeam("Chicago Bulls", "Chicago", "Bulls", "CHI", "chi", 0, 0xFFCE1141, 0xFF111111, 90),
        NbaTeam("Cleveland Cavaliers", "Cleveland", "Cavaliers", "CLE", "cle", 0, 0xFF860038, 0xFFFDBB30, 64),
        NbaTeam("Detroit Pistons", "Detroit", "Pistons", "DET", "det", 0, 0xFFC8102E, 0xFF1D42BA, 68),
        NbaTeam("Indiana Pacers", "Indiana", "Pacers", "IND", "ind", 0, 0xFF002D62, 0xFFFDBB30, 60),
        NbaTeam("Miami Heat", "Miami", "Heat", "MIA", "mia", 0, 0xFF98002E, 0xFFF9A01B, 84),
        NbaTeam("Milwaukee Bucks", "Milwaukee", "Bucks", "MIL", "mil", 0, 0xFF00471B, 0xFFEEE1C6, 62),
        NbaTeam("New York Knicks", "New York", "Knicks", "NYK", "ny", 0, 0xFF006BB6, 0xFFF58426, 98),
        NbaTeam("Orlando Magic", "Orlando", "Magic", "ORL", "orl", 0, 0xFF0077C0, 0xFFC4CED4, 66),
        NbaTeam("Philadelphia 76ers", "Philadelphia", "76ers", "PHI", "phi", 0, 0xFF006BB6, 0xFFED174C, 82),
        NbaTeam("Toronto Raptors", "Toronto", "Raptors", "TOR", "tor", 0, 0xFFCE1141, 0xFF111111, 80),
        NbaTeam("Washington Wizards", "Washington", "Wizards", "WAS", "wsh", 0, 0xFF002B5C, 0xFFE31837, 72),
        NbaTeam("Dallas Mavericks", "Dallas", "Mavericks", "DAL", "dal", 1, 0xFF00538C, 0xFFB8C4CA, 82),
        NbaTeam("Denver Nuggets", "Denver", "Nuggets", "DEN", "den", 1, 0xFF0E2240, 0xFFFEC524, 72),
        NbaTeam("Golden State Warriors", "Golden State", "Warriors", "GSW", "gs", 1, 0xFF1D428A, 0xFFFFC72C, 92),
        NbaTeam("Houston Rockets", "Houston", "Rockets", "HOU", "hou", 1, 0xFFCE1141, 0xFFC4CED4, 80),
        NbaTeam("LA Clippers", "LA", "Clippers", "LAC", "lac", 1, 0xFFC8102E, 0xFF1D428A, 84),
        NbaTeam("Los Angeles Lakers", "Los Angeles", "Lakers", "LAL", "lal", 1, 0xFF552583, 0xFFFDB927, 99),
        NbaTeam("Memphis Grizzlies", "Memphis", "Grizzlies", "MEM", "mem", 1, 0xFF12173F, 0xFF5D76A9, 56),
        NbaTeam("Minnesota Timberwolves", "Minnesota", "Timberwolves", "MIN", "min", 1, 0xFF0C2340, 0xFF78BE20, 62),
        NbaTeam("New Orleans Pelicans", "New Orleans", "Pelicans", "NOP", "no", 1, 0xFF0C2340, 0xFFC8102E, 58),
        NbaTeam("Oklahoma City Thunder", "Oklahoma City", "Thunder", "OKC", "okc", 1, 0xFF007AC1, 0xFFEF3B24, 60),
        NbaTeam("Phoenix Suns", "Phoenix", "Suns", "PHX", "phx", 1, 0xFF1D1160, 0xFFE56020, 76),
        NbaTeam("Portland Trail Blazers", "Portland", "Trail Blazers", "POR", "por", 1, 0xFFE03A3E, 0xFF111111, 62),
        NbaTeam("Sacramento Kings", "Sacramento", "Kings", "SAC", "sac", 1, 0xFF5A2D81, 0xFF63727A, 58),
        NbaTeam("San Antonio Spurs", "San Antonio", "Spurs", "SAS", "sa", 1, 0xFF111111, 0xFFC4CED4, 66),
        NbaTeam("Utah Jazz", "Utah", "Jazz", "UTA", "utah", 1, 0xFF002B5C, 0xFFF9A01B, 56),
    )

    private const val P0 = """
0|Nickeil Alexander-Walker|7|SG|89|28|atlanta-hawks/nickeil-alexander-walker
0|Dyson Daniels|5|PG|88|23|atlanta-hawks/dyson-daniels
0|RayJ Dennis|10|PG|78|25|atlanta-hawks/rayj-dennis
0|Luguentz Dort|0|SG|86|27|atlanta-hawks/luguentz-dort
0|Zuby Ejiofor|20|PF|80|22|atlanta-hawks/zuby-ejiofor
0|Dorian Finney-Smith|8|SF|82|33|atlanta-hawks/dorian-finney-smith
0|Kingston Flemings|4|PG|82|19|atlanta-hawks/kingston-flemings
0|Keshon Gilbert|0|PG|78|23|atlanta-hawks/keshon-gilbert
0|Mouhamed Gueye|18|PF|81|24|atlanta-hawks/mouhamed-gueye
0|Jalen Johnson|1|PF|92|24|atlanta-hawks/jalen-johnson
0|Corey Kispert|24|SF|82|27|atlanta-hawks/corey-kispert
0|Jock Landale|31|C|83|30|atlanta-hawks/jock-landale
0|CJ McCollum|3|SG|89|35|atlanta-hawks/cj-mccollum
0|Asa Newell|14|PF|82|21|atlanta-hawks/asa-newell
0|Onyeka Okongwu|17|SF|88|25|atlanta-hawks/onyeka-okongwu
0|Henri Veesaar|13|C|79|22|atlanta-hawks/henri-veesaar
0|Aaron Wiggins|2|SG|84|27|atlanta-hawks/aaron-wiggins
0|Jalen Wilson|22|SF|80|26|atlanta-hawks/jalen-wilson
1|Devin Carter|99|PG|83|24|boston-celtics/devin-carter
1|Chris Cenac Jr.|12|PF|80|19|boston-celtics/chris-cenac-jr
1|Mike Conley|45|PG|83|39|boston-celtics/mike-conley
1|Luka Garza|52|C|83|27|boston-celtics/luka-garza
1|Paul George|13|SF|88|36|boston-celtics/paul-george
1|Hugo Gonzalez|28|SG|83|20|boston-celtics/hugo-gonzalez
1|Ron Harper Jr.|8|SG|80|26|boston-celtics/ron-harper-jr
1|Sam Hauser|30|SF|84|28|boston-celtics/sam-hauser
1|Dillon Mitchell|20|SF|78|23|boston-celtics/dillon-mitchell
1|Payton Pritchard|11|PG|88|28|boston-celtics/payton-pritchard
1|Neemias Queta|88|C|87|27|boston-celtics/neemias-queta
1|Mitchell Robinson|4|C|87|28|boston-celtics/mitchell-robinson
1|Baylor Scheierman|55|SG|83|26|boston-celtics/baylor-scheierman
1|Jayson Tatum|0|SF|96|28|boston-celtics/jayson-tatum
1|Jordan Walsh|27|SG|82|22|boston-celtics/jordan-walsh
1|Derrick White|9|SG|89|32|boston-celtics/derrick-white
1|Amari Williams|77|SF|78|23|boston-celtics/amari-williams
2|Tyler Bilodeau|34|SF|78|22|brooklyn-nets/tyler-bilodeau
2|Mikel Brown Jr.|0|PG|82|19|brooklyn-nets/mikel-brown-jr
2|Noah Clowney|21|PF|84|22|brooklyn-nets/noah-clowney
2|Egor Demin|8|PG|84|20|brooklyn-nets/egor-demin
2|Keon Ellis|1|SG|84|26|brooklyn-nets/keon-ellis
2|Joshua Jefferson|9|SF|79|22|brooklyn-nets/joshua-jefferson
2|Chaney Johnson|31|SF|80|23|brooklyn-nets/chaney-johnson
2|Terance Mann|14|SG|82|30|brooklyn-nets/terance-mann
2|Josh Minott|0|PF|82|24|brooklyn-nets/josh-minott
2|Grant Nelson|16|PF|79|24|brooklyn-nets/grant-nelson
2|Michael Porter Jr.|17|SF|90|28|brooklyn-nets/michael-porter-jr
2|Drake Powell|4|SG|81|21|brooklyn-nets/drake-powell
2|Julius Randle|30|PF|89|31|brooklyn-nets/julius-randle
2|Ben Saraf|77|SG|82|20|brooklyn-nets/ben-saraf
2|Day'Ron Sharpe|20|C|83|25|brooklyn-nets/day-ron-sharpe
2|Nolan Traore|10|PG|84|20|brooklyn-nets/nolan-traore
2|Moritz Wagner|13|C|82|29|brooklyn-nets/moritz-wagner
2|Danny Wolf|2|PF|83|22|brooklyn-nets/danny-wolf
3|Grayson Allen|8|SG|87|31|charlotte-hornets/grayson-allen
3|Christian Anderson|5|PG|79|20|charlotte-hornets/christian-anderson
3|Kylan Boswell|18|SG|78|22|charlotte-hornets/kylan-boswell
3|Moussa Diabate|14|PF|86|25|charlotte-hornets/moussa-diabate
3|PJ Hall|16|C|79|24|charlotte-hornets/pj-hall
3|Sion James|4|SG|82|24|charlotte-hornets/sion-james
3|Ryan Kalkbrenner|11|C|86|24|charlotte-hornets/ryan-kalkbrenner
3|Kon Knueppel|7|SG|90|21|charlotte-hornets/kon-knueppel
3|Liam McNeeley|33|SG|80|21|charlotte-hornets/liam-mcneeley
3|Brandon Miller|24|SF|90|24|charlotte-hornets/brandon-miller
3|Royce O'Neale|0|SF|84|33|charlotte-hornets/royce-o-neale
3|Naz Reid|0|C|88|27|charlotte-hornets/naz-reid
3|Tidjane Salaun|31|SF|82|21|charlotte-hornets/tidjane-salaun
3|Dennis Schroder|17|PG|84|33|charlotte-hornets/dennis-schroder
3|Hannes Steinbach|22|PF|80|20|charlotte-hornets/hannes-steinbach
3|Coby White|3|PG|87|26|charlotte-hornets/coby-white
3|Grant Williams|2|PF|82|28|charlotte-hornets/grant-williams
4|Tobe Awaka|19|PF|78|23|chicago-bulls/tobe-awaka
4|Matas Buzelis|14|SF|88|22|chicago-bulls/matas-buzelis
4|Nic Claxton|9|C|87|27|chicago-bulls/nic-claxton
4|Zach Collins|12|PF|83|28|chicago-bulls/zach-collins
4|Noa Essengue|2|PF|81|20|chicago-bulls/noa-essengue
4|Josh Giddey|3|PG|90|24|chicago-bulls/josh-giddey
4|Jordan Hawkins|15|SG|80|24|chicago-bulls/jordan-hawkins
4|Buddy Hield|7|SG|82|34|chicago-bulls/buddy-hield
4|Tre Jones|30|PG|86|26|chicago-bulls/tre-jones
4|Leonard Miller|11|PF|82|22|chicago-bulls/leonard-miller
4|Isaac Okoro|35|SF|83|25|chicago-bulls/isaac-okoro
4|Norman Powell|24|SG|90|33|chicago-bulls/norman-powell
4|Jaylin Sellers|22|SG|78|23|chicago-bulls/jaylin-sellers
4|Jalen Smith|25|PF|84|26|chicago-bulls/jalen-smith
4|Dailyn Swain|5|SG|80|21|chicago-bulls/dailyn-swain
4|Patrick Williams|44|PF|82|25|chicago-bulls/patrick-williams
4|Caleb Wilson|8|PF|85|20|chicago-bulls/caleb-wilson
5|Jarrett Allen|31|C|90|28|cleveland-cavaliers/jarrett-allen
5|Thomas Bryant|3|C|82|29|cleveland-cavaliers/thomas-bryant
5|Tristan Enaruna|21|SF|78|27|cleveland-cavaliers/tristan-enaruna
5|James Harden|1|PG|92|37|cleveland-cavaliers/james-harden
5|Mario Hezonja|33|SF|82|31|cleveland-cavaliers/mario-hezonja
5|Sam Merrill|5|SG|85|30|cleveland-cavaliers/sam-merrill
5|Riley Minix|12|SF|78|25|cleveland-cavaliers/riley-minix
5|Donovan Mitchell|45|SG|96|30|cleveland-cavaliers/donovan-mitchell
5|Evan Mobley|4|C|92|25|cleveland-cavaliers/evan-mobley
5|Craig Porter Jr.|9|PG|81|26|cleveland-cavaliers/craig-porter-jr
5|Tyrese Proctor|24|PG|80|22|cleveland-cavaliers/tyrese-proctor
5|Meleek Thomas|15|SG|79|20|cleveland-cavaliers/meleek-thomas
5|Jaylon Tyson|20|SG|84|24|cleveland-cavaliers/jaylon-tyson
5|Ernest Udeh Jr.|88|C|78|23|cleveland-cavaliers/ernest-udeh-jr
5|Peyton Watson|8|SG|86|24|cleveland-cavaliers/peyton-watson
15|Santi Aldama|34|PF|86|25|dallas-mavericks/santi-aldama
15|Tarik Biberovic|13|SF|80|25|dallas-mavericks/tarik-biberovic
15|Max Christie|0|SG|83|23|dallas-mavericks/max-christie
15|Moussa Cisse|30|C|80|23|dallas-mavericks/moussa-cisse
15|Walter Clayton Jr.|1|PG|82|23|dallas-mavericks/walter-clayton-jr
15|Sergio de Larrea|55|SF|78|20|dallas-mavericks/sergio-de-larrea
15|Cooper Flagg|32|SF|92|19|dallas-mavericks/cooper-flagg
15|Daniel Gafford|21|SF|86|28|dallas-mavericks/daniel-gafford
15|Jett Howard|9|SG|80|23|dallas-mavericks/jett-howard
15|Kyrie Irving|11|PG|92|34|dallas-mavericks/kyrie-irving
15|Morez Johnson Jr.|14|PF|82|20|dallas-mavericks/morez-johnson-jr
15|Tobi Lawal|33|SF|78|23|dallas-mavericks/tobi-lawal
15|Dereck Lively II|2|C|86|22|dallas-mavericks/dereck-lively-ii
15|Naji Marshall|3|SF|86|28|dallas-mavericks/naji-marshall
15|Caleb Martin|16|SF|81|30|dallas-mavericks/caleb-martin
15|Zaccharie Risacher|10|SF|84|21|dallas-mavericks/zaccharie-risacher
15|Marcus Sasser|8|PG|82|26|dallas-mavericks/marcus-sasser
15|P.J. Washington|25|PF|86|28|dallas-mavericks/p-j-washington
16|Marvin Bagley III|3|PF|84|27|denver-nuggets/marvin-bagley-iii
16|Christian Braun|0|SG|86|25|denver-nuggets/christian-braun
16|Trevon Brazile|7|SF|78|24|denver-nuggets/trevon-brazile
16|DeMar DeRozan|10|SG|89|37|denver-nuggets/demar-derozan
16|Alpha Diallo|11|SF|80|29|denver-nuggets/alpha-diallo
16|Aaron Gordon|32|PF|89|31|denver-nuggets/aaron-gordon
16|DaRon Holmes II|14|PF|80|23|denver-nuggets/daron-holmes-ii
16|Bryce Hopkins|13|SF|78|24|denver-nuggets/bryce-hopkins
16|Cameron Johnson|23|SF|87|30|denver-nuggets/cameron-johnson
16|Nikola Jokic|15|C|98|31|denver-nuggets/nikola-jokic
16|Spencer Jones|21|SF|81|25|denver-nuggets/spencer-jones
16|Tyus Jones|5|PG|82|30|denver-nuggets/tyus-jones
16|Jamal Murray|27|PG|93|29|denver-nuggets/jamal-murray
16|Ryan Nembhard|9|PG|81|23|denver-nuggets/ryan-nembhard
16|Zeke Nnaji|22|PF|80|25|denver-nuggets/zeke-nnaji
16|Julian Strawther|1|SG|83|24|denver-nuggets/julian-strawther
16|Cam Whitmore|0|SF|82|22|denver-nuggets/cam-whitmore
6|John Collins|20|PF|86|29|detroit-pistons/john-collins
6|Cade Cunningham|2|PG|96|25|detroit-pistons/cade-cunningham
6|Jalen Duren|0|C|90|22|detroit-pistons/jalen-duren
6|Javonte Green|31|SG|83|33|detroit-pistons/javonte-green
6|Elijah Harkless|94|SG|79|26|detroit-pistons/elijah-harkless
6|Ronald Holland II|5|SF|83|21|detroit-pistons/ronald-holland-ii
6|Kevin Huerter|27|SG|83|28|detroit-pistons/kevin-huerter
6|Daniss Jenkins|24|PG|86|25|detroit-pistons/daniss-jenkins
6|Isaiah Joe|17|SG|86|27|detroit-pistons/isaiah-joe
6|Isaac Jones|13|C|79|26|detroit-pistons/isaac-jones
6|Chaz Lanier|8|SG|78|24|detroit-pistons/chaz-lanier
6|Ebuka Okorie|23|SG|80|20|detroit-pistons/ebuka-okorie
6|Ugonna Onyenso|34|C|78|22|detroit-pistons/ugonna-onyenso
6|Taurean Prince|12|SF|82|32|detroit-pistons/taurean-prince
6|Paul Reed|7|PF|85|27|detroit-pistons/paul-reed
6|Duncan Robinson|55|SF|86|32|detroit-pistons/duncan-robinson
6|Tolu Smith|35|SF|78|26|detroit-pistons/tolu-smith
6|Ausar Thompson|9|SG|90|23|detroit-pistons/ausar-thompson
17|Charles Bassey|28|C|80|27|golden-state-warriors/charles-bassey
17|Jimmy Butler III|10|SF|90|37|golden-state-warriors/jimmy-butler-iii
17|LJ Cryer|18|SG|80|24|golden-state-warriors/lj-cryer
17|Stephen Curry|30|PG|96|38|golden-state-warriors/stephen-curry
17|Draymond Green|23|PF|86|36|golden-state-warriors/draymond-green
17|Al Horford|20|C|85|40|golden-state-warriors/al-horford
17|Graham Ike|0|SF|78|24|golden-state-warriors/graham-ike
17|Yaxel Lendeborg|1|SF|82|23|golden-state-warriors/yaxel-lendeborg
17|Malevy Leons|33|SF|78|26|golden-state-warriors/malevy-leons
17|De'Anthony Melton|8|SG|84|28|golden-state-warriors/de-anthony-melton
17|Moses Moody|4|SG|84|24|golden-state-warriors/moses-moody
17|Georges Niang|32|SF|80|33|golden-state-warriors/georges-niang
17|Gary Payton II|0|SG|83|33|golden-state-warriors/gary-payton-ii
17|Brandin Podziemski|2|SG|86|23|golden-state-warriors/brandin-podziemski
17|Kristaps Porzingis|7|C|88|31|golden-state-warriors/kristaps-porzingis
17|Will Richard|3|SG|82|23|golden-state-warriors/will-richard
17|Gui Santos|15|SF|86|24|golden-state-warriors/gui-santos
17|Brandon Williams|12|PG|84|26|golden-state-warriors/brandon-williams
18|Steven Adams|12|C|82|33|houston-rockets/steven-adams
18|Bogdan Bogdanovic|31|SG|82|34|houston-rockets/bogdan-bogdanovic
18|Clint Capela|30|C|82|32|houston-rockets/clint-capela
18|Quadir Copeland|25|SG|78|23|houston-rockets/quadir-copeland
18|Isaiah Crawford|27|SF|78|24|houston-rockets/isaiah-crawford
18|Kevin Durant|7|SF|96|38|houston-rockets/kevin-durant
18|Tari Eason|17|PF|86|25|houston-rockets/tari-eason
18|Julian Phillips|3|SF|80|23|houston-rockets/julian-phillips
18|Alperen Sengun|28|C|92|24|houston-rockets/alperen-sengun
18|Reed Sheppard|15|PG|86|22|houston-rockets/reed-sheppard
18|Marcus Smart|36|PG|86|32|houston-rockets/marcus-smart
18|Jabari Smith Jr.|10|PF|87|23|houston-rockets/jabari-smith-jr
18|Jae'Sean Tate|8|SF|81|31|houston-rockets/jae-sean-tate
18|Amen Thompson|1|SG|91|23|houston-rockets/amen-thompson
18|Bruce Thornton|2|PG|79|22|houston-rockets/bruce-thornton
18|Oscar Tshiebwe|9|C|80|26|houston-rockets/oscar-tshiebwe
18|Fred VanVleet|5|PG|86|32|houston-rockets/fred-vanvleet
7|Kobe Brown|24|SG|82|26|indiana-pacers/kobe-brown
7|Johnny Furphy|12|SG|81|22|indiana-pacers/johnny-furphy
7|Tyrese Haliburton|0|PG|94|26|indiana-pacers/tyrese-haliburton
7|Jay Huff|32|C|83|28|indiana-pacers/jay-huff
7|Quenton Jackson|29|SG|82|28|indiana-pacers/quenton-jackson
7|T.J. McConnell|9|PG|86|34|indiana-pacers/t-j-mcconnell
7|Larry Nance Jr.|22|PF|80|33|indiana-pacers/larry-nance-jr
7|Andrew Nembhard|2|PG|88|26|indiana-pacers/andrew-nembhard
7|Aaron Nesmith|23|SG|86|27|indiana-pacers/aaron-nesmith
7|Kelly Oubre Jr.|10|SG|86|30|indiana-pacers/kelly-oubre-jr
7|Ben Sheppard|26|SG|82|25|indiana-pacers/ben-sheppard
7|Pascal Siakam|43|PF|92|32|indiana-pacers/pascal-siakam
7|Jalen Slawson|18|SF|80|27|indiana-pacers/jalen-slawson
7|Braden Smith|3|PG|79|22|indiana-pacers/braden-smith
7|Obi Toppin|1|PF|86|28|indiana-pacers/obi-toppin
7|Jarace Walker|5|PF|86|23|indiana-pacers/jarace-walker
7|Ivica Zubac|40|C|90|29|indiana-pacers/ivica-zubac
19|Bradley Beal|3|SG|84|33|la-clippers/bradley-beal
19|Cam Christie|24|SG|79|21|la-clippers/cam-christie
19|Gradey Dick|9|SG|82|22|la-clippers/gradey-dick
19|Kris Dunn|8|PG|83|32|la-clippers/kris-dunn
19|Darius Garland|10|PG|90|26|la-clippers/darius-garland
19|Rui Hachimura|28|SF|86|28|la-clippers/rui-hachimura
19|Brandon Ingram|7|SF|90|29|la-clippers/brandon-ingram
19|Isaiah Jackson|23|SF|82|24|la-clippers/isaiah-jackson
19|Derrick Jones Jr.|5|SF|85|29|la-clippers/derrick-jones-jr
19|Brook Lopez|11|C|85|38|la-clippers/brook-lopez
19|Nick Martinelli|12|SF|78|22|la-clippers/nick-martinelli
19|Baba Miller|18|SF|78|22|la-clippers/baba-miller
19|Jordan Miller|22|SG|82|27|la-clippers/jordan-miller
19|Yanic Konan Niederhauser|14|C|82|23|la-clippers/yanic-konan-niederhauser
19|Jalen Pickett|21|PG|82|26|la-clippers/jalen-pickett
19|Kobe Sanders|4|SG|82|24|la-clippers/kobe-sanders
19|Max Strus|31|SG|86|30|la-clippers/max-strus
19|Keaton Wagler|1|SG|83|19|la-clippers/keaton-wagler
19|Blake Wesley|20|SG|80|23|la-clippers/blake-wesley
20|Cameron Carr|43|SG|81|22|los-angeles-lakers/cameron-carr
20|Luka Doncic|77|PG|98|27|los-angeles-lakers/luka-doncic
20|Quentin Grimes|5|SG|86|26|los-angeles-lakers/quentin-grimes
20|Jaden Hardy|7|SG|82|24|los-angeles-lakers/jaden-hardy
20|Bronny James|9|SG|79|22|los-angeles-lakers/bronny-james
20|Arthur Kaluma|47|SF|78|24|los-angeles-lakers/arthur-kaluma
20|Walker Kessler|14|C|88|25|los-angeles-lakers/walker-kessler
20|Jake LaRavia|12|SF|83|24|los-angeles-lakers/jake-laravia
20|Kevon Looney|55|PF|80|30|los-angeles-lakers/kevon-looney
20|Lawson Lovering|0|C|78|24|los-angeles-lakers/lawson-lovering
20|Sandro Mamukelashvili|54|PF|84|27|los-angeles-lakers/sandro-mamukelashvili
20|Chris Manon|30|SG|78|24|los-angeles-lakers/chris-manon
20|AK Okereke|31|SF|80|23|los-angeles-lakers/ak-okereke
20|Austin Reaves|15|SG|92|28|los-angeles-lakers/austin-reaves
20|Collin Sexton|10|PG|86|27|los-angeles-lakers/collin-sexton
20|Adou Thiero|1|SF|79|22|los-angeles-lakers/adou-thiero
20|Matisse Thybulle|4|SG|82|29|los-angeles-lakers/matisse-thybulle
20|Jarred Vanderbilt|2|PF|84|27|los-angeles-lakers/jarred-vanderbilt
20|Ziaire Williams|11|SF|83|25|los-angeles-lakers/ziaire-williams
21|Cameron Boozer|27|PF|86|19|memphis-grizzlies/cameron-boozer
21|Carson Cooper|20|C|78|22|memphis-grizzlies/carson-cooper
21|Cedric Coward|23|SF|87|23|memphis-grizzlies/cedric-coward
21|Zach Edey|14|C|87|24|memphis-grizzlies/zach-edey
21|Jerami Grant|3|PF|86|32|memphis-grizzlies/jerami-grant
21|Taylor Hendricks|22|PF|82|23|memphis-grizzlies/taylor-hendricks
21|GG Jackson|45|SF|85|22|memphis-grizzlies/gg-jackson
21|Ty Jerome|2|PG|87|29|memphis-grizzlies/ty-jerome
21|Karim Lopez|35|PF|81|19|memphis-grizzlies/karim-lopez
21|Jahmai Mashack|21|SG|81|23|memphis-grizzlies/jahmai-mashack
21|Kris Murray|17|SF|82|26|memphis-grizzlies/kris-murray
21|Scotty Pippen Jr.|1|PG|83|26|memphis-grizzlies/scotty-pippen-jr
21|Quinten Post|41|C|82|26|memphis-grizzlies/quinten-post
21|Olivier-Maxence Prosper|18|SF|83|24|memphis-grizzlies/olivier-maxence-prosper
21|Javon Small|10|PG|82|22|memphis-grizzlies/javon-small
21|Cam Spencer|24|PG|84|26|memphis-grizzlies/cam-spencer
21|Isaiah Stewart|28|C|85|25|memphis-grizzlies/isaiah-stewart
21|Jaylen Wells|0|SF|86|23|memphis-grizzlies/jaylen-wells
8|Bam Adebayo|13|C|92|29|miami-heat/bam-adebayo
8|Giannis Antetokounmpo|7|PF|98|31|miami-heat/giannis-antetokounmpo
8|Ryan Conwell|4|SG|78|22|miami-heat/ryan-conwell
8|Tre Donaldson|24|PG|78|23|miami-heat/tre-donaldson
8|Simone Fontecchio|0|SF|82|30|miami-heat/simone-fontecchio
8|Myron Gardner|15|SF|81|23|miami-heat/myron-gardner
8|Vladislav Goldin|50|C|78|25|miami-heat/vladislav-goldin
8|Tim Hardaway Jr.|10|SG|86|34|miami-heat/tim-hardaway-jr
8|Keshad Johnson|16|SF|79|25|miami-heat/keshad-johnson
8|Nikola Jovic|5|SF|82|23|miami-heat/nikola-jovic
8|Pelle Larsson|9|SG|86|25|miami-heat/pelle-larsson
8|Davion Mitchell|2|PG|85|28|miami-heat/davion-mitchell
8|Bobby Portis|95|PF|87|31|miami-heat/bobby-portis
8|Nick Richards|8|C|82|29|miami-heat/nick-richards
8|Dru Smith|12|SG|82|28|miami-heat/dru-smith
8|Klay Thompson|11|SG|84|36|miami-heat/klay-thompson
8|Andrew Wiggins|22|SF|86|31|miami-heat/andrew-wiggins
9|Nate Ament|15|SF|81|19|milwaukee-bucks/nate-ament
9|Brayden Burries|0|SG|82|20|milwaukee-bucks/brayden-burries
9|Ousmane Dieng|21|SF|84|23|milwaukee-bucks/ousmane-dieng
9|AJ Green|20|SG|82|27|milwaukee-bucks/aj-green
9|Tyler Herro|11|SG|90|26|milwaukee-bucks/tyler-herro
9|Kasparas Jakucionis|25|PG|82|20|milwaukee-bucks/kasparas-jakucionis
9|Jaime Jaquez Jr.|24|SF|86|25|milwaukee-bucks/jaime-jaquez-jr
9|Kam Jones|19|SG|81|24|milwaukee-bucks/kam-jones
9|Kyle Kuzma|18|PF|85|31|milwaukee-bucks/kyle-kuzma
9|Caris LeVert|12|SG|86|32|milwaukee-bucks/caris-levert
9|Bogoljub Markovic|23|SF|78|21|milwaukee-bucks/bogoljub-markovic
9|Pete Nance|35|SF|80|26|milwaukee-bucks/pete-nance
9|Ryan Rollins|13|PG|87|24|milwaukee-bucks/ryan-rollins
9|Cormac Ryan|30|SG|79|26|milwaukee-bucks/cormac-ryan
9|Jericho Sims|0|C|81|27|milwaukee-bucks/jericho-sims
9|Gary Trent Jr.|5|SG|82|27|milwaukee-bucks/gary-trent-jr
9|Myles Turner|3|C|86|30|milwaukee-bucks/myles-turner
9|Kel'el Ware|9|C|86|22|milwaukee-bucks/kel-el-ware
22|LaMelo Ball|1|PG|91|25|minnesota-timberwolves/lamelo-ball
22|Joan Beringer|19|PF|81|20|minnesota-timberwolves/joan-beringer
22|Jaylen Clark|7|SG|81|25|minnesota-timberwolves/jaylen-clark
22|Donte DiVincenzo|0|SG|86|29|minnesota-timberwolves/donte-divincenzo
22|Ayo Dosunmu|13|SG|88|26|minnesota-timberwolves/ayo-dosunmu
22|Anthony Edwards|5|SG|97|25|minnesota-timberwolves/anthony-edwards
22|Isaiah Evans|33|SF|79|20|minnesota-timberwolves/isaiah-evans
22|Enrique Freeman|25|SF|78|26|minnesota-timberwolves/enrique-freeman
22|Rudy Gobert|27|C|90|34|minnesota-timberwolves/rudy-gobert
22|Bones Hyland|8|PG|82|26|minnesota-timberwolves/bones-hyland
22|Jonathan Kuminga|24|PF|82|24|minnesota-timberwolves/jonathan-kuminga
22|Trey Lyles|41|PF|82|30|minnesota-timberwolves/trey-lyles
22|Jaden McDaniels|3|SF|90|25|minnesota-timberwolves/jaden-mcdaniels
22|Zyon Pullin|15|PG|78|25|minnesota-timberwolves/zyon-pullin
22|Terrence Shannon Jr.|4|SG|83|26|minnesota-timberwolves/terrence-shannon-jr
22|Cody Williams|9|SF|82|22|minnesota-timberwolves/cody-williams
22|Rocco Zikarsky|44|C|78|20|minnesota-timberwolves/rocco-zikarsky
23|Saddiq Bey|41|SG|87|27|new-orleans-pelicans/saddiq-bey
23|Malik Dia|8|SF|78|23|new-orleans-pelicans/malik-dia
23|Jeremiah Fears|0|PG|86|20|new-orleans-pelicans/jeremiah-fears
23|AJ Johnson|14|SG|79|22|new-orleans-pelicans/aj-johnson
23|Herbert Jones|2|SF|86|28|new-orleans-pelicans/herbert-jones
23|DeAndre Jordan|6|C|82|38|new-orleans-pelicans/deandre-jordan
23|Bennedict Mathurin|24|SG|86|24|new-orleans-pelicans/bennedict-mathurin
23|Karlo Matkovic|17|PF|82|25|new-orleans-pelicans/karlo-matkovic
23|Bryce McGowens|11|SG|82|24|new-orleans-pelicans/bryce-mcgowens
23|Yves Missi|21|C|83|22|new-orleans-pelicans/yves-missi
23|Trey Murphy III|25|SF|90|26|new-orleans-pelicans/trey-murphy-iii
23|Dejounte Murray|5|PG|88|30|new-orleans-pelicans/dejounte-murray
23|Jaron Pierre Jr.|10|SG|78|23|new-orleans-pelicans/jaron-pierre-jr
23|Jordan Poole|3|PG|82|27|new-orleans-pelicans/jordan-poole
23|Derik Queen|22|C|86|21|new-orleans-pelicans/derik-queen
23|Julian Reese|13|PF|82|23|new-orleans-pelicans/julian-reese
23|Trendon Watford|9|PF|82|26|new-orleans-pelicans/trendon-watford
23|Zion Williamson|1|PF|90|26|new-orleans-pelicans/zion-williamson
10|Jaden Akins|14|SG|78|23|new-york-knicks/jaden-akins
10|Jose Alvarado|5|PG|85|28|new-york-knicks/jose-alvarado
10|OG Anunoby|8|SF|92|29|new-york-knicks/og-anunoby
10|Mikal Bridges|25|SG|90|30|new-york-knicks/mikal-bridges
10|Jalen Brunson|11|PG|98|30|new-york-knicks/jalen-brunson
10|Jordan Clarkson|0|SG|84|34|new-york-knicks/jordan-clarkson
10|Pacome Dadiet|4|SF|78|21|new-york-knicks/pacome-dadiet
10|Mohamed Diawara|51|SF|81|21|new-york-knicks/mohamed-diawara
10|Andre Drummond|0|C|84|33|new-york-knicks/andre-drummond
10|Josh Hart|3|SG|88|31|new-york-knicks/josh-hart
10|Dalton Knecht|99|SF|82|25|new-york-knicks/dalton-knecht
10|Tyler Kolek|13|PG|82|25|new-york-knicks/tyler-kolek
10|Miles McBride|2|SG|86|26|new-york-knicks/miles-mcbride
10|Tyler Nickel|55|SF|78|23|new-york-knicks/tyler-nickel
10|Landry Shamet|44|SG|85|29|new-york-knicks/landry-shamet
10|Karl-Anthony Towns|32|C|96|30|new-york-knicks/karl-anthony-towns
10|James Wiseman|35|C|80|25|new-york-knicks/james-wiseman
24|Ochai Agbaji|30|SG|83|26|oklahoma-city-thunder/ochai-agbaji
24|Brooks Barnhizer|23|SF|78|24|oklahoma-city-thunder/brooks-barnhizer
24|Alex Caruso|9|SG|88|32|oklahoma-city-thunder/alex-caruso
24|Josh Dix|21|SG|78|24|oklahoma-city-thunder/josh-dix
24|Shai Gilgeous-Alexander|2|PG|98|28|oklahoma-city-thunder/shai-gilgeous-alexander
24|Isaiah Hartenstein|55|C|88|28|oklahoma-city-thunder/isaiah-hartenstein
24|Chet Holmgren|7|C|91|24|oklahoma-city-thunder/chet-holmgren
24|Aday Mara|15|C|82|21|oklahoma-city-thunder/aday-mara
24|Jared McCain|3|SG|86|22|oklahoma-city-thunder/jared-mccain
24|Ajay Mitchell|25|PG|88|24|oklahoma-city-thunder/ajay-mitchell
24|Micah Peavy|0|SG|80|25|oklahoma-city-thunder/micah-peavy
24|Thomas Sorber|12|C|80|20|oklahoma-city-thunder/thomas-sorber
24|Bennett Stirtz|14|PG|79|22|oklahoma-city-thunder/bennett-stirtz
24|Nikola Topic|44|PG|81|21|oklahoma-city-thunder/nikola-topic
24|Cason Wallace|22|SG|88|22|oklahoma-city-thunder/cason-wallace
24|Jalen Williams|8|SG|90|25|oklahoma-city-thunder/jalen-williams
24|Jaylin Williams|6|SF|85|24|oklahoma-city-thunder/jaylin-williams
24|Kenrich Williams|34|SG|84|32|oklahoma-city-thunder/kenrich-williams
11|Paolo Banchero|5|PF|92|23|orlando-magic/paolo-banchero
11|Desmond Bane|3|SG|90|28|orlando-magic/desmond-bane
11|Goga Bitadze|35|C|82|27|orlando-magic/goga-bitadze
11|Anthony Black|0|PG|87|22|orlando-magic/anthony-black
11|Jamal Cain|8|SF|81|27|orlando-magic/jamal-cain
11|Jevon Carter|2|PG|82|31|orlando-magic/jevon-carter
11|Wendell Carter Jr.|34|C|86|27|orlando-magic/wendell-carter-jr
11|Colin Castleton|14|C|79|26|orlando-magic/colin-castleton
11|Tristan da Silva|23|SF|84|25|orlando-magic/tristan-da-silva
11|Jonathan Isaac|1|PF|81|29|orlando-magic/jonathan-isaac
11|Alex Morales|30|SG|78|24|orlando-magic/alex-morales
11|Izaiyah Nelson|25|SF|78|22|orlando-magic/izaiyah-nelson
11|Noah Penda|93|SF|82|21|orlando-magic/noah-penda
11|Jase Richardson|11|SG|81|21|orlando-magic/jase-richardson
11|Jalen Suggs|4|PG|87|25|orlando-magic/jalen-suggs
11|Nikola Vucevic|9|C|86|36|orlando-magic/nikola-vucevic
11|Franz Wagner|22|SF|90|25|orlando-magic/franz-wagner
12|Dominick Barlow|25|PF|82|23|philadelphia-76ers/dominick-barlow
12|Adem Bona|30|C|83|23|philadelphia-76ers/adem-bona
12|Jaylen Brown|7|SG|94|29|philadelphia-76ers/jaylen-brown
12|Kentavious Caldwell-Pope|1|SG|83|33|philadelphia-76ers/kentavious-caldwell-pope
12|VJ Edgecombe|77|SG|89|21|philadelphia-76ers/vj-edgecombe
12|Justin Edwards|11|SF|82|22|philadelphia-76ers/justin-edwards
12|Joel Embiid|21|C|94|32|philadelphia-76ers/joel-embiid
12|Tacko Fall|99|C|80|30|philadelphia-76ers/tacko-fall
12|Ariel Hukporti|55|C|81|24|philadelphia-76ers/ariel-hukporti
12|LeBron James|23|SF|94|41|philadelphia-76ers/lebron-james
12|Caleb Love|8|SG|82|25|philadelphia-76ers/caleb-love
12|Tyrese Maxey|0|PG|95|25|philadelphia-76ers/tyrese-maxey
12|Labaron Philon Jr.|0|PG|80|20|philadelphia-76ers/labaron-philon-jr
12|Rayan Rupert|12|SG|82|22|philadelphia-76ers/rayan-rupert
12|Anfernee Simons|5|SG|86|27|philadelphia-76ers/anfernee-simons
12|Dean Wade|45|PF|84|29|philadelphia-76ers/dean-wade
12|Jabari Walker|33|PF|80|24|philadelphia-76ers/jabari-walker
25|Devin Booker|15|SG|94|29|phoenix-suns/devin-booker
25|Jamaree Bouyea|17|SG|80|27|phoenix-suns/jamaree-bouyea
25|Miles Bridges|22|SF|87|28|phoenix-suns/miles-bridges
25|Dillon Brooks|3|SF|88|30|phoenix-suns/dillon-brooks
25|Ryan Dunn|0|SF|82|23|phoenix-suns/ryan-dunn
25|Rasheer Fleming|20|SF|82|22|phoenix-suns/rasheer-fleming
25|Collin Gillespie|12|PG|86|27|phoenix-suns/collin-gillespie
25|Jordan Goodwin|23|SG|83|28|phoenix-suns/jordan-goodwin
25|Jalen Green|4|SG|88|24|phoenix-suns/jalen-green
25|Oso Ighodaro|11|SF|83|24|phoenix-suns/oso-ighodaro
25|Luke Kennard|8|SG|83|30|phoenix-suns/luke-kennard
25|Khaman Maluach|10|C|82|20|phoenix-suns/khaman-maluach
25|Koa Peat|18|PF|80|19|phoenix-suns/koa-peat
25|Duop Reath|26|C|80|30|phoenix-suns/duop-reath
25|Pat Spencer|61|PG|83|30|phoenix-suns/pat-spencer
25|Drew Timme|0|SF|79|26|phoenix-suns/drew-timme
25|Mark Williams|25|C|86|24|phoenix-suns/mark-williams
26|Deni Avdija|8|SF|92|25|portland-trail-blazers/deni-avdija
26|Toumani Camara|33|SF|87|26|portland-trail-blazers/toumani-camara
26|Branden Carlson|10|C|80|27|portland-trail-blazers/branden-carlson
26|Sidy Cissoko|91|SG|82|22|portland-trail-blazers/sidy-cissoko
26|Donovan Clingan|23|C|88|22|portland-trail-blazers/donovan-clingan
26|Yang Hansen|16|C|79|21|portland-trail-blazers/yang-hansen
26|Scoot Henderson|0|PG|86|22|portland-trail-blazers/scoot-henderson
26|Jrue Holiday|5|PG|88|36|portland-trail-blazers/jrue-holiday
26|Jayson Kent|29|SG|78|23|portland-trail-blazers/jayson-kent
26|Vit Krejci|27|SG|82|26|portland-trail-blazers/vit-krejci
26|Damian Lillard|0|PG|91|36|portland-trail-blazers/damian-lillard
26|Ja Morant|1|PG|90|27|portland-trail-blazers/ja-morant
26|Micah Potter|9|C|81|28|portland-trail-blazers/micah-potter
26|Shaedon Sharpe|17|SG|88|23|portland-trail-blazers/shaedon-sharpe
26|John Tonje|2|SF|78|25|portland-trail-blazers/john-tonje
26|Robert Williams III|35|C|86|29|portland-trail-blazers/robert-williams-iii
26|Chris Youngblood|11|SG|78|24|portland-trail-blazers/chris-youngblood
27|Precious Achiuwa|9|PF|84|27|sacramento-kings/precious-achiuwa
27|Darius Acuff Jr.|5|PG|83|19|sacramento-kings/darius-acuff-jr
27|Leaky Black|19|SF|80|27|sacramento-kings/leaky-black
27|Dylan Cardwell|32|C|82|25|sacramento-kings/dylan-cardwell
27|Nique Clifford|10|SG|83|24|sacramento-kings/nique-clifford
27|De'Andre Hunter|15|SF|85|29|sacramento-kings/de-andre-hunter
27|Alex Karaban|33|PF|80|23|sacramento-kings/alex-karaban
27|Zach LaVine|8|SG|88|31|sacramento-kings/zach-lavine
27|Jonathan Mogbo|22|PF|80|24|sacramento-kings/jonathan-mogbo
27|Malik Monk|0|SG|86|28|sacramento-kings/malik-monk
27|Keegan Murray|13|PF|86|26|sacramento-kings/keegan-murray
27|Sean Pedulla|3|PG|78|23|sacramento-kings/sean-pedulla
27|Daeqwon Plowden|29|SG|80|28|sacramento-kings/daeqwon-plowden
27|Maxime Raynaud|42|C|86|23|sacramento-kings/maxime-raynaud
27|Domantas Sabonis|11|SF|90|30|sacramento-kings/domantas-sabonis
27|Emanuel Sharp|34|SG|78|23|sacramento-kings/emanuel-sharp
27|Ben Simmons|25|PG|82|30|sacramento-kings/ben-simmons
28|Harrison Barnes|40|SF|84|34|san-antonio-spurs/harrison-barnes
28|Maliq Brown|15|SF|78|23|san-antonio-spurs/maliq-brown
28|Carter Bryant|11|SF|84|21|san-antonio-spurs/carter-bryant
28|Stephon Castle|5|SG|92|21|san-antonio-spurs/stephon-castle
28|Julian Champagnie|30|SF|86|25|san-antonio-spurs/julian-champagnie
28|De'Aaron Fox|4|PG|90|28|san-antonio-spurs/de-aaron-fox
28|Ja'Kobi Gillespie|17|SG|78|23|san-antonio-spurs/ja-kobi-gillespie
28|Dylan Harper|2|SG|90|20|san-antonio-spurs/dylan-harper
28|Tobias Harris|23|PF|88|34|san-antonio-spurs/tobias-harris
28|Keldon Johnson|3|SF|86|27|san-antonio-spurs/keldon-johnson
28|David Jones Garcia|25|SF|79|24|san-antonio-spurs/david-jones-garcia
28|Luke Kornet|7|C|84|31|san-antonio-spurs/luke-kornet
28|Jordan McLaughlin|0|PG|80|30|san-antonio-spurs/jordan-mclaughlin
28|Jayden Quaintance|22|PF|79|19|san-antonio-spurs/jayden-quaintance
28|Tarris Reed Jr.|10|C|80|23|san-antonio-spurs/tarris-reed-jr
28|Devin Vassell|24|SG|88|26|san-antonio-spurs/devin-vassell
28|Victor Wembanyama|1|SF|98|22|san-antonio-spurs/victor-wembanyama
13|Kyle Anderson|12|SF|82|33|toronto-raptors/kyle-anderson
13|Scottie Barnes|4|PF|92|25|toronto-raptors/scottie-barnes
13|RJ Barrett|9|SF|88|26|toronto-raptors/rj-barrett
13|Jamison Battle|77|SF|82|25|toronto-raptors/jamison-battle
13|Nate Bittle|0|C|78|23|toronto-raptors/nate-bittle
13|Jaden Bradley|8|PG|78|23|toronto-raptors/jaden-bradley
13|Allen Graves|22|SF|80|20|toronto-raptors/allen-graves
13|Trayce Jackson-Davis|32|PF|80|26|toronto-raptors/trayce-jackson-davis
13|Kawhi Leonard|2|SF|96|35|toronto-raptors/kawhi-leonard
13|Alijah Martin|55|SG|79|25|toronto-raptors/alijah-martin
13|Collin Murray-Boyles|30|PF|87|21|toronto-raptors/collin-murray-boyles
13|Jakob Poeltl|19|C|86|31|toronto-raptors/jakob-poeltl
13|Immanuel Quickley|5|PG|88|27|toronto-raptors/immanuel-quickley
13|Jamal Shead|23|PG|84|24|toronto-raptors/jamal-shead
13|Malachi Smith|18|SG|79|24|toronto-raptors/malachi-smith
13|Ja'Kobe Walter|14|SG|84|22|toronto-raptors/ja-kobe-walter
29|Trey Alexander|15|SG|78|23|utah-jazz/trey-alexander
29|Ace Bailey|19|SG|86|20|utah-jazz/ace-bailey
29|Mo Bamba|44|C|81|28|utah-jazz/mo-bamba
29|Tony Bradley|13|C|80|29|utah-jazz/tony-bradley
29|Isaiah Collier|8|PG|87|22|utah-jazz/isaiah-collier
29|Kyle Filipowski|2|PF|86|22|utah-jazz/kyle-filipowski
29|Keyonte George|3|PG|90|23|utah-jazz/keyonte-george
29|Josh Green|5|SG|82|26|utah-jazz/josh-green
29|Jaxson Hayes|11|C|84|26|utah-jazz/jaxson-hayes
29|Blake Hinson|25|SF|80|27|utah-jazz/blake-hinson
29|Harrison Ingram|0|SF|78|24|utah-jazz/harrison-ingram
29|Jaren Jackson Jr.|20|PF|91|27|utah-jazz/jaren-jackson-jr
29|Lauri Markkanen|23|PF|91|29|utah-jazz/lauri-markkanen
29|Svi Mykhailiuk|10|SG|83|29|utah-jazz/svi-mykhailiuk
29|Jusuf Nurkic|30|C|84|32|utah-jazz/jusuf-nurkic
29|Josh Okogie|21|SG|82|28|utah-jazz/josh-okogie
29|Darryn Peterson|22|SG|86|19|utah-jazz/darryn-peterson
29|Brice Sensabaugh|28|SF|86|23|utah-jazz/brice-sensabaugh
14|Deandre Ayton|8|C|86|28|washington-wizards/deandre-ayton
14|Reece Beekman|26|PG|78|25|washington-wizards/reece-beekman
14|Bub Carrington|7|PG|84|21|washington-wizards/bub-carrington
14|Justin Champagnie|9|SF|84|25|washington-wizards/justin-champagnie
14|Bilal Coulibaly|0|SG|86|22|washington-wizards/bilal-coulibaly
14|Anthony Davis|23|PF|94|33|washington-wizards/anthony-davis
14|AJ Dybantsa|4|SF|86|19|washington-wizards/aj-dybantsa
14|Kyshawn George|18|SF|87|22|washington-wizards/kyshawn-george
14|Anthony Gill|16|SF|82|34|washington-wizards/anthony-gill
14|Tre Johnson|12|SG|86|20|washington-wizards/tre-johnson
14|Tre Mann|1|PG|82|25|washington-wizards/tre-mann
14|Khris Middleton|22|SF|84|35|washington-wizards/khris-middleton
14|Felix Okpara|34|SF|78|23|washington-wizards/felix-okpara
14|Will Riley|27|SG|86|20|washington-wizards/will-riley
14|Alex Sarr|20|C|89|21|washington-wizards/alex-sarr
14|Tristan Vukcevic|0|SF|81|23|washington-wizards/tristan-vukcevic
14|Jamir Watkins|5|SG|82|25|washington-wizards/jamir-watkins
14|Trae Young|3|PG|92|28|washington-wizards/trae-young
"""

    val PLAYERS: List<NbaPlayer> by lazy {
        listOf(P0).flatMap { it.trim().lines() }.filter { it.isNotBlank() }.map { l ->
            val f = l.split("|")
            NbaPlayer(f[0].toInt(), f[1], f[2].toInt(), Pos.valueOf(f[3]), f[4].toInt(), f[5].toInt(), f[6])
        }
    }
}

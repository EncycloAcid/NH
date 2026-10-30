package com.nh.hoopscoach.game

/** Name pools. All schools and people are fictional. */
object Names {
    val first = listOf(
        "Aaron", "Adrian", "Ahmad", "Aidan", "Alex", "Amari", "Andre", "Anthony", "Antonio", "Armando", "Asa", "Austin",
        "Bennett", "Blake", "Brandon", "Brayden", "Brennan", "Bryce", "Caleb", "Cameron", "Carson", "Carter", "Cedric",
        "Chase", "Christian", "Cole", "Colin", "Cooper", "Corey", "Cortez", "Dakota", "Damian", "Damon", "Dante",
        "Darius", "Darnell", "Davion", "Dawson", "Deandre", "Dejon", "Demetrius", "Derrick", "Desmond", "Devin",
        "Dexter", "Dominic", "Donovan", "Drew", "Dylan", "Elijah", "Elliot", "Emmanuel", "Eric", "Ethan", "Evan",
        "Fletcher", "Gabriel", "Gavin", "Grant", "Griffin", "Hayden", "Hector", "Hudson", "Ian", "Isaac", "Isaiah",
        "Ivan", "Jabari", "Jace", "Jackson", "Jalen", "Jamal", "Jamir", "Jared", "Jaylen", "Jaxon", "Jeremiah", "Jerome",
        "Jesse", "Jonah", "Jordan", "Josiah", "Julian", "Justin", "Kade", "Kaden", "Kai", "Keegan", "Keith", "Kendall",
        "Kendrick", "Kenyon", "Kevin", "Khalil", "Kobe", "Kyle", "Lamar", "Landon", "Lawrence", "Leon", "Levi", "Liam",
        "Lucas", "Luke", "Malachi", "Malik", "Marcus", "Mario", "Mason", "Matteo", "Maurice", "Max", "Micah", "Miles",
        "Mitchell", "Nasir", "Nathan", "Nico", "Noah", "Omar", "Owen", "Parker", "Pedro", "Quentin", "Quincy", "Rashad",
        "Reggie", "Ricky", "Robert", "Roman", "Ryan", "Samuel", "Sean", "Silas", "Simon", "Spencer", "Terrance",
        "Theo", "Tobias", "Trey", "Tristan", "Tyler", "Tyrese", "Ulysses", "Victor", "Vince", "Wade", "Wesley", "Xavier",
        "Yusuf", "Zach", "Zion",
    )
    val last = listOf(
        "Abbott", "Adams", "Akins", "Alexander", "Allen", "Alvarez", "Anderson", "Armstrong", "Bailey", "Baker", "Banks",
        "Barnes", "Barrett", "Bell", "Bender", "Bishop", "Blackwell", "Bowman", "Boyd", "Bradley", "Brewer", "Brooks",
        "Bryant", "Burke", "Burns", "Butler", "Caldwell", "Carroll", "Carter", "Chambers", "Chavez", "Clark", "Coleman",
        "Collins", "Cooper", "Crawford", "Cross", "Cunningham", "Daniels", "Davenport", "Dawson", "Dixon", "Douglas",
        "Duncan", "Dunn", "Edwards", "Elliott", "Ellis", "Elmore", "Evans", "Ferguson", "Fields", "Fleming", "Fletcher",
        "Ford", "Foster", "Franklin", "Freeman", "Fuller", "Garner", "Garrett", "Gibson", "Giddings", "Gilbert", "Goodwin",
        "Graham", "Grant", "Graves", "Greene", "Griffin", "Hale", "Hamilton", "Harper", "Harris", "Harrison", "Hawkins",
        "Hayes", "Henderson", "Hicks", "Holland", "Holloway", "Hopkins", "Howard", "Hudson", "Hughes", "Hunt", "Jacobs",
        "James", "Jenkins", "Johnson", "Jordan", "Keller", "Kennedy", "Kincaid", "King", "Knight", "Lambert", "Lane",
        "Larson", "Lawson", "Lewis", "Lowe", "Lyons", "Mack", "Malone", "Manning", "Marsh", "Martin", "Mason", "Matthews",
        "Maxwell", "McCoy", "McDaniel", "Meadows", "Miles", "Mitchell", "Montgomery", "Moore", "Morgan", "Morris",
        "Murphy", "Murray", "Nash", "Newton", "Norris", "Oliver", "Owens", "Palmer", "Parker", "Patterson", "Payne",
        "Pearson", "Perkins", "Perry", "Peters", "Phillips", "Pierce", "Porter", "Powell", "Price", "Quinn", "Ramsey",
        "Randall", "Reed", "Reese", "Reid", "Reynolds", "Rhodes", "Richardson", "Riley", "Rivers", "Roberts", "Robinson",
        "Rogers", "Ross", "Russell", "Sanders", "Sharp", "Shaw", "Simmons", "Sims", "Singleton", "Slater", "Spencer",
        "Stanley", "Stephens", "Stewart", "Stone", "Sullivan", "Sutton", "Tate", "Terry", "Thomas", "Thornton",
        "Threadgill", "Tucker", "Turner", "Vaughn", "Wade", "Walker", "Wallace", "Walton", "Ward", "Warren", "Washington",
        "Watkins", "Webb", "Webster", "Wells", "West", "Wheeler", "White", "Wiggins", "Wilkins", "Williams", "Willis",
        "Wilson", "Woods", "Wright", "Young",
    )
    val intlFirst = mapOf(
        "Australia" to listOf("Lachlan", "Jock", "Mitch", "Harrison", "Callum"),
        "Croatia" to listOf("Ivan", "Luka", "Mario", "Dario", "Ante"),
        "Serbia" to listOf("Nikola", "Stefan", "Vasilije", "Marko", "Bogdan"),
        "France" to listOf("Theo", "Hugo", "Mathis", "Yanis", "Bastien"),
        "Spain" to listOf("Pablo", "Sergio", "Hugo", "Alvaro", "Dani"),
        "Lithuania" to listOf("Rokas", "Domantas", "Matas", "Tadas", "Arnas"),
        "Nigeria" to listOf("Chidi", "Emeka", "Tobi", "Femi", "Obinna"),
        "Canada" to listOf("Owen", "Liam", "Noah", "Elijah", "Benjamin"),
        "Germany" to listOf("Jonas", "Lukas", "Felix", "Moritz", "Tim"),
        "Senegal" to listOf("Mamadou", "Cheikh", "Ousmane", "Pape", "Ibrahima"),
        "Greece" to listOf("Giannis", "Kostas", "Nikos", "Thanasis", "Dimitris"),
        "Finland" to listOf("Lauri", "Elias", "Mikael", "Sasu", "Aleksi"),
        "Brazil" to listOf("Gui", "Lucas", "Bruno", "Rafael", "Caio"),
    )
    val intlLast = mapOf(
        "Australia" to listOf("McAllister", "Hargreaves", "Whitlock", "Barlow", "Kennard"),
        "Croatia" to listOf("Kovac", "Horvat", "Babic", "Maric", "Juric"),
        "Serbia" to listOf("Jovanovic", "Nikolic", "Markovic", "Stojanovic", "Lazic"),
        "France" to listOf("Moreau", "Dupont", "Girard", "Lefevre", "Rousseau"),
        "Spain" to listOf("Ruiz", "Navarro", "Ortega", "Molina", "Serrano"),
        "Lithuania" to listOf("Kazlauskas", "Petrauskas", "Jankauskas", "Butkus", "Vaitkus"),
        "Nigeria" to listOf("Okonkwo", "Nwosu", "Okeke", "Eze", "Adeyemi"),
        "Canada" to listOf("Tremblay", "Gagnon", "Roy", "Campbell", "MacDonald"),
        "Germany" to listOf("Hartmann", "Becker", "Fischer", "Weber", "Schulz"),
        "Senegal" to listOf("Diallo", "Ndiaye", "Sow", "Diop", "Fall"),
        "Greece" to listOf("Papadakis", "Georgiou", "Nikolaou", "Dimitriou", "Karras"),
        "Finland" to listOf("Koponen", "Salin", "Lahtinen", "Kuusela", "Virtanen"),
        "Brazil" to listOf("Santos", "Barbosa", "Silva", "Oliveira", "Costa"),
    )
    val towns = listOf(
        "Compton, CA", "Oakland, CA", "Fresno, CA", "Long Beach, CA", "San Diego, CA", "Houston, TX", "Dallas, TX",
        "San Antonio, TX", "Austin, TX", "Fort Worth, TX", "Atlanta, GA", "Savannah, GA", "Macon, GA", "Miami, FL",
        "Orlando, FL", "Tampa, FL", "Jacksonville, FL", "Charlotte, NC", "Raleigh, NC", "Durham, NC", "Greensboro, NC",
        "Richmond, VA", "Norfolk, VA", "Baltimore, MD", "Philadelphia, PA", "Pittsburgh, PA", "Newark, NJ",
        "Camden, NJ", "Bronx, NY", "Brooklyn, NY", "Queens, NY", "Rochester, NY", "Buffalo, NY", "Boston, MA",
        "Hartford, CT", "Chicago, IL", "Peoria, IL", "Detroit, MI", "Flint, MI", "Grand Rapids, MI", "Cleveland, OH",
        "Columbus, OH", "Cincinnati, OH", "Akron, OH", "Indianapolis, IN", "Gary, IN", "Louisville, KY",
        "Lexington, KY", "Memphis, TN", "Nashville, TN", "Birmingham, AL", "Mobile, AL", "Jackson, MS", "New Orleans, LA",
        "Baton Rouge, LA", "Little Rock, AR", "Tulsa, OK", "Oklahoma City, OK", "Wichita, KS", "Kansas City, MO",
        "St. Louis, MO", "Omaha, NE", "Minneapolis, MN", "Milwaukee, WI", "Des Moines, IA", "Denver, CO", "Phoenix, AZ",
        "Las Vegas, NV", "Salt Lake City, UT", "Seattle, WA", "Tacoma, WA", "Portland, OR", "Albuquerque, NM",
        "Washington, DC", "Wilmington, DE", "Columbia, SC", "Charleston, SC", "Knoxville, TN", "Shreveport, LA",
        "Montgomery, AL", "Toledo, OH", "Dayton, OH", "Syracuse, NY", "Providence, RI", "Anchorage, AK", "Honolulu, HI",
    )
    val hsSuffix = listOf("High", "Prep", "Academy", "Christian", "Catholic", "Central", "North", "West", "Tech", "Heights")
    val hsPrefix = listOf(
        "Dominguez", "Oak Hill", "Montverde", "Findlay", "Wheeler", "Lincoln", "Roosevelt", "Kennedy", "Jefferson",
        "St. Vincent", "Mater Dei", "Sierra Canyon", "Westchester", "Brewster", "La Lumiere", "Hillcrest", "IMG",
        "Prolific", "Link", "Long Island Lutheran", "Paul VI", "DeMatha", "Gonzaga", "Bishop Gorman", "Corona",
        "Harvard-Westlake", "Duncanville", "Morgan Park", "Simeon", "Whitney Young", "Riverside", "Eastside",
        "Lakeview", "Cedar Ridge", "Pine Valley", "Northwood", "Southridge", "Mount Zion", "Grace", "Liberty",
    )

    /** Places used to build fictional school names. */
    val places = listOf(
        "Ashford", "Bayport", "Bellmont", "Blue Ridge", "Briarwood", "Brookhaven", "Calder", "Cambria", "Carver",
        "Cedar Falls", "Cedarcrest", "Centralia", "Chesapeake", "Clearwater", "Coldwater", "Columbia Valley", "Copper Canyon",
        "Crestview", "Cumberland", "Dalton", "Deer Park", "Delmar", "Eastbrook", "Easton", "Elk River", "Emerald Coast",
        "Fairhaven", "Falcon Ridge", "Fox Valley", "Franklin", "Galveston", "Glen Echo", "Grand Prairie", "Granite Falls",
        "Great Plains", "Greenfield", "Hartwell", "Harbor City", "Hawthorne", "Highland", "Hollister", "Hudson Valley",
        "Iron Range", "Jasper", "Juniper", "Kearney", "Keystone", "Kingsport", "Lake Shore", "Lakewood", "Laredo",
        "Lewisburg", "Lincoln Park", "Long Bay", "Magnolia", "Maple Grove", "Marion", "Meadowbrook", "Merrimack",
        "Mesa Verde", "Midland", "Millbrook", "Mohave", "Montclair", "Mount Pleasant", "New Albion", "Northgate",
        "Oak Harbor", "Oakmont", "Ocean Bluff", "Olympia", "Orchard Park", "Palisades", "Palmetto", "Pine Bluff",
        "Pinecrest", "Port Royal", "Prairie View", "Quarry Hill", "Redwood", "Ridgemont", "Riverbend", "Rockport",
        "Rolling Hills", "Saddleback", "Salem", "Sandpoint", "Sawtooth", "Seabrook", "Shelby", "Silver Lake",
        "Southport", "Springdale", "Stonebridge", "Summit", "Sun Valley", "Sycamore", "Tahoma", "Tidewater",
        "Timberline", "Trinity", "Twin Rivers", "Union City", "Valley Forge", "Vista", "Wabash Valley", "Walnut Creek",
        "Waverly", "West Haven", "Westfield", "Whitewater", "Wildwood", "Willow Bend", "Windham", "Woodland",
        "Yellowstone", "Yorktown", "Appalachia", "Bayou", "Big Sky", "Black Hills", "Blackstone", "Bluegrass",
        "Bridgeport", "Canyon", "Capital City", "Carolina Coast", "Cascade", "Central Plains", "Coastal", "Delta",
        "Desert", "Driftwood", "Eagle Pass", "Flint Hills", "Foothills", "Frontier", "Gateway", "Gulf Coast", "Heartland",
        "High Desert", "Hill Country", "Inland", "Lakeland", "Lone Pine", "Lowcountry", "Mid-Atlantic", "Midway",
        "Mountain", "North Coast", "Northern", "Ozark", "Pacific", "Panhandle", "Piedmont", "Prairie", "Red River",
        "Rio Grande", "River City", "Sierra", "Southern", "Southwest", "Sunbelt", "Tri-State", "Upstate", "Western",
        "Albion", "Arden", "Avalon", "Beaumont", "Belmont Park", "Brighton", "Carlisle", "Chandler", "Clayton",
        "Concord", "Dover", "Elmhurst", "Fairfield", "Glenwood", "Hamilton", "Harrisburg", "Kent Falls", "Lancaster",
        "Lexington Hills", "Madison Park", "Marlow", "Newport", "Norwood", "Oxford Hills", "Pembroke", "Preston",
        "Quincy", "Richland", "Rutherford", "Sheffield", "Somerset", "Stamford", "Sterling", "Thornton", "Trenton Falls",
        "Vernon", "Wakefield", "Wellington", "Weston", "Whitfield", "Wilmington Bay", "Winchester", "Wyndham",
        "Aurora", "Bellevue", "Bristol", "Clifton", "Dearborn", "Edgewood", "Fulton", "Garland", "Hampton",
        "Irving", "Jackson Hole", "Kingston", "Lafayette", "Mansfield", "Monroe", "Nashua", "Ogden", "Peyton",
        "Raleigh Hills", "Saratoga", "Tyler", "Urbana", "Valdosta", "Waco Falls", "Xenia", "Yuma", "Zephyr",
    )
    val saints = listOf("Bonaventure", "Agnes", "Ambrose", "Cyril", "Dominic", "Edmund", "Florian", "Gregory", "Jerome", "Lucian", "Martin", "Rafael", "Sebastian", "Thaddeus")
    val mascots = listOf(
        "Gators", "Cougars", "Wolves", "Hawks", "Eagles", "Bears", "Tigers", "Lions", "Panthers", "Falcons", "Knights",
        "Pioneers", "Rams", "Bulldogs", "Mustangs", "Broncos", "Wildcats", "Hornets", "Owls", "Ravens", "Comets",
        "Rockets", "Storm", "Thunder", "Lightning", "Cyclones", "Hurricanes", "Blazers", "Flames", "Spartans",
        "Titans", "Trojans", "Vikings", "Raiders", "Pirates", "Mariners", "Seahawks", "Ospreys", "Pelicans", "Herons",
        "Badgers", "Wolverines", "Bobcats", "Lynx", "Jaguars", "Cardinals", "Blue Jays", "Orioles", "Jayhawks",
        "Bison", "Buffaloes", "Longhorns", "Stallions", "Chargers", "Patriots", "Minutemen", "Colonials", "Rebels",
        "Mountaineers", "Rangers", "Outlaws", "Sooners", "Huskies", "Retrievers", "Terriers", "Greyhounds", "Foxes",
        "Coyotes", "Scorpions", "Rattlers", "Cobras", "Dragons", "Griffins", "Phoenix", "Thunderbirds", "Golden Eagles",
        "Red Hawks", "Black Bears", "Grizzlies", "Kodiaks", "Timberwolves", "Lumberjacks", "Miners", "Railers",
        "Engineers", "Monarchs", "Royals", "Crusaders", "Saints", "Friars", "Dukes", "Barons", "Generals", "Admirals",
        "Captains", "Sharks", "Stingrays", "Marlins", "Dolphins", "Gulls", "Tide", "Waves", "Surge", "Rapids",
        "Aggies", "Sentinels", "Highlanders", "Leopards", "Catamounts", "Mavericks", "Wranglers", "Roadrunners",
    )
    val colors: List<Pair<Long, Long>> = listOf(
        0xFF0021A5 to 0xFFFA4616, 0xFF002E5D to 0xFFFFFFFF, 0xFF7B1113 to 0xFFFFC72C, 0xFF00274C to 0xFFFFCB05,
        0xFF4B2E83 to 0xFFB7A57A, 0xFF8C1D40 to 0xFFFFC627, 0xFF154734 to 0xFFFFC72C, 0xFFBA0C2F to 0xFF000000,
        0xFF0C2340 to 0xFFC8102E, 0xFF13294B to 0xFF4B9CD3, 0xFF500000 to 0xFFFFFFFF, 0xFFCC0000 to 0xFF000000,
        0xFFFF8200 to 0xFFFFFFFF, 0xFF461D7C to 0xFFFDD023, 0xFF00843D to 0xFFFFFFFF, 0xFF041E42 to 0xFFB3A369,
        0xFF9E1B32 to 0xFF828A8F, 0xFF003087 to 0xFFFFFFFF, 0xFF18453B to 0xFFFFFFFF, 0xFF862633 to 0xFFFFFFFF,
        0xFF002855 to 0xFFF2A900, 0xFF0033A0 to 0xFFE31837, 0xFF231F20 to 0xFFF1BE48, 0xFFE87722 to 0xFF003366,
        0xFF5C068C to 0xFFFFFFFF, 0xFF006747 to 0xFFF0B323, 0xFF00205B to 0xFFA2AAAD, 0xFFC5050C to 0xFFFFFFFF,
        0xFF2D68C4 to 0xFFF2A900, 0xFF6F263D to 0xFFA2AAAD, 0xFF00B2A9 to 0xFF000000, 0xFF8A2432 to 0xFFF2C75C,
        0xFF003DA5 to 0xFF99D6EA, 0xFF2C2A29 to 0xFFCF4520, 0xFF0B3D91 to 0xFFF1B82D, 0xFF990000 to 0xFFE0E0E0,
    )

    val confNames = listOf(
        // tier 0 (power)
        Triple("Atlantic Premier", "APC", 0), Triple("Southern Union", "SUN", 0), Triple("Great Lakes", "GLC", 0),
        Triple("Pacific Coast", "PCC", 0), Triple("Heartland", "HRT", 0),
        // tier 1 (mid-major)
        Triple("Mountain Range", "MRC", 1), Triple("Gulf Coast", "GCC", 1), Triple("River Valley", "RVC", 1),
        Triple("Keystone", "KEY", 1), Triple("Frontier", "FRO", 1), Triple("Cascade", "CAS", 1),
        // tier 2 (low-major)
        Triple("Piedmont", "PIE", 2), Triple("Bayou", "BAY", 2), Triple("Redwood", "RED", 2), Triple("Granite", "GRN", 2),
        Triple("Ozark", "OZK", 2), Triple("Lakeshore", "LKS", 2), Triple("Mesa", "MSA", 2), Triple("Tidewater", "TDW", 2),
        Triple("Northwoods", "NWD", 2),
    )
    val confSizes = listOf(16, 16, 14, 14, 12, 12, 12, 12, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10)

    val events = listOf("Paradise Invitational", "Empire Classic", "Lone Star Showcase", "Bluegrass Shootout")
}

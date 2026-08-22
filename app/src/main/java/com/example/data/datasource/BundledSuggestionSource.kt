package com.example.data.datasource

import com.example.data.model.LocationCategory
import com.example.data.model.LocationSuggestion

interface BundledSuggestionSource {
    fun getCuratedLocations(): List<LocationSuggestion>
}

class BundledSuggestionSourceImpl : BundledSuggestionSource {

    private val cachedCuratedLocations: List<LocationSuggestion> by lazy {
        listOf(
            // Monuments & Landmarks
            LocationSuggestion(
                name = "Gateway of India",
                destination = "Mumbai, Maharashtra",
                category = LocationCategory.MONUMENT,
                aliases = listOf("gateway", "colaba gateway", "gateway mumbai")
            ),
            LocationSuggestion(
                name = "Taj Mahal",
                destination = "Agra, Uttar Pradesh",
                category = LocationCategory.MONUMENT,
                aliases = listOf("taj", "agra taj mahal", "wonder of world")
            ),
            LocationSuggestion(
                name = "India Gate",
                destination = "New Delhi",
                category = LocationCategory.MONUMENT,
                aliases = listOf("delhi gate", "war memorial")
            ),
            LocationSuggestion(
                name = "Qutub Minar",
                destination = "New Delhi",
                category = LocationCategory.MONUMENT,
                aliases = listOf("qutab minar", "mehrauli")
            ),
            LocationSuggestion(
                name = "Red Fort",
                destination = "Old Delhi",
                category = LocationCategory.MONUMENT,
                aliases = listOf("lal qila", "delhi red fort")
            ),
            LocationSuggestion(
                name = "Charminar",
                destination = "Hyderabad, Telangana",
                category = LocationCategory.MONUMENT,
                aliases = listOf("hyderabad monument", "charminar old city")
            ),
            LocationSuggestion(
                name = "Victoria Memorial",
                destination = "Kolkata, West Bengal",
                category = LocationCategory.MONUMENT,
                aliases = listOf("victoria hall", "kolkata memorial")
            ),
            LocationSuggestion(
                name = "Hawa Mahal",
                destination = "Jaipur, Rajasthan",
                category = LocationCategory.MONUMENT,
                aliases = listOf("palace of winds", "jaipur hawa mahal")
            ),

            // Heritage & Ancient Sites
            LocationSuggestion(
                name = "Hampi Ruins",
                destination = "Vijayanagara, Karnataka",
                category = LocationCategory.HERITAGE,
                aliases = listOf("hampi", "vijayanagara empire", "virupaksha temple")
            ),
            LocationSuggestion(
                name = "Ajanta Caves",
                destination = "Chhatrapati Sambhajinagar, Maharashtra",
                category = LocationCategory.HERITAGE,
                aliases = listOf("ajanta", "aurangabad caves")
            ),
            LocationSuggestion(
                name = "Ellora Caves",
                destination = "Chhatrapati Sambhajinagar, Maharashtra",
                category = LocationCategory.HERITAGE,
                aliases = listOf("ellora", "kailasa temple", "verul")
            ),
            LocationSuggestion(
                name = "Mysore Palace",
                destination = "Mysuru, Karnataka",
                category = LocationCategory.HERITAGE,
                aliases = listOf("mysuru palace", "ambavilas palace")
            ),
            LocationSuggestion(
                name = "Jaipur City Palace",
                destination = "Jaipur, Rajasthan",
                category = LocationCategory.HERITAGE,
                aliases = listOf("city palace jaipur", "pink city")
            ),
            LocationSuggestion(
                name = "Konark Sun Temple",
                destination = "Puri, Odisha",
                category = LocationCategory.HERITAGE,
                aliases = listOf("sun temple", "black pagoda")
            ),

            // Forts
            LocationSuggestion(
                name = "Harihar Fort",
                destination = "Nashik, Maharashtra",
                category = LocationCategory.FORT,
                aliases = listOf("harihar", "harshagad", "rock cut steps")
            ),
            LocationSuggestion(
                name = "Rajgad Fort",
                destination = "Gunjavane, Pune, Maharashtra",
                category = LocationCategory.FORT,
                aliases = listOf("rajgad", "king of forts", "suvela machi")
            ),
            LocationSuggestion(
                name = "Torna Fort",
                destination = "Velhe, Pune, Maharashtra",
                category = LocationCategory.FORT,
                aliases = listOf("torna", "prachandagad")
            ),
            LocationSuggestion(
                name = "Raigad Fort",
                destination = "Mahad, Raigad, Maharashtra",
                category = LocationCategory.FORT,
                aliases = listOf("raigad", "capital fort", "takmak tok")
            ),
            LocationSuggestion(
                name = "Sinhagad Fort",
                destination = "Haveli, Pune, Maharashtra",
                category = LocationCategory.FORT,
                aliases = listOf("sinhagad", "kondhana", "pune fort")
            ),
            LocationSuggestion(
                name = "Lohagad Fort",
                destination = "Lonavala, Pune, Maharashtra",
                category = LocationCategory.FORT,
                aliases = listOf("lohagad", "iron fort", "vinchu kata")
            ),
            LocationSuggestion(
                name = "Visapur Fort",
                destination = "Malavli, Pune, Maharashtra",
                category = LocationCategory.FORT,
                aliases = listOf("visapur", "waterfall trek fort")
            ),
            LocationSuggestion(
                name = "Tikona Fort",
                destination = "Kamshet, Pune, Maharashtra",
                category = LocationCategory.FORT,
                aliases = listOf("tikona", "vitandgad", "pawna lake fort")
            ),
            LocationSuggestion(
                name = "Korigad Fort",
                destination = "Aamby Valley, Lonavala, Maharashtra",
                category = LocationCategory.FORT,
                aliases = listOf("korigad", "aamby valley fort")
            ),
            LocationSuggestion(
                name = "Pratapgad Fort",
                destination = "Mahabaleshwar, Satara, Maharashtra",
                category = LocationCategory.FORT,
                aliases = listOf("pratapgad", "satara fort")
            ),
            LocationSuggestion(
                name = "Murud Janjira",
                destination = "Murud, Raigad, Maharashtra",
                category = LocationCategory.FORT,
                aliases = listOf("janjira", "sea fort", "murud")
            ),
            LocationSuggestion(
                name = "Mehrangarh Fort",
                destination = "Jodhpur, Rajasthan",
                category = LocationCategory.FORT,
                aliases = listOf("mehrangarh", "jodhpur blue city fort")
            ),
            LocationSuggestion(
                name = "Chittorgarh Fort",
                destination = "Chittorgarh, Rajasthan",
                category = LocationCategory.FORT,
                aliases = listOf("chittor", "vijay stambha")
            ),
            LocationSuggestion(
                name = "Gwalior Fort",
                destination = "Gwalior, Madhya Pradesh",
                category = LocationCategory.FORT,
                aliases = listOf("gwalior", "pearl of indian forts")
            ),
            LocationSuggestion(
                name = "Golconda Fort",
                destination = "Hyderabad, Telangana",
                category = LocationCategory.FORT,
                aliases = listOf("golconda", "hyderabad fort")
            ),

            // Treks & Peaks
            LocationSuggestion(
                name = "Kalsubai Peak",
                destination = "Igatpuri, Maharashtra",
                category = LocationCategory.TREK,
                aliases = listOf("kalsubai", "highest peak maharashtra", "everest of maharashtra")
            ),
            LocationSuggestion(
                name = "Harishchandragad",
                destination = "Khireshwar, Ahmednagar, Maharashtra",
                category = LocationCategory.TREK,
                aliases = listOf("kokankada", "taramati peak", "kedareswar cave")
            ),
            LocationSuggestion(
                name = "Kalavantin Durg",
                destination = "Panvel, Raigad, Maharashtra",
                category = LocationCategory.TREK,
                aliases = listOf("kalavantin", "prabalmachi", "steep steps")
            ),
            LocationSuggestion(
                name = "Sandakphu Trek",
                destination = "Darjeeling, West Bengal",
                category = LocationCategory.TREK,
                aliases = listOf("sandakphu", "sleeping buddha", "singalila")
            ),
            LocationSuggestion(
                name = "Kedarkantha Trek",
                destination = "Sankri, Uttarakhand",
                category = LocationCategory.TREK,
                aliases = listOf("kedarkantha", "winter snow trek")
            ),
            LocationSuggestion(
                name = "Roopkund Lake Trek",
                destination = "Chamoli, Uttarakhand",
                category = LocationCategory.TREK,
                aliases = listOf("roopkund", "mystery lake trek")
            ),
            LocationSuggestion(
                name = "Triund Trek",
                destination = "Dharamshala, Himachal Pradesh",
                category = LocationCategory.TREK,
                aliases = listOf("triund", "mcleodganj ridge")
            ),
            LocationSuggestion(
                name = "Kumara Parvatha Trek",
                destination = "Subrahmanya, Karnataka",
                category = LocationCategory.TREK,
                aliases = listOf("kp trek", "pushpagiri", "kukke")
            ),
            LocationSuggestion(
                name = "Kudremukh Peak",
                destination = "Chikkamagaluru, Karnataka",
                category = LocationCategory.TREK,
                aliases = listOf("kudremukh", "horse face peak")
            ),

            // Waterfalls
            LocationSuggestion(
                name = "Dudhsagar Falls",
                destination = "Goa - Karnataka Border",
                category = LocationCategory.WATERFALL,
                aliases = listOf("dudhsagar", "sea of milk", "railway bridge falls")
            ),
            LocationSuggestion(
                name = "Jog Falls",
                destination = "Shivamogga, Karnataka",
                category = LocationCategory.WATERFALL,
                aliases = listOf("jog", "gerusoppa falls", "sharavathi")
            ),
            LocationSuggestion(
                name = "Nohkalikai Falls",
                destination = "Cherrapunji, Meghalaya",
                category = LocationCategory.WATERFALL,
                aliases = listOf("nohkalikai", "sohra waterfall", "tallest plunge")
            ),
            LocationSuggestion(
                name = "Athirappilly Falls",
                destination = "Thrissur, Kerala",
                category = LocationCategory.WATERFALL,
                aliases = listOf("athirappilly", "niagara of india", "chalakkudy")
            ),
            LocationSuggestion(
                name = "Devkund Waterfall",
                destination = "Bhira, Raigad, Maharashtra",
                category = LocationCategory.WATERFALL,
                aliases = listOf("devkund", "bhira dam waterfall", "plus valley")
            ),
            LocationSuggestion(
                name = "Hogenakkal Falls",
                destination = "Dharmapuri, Tamil Nadu",
                category = LocationCategory.WATERFALL,
                aliases = listOf("hogenakkal", "smoking rocks", "kaveri falls")
            ),

            // Beaches & Coastal
            LocationSuggestion(
                name = "Gokarna Coast",
                destination = "Kumta, Karnataka",
                category = LocationCategory.BEACH,
                aliases = listOf("om beach", "kudle beach", "half moon beach", "gokarna")
            ),
            LocationSuggestion(
                name = "Palolem Beach",
                destination = "Canacona, South Goa",
                category = LocationCategory.BEACH,
                aliases = listOf("palolem", "south goa beach", "butterfly beach")
            ),
            LocationSuggestion(
                name = "Radhanagar Beach",
                destination = "Havelock Island, Andaman & Nicobar",
                category = LocationCategory.BEACH,
                aliases = listOf("radhanagar", "havelock beach no 7", "andaman beach")
            ),
            LocationSuggestion(
                name = "Varkala Cliff Beach",
                destination = "Varkala, Kerala",
                category = LocationCategory.BEACH,
                aliases = listOf("varkala", "papanasam beach", "kerala cliff")
            ),
            LocationSuggestion(
                name = "Marina Beach",
                destination = "Chennai, Tamil Nadu",
                category = LocationCategory.BEACH,
                aliases = listOf("marina", "chennai coast", "longest beach")
            ),
            LocationSuggestion(
                name = "Kovalam Beach",
                destination = "Thiruvananthapuram, Kerala",
                category = LocationCategory.BEACH,
                aliases = listOf("kovalam", "lighthouse beach", "hawa beach")
            ),
            LocationSuggestion(
                name = "Tarkarli Beach",
                destination = "Malvan, Sindhudurg, Maharashtra",
                category = LocationCategory.BEACH,
                aliases = listOf("tarkarli", "scuba beach", "malvan coast")
            ),
            LocationSuggestion(
                name = "Puri Sea Beach",
                destination = "Puri, Odisha",
                category = LocationCategory.BEACH,
                aliases = listOf("puri beach", "golden beach odisha")
            ),

            // Hill Stations & Valleys
            LocationSuggestion(
                name = "Munnar Tea Gardens",
                destination = "Idukki, Kerala",
                category = LocationCategory.HILL_STATION,
                aliases = listOf("munnar", "anamudi", "tea estates")
            ),
            LocationSuggestion(
                name = "Manali Valley",
                destination = "Kullu, Himachal Pradesh",
                category = LocationCategory.HILL_STATION,
                aliases = listOf("manali", "solang valley", "old manali")
            ),
            LocationSuggestion(
                name = "Ooty Lake & Hills",
                destination = "Nilgiris, Tamil Nadu",
                category = LocationCategory.HILL_STATION,
                aliases = listOf("ooty", "udhagamandalam", "doddabetta")
            ),
            LocationSuggestion(
                name = "Shimla Ridge",
                destination = "Shimla, Himachal Pradesh",
                category = LocationCategory.HILL_STATION,
                aliases = listOf("shimla", "mall road shimla", "jakhu")
            ),
            LocationSuggestion(
                name = "Coorg Coffee Hills",
                destination = "Kodagu, Karnataka",
                category = LocationCategory.HILL_STATION,
                aliases = listOf("coorg", "madikeri", "scotland of india", "raja seat")
            ),
            LocationSuggestion(
                name = "Mahabaleshwar Plateau",
                destination = "Satara, Maharashtra",
                category = LocationCategory.HILL_STATION,
                aliases = listOf("mahabaleshwar", "arthurs seat", "venna lake", "strawberry city")
            ),
            LocationSuggestion(
                name = "Darjeeling Hills",
                destination = "Darjeeling, West Bengal",
                category = LocationCategory.HILL_STATION,
                aliases = listOf("darjeeling", "tiger hill", "toy train", "batasia loop")
            ),
            LocationSuggestion(
                name = "Kodaikanal Lake",
                destination = "Dindigul, Tamil Nadu",
                category = LocationCategory.HILL_STATION,
                aliases = listOf("kodaikanal", "princess of hill stations", "pillar rocks")
            ),

            // Nature & Wilderness
            LocationSuggestion(
                name = "Valley of Flowers",
                destination = "Chamoli, Uttarakhand",
                category = LocationCategory.NATURE,
                aliases = listOf("valley of flowers", "pushpawati", "hemkund sahib")
            ),
            LocationSuggestion(
                name = "Spiti Valley",
                destination = "Lahaul and Spiti, Himachal Pradesh",
                category = LocationCategory.NATURE,
                aliases = listOf("spiti", "kaza", "key monastery", "chandratal")
            ),
            LocationSuggestion(
                name = "Pangong Tso Lake",
                destination = "Leh, Ladakh",
                category = LocationCategory.NATURE,
                aliases = listOf("pangong", "ladakh lake", "changthang")
            ),
            LocationSuggestion(
                name = "Nubra Valley",
                destination = "Ladakh",
                category = LocationCategory.NATURE,
                aliases = listOf("nubra", "hunder sand dunes", "diskit")
            ),
            LocationSuggestion(
                name = "Rann of Kutch",
                destination = "Dhordo, Kutch, Gujarat",
                category = LocationCategory.NATURE,
                aliases = listOf("white desert", "rann utsav", "kutch")
            ),
            LocationSuggestion(
                name = "Alleppey Backwaters",
                destination = "Alappuzha, Kerala",
                category = LocationCategory.NATURE,
                aliases = listOf("alleppey", "alappuzha", "houseboat backwaters", "vembanad")
            ),
            LocationSuggestion(
                name = "Jim Corbett National Park",
                destination = "Nainital, Uttarakhand",
                category = LocationCategory.NATURE,
                aliases = listOf("corbett", "tiger safari", "ramnagar")
            ),
            LocationSuggestion(
                name = "Kaziranga National Park",
                destination = "Golaghat & Nagaon, Assam",
                category = LocationCategory.NATURE,
                aliases = listOf("kaziranga", "rhino sanctuary", "brahmaputra")
            ),
            LocationSuggestion(
                name = "Sundarbans National Park",
                destination = "South 24 Parganas, West Bengal",
                category = LocationCategory.NATURE,
                aliases = listOf("sundarbans", "mangrove forest", "royal bengal tiger")
            ),

            // Temples & Spiritual
            LocationSuggestion(
                name = "Golden Temple",
                destination = "Amritsar, Punjab",
                category = LocationCategory.TEMPLE,
                aliases = listOf("harmandir sahib", "darbar sahib", "amritsar")
            ),
            LocationSuggestion(
                name = "Kedarnath Temple",
                destination = "Rudraprayag, Uttarakhand",
                category = LocationCategory.TEMPLE,
                aliases = listOf("kedarnath", "char dham", "himalayan temple")
            ),
            LocationSuggestion(
                name = "Badrinath Temple",
                destination = "Chamoli, Uttarakhand",
                category = LocationCategory.TEMPLE,
                aliases = listOf("badrinath", "alknanda", "char dham")
            ),
            LocationSuggestion(
                name = "Meenakshi Amman Temple",
                destination = "Madurai, Tamil Nadu",
                category = LocationCategory.TEMPLE,
                aliases = listOf("meenakshi temple", "madurai", "gopuram temple")
            ),
            LocationSuggestion(
                name = "Varanasi Ghats",
                destination = "Varanasi, Uttar Pradesh",
                category = LocationCategory.TEMPLE,
                aliases = listOf("kashi", "dashashwamedh ghat", "ganga aarti", "banaras")
            ),
            LocationSuggestion(
                name = "Tirupati Balaji Temple",
                destination = "Tirupati, Andhra Pradesh",
                category = LocationCategory.TEMPLE,
                aliases = listOf("tirumala", "venkateswara temple", "seven hills")
            ),
            LocationSuggestion(
                name = "Jagannath Temple",
                destination = "Puri, Odisha",
                category = LocationCategory.TEMPLE,
                aliases = listOf("puri temple", "rath yatra", "jagannath dham")
            ),
            LocationSuggestion(
                name = "Somnath Temple",
                destination = "Prabhas Patan, Gujarat",
                category = LocationCategory.TEMPLE,
                aliases = listOf("somnath", "jyotirlinga", "veraval coast")
            )
        )
    }

    override fun getCuratedLocations(): List<LocationSuggestion> = cachedCuratedLocations
}

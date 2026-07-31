package com.glancemap.glancemapwearos.data.repository

import java.util.Locale

private val INTEGER_REGEX = Regex("-?\\d+")

internal fun parseDisplayName(tags: Map<String, String>): String? {
    if (tags.isEmpty()) return null
    val nameEn = tags["name:en"]?.trim().orEmpty()
    if (nameEn.isNotBlank()) return nameEn

    val name = tags["name"]?.trim().orEmpty()
    return name.takeIf { it.isNotBlank() }
}

internal fun poiSearchMatchRank(
    name: String,
    queryLower: String,
): Int {
    val normalized = name.trim().lowercase(Locale.ROOT)
    return when {
        normalized == queryLower -> 0
        normalized.startsWith(queryLower) -> 1
        normalized.split(Regex("\\s+")).any { it.startsWith(queryLower) } -> 2
        normalized.contains(queryLower) -> 3
        else -> 4
    }
}

internal fun classifyPoiType(
    tags: Map<String, String>,
    categoryName: String,
    rawData: String,
): PoiType {
    if (tags.isEmpty() && rawData.isBlank()) return PoiType.GENERIC
    val amenity = tags["amenity"]?.lowercase(Locale.ROOT)
    val tourism = tags["tourism"]?.lowercase(Locale.ROOT)
    val natural = tags["natural"]?.lowercase(Locale.ROOT)
    val highway = tags["highway"]?.lowercase(Locale.ROOT)
    val publicTransport = tags["public_transport"]?.lowercase(Locale.ROOT)
    val railway = tags["railway"]?.lowercase(Locale.ROOT)
    val leisure = tags["leisure"]?.lowercase(Locale.ROOT)
    val place = tags["place"]?.lowercase(Locale.ROOT)
    val historic = tags["historic"]?.lowercase(Locale.ROOT)
    val manMade = tags["man_made"]?.lowercase(Locale.ROOT)
    val shop = tags["shop"]?.lowercase(Locale.ROOT)
    val fromRefuges = classifyPoiTypeFromRefugesTags(tags = tags, categoryName = categoryName)
    if (fromRefuges != PoiType.GENERIC) return fromRefuges

    if (natural == "peak" || natural == "volcano") return PoiType.PEAK
    if (natural == "waterfall") return PoiType.WATER
    if (natural == "cave_entrance") return PoiType.VIEWPOINT
    if (tourism == "viewpoint") return PoiType.VIEWPOINT
    if (tourism == "camp_site" || tourism == "caravan_site") return PoiType.CAMP
    if (tourism in setOf("alpine_hut", "wilderness_hut", "hut", "hostel", "guest_house")) {
        return PoiType.HUT
    }
    if (tourism in setOf("attraction", "museum", "artwork", "information")) return PoiType.VIEWPOINT
    if (tourism in setOf("hotel", "apartment", "motel")) return PoiType.HUT

    if (amenity in setOf("drinking_water", "water_point", "fountain")) return PoiType.WATER
    if (amenity in setOf("shelter")) return PoiType.HUT
    if (amenity in setOf("toilets")) return PoiType.TOILET
    if (amenity in setOf("restaurant", "cafe", "fast_food", "bar", "pub")) return PoiType.FOOD
    if (amenity in setOf("bicycle_rental", "bicycle_parking", "bicycle_repair_station")) return PoiType.BIKE
    if (amenity in setOf("parking", "parking_space")) return PoiType.PARKING
    if (amenity in setOf("place_of_worship", "townhall")) return PoiType.VIEWPOINT
    if (amenity in setOf("pharmacy", "doctors", "hospital", "clinic", "school", "fuel", "bank", "post_box", "post_office", "marketplace", "car_repair", "community_centre", "defibrillator")) {
        return PoiType.SHOP
    }

    if (highway == "bus_stop" || publicTransport?.contains("stop") == true) return PoiType.TRANSPORT
    if (railway in setOf("station", "halt", "tram_stop", "subway_entrance")) return PoiType.TRANSPORT
    if (shop?.contains("bicycle") == true) return PoiType.BIKE
    if (shop != null) return PoiType.SHOP
    if (leisure in setOf("pitch", "playground", "sports_centre", "park", "picnic_table", "swimming_pool")) {
        return PoiType.VIEWPOINT
    }
    if (historic != null || manMade in setOf("tower", "communications_tower", "water_tower")) {
        return PoiType.VIEWPOINT
    }
    if (place in setOf("hamlet", "village", "suburb", "town", "city")) return PoiType.VIEWPOINT

    val fromCategory = classifyPoiTypeFromCategory(categoryName)
    if (fromCategory != PoiType.GENERIC) return fromCategory

    val normalized = rawData.lowercase(Locale.ROOT)
    return when {
        "drinking_water=" in normalized || "spring" in normalized || "water_well" in normalized -> {
            PoiType.WATER
        }
        "toilets=" in normalized -> PoiType.TOILET
        "bicycle" in normalized -> PoiType.BIKE
        "camp" in normalized -> PoiType.CAMP
        "hut" in normalized || "shelter" in normalized -> PoiType.HUT
        "restaurant" in normalized || "cafe" in normalized || "pub" in normalized || "bar" in normalized -> PoiType.FOOD
        "bus_stop" in normalized || "railway=station" in normalized || "public_transport" in normalized -> PoiType.TRANSPORT
        "place_of_worship" in normalized || "tourism=attraction" in normalized || "historic=" in normalized -> PoiType.VIEWPOINT
        "shop=" in normalized || "amenity=pharmacy" in normalized || "amenity=doctors" in normalized -> PoiType.SHOP
        "parking" in normalized -> PoiType.PARKING
        else -> PoiType.GENERIC
    }
}

internal fun buildPoiPointDetails(
    tags: Map<String, String>,
    categoryName: String,
): PoiPointDetails? {
    if (tags.isEmpty() && categoryName.isBlank()) return null

    val typeLabel = tags["refuges_info:type"]?.trim().orEmpty().takeIf { it.isNotBlank() }
    val elevationMeters = parseIntegerTag(tags["ele"])
    val sleepingPlaces =
        parseIntegerTag(
            tags["refuges_info:places"] ?: tags["capacity"],
        )
    val state = tags["refuges_info:state"]?.trim().orEmpty().takeIf { it.isNotBlank() }
    val shortDescription =
        tags["refuges_info:description"]
            ?.trim()
            .orEmpty()
            .takeIf { it.isNotBlank() }
    val website = tags["website"]?.trim().orEmpty().takeIf { it.isNotBlank() }
    val source = tags["source"]?.trim().orEmpty().takeIf { it.isNotBlank() }

    if (
        typeLabel == null &&
        elevationMeters == null &&
        sleepingPlaces == null &&
        state == null &&
        shortDescription == null &&
        website == null &&
        source == null
    ) {
        return null
    }

    return PoiPointDetails(
        typeLabel = typeLabel ?: categoryName.takeIf { it.isNotBlank() },
        elevationMeters = elevationMeters,
        sleepingPlaces = sleepingPlaces,
        state = state,
        shortDescription = shortDescription,
        website = website,
        source = source,
    )
}

internal fun classifyPoiTypeFromRefugesTags(
    tags: Map<String, String>,
    categoryName: String,
): PoiType {
    val refugesHaystack =
        listOf(
            tags["refuges_info:type"],
            tags["refuges_info:sym"],
            tags["refuges_info:icon"],
            categoryName,
        ).joinToString(" ")

    return classifyPoiTypeFromCategory(refugesHaystack)
}

internal fun classifyPoiTypeFromCategory(categoryName: String): PoiType {
    val normalized =
        categoryName
            .replace('\u00A0', ' ')
            .replace('’', '\'')
            .substringBefore(" / ")
            .lowercase(Locale.ROOT)
            .trim()
    if (normalized.isBlank()) return PoiType.GENERIC
    return when {
        "peak" in normalized || "summit" in normalized || "mountain" in normalized -> PoiType.PEAK
        "drink" in normalized ||
            "water" in normalized ||
            "spring" in normalized ||
            "well" in normalized ||
            "point d'eau" in normalized ||
            "eau" in normalized ||
            "source" in normalized
        -> PoiType.WATER
        "hut" in normalized ||
            "shelter" in normalized ||
            "alpine" in normalized ||
            "hotel" in normalized ||
            "guest" in normalized ||
            "apartment" in normalized ||
            "hostel" in normalized ||
            "refuge" in normalized ||
            "cabane" in normalized ||
            "abri" in normalized ||
            "gite" in normalized ||
            "gîte" in normalized ||
            "chalet" in normalized ||
            "batiment en montagne" in normalized ||
            "bâtiment en montagne" in normalized
        -> PoiType.HUT
        "camp" in normalized -> PoiType.CAMP
        "restaurant" in normalized || "cafe" in normalized || "food" in normalized || "bar" in normalized || "pub" in normalized || "grill" in normalized || "bbq" in normalized -> PoiType.FOOD
        "toilet" in normalized || "wc" in normalized -> PoiType.TOILET
        "bus" in normalized || "transport" in normalized || "station" in normalized || "railway" in normalized -> PoiType.TRANSPORT
        "bicycle" in normalized || "cycle" in normalized -> PoiType.BIKE
        "parking" in normalized -> PoiType.PARKING
        "shop" in normalized ||
            "supermarket" in normalized ||
            "convenience" in normalized ||
            "bakery" in normalized ||
            "pharmacy" in normalized ||
            "doctor" in normalized ||
            "bank" in normalized ||
            "fuel" in normalized ||
            "post office" in normalized ||
            "post box" in normalized ||
            "school" in normalized ||
            "defibrillator" in normalized ||
            "car repair" in normalized ||
            "clothes" in normalized ||
            "sporting goods" in normalized
        -> PoiType.SHOP
        "view" in normalized ||
            "viewpoint" in normalized ||
            "panorama" in normalized ||
            "church" in normalized ||
            "mosque" in normalized ||
            "temple" in normalized ||
            "attraction" in normalized ||
            "memorial" in normalized ||
            "museum" in normalized ||
            "castle" in normalized ||
            "ruin" in normalized ||
            "artwork" in normalized ||
            "cave" in normalized ||
            "grotte" in normalized ||
            "tower" in normalized ||
            ("park" in normalized && "parking" !in normalized) ||
            "hamlet" in normalized ||
            "village" in normalized ||
            "suburb" in normalized ||
            "playground" in normalized ||
            "pitch" in normalized ||
            "sport" in normalized ||
            "tennis" in normalized ||
            "soccer" in normalized ||
            "swimming" in normalized ||
            "climbing" in normalized ||
            "townhall" in normalized ||
            "infooffice" in normalized ||
            "community" in normalized ||
            "bunker" in normalized ||
            "passage" in normalized ||
            "delicat" in normalized ||
            "delicate" in normalized
        -> PoiType.VIEWPOINT
        else -> PoiType.GENERIC
    }
}

internal fun parseTagMap(data: String): Map<String, String> {
    if (data.isBlank()) return emptyMap()
    return data
        .split('\r', '\n')
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && '=' in it }
        .mapNotNull { token ->
            val idx = token.indexOf('=')
            if (idx <= 0 || idx >= token.lastIndex) {
                null
            } else {
                val key = token.substring(0, idx).trim()
                val value = token.substring(idx + 1).trim()
                if (key.isBlank() || value.isBlank()) null else key to value
            }
        }.toMap()
}

internal fun parseIntegerTag(raw: String?): Int? {
    if (raw.isNullOrBlank()) return null
    val trimmed = raw.trim()
    trimmed.toIntOrNull()?.let { return it }
    val match = INTEGER_REGEX.find(trimmed)?.value ?: return null
    return match.toIntOrNull()
}

package com.glancemap.glancemapcompanionapp.refuges

internal data class OsmPoiImportCategory(
    val id: String,
    val label: String,
    val tagValues: Map<String, Set<String>>,
)

internal data class OsmPoiImportCategoryPreset(
    val label: String,
    val categoryIds: Set<String>,
)

private val OSM_POI_IMPORT_CATEGORIES =
    listOf(
        OsmPoiImportCategory(
            id = "huts",
            label = "Huts",
            tagValues =
                mapOf(
                    "tourism" to setOf("alpine_hut", "wilderness_hut", "hut", "hostel", "guest_house"),
                    "amenity" to setOf("shelter"),
                ),
        ),
        OsmPoiImportCategory(
            id = "water",
            label = "Water",
            tagValues =
                mapOf(
                    "amenity" to setOf("drinking_water", "water_point", "fountain"),
                    "natural" to setOf("spring", "waterfall"),
                ),
        ),
        OsmPoiImportCategory(
            id = "toilets",
            label = "Toilets",
            tagValues =
                mapOf(
                    "amenity" to setOf("toilets"),
                ),
        ),
        OsmPoiImportCategory(
            id = "food",
            label = "Food",
            tagValues =
                mapOf(
                    "amenity" to setOf("restaurant", "cafe", "fast_food", "bar", "pub"),
                ),
        ),
        OsmPoiImportCategory(
            id = "parking",
            label = "Parking",
            tagValues =
                mapOf(
                    "amenity" to setOf("parking", "parking_space"),
                ),
        ),
        OsmPoiImportCategory(
            id = "peaks",
            label = "Peaks",
            tagValues =
                mapOf(
                    "natural" to setOf("peak", "volcano"),
                ),
        ),
        OsmPoiImportCategory(
            id = "camping",
            label = "Camping",
            tagValues =
                mapOf(
                    "tourism" to setOf("camp_site", "caravan_site"),
                ),
        ),
        OsmPoiImportCategory(
            id = "viewpoints",
            label = "Viewpoints",
            tagValues =
                mapOf(
                    "tourism" to setOf("viewpoint"),
                ),
        ),
    )

private val DEFAULT_OSM_POI_CATEGORY_IDS =
    linkedSetOf(
        "huts",
        "water",
        "peaks",
    )

internal fun osmPoiImportCategories(): List<OsmPoiImportCategory> = OSM_POI_IMPORT_CATEGORIES

internal fun defaultOsmPoiCategoryIds(): Set<String> = DEFAULT_OSM_POI_CATEGORY_IDS

internal fun normalizeOsmPoiCategoryIds(categoryIds: Set<String>): Set<String> {
    val validIds = OSM_POI_IMPORT_CATEGORIES.map { it.id }.toSet()
    return categoryIds
        .map { it.trim() }
        .filter { it in validIds }
        .toCollection(linkedSetOf())
}

internal fun resolveOsmPoiImportCategories(categoryIds: Set<String>): List<OsmPoiImportCategory> {
    val normalized = normalizeOsmPoiCategoryIds(categoryIds)
    return OSM_POI_IMPORT_CATEGORIES.filter { it.id in normalized }
}

internal fun buildOsmPoiCategoryPresets(allCategoryIds: Set<String>): List<OsmPoiImportCategoryPreset> {
    val essentials = allCategoryIds.intersect(defaultOsmPoiCategoryIds())
    return listOf(
        OsmPoiImportCategoryPreset(
            label = "Essentials",
            categoryIds = essentials.ifEmpty { allCategoryIds },
        ),
        OsmPoiImportCategoryPreset(
            label = "All",
            categoryIds = allCategoryIds,
        ),
    )
}

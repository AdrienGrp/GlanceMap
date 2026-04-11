package com.glancemap.glancemapwearos.data.repository

import java.util.Locale

private val LEADING_CATEGORY_CODE_REGEX = Regex("^\\d{3}:\\s*")

internal fun expandCategoryIdsWithAliasMap(
    categoryIds: Set<Int>,
    aliasMap: Map<Int, Set<Int>>
): Set<Int> {
    if (categoryIds.isEmpty()) return emptySet()
    if (aliasMap.isEmpty()) return categoryIds
    return buildSet {
        categoryIds.forEach { id ->
            val expanded = aliasMap[id]
            if (expanded.isNullOrEmpty()) add(id) else addAll(expanded)
        }
    }
}

internal fun keepCategoriesWithPoiData(
    raw: List<RawPoiCategory>,
    usedCategoryIds: Set<Int>
): List<RawPoiCategory> {
    if (raw.isEmpty()) return emptyList()
    if (usedCategoryIds.isEmpty()) return emptyList()

    val byId = raw.associateBy { it.id }
    val directKeepIds = usedCategoryIds.toSet()

    fun findNearestRetainedParent(startParentId: Int?): Int? {
        var currentId = startParentId
        val guard = mutableSetOf<Int>()
        while (currentId != null && guard.add(currentId)) {
            if (currentId in directKeepIds) return currentId
            currentId = byId[currentId]?.parent
        }
        return null
    }

    return raw.asSequence()
        .filter { it.id in directKeepIds }
        .map { category ->
            category.copy(parent = findNearestRetainedParent(category.parent))
        }
        .toList()
}

internal fun collapseDuplicateRetainedCategories(
    retained: List<RawPoiCategory>
): Pair<List<RawPoiCategory>, Map<Int, Set<Int>>> {
    if (retained.isEmpty()) return emptyList<RawPoiCategory>() to emptyMap()

    val grouped = retained.groupBy { category ->
        canonicalCategoryKey(category.name)
    }
    val aliasById = mutableMapOf<Int, Set<Int>>()
    val canonicalById = mutableMapOf<Int, Int>()

    grouped.values.forEach { group ->
        val ids = group.map { it.id }.toSet()
        val canonicalId = group.minOf { it.id }
        group.forEach { item ->
            aliasById[item.id] = ids
            canonicalById[item.id] = canonicalId
        }
    }

    fun resolveCanonical(id: Int?): Int? {
        var current = id ?: return null
        val guard = mutableSetOf<Int>()
        while (guard.add(current)) {
            val next = canonicalById[current] ?: return current
            if (next == current) return current
            current = next
        }
        return current
    }

    val collapsed = grouped.values.mapNotNull { group ->
        val canonical = group.minByOrNull { it.id } ?: return@mapNotNull null
        val parentCandidates = group.asSequence()
            .mapNotNull { item -> resolveCanonical(item.parent) }
            .filter { it != canonical.id }
            .toSet()
        val resolvedParent = if (parentCandidates.size == 1) {
            parentCandidates.first()
        } else {
            null
        }
        canonical.copy(
            name = canonicalCategoryName(canonical.name),
            parent = resolvedParent
        )
    }

    return collapsed to aliasById
}

internal fun applySyntheticTopLevelGrouping(
    categories: List<RawPoiCategory>
): Pair<List<RawPoiCategory>, Map<Int, Set<Int>>> {
    if (categories.isEmpty()) return emptyList<RawPoiCategory>() to emptyMap()

    val topLevel = categories.filter { it.parent == null }
    if (topLevel.isEmpty()) return categories to emptyMap()

    val groupedTopLevel = topLevel.groupBy { category ->
        topGroupForCategoryName(category.name)
    }
    val groupsToCreate = groupedTopLevel
        .filter { (group, members) ->
            val normalizedMemberKeys = members.map { canonicalCategoryKey(it.name) }.toSet()
            !(normalizedMemberKeys.size == 1 && normalizedMemberKeys.first() == group.label.lowercase(Locale.ROOT))
        }
        .keys
    if (groupsToCreate.isEmpty()) return categories to emptyMap()

    val childrenByParent = categories.groupBy { it.parent }
    val groupedTopLevelIds = groupedTopLevel
        .filterKeys { it in groupsToCreate }
        .values
        .flatten()
        .map { it.id }
        .toSet()

    val remappedCategories = categories.map { category ->
        if (category.id in groupedTopLevelIds && category.parent == null) {
            category.copy(parent = topGroupForCategoryName(category.name).syntheticId)
        } else {
            category
        }
    }

    val syntheticGroups = groupsToCreate.map { group ->
        RawPoiCategory(
            id = group.syntheticId,
            name = group.label,
            parent = null
        )
    }

    val syntheticAliases = groupsToCreate.associate { group ->
        val memberIds = groupedTopLevel[group].orEmpty().map { it.id }
        val subtree = memberIds.flatMapTo(mutableSetOf()) { rootId ->
            collectSubtreeIds(
                rootId = rootId,
                childrenByParent = childrenByParent
            )
        }
        group.syntheticId to subtree
    }

    return (remappedCategories + syntheticGroups) to syntheticAliases
}

internal fun buildCategorySortWeights(
    categories: List<RawPoiCategory>,
    directPointCountsByCategoryId: Map<Int, Int>,
    aliasMap: Map<Int, Set<Int>>
): Map<Int, Int> {
    if (categories.isEmpty()) return emptyMap()
    val childrenByParent = categories.groupBy { it.parent }
    val memo = mutableMapOf<Int, Int>()

    fun directCountFor(categoryId: Int): Int {
        if (categoryId < 0) return 0
        val aliases = aliasMap[categoryId].orEmpty()
        if (aliases.isEmpty()) return directPointCountsByCategoryId[categoryId] ?: 0
        return aliases.sumOf { aliasId -> directPointCountsByCategoryId[aliasId] ?: 0 }
    }

    fun subtreeCountFor(categoryId: Int): Int {
        memo[categoryId]?.let { return it }
        var total = directCountFor(categoryId)
        childrenByParent[categoryId].orEmpty().forEach { child ->
            total += subtreeCountFor(child.id)
        }
        memo[categoryId] = total
        return total
    }

    categories.forEach { category ->
        subtreeCountFor(category.id)
    }
    return memo
}

internal fun collectSubtreeIds(
    rootId: Int,
    childrenByParent: Map<Int?, List<RawPoiCategory>>
): Set<Int> {
    val result = mutableSetOf<Int>()
    fun walk(currentId: Int) {
        if (!result.add(currentId)) return
        childrenByParent[currentId].orEmpty().forEach { child ->
            walk(child.id)
        }
    }
    walk(rootId)
    return result
}

internal fun topGroupForCategoryName(categoryName: String): SyntheticTopGroup {
    val normalized = canonicalCategoryName(categoryName)
        .replace('’', '\'')
        .lowercase(Locale.ROOT)
        .trim()

    if (normalized.isBlank()) return SyntheticTopGroup.SERVICES

    val isSportsLeisure = normalized.contains("sport") ||
        normalized.contains("stadium") ||
        normalized.contains("pitch") ||
        normalized.contains("tennis") ||
        normalized.contains("golf") ||
        normalized.contains("rugby") ||
        normalized.contains("bmx") ||
        normalized.contains("kart") ||
        normalized.contains("playground") ||
        normalized.contains("swimming") ||
        normalized.contains("ski") ||
        normalized.contains("climbing") ||
        normalized.contains("summer toboggan") ||
        normalized.contains("theme park") ||
        normalized.contains("water park")

    if (isSportsLeisure) return SyntheticTopGroup.SPORTS_LEISURE

    return when (classifyPoiTypeFromCategory(normalized)) {
        PoiType.WATER -> SyntheticTopGroup.WATER
        PoiType.HUT, PoiType.CAMP -> SyntheticTopGroup.SHELTER
        PoiType.TRANSPORT, PoiType.BIKE, PoiType.PARKING -> SyntheticTopGroup.TRANSPORT
        PoiType.SHOP, PoiType.FOOD, PoiType.TOILET -> SyntheticTopGroup.SERVICES
        PoiType.PEAK, PoiType.VIEWPOINT -> SyntheticTopGroup.LANDMARKS
        PoiType.GENERIC, PoiType.CUSTOM -> SyntheticTopGroup.SERVICES
    }
}

internal enum class SyntheticTopGroup(
    val syntheticId: Int,
    val label: String
) {
    WATER(Int.MIN_VALUE + 101, "Water"),
    SHELTER(Int.MIN_VALUE + 102, "Shelter"),
    TRANSPORT(Int.MIN_VALUE + 103, "Transport"),
    SERVICES(Int.MIN_VALUE + 104, "Services"),
    LANDMARKS(Int.MIN_VALUE + 105, "Landmarks"),
    SPORTS_LEISURE(Int.MIN_VALUE + 106, "Sports/Leisure")
}

internal fun buildCategoryTree(
    raw: List<RawPoiCategory>,
    sortWeightByCategoryId: Map<Int, Int>
): List<PoiCategory> {
    if (raw.isEmpty()) return emptyList()

    val childrenByParent = raw.groupBy { it.parent }
    val byId = raw.associateBy { it.id }
    val visited = mutableSetOf<Int>()
    val output = mutableListOf<PoiCategory>()

    fun walk(parentId: Int?, depth: Int) {
        val children = childrenByParent[parentId]
            .orEmpty()
            .sortedWith(
                compareBy<RawPoiCategory> { category ->
                    if (childrenByParent[category.id].orEmpty().isNotEmpty()) 0 else 1
                }.thenByDescending { category ->
                    sortWeightByCategoryId[category.id] ?: 0
                }.thenBy { canonicalCategoryKey(it.name) }
            )
        children.forEach { category ->
            if (!visited.add(category.id)) return@forEach
            val normalized = canonicalCategoryName(category.name)
            val isSyntheticRoot = normalized.equals("root", ignoreCase = true)
            if (isSyntheticRoot) {
                walk(category.id, depth)
            } else {
                output += PoiCategory(
                    id = category.id,
                    name = normalized,
                    parentId = category.parent,
                    depth = depth,
                    hasChildren = childrenByParent[category.id].orEmpty().isNotEmpty()
                )
                walk(category.id, depth + 1)
            }
        }
    }

    walk(parentId = null, depth = 0)

    if (output.size < raw.size) {
        val linked = output.map { it.id }.toSet()
        raw.asSequence()
            .filter { category ->
                category.id !in linked &&
                    !canonicalCategoryName(category.name).equals("root", ignoreCase = true)
            }
            .sortedWith(
                compareBy<RawPoiCategory> { category ->
                    if (childrenByParent[category.id].orEmpty().isNotEmpty()) 0 else 1
                }.thenByDescending { category ->
                    sortWeightByCategoryId[category.id] ?: 0
                }.thenBy { canonicalCategoryKey(it.name) }
            )
            .forEach { category ->
                val depth = resolveDepth(category.id, byId)
                output += PoiCategory(
                    id = category.id,
                    name = canonicalCategoryName(category.name),
                    parentId = category.parent,
                    depth = depth,
                    hasChildren = childrenByParent[category.id].orEmpty().isNotEmpty()
                )
            }
    }

    return output
}

internal fun normalizeCategoryName(raw: String): String {
    val cleaned = raw.replace('\u00A0', ' ').trim()
    val withoutCode = cleaned.replace(LEADING_CATEGORY_CODE_REGEX, "")
    val primary = withoutCode.substringBefore(" / ").trim()
    return if (primary.isNotBlank()) primary else withoutCode
}

internal fun canonicalCategoryName(raw: String): String {
    val normalized = normalizeCategoryName(raw)
        .replace('’', '\'')
        .replace(Regex("\\s+"), " ")
        .trim()
    if (normalized.isBlank()) return normalized

    return when (normalized.lowercase(Locale.ROOT)) {
        "alpine hut", "alpine huts" -> "Alpine Huts"
        "camp site", "camp sites", "camping site", "camping sites", "camping" -> "Camping"
        "peak", "peaks" -> "Peaks"
        "viewpoint", "viewpoints" -> "Viewpoints"
        "toilet", "toilets" -> "Toilets"
        "food & drink", "food and drink" -> "Food"
        "parking area", "parking areas" -> "Parking"
        else -> normalized
    }
}

internal fun canonicalCategoryKey(raw: String): String {
    return canonicalCategoryName(raw).lowercase(Locale.ROOT)
}

internal fun resolveDepth(id: Int, byId: Map<Int, RawPoiCategory>): Int {
    var depth = 0
    var current: RawPoiCategory? = byId[id]
    val guard = mutableSetOf<Int>()
    while (true) {
        val parentId = current?.parent ?: break
        if (!guard.add(parentId)) break
        val parent = byId[parentId] ?: break
        val normalized = normalizeCategoryName(parent.name)
        if (!normalized.equals("root", ignoreCase = true)) {
            depth += 1
        }
        current = parent
    }
    return depth
}

internal data class RawPoiCategory(
    val id: Int,
    val name: String,
    val parent: Int?
)

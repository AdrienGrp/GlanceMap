package com.glancemap.glancemapwearos.presentation.features.poi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glancemap.glancemapwearos.data.repository.PoiCategory
import com.glancemap.glancemapwearos.data.repository.PoiPointDetails
import com.glancemap.glancemapwearos.data.repository.PoiRepository
import com.glancemap.glancemapwearos.data.repository.PoiType
import com.glancemap.glancemapwearos.data.repository.PoiViewport
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.data.repository.USER_POI_CATEGORY_ID
import com.glancemap.glancemapwearos.data.repository.USER_POI_CATEGORY_NAME
import com.glancemap.glancemapwearos.data.repository.USER_POI_SOURCE_NAME
import com.glancemap.glancemapwearos.data.repository.USER_POI_SOURCE_PATH
import com.glancemap.glancemapwearos.data.repository.UserPoiRecord
import com.glancemap.glancemapwearos.data.repository.UserPoiRepository
import com.glancemap.glancemapwearos.data.repository.UserPoiSourceState
import com.glancemap.glancemapwearos.presentation.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Collator
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class PoiCategoryUiState(
    val id: Int,
    val name: String,
    val parentId: Int?,
    val depth: Int,
    val hasChildren: Boolean,
    val enabled: Boolean,
)

data class PoiFileUiState(
    val name: String,
    val path: String,
    val isEnabled: Boolean,
    val isExpanded: Boolean,
    val categories: List<PoiCategoryUiState>,
    val enabledPoiCount: Int,
    val totalPoiCount: Int,
)

data class PoiCategoryPreviewKey(
    val filePath: String,
    val categoryId: Int,
)

data class PoiCategoryPreviewPointUiState(
    val id: Long,
    val name: String,
    val type: PoiType,
    val lat: Double,
    val lon: Double,
    val details: PoiPointDetails? = null,
)

data class PoiCategoryPreviewUiState(
    val isLoading: Boolean = false,
    val isLoaded: Boolean = false,
    val totalPoiCount: Int = 0,
    val points: List<PoiCategoryPreviewPointUiState> = emptyList(),
    val errorMessage: String? = null,
) {
    val hasMore: Boolean get() = totalPoiCount > points.size
}

data class PoiCategoryCountUiState(
    val isLoading: Boolean = false,
    val isLoaded: Boolean = false,
    val enabledPoiCount: Int = 0,
    val totalPoiCount: Int = 0,
    val errorMessage: String? = null,
)

data class PoiOverlaySource(
    val filePath: String,
    val fileName: String,
    val enabledCategoryIds: Set<Int>,
)

data class PoiOverlayMarker(
    val key: String,
    val lat: Double,
    val lon: Double,
    val label: String?,
    val type: PoiType,
    val details: PoiPointDetails? = null,
)

data class PoiNavigateTarget(
    val lat: Double,
    val lon: Double,
    val label: String?,
    val type: PoiType,
    val details: PoiPointDetails? = null,
)

data class PoiSearchResultUiState(
    val id: Long,
    val filePath: String,
    val fileName: String,
    val name: String,
    val type: PoiType,
    val lat: Double,
    val lon: Double,
    val details: PoiPointDetails? = null,
)

data class PoiSearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<PoiSearchResultUiState> = emptyList(),
    val errorMessage: String? = null,
)

class PoiViewModel(
    private val poiRepository: PoiRepository,
    private val userPoiRepository: UserPoiRepository,
    private val settingsRepository: SettingsRepository,
    private val syncManager: SyncManager,
) : ViewModel() {
    private companion object {
        private const val CATEGORY_PREVIEW_LIMIT = 200
        private const val SEARCH_RESULT_LIMIT = 20
    }

    private var userPoiSourceState =
        UserPoiSourceState(
            fileEnabled = false,
            categoryEnabled = false,
            points = emptyList(),
        )

    private val _poiFiles = MutableStateFlow<List<PoiFileUiState>>(emptyList())
    val poiFiles: StateFlow<List<PoiFileUiState>> = _poiFiles.asStateFlow()

    private val _categoryPreviews =
        MutableStateFlow<Map<PoiCategoryPreviewKey, PoiCategoryPreviewUiState>>(emptyMap())
    val categoryPreviews: StateFlow<Map<PoiCategoryPreviewKey, PoiCategoryPreviewUiState>> =
        _categoryPreviews.asStateFlow()

    private val _categoryCounts =
        MutableStateFlow<Map<PoiCategoryPreviewKey, PoiCategoryCountUiState>>(emptyMap())
    val categoryCounts: StateFlow<Map<PoiCategoryPreviewKey, PoiCategoryCountUiState>> =
        _categoryCounts.asStateFlow()

    private val _navigateTarget = MutableStateFlow<PoiNavigateTarget?>(null)
    val navigateTarget: StateFlow<PoiNavigateTarget?> = _navigateTarget.asStateFlow()

    private val _offlineSearchUiState = MutableStateFlow(PoiSearchUiState())
    val offlineSearchUiState: StateFlow<PoiSearchUiState> = _offlineSearchUiState.asStateFlow()
    private var poiSearchJob: Job? = null

    private val selectedMapPath: StateFlow<String?> =
        settingsRepository.selectedMapPath
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val poiTapToCenterEnabled: StateFlow<Boolean> =
        settingsRepository.poiTapToCenterEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val activeOverlaySources: StateFlow<List<PoiOverlaySource>> =
        combine(
            _poiFiles,
            selectedMapPath,
        ) { files, mapPath ->
            resolveActiveOverlaySources(files, mapPath)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            reloadFromDisk()
        }

        syncManager.poiSyncRequest
            .onEach {
                reloadFromDisk()
            }.launchIn(viewModelScope)
    }

    fun loadPoiFiles() {
        viewModelScope.launch {
            reloadFromDisk()
        }
    }

    fun toggleExpanded(path: String) {
        _poiFiles.value =
            _poiFiles.value.map { file ->
                if (file.path == path) file.copy(isExpanded = !file.isExpanded) else file
            }
    }

    fun setFileEnabled(
        path: String,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            if (isUserPoiPath(path)) {
                withContext(Dispatchers.IO) {
                    userPoiRepository.setFileEnabled(enabled)
                }
                refreshUserPoiSourceFromDisk()
                return@launch
            }
            val file = _poiFiles.value.firstOrNull { it.path == path } ?: return@launch
            val enabledIds =
                if (enabled) {
                    file.categories.map { it.id }.toSet()
                } else {
                    emptySet()
                }

            poiRepository.setFileEnabled(path, enabled)
            poiRepository.setEnabledCategories(path, enabledIds)
            _poiFiles.update { files ->
                files.map { current ->
                    if (current.path == path) {
                        current.copy(
                            isEnabled = enabled,
                            categories =
                                current.categories.map { category ->
                                    category.copy(enabled = enabled)
                                },
                        )
                    } else {
                        current
                    }
                }
            }
            refreshPoiCounts(path)
            clearCategoryCounts(path)
        }
    }

    fun setAllCategories(
        path: String,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            if (isUserPoiPath(path)) {
                withContext(Dispatchers.IO) {
                    userPoiRepository.setCategoryEnabled(enabled)
                }
                refreshUserPoiSourceFromDisk()
                return@launch
            }
            val file = _poiFiles.value.firstOrNull { it.path == path } ?: return@launch
            val enabledIds = if (enabled) file.categories.map { it.id }.toSet() else emptySet()
            poiRepository.setEnabledCategories(path, enabledIds)
            _poiFiles.update { files ->
                files.map { item ->
                    if (item.path == path) {
                        item.copy(categories = item.categories.map { it.copy(enabled = enabled) })
                    } else {
                        item
                    }
                }
            }
            refreshPoiCounts(path)
            clearCategoryCounts(path)
        }
    }

    fun setCategoryEnabled(
        path: String,
        categoryId: Int,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            if (isUserPoiPath(path) && categoryId == USER_POI_CATEGORY_ID) {
                withContext(Dispatchers.IO) {
                    userPoiRepository.setCategoryEnabled(enabled)
                }
                refreshUserPoiSourceFromDisk()
                return@launch
            }
            val file = _poiFiles.value.firstOrNull { it.path == path } ?: return@launch
            val descendants = descendantCategoryIds(file, categoryId)
            val ancestors = ancestorCategoryIds(file, categoryId)
            val currentEnabled =
                file.categories
                    .asSequence()
                    .filter { it.enabled }
                    .map { it.id }
                    .toMutableSet()

            if (enabled) {
                currentEnabled.add(categoryId)
                currentEnabled.addAll(descendants)
                currentEnabled.addAll(ancestors)
            } else {
                currentEnabled.remove(categoryId)
                descendants.forEach { currentEnabled.remove(it) }
            }

            poiRepository.setEnabledCategories(path, currentEnabled)

            _poiFiles.update { files ->
                files.map { item ->
                    if (item.path == path) {
                        item.copy(
                            categories =
                                item.categories.map { category ->
                                    category.copy(enabled = category.id in currentEnabled)
                                },
                        )
                    } else {
                        item
                    }
                }
            }
            refreshPoiCounts(path)
            clearCategoryCounts(path)
        }
    }

    fun loadCategoryPreview(
        path: String,
        categoryId: Int,
        forceRefresh: Boolean = false,
    ) {
        viewModelScope.launch {
            val file = _poiFiles.value.firstOrNull { it.path == path } ?: return@launch
            if (file.categories.none { it.id == categoryId }) return@launch

            val key = PoiCategoryPreviewKey(filePath = path, categoryId = categoryId)
            val current = _categoryPreviews.value[key]
            if (!forceRefresh && current != null && (current.isLoading || current.isLoaded)) {
                return@launch
            }

            _categoryPreviews.update { previews ->
                previews + (key to PoiCategoryPreviewUiState(isLoading = true))
            }

            val loaded =
                runCatching {
                    if (isUserPoiPath(path) && categoryId == USER_POI_CATEGORY_ID) {
                        val points = userPoiSourceState.points.take(CATEGORY_PREVIEW_LIMIT)
                        return@runCatching PoiCategoryPreviewUiState(
                            isLoading = false,
                            isLoaded = true,
                            totalPoiCount = userPoiSourceState.points.size,
                            points = points.map { point -> point.toPreviewUiState() },
                        )
                    }
                    val categoryIds = setOf(categoryId)
                    val totalPoiCount = poiRepository.countPoiPoints(path, categoryIds)
                    val points =
                        if (totalPoiCount > 0) {
                            poiRepository.queryPoiPointsByCategories(
                                path = path,
                                categoryIds = categoryIds,
                                limit = CATEGORY_PREVIEW_LIMIT,
                            )
                        } else {
                            emptyList()
                        }

                    PoiCategoryPreviewUiState(
                        isLoading = false,
                        isLoaded = true,
                        totalPoiCount = totalPoiCount,
                        points =
                            points.map { point ->
                                PoiCategoryPreviewPointUiState(
                                    id = point.id,
                                    name = point.name?.takeIf { it.isNotBlank() } ?: "Unnamed POI",
                                    type = point.type,
                                    lat = point.lat,
                                    lon = point.lon,
                                    details = point.details,
                                )
                            },
                    )
                }.getOrElse {
                    PoiCategoryPreviewUiState(
                        isLoading = false,
                        isLoaded = true,
                        errorMessage = "Unable to load POI.",
                    )
                }

            _categoryPreviews.update { previews ->
                previews + (key to loaded)
            }
        }
    }

    fun loadCategoryCount(
        path: String,
        categoryId: Int,
        forceRefresh: Boolean = false,
    ) {
        viewModelScope.launch {
            val file = _poiFiles.value.firstOrNull { it.path == path } ?: return@launch
            if (file.categories.none { it.id == categoryId }) return@launch

            val key = PoiCategoryPreviewKey(filePath = path, categoryId = categoryId)
            val current = _categoryCounts.value[key]
            if (!forceRefresh && current != null && (current.isLoading || current.isLoaded)) {
                return@launch
            }

            _categoryCounts.update { counts ->
                counts + (key to PoiCategoryCountUiState(isLoading = true))
            }

            val loaded =
                runCatching {
                    if (isUserPoiPath(path) && categoryId == USER_POI_CATEGORY_ID) {
                        val totalPoiCount = userPoiSourceState.points.size
                        val enabledPoiCount =
                            if (file.isEnabled && file.categories.any { it.enabled }) {
                                totalPoiCount
                            } else {
                                0
                            }
                        return@runCatching PoiCategoryCountUiState(
                            isLoading = false,
                            isLoaded = true,
                            enabledPoiCount = enabledPoiCount,
                            totalPoiCount = totalPoiCount,
                        )
                    }
                    val scopedCategoryIds =
                        buildSet {
                            add(categoryId)
                            addAll(descendantCategoryIds(file, categoryId))
                        }
                    val totalPoiCount = poiRepository.countPoiPoints(path, scopedCategoryIds)
                    val enabledIds =
                        file.categories
                            .asSequence()
                            .filter { it.enabled }
                            .map { it.id }
                            .toSet()
                    val enabledScopedCategoryIds =
                        scopedCategoryIds.filterTo(mutableSetOf()) {
                            it in enabledIds
                        }
                    val enabledPoiCount =
                        when {
                            enabledScopedCategoryIds.isEmpty() -> 0
                            enabledScopedCategoryIds == scopedCategoryIds -> totalPoiCount
                            else -> poiRepository.countPoiPoints(path, enabledScopedCategoryIds)
                        }

                    PoiCategoryCountUiState(
                        isLoading = false,
                        isLoaded = true,
                        enabledPoiCount = enabledPoiCount,
                        totalPoiCount = totalPoiCount,
                    )
                }.getOrElse {
                    PoiCategoryCountUiState(
                        isLoading = false,
                        isLoaded = true,
                        errorMessage = "Unable to load counts.",
                    )
                }

            _categoryCounts.update { counts ->
                counts + (key to loaded)
            }
        }
    }

    fun deletePoiFile(path: String) {
        viewModelScope.launch {
            if (isUserPoiPath(path)) {
                withContext(Dispatchers.IO) {
                    userPoiRepository.clearAll()
                }
                _categoryPreviews.update { previews ->
                    previews.filterKeys { key -> key.filePath != path }
                }
                _categoryCounts.update { counts ->
                    counts.filterKeys { key -> key.filePath != path }
                }
                refreshUserPoiSourceFromDisk()
                return@launch
            }
            poiRepository.deletePoiFile(path)
            _categoryPreviews.update { previews ->
                previews.filterKeys { key -> key.filePath != path }
            }
            _categoryCounts.update { counts ->
                counts.filterKeys { key -> key.filePath != path }
            }
            reloadFromDisk()
        }
    }

    fun requestNavigateToPoi(point: PoiCategoryPreviewPointUiState) {
        _navigateTarget.value =
            PoiNavigateTarget(
                lat = point.lat,
                lon = point.lon,
                label = point.name,
                type = point.type,
                details = point.details,
            )
    }

    suspend fun renameMyCreationPoi(
        id: Long,
        newName: String,
    ) {
        withContext(Dispatchers.IO) {
            userPoiRepository.renamePoi(id = id, newName = newName)
        }
        refreshUserPoiSourceFromDisk()
    }

    suspend fun deleteMyCreationPoi(id: Long) {
        withContext(Dispatchers.IO) {
            userPoiRepository.deletePoi(id)
        }
        refreshUserPoiSourceFromDisk()
    }

    fun consumeNavigateTarget() {
        _navigateTarget.value = null
    }

    fun clearOfflinePoiSearch() {
        poiSearchJob?.cancel()
        _offlineSearchUiState.value = PoiSearchUiState()
    }

    fun searchOfflinePoi(query: String) {
        val normalizedQuery = query.trim()
        poiSearchJob?.cancel()

        if (normalizedQuery.isBlank()) {
            _offlineSearchUiState.value = PoiSearchUiState()
            return
        }

        if (normalizedQuery.length < 2) {
            _offlineSearchUiState.value =
                PoiSearchUiState(
                    query = normalizedQuery,
                    errorMessage = "Type at least 2 letters.",
                )
            return
        }

        val sources = activeOverlaySources.value
        if (sources.isEmpty()) {
            _offlineSearchUiState.value =
                PoiSearchUiState(
                    query = normalizedQuery,
                    errorMessage = "Enable a POI source first.",
                )
            return
        }

        _offlineSearchUiState.value =
            PoiSearchUiState(
                query = normalizedQuery,
                isLoading = true,
            )

        poiSearchJob =
            viewModelScope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        val perFileLimit = max(10, SEARCH_RESULT_LIMIT)
                        val aggregated = mutableListOf<PoiSearchResultUiState>()

                        sources.forEach { source ->
                            if (isUserPoiPath(source.filePath)) {
                                searchUserPoiPoints(normalizedQuery, perFileLimit).forEach { point ->
                                    aggregated += point
                                }
                            } else {
                                poiRepository
                                    .searchPoiPoints(
                                        path = source.filePath,
                                        query = normalizedQuery,
                                        enabledCategoryIds = source.enabledCategoryIds,
                                        limit = perFileLimit,
                                    ).forEach { point ->
                                        val name = point.name?.takeIf { it.isNotBlank() } ?: return@forEach
                                        aggregated +=
                                            PoiSearchResultUiState(
                                                id = point.id,
                                                filePath = source.filePath,
                                                fileName = source.fileName,
                                                name = name,
                                                type = point.type,
                                                lat = point.lat,
                                                lon = point.lon,
                                                details = point.details,
                                            )
                                    }
                            }
                        }

                        val queryLower = normalizedQuery.lowercase(Locale.ROOT)
                        val collator =
                            Collator.getInstance(Locale.getDefault()).apply {
                                strength = Collator.PRIMARY
                            }
                        aggregated
                            .distinctBy { "${it.filePath}#${it.id}" }
                            .sortedWith(
                                compareBy<PoiSearchResultUiState>(
                                    { poiSearchMatchRank(it.name, queryLower) },
                                    { it.fileName.lowercase(Locale.ROOT) },
                                ).thenComparator { left, right ->
                                    collator.compare(left.name, right.name)
                                },
                            ).take(SEARCH_RESULT_LIMIT)
                    }

                _offlineSearchUiState.value =
                    if (result.isEmpty()) {
                        PoiSearchUiState(
                            query = normalizedQuery,
                            isLoading = false,
                            errorMessage = "No offline POI found.",
                        )
                    } else {
                        PoiSearchUiState(
                            query = normalizedQuery,
                            isLoading = false,
                            results = result,
                        )
                    }
            }
    }

    suspend fun queryVisibleMarkers(
        viewport: PoiViewport,
        zoomLevel: Int,
    ): List<PoiOverlayMarker> =
        withContext(Dispatchers.IO) {
            if (zoomLevel < 10) return@withContext emptyList()
            val sources = activeOverlaySources.value
            if (sources.isEmpty()) return@withContext emptyList()

            val maxTotal =
                when {
                    zoomLevel >= 16 -> 220
                    zoomLevel >= 14 -> 170
                    else -> 130
                }
            val perFileLimit = max(40, maxTotal / sources.size)
            val markers = ArrayList<PoiOverlayMarker>(maxTotal)

            for (source in sources) {
                val remaining = maxTotal - markers.size
                if (remaining <= 0) break
                val fileLimit = min(perFileLimit, remaining)
                val points =
                    if (isUserPoiPath(source.filePath)) {
                        queryUserPoiPoints(
                            viewport = viewport,
                            limit = fileLimit,
                        )
                    } else {
                        poiRepository.queryPoiPoints(
                            path = source.filePath,
                            viewport = viewport,
                            enabledCategoryIds = source.enabledCategoryIds,
                            limit = fileLimit,
                        )
                    }

                points.forEach { point ->
                    markers +=
                        PoiOverlayMarker(
                            key = "${source.filePath}#${point.id}",
                            lat = point.lat,
                            lon = point.lon,
                            label = point.name,
                            type = point.type,
                            details = point.details,
                        )
                }
            }

            markers
        }

    private suspend fun reloadFromDisk() {
        val previousExpanded = _poiFiles.value.associate { it.path to it.isExpanded }
        val files = poiRepository.listPoiFiles()
        userPoiSourceState = userPoiRepository.readSourceState()

        val importedFiles =
            withContext(Dispatchers.IO) {
                files.map { file ->
                    val categories = poiRepository.readCategories(file.absolutePath)
                    val categoryIds = categories.map { it.id }.toSet()
                    val enabledIds = poiRepository.getEnabledCategories(file.absolutePath, categoryIds)
                    val isEnabled = poiRepository.isFileEnabled(file.absolutePath)
                    val (enabledPoiCount, totalPoiCount) =
                        computePoiCounts(
                            path = file.absolutePath,
                            allCategoryIds = categoryIds,
                            enabledCategoryIds = enabledIds,
                        )

                    PoiFileUiState(
                        name = file.nameWithoutExtension,
                        path = file.absolutePath,
                        isEnabled = isEnabled,
                        isExpanded = previousExpanded[file.absolutePath] ?: false,
                        categories =
                            categories.map { category ->
                                category.toUiState(enabled = category.id in enabledIds)
                            },
                        enabledPoiCount = enabledPoiCount,
                        totalPoiCount = totalPoiCount,
                    )
                }
            }

        val syntheticUserFile =
            buildUserPoiFileUiState(
                isExpanded = previousExpanded[USER_POI_SOURCE_PATH] ?: userPoiSourceState.points.isNotEmpty(),
            )

        _poiFiles.value = listOf(syntheticUserFile) + importedFiles
        _categoryPreviews.value = emptyMap()
        _categoryCounts.value = emptyMap()
    }

    private suspend fun refreshPoiCounts(path: String) {
        val file = _poiFiles.value.firstOrNull { it.path == path } ?: return
        val allCategoryIds = file.categories.map { it.id }.toSet()
        val enabledCategoryIds =
            file.categories
                .asSequence()
                .filter { it.enabled }
                .map { it.id }
                .toSet()
        val (enabledPoiCount, totalPoiCount) =
            computePoiCounts(
                path = path,
                allCategoryIds = allCategoryIds,
                enabledCategoryIds = enabledCategoryIds,
            )

        _poiFiles.update { files ->
            files.map { current ->
                if (current.path == path) {
                    current.copy(
                        enabledPoiCount = enabledPoiCount,
                        totalPoiCount = totalPoiCount,
                    )
                } else {
                    current
                }
            }
        }
    }

    private suspend fun computePoiCounts(
        path: String,
        allCategoryIds: Set<Int>,
        enabledCategoryIds: Set<Int>,
    ): Pair<Int, Int> {
        if (isUserPoiPath(path)) {
            val totalPoiCount = userPoiSourceState.points.size
            val enabledPoiCount = if (enabledCategoryIds.isEmpty()) 0 else totalPoiCount
            return enabledPoiCount to totalPoiCount
        }
        if (allCategoryIds.isEmpty()) return 0 to 0
        val totalPoiCount = poiRepository.countPoiPoints(path, allCategoryIds)
        val enabledPoiCount =
            when {
                enabledCategoryIds.isEmpty() -> 0
                enabledCategoryIds == allCategoryIds -> totalPoiCount
                else -> poiRepository.countPoiPoints(path, enabledCategoryIds)
            }
        return enabledPoiCount to totalPoiCount
    }

    private fun clearCategoryCounts(path: String) {
        _categoryCounts.update { counts ->
            counts.filterKeys { key -> key.filePath != path }
        }
    }

    private fun resolveActiveOverlaySources(
        files: List<PoiFileUiState>,
        selectedMapPath: String?,
    ): List<PoiOverlaySource> {
        val enabledFiles =
            files
                .asSequence()
                .filter { it.isEnabled }
                .mapNotNull { file ->
                    val enabledIds =
                        file.categories
                            .asSequence()
                            .filter { it.enabled }
                            .map { it.id }
                            .toSet()
                    if (enabledIds.isEmpty()) {
                        null
                    } else {
                        PoiOverlaySource(
                            filePath = file.path,
                            fileName = file.name,
                            enabledCategoryIds = enabledIds,
                        )
                    }
                }.toList()

        if (enabledFiles.isEmpty()) return emptyList()

        val alwaysIncluded = enabledFiles.filter { isUserPoiPath(it.filePath) }
        val mapScoped = enabledFiles.filterNot { isUserPoiPath(it.filePath) }

        val mapBase =
            selectedMapPath
                ?.let { File(it).nameWithoutExtension.lowercase(Locale.ROOT) }
                ?.takeIf { it.isNotBlank() }
                ?: return alwaysIncluded + mapScoped

        val exact =
            mapScoped.filter { source ->
                File(source.filePath).nameWithoutExtension.lowercase(Locale.ROOT) == mapBase
            }
        return alwaysIncluded + if (exact.isNotEmpty()) exact else mapScoped
    }

    suspend fun createMyCreationPoiAt(
        lat: Double,
        lon: Double,
    ): UserPoiRecord {
        val record =
            withContext(Dispatchers.IO) {
                userPoiRepository.createPoi(lat = lat, lon = lon)
            }
        refreshUserPoiSourceFromDisk()
        return record
    }

    private suspend fun refreshUserPoiSourceFromDisk() {
        val wasExpanded = _poiFiles.value.firstOrNull { isUserPoiPath(it.path) }?.isExpanded
        userPoiSourceState =
            withContext(Dispatchers.IO) {
                userPoiRepository.readSourceState()
            }
        val syntheticUserFile =
            buildUserPoiFileUiState(
                isExpanded = wasExpanded ?: userPoiSourceState.points.isNotEmpty(),
            )
        _poiFiles.update { files ->
            if (files.any { isUserPoiPath(it.path) }) {
                files.map { file ->
                    if (isUserPoiPath(file.path)) syntheticUserFile else file
                }
            } else {
                listOf(syntheticUserFile) + files
            }
        }
        _categoryPreviews.update { previews ->
            previews.filterKeys { key -> key.filePath != USER_POI_SOURCE_PATH }
        }
        _categoryCounts.update { counts ->
            counts.filterKeys { key -> key.filePath != USER_POI_SOURCE_PATH }
        }
    }

    private fun buildUserPoiFileUiState(isExpanded: Boolean): PoiFileUiState {
        val enabled = userPoiSourceState.fileEnabled && userPoiSourceState.categoryEnabled
        val totalPoiCount = userPoiSourceState.points.size
        return PoiFileUiState(
            name = USER_POI_SOURCE_NAME,
            path = USER_POI_SOURCE_PATH,
            isEnabled = enabled,
            isExpanded = isExpanded,
            categories =
                listOf(
                    PoiCategoryUiState(
                        id = USER_POI_CATEGORY_ID,
                        name = USER_POI_CATEGORY_NAME,
                        parentId = null,
                        depth = 0,
                        hasChildren = false,
                        enabled = enabled,
                    ),
                ),
            enabledPoiCount = if (enabled) totalPoiCount else 0,
            totalPoiCount = totalPoiCount,
        )
    }

    private fun queryUserPoiPoints(
        viewport: PoiViewport,
        limit: Int,
    ): List<com.glancemap.glancemapwearos.data.repository.PoiPoint> =
        userPoiSourceState.points
            .asSequence()
            .filter { point ->
                point.lat in viewport.minLat..viewport.maxLat &&
                    point.lon in viewport.minLon..viewport.maxLon
            }.take(limit)
            .map { point ->
                com.glancemap.glancemapwearos.data.repository.PoiPoint(
                    id = point.id,
                    lat = point.lat,
                    lon = point.lon,
                    name = point.name,
                    type = PoiType.CUSTOM,
                    details = point.details,
                )
            }.toList()

    private fun searchUserPoiPoints(
        query: String,
        limit: Int,
    ): List<PoiSearchResultUiState> {
        val queryLower = query.lowercase(Locale.ROOT)
        return userPoiSourceState.points
            .asSequence()
            .filter { point -> point.name.lowercase(Locale.ROOT).contains(queryLower) }
            .sortedBy { poiSearchMatchRank(it.name, queryLower) }
            .take(limit)
            .map { point ->
                PoiSearchResultUiState(
                    id = point.id,
                    filePath = USER_POI_SOURCE_PATH,
                    fileName = USER_POI_SOURCE_NAME,
                    name = point.name,
                    type = PoiType.CUSTOM,
                    lat = point.lat,
                    lon = point.lon,
                    details = point.details,
                )
            }.toList()
    }

    private fun UserPoiRecord.toPreviewUiState(): PoiCategoryPreviewPointUiState =
        PoiCategoryPreviewPointUiState(
            id = id,
            name = name,
            type = PoiType.CUSTOM,
            lat = lat,
            lon = lon,
            details = details,
        )

    private fun isUserPoiPath(path: String): Boolean = path == USER_POI_SOURCE_PATH

    private fun descendantCategoryIds(
        file: PoiFileUiState,
        categoryId: Int,
    ): Set<Int> {
        val childrenByParent = file.categories.groupBy { it.parentId }
        val result = mutableSetOf<Int>()

        fun walk(parent: Int) {
            childrenByParent[parent].orEmpty().forEach { child ->
                if (result.add(child.id)) {
                    walk(child.id)
                }
            }
        }
        walk(categoryId)
        return result
    }

    private fun ancestorCategoryIds(
        file: PoiFileUiState,
        categoryId: Int,
    ): Set<Int> {
        val byId = file.categories.associateBy { it.id }
        val result = mutableSetOf<Int>()
        var current = byId[categoryId]
        val guard = mutableSetOf<Int>()
        while (true) {
            val parentId = current?.parentId ?: break
            if (!guard.add(parentId)) break
            if (byId[parentId] != null) {
                result.add(parentId)
            }
            current = byId[parentId]
        }
        return result
    }

    private fun PoiCategory.toUiState(enabled: Boolean): PoiCategoryUiState =
        PoiCategoryUiState(
            id = id,
            name = name,
            parentId = parentId,
            depth = depth,
            hasChildren = hasChildren,
            enabled = enabled,
        )

    private fun poiSearchMatchRank(
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
}

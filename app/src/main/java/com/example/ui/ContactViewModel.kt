package com.example.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.PhotoStorage
import com.example.data.local.PreferencesRepository
import com.example.data.repository.ContactRepository
import com.example.domain.model.BackupRecord
import com.example.domain.model.ContactDetail
import com.example.domain.model.ContactFilter
import com.example.domain.model.ContactSummary
import com.example.domain.model.DuplicateCluster
import com.example.domain.model.ExportFormat
import com.example.domain.model.GroupItem
import com.example.domain.model.MergePreviewSummary
import com.example.domain.model.MergeSourceFile
import com.example.domain.model.MergeStrategy
import com.example.domain.model.OperationProgress
import com.example.domain.model.SortOrder
import com.example.domain.model.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.measureTimeMillis

data class ContactsUiState(
    val searchQuery: String = "",
    val filter: ContactFilter = ContactFilter.ALL,
    val selectedGroupId: Long? = null,
    val selectedLetter: String? = null,
    val sortOrder: SortOrder = SortOrder.ALPHA_ASC,
    val currentPage: Int = 0,
    val pageSize: Int = 60,
    val totalFilteredCount: Int = 0,
    val contacts: List<ContactSummary> = emptyList(),
    val isLoading: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val lastAnchorId: Long? = null,
    val inspectedContact: ContactDetail? = null,
    val editingContact: ContactDetail? = null,
    val showBatchEditDialog: Boolean = false,
    val showExportSheet: Boolean = false,
    val pendingExportContactIds: List<Long>? = null
) {
    val totalPages: Int
        get() = if (totalFilteredCount == 0) 1 else ((totalFilteredCount - 1) / pageSize) + 1
    val isSelectionMode: Boolean
        get() = selectedIds.isNotEmpty()
}

data class MergeUiState(
    val sourceFiles: List<MergeSourceFile> = emptyList(),
    val strategy: MergeStrategy = MergeStrategy.SMART_MERGE,
    val preview: MergePreviewSummary? = null,
    val isAnalyzing: Boolean = false
)

data class DuplicatesUiState(
    val clusters: List<DuplicateCluster> = emptyList(),
    val isScanning: Boolean = false,
    val hasScannedOnce: Boolean = false
)

class ContactViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val photoStorage = PhotoStorage(application)
    private val prefsRepo = PreferencesRepository(application)
    private val repository = ContactRepository(application, db, photoStorage)

    val themeMode: StateFlow<ThemeMode> = prefsRepo.themeMode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ThemeMode.SYSTEM
    )

    val totalContactsCount: StateFlow<Int> = repository.totalCount.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0
    )

    val noPhoneCount: StateFlow<Int> = repository.noPhoneCount.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0
    )

    val groups: StateFlow<List<GroupItem>> = repository.groupsWithCounts.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private val _contactsState = MutableStateFlow(ContactsUiState())
    val contactsState: StateFlow<ContactsUiState> = _contactsState.asStateFlow()

    private val _mergeState = MutableStateFlow(MergeUiState())
    val mergeState: StateFlow<MergeUiState> = _mergeState.asStateFlow()

    private val _duplicatesState = MutableStateFlow(DuplicatesUiState())
    val duplicatesState: StateFlow<DuplicatesUiState> = _duplicatesState.asStateFlow()

    private val _operationProgress = MutableStateFlow<OperationProgress?>(null)
    val operationProgress: StateFlow<OperationProgress?> = _operationProgress.asStateFlow()

    private val _backups = MutableStateFlow<List<BackupRecord>>(emptyList())
    val backups: StateFlow<List<BackupRecord>> = _backups.asStateFlow()

    private val _benchmarkReport = MutableStateFlow<String?>(null)
    val benchmarkReport: StateFlow<String?> = _benchmarkReport.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private var searchDebounceJob: Job? = null
    private var queryJob: Job? = null
    private var activeOperationJob: Job? = null
    private var cancelFlag = AtomicBoolean(false)

    init {
        refreshContactsPage()
        refreshBackups()
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    }

    fun onSearchQueryChanged(query: String) {
        _contactsState.update { it.copy(searchQuery = query, currentPage = 0) }
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(180)
            refreshContactsPage()
        }
    }

    fun onFilterChanged(filter: ContactFilter) {
        _contactsState.update { it.copy(filter = filter, currentPage = 0) }
        refreshContactsPage()
    }

    fun onGroupFilterChanged(groupId: Long?) {
        _contactsState.update { it.copy(selectedGroupId = groupId, currentPage = 0) }
        refreshContactsPage()
    }

    fun onLetterSelected(letter: String?) {
        _contactsState.update {
            val next = if (it.selectedLetter == letter) null else letter
            it.copy(selectedLetter = next, currentPage = 0)
        }
        refreshContactsPage()
    }

    fun onSortOrderChanged(sortOrder: SortOrder) {
        _contactsState.update { it.copy(sortOrder = sortOrder, currentPage = 0) }
        refreshContactsPage()
    }

    fun goToPage(page: Int) {
        val maxPage = (_contactsState.value.totalPages - 1).coerceAtLeast(0)
        val clamped = page.coerceIn(0, maxPage)
        _contactsState.update { it.copy(currentPage = clamped) }
        refreshContactsPage()
    }

    fun refreshContactsPage() {
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            val st = _contactsState.value
            _contactsState.update { it.copy(isLoading = true) }
            try {
                val (items, total) = repository.queryContactsPage(
                    searchQuery = st.searchQuery,
                    filter = st.filter,
                    groupId = st.selectedGroupId,
                    firstLetter = st.selectedLetter,
                    sortOrder = st.sortOrder,
                    limit = st.pageSize,
                    offset = st.currentPage * st.pageSize
                )
                _contactsState.update {
                    it.copy(
                        contacts = items,
                        totalFilteredCount = total,
                        isLoading = false
                    )
                }
            } catch (_: CancellationException) {
            } catch (t: Throwable) {
                _contactsState.update { it.copy(isLoading = false) }
                _snackbarEvents.tryEmit("Erreur de lecture : ${t.localizedMessage ?: "inconnue"}")
            }
        }
    }

    // Selection & Multi-select
    fun toggleSelectContact(contactId: Long) {
        _contactsState.update { st ->
            val next = st.selectedIds.toMutableSet()
            if (!next.add(contactId)) next.remove(contactId)
            st.copy(selectedIds = next, lastAnchorId = contactId)
        }
    }

    fun selectRangeTo(targetId: Long) {
        _contactsState.update { st ->
            val list = st.contacts
            val anchorId = st.lastAnchorId
            if (anchorId == null) {
                return@update st.copy(selectedIds = st.selectedIds + targetId, lastAnchorId = targetId)
            }
            val idxA = list.indexOfFirst { it.id == anchorId }
            val idxB = list.indexOfFirst { it.id == targetId }
            if (idxA < 0 || idxB < 0) {
                return@update st.copy(selectedIds = st.selectedIds + targetId, lastAnchorId = targetId)
            }
            val range = minOf(idxA, idxB)..maxOf(idxA, idxB)
            val next = st.selectedIds + list.slice(range).map { it.id }
            st.copy(selectedIds = next, lastAnchorId = targetId)
        }
    }

    fun selectAllOnCurrentPage() {
        _contactsState.update { st ->
            st.copy(selectedIds = st.selectedIds + st.contacts.map { it.id })
        }
    }

    fun clearSelection() {
        _contactsState.update { it.copy(selectedIds = emptySet(), lastAnchorId = null) }
    }

    // Detail & Editor
    fun inspectContact(contactId: Long) {
        viewModelScope.launch {
            val detail = repository.getContactDetail(contactId)
            _contactsState.update { it.copy(inspectedContact = detail) }
        }
    }

    fun closeInspection() {
        _contactsState.update { it.copy(inspectedContact = null) }
    }

    fun openNewContactEditor() {
        _contactsState.update { it.copy(editingContact = ContactDetail()) }
    }

    fun openEditContactEditor(contactId: Long) {
        viewModelScope.launch {
            val detail = repository.getContactDetail(contactId)
            if (detail != null) {
                _contactsState.update { it.copy(editingContact = detail) }
            }
        }
    }

    fun closeEditor() {
        _contactsState.update { it.copy(editingContact = null) }
    }

    fun saveContact(detail: ContactDetail, newPhotoUri: Uri?, removePhoto: Boolean) {
        viewModelScope.launch {
            try {
                val savedId = repository.saveContact(detail, newPhotoUri, removePhoto)
                val refreshed = repository.getContactDetail(savedId)
                _contactsState.update {
                    it.copy(
                        editingContact = null,
                        inspectedContact = if (it.inspectedContact?.id == savedId) refreshed else it.inspectedContact
                    )
                }
                refreshContactsPage()
                _snackbarEvents.tryEmit(
                    if (detail.id == 0L) "Contact ajouté avec succès" else "Contact mis à jour"
                )
            } catch (t: Throwable) {
                _snackbarEvents.tryEmit("Erreur lors de l'enregistrement : ${t.localizedMessage}")
            }
        }
    }

    fun duplicateContact(contactId: Long) {
        viewModelScope.launch {
            val newId = repository.duplicateContact(contactId)
            if (newId != null) {
                refreshContactsPage()
                _snackbarEvents.tryEmit("Contact dupliqué avec succès")
            }
        }
    }

    fun deleteContacts(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteContacts(ids, createSafetyBackup = true)
            _contactsState.update { st ->
                st.copy(
                    selectedIds = st.selectedIds - ids.toSet(),
                    inspectedContact = if (st.inspectedContact?.id in ids) null else st.inspectedContact
                )
            }
            refreshContactsPage()
            refreshBackups()
            _snackbarEvents.tryEmit("${ids.size} contact(s) supprimé(s) (sauvegarde auto créée)")
        }
    }

    fun setShowBatchEditDialog(show: Boolean) {
        _contactsState.update { it.copy(showBatchEditDialog = show) }
    }

    fun applyBatchEdit(groupId: Long?, organization: String?, noteToAppend: String?) {
        val ids = _contactsState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            if (groupId != null) {
                repository.batchAssignGroup(ids, groupId)
            }
            if (!organization.isNullOrBlank() || !noteToAppend.isNullOrBlank()) {
                repository.batchUpdateOrganizationAndNote(ids, organization, noteToAppend)
            }
            _contactsState.update { it.copy(showBatchEditDialog = false, selectedIds = emptySet()) }
            refreshContactsPage()
            _snackbarEvents.tryEmit("Modification groupée appliquée à ${ids.size} contacts")
        }
    }

    fun assignSingleContactToGroup(contactId: Long, groupId: Long) {
        viewModelScope.launch {
            repository.batchAssignGroup(listOf(contactId), groupId)
            if (_contactsState.value.inspectedContact?.id == contactId) {
                inspectContact(contactId)
            }
            refreshContactsPage()
            _snackbarEvents.tryEmit("Contact ajouté au groupe")
        }
    }

    // Groups
    fun createGroup(name: String, colorHex: String) {
        viewModelScope.launch {
            val id = repository.createGroup(name, colorHex)
            if (id > 0L) _snackbarEvents.tryEmit("Groupe « $name » créé")
        }
    }

    fun renameGroup(groupId: Long, newName: String, colorHex: String) {
        viewModelScope.launch {
            repository.renameGroup(groupId, newName, colorHex)
            refreshContactsPage()
            _snackbarEvents.tryEmit("Groupe mis à jour")
        }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch {
            repository.deleteGroup(groupId)
            if (_contactsState.value.selectedGroupId == groupId) {
                _contactsState.update { it.copy(selectedGroupId = null) }
            }
            refreshContactsPage()
            _snackbarEvents.tryEmit("Groupe supprimé")
        }
    }

    // Import VCF
    fun importVcfFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        cancelFlag.set(false)
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            _operationProgress.value = OperationProgress(
                title = "Importation VCF en cours…",
                current = 0,
                total = 0,
                indeterminate = true,
                detail = "Lecture des fiches vCard…"
            )
            try {
                val count = repository.importVcfStreams(
                    uris = uris,
                    onProgress = { imported, fileIdx, totalFiles ->
                        _operationProgress.value = OperationProgress(
                            title = "Importation VCF ($fileIdx/$totalFiles)",
                            current = imported,
                            total = 0,
                            indeterminate = true,
                            detail = "$imported contacts importés"
                        )
                    },
                    isCancelled = { cancelFlag.get() }
                )
                refreshContactsPage()
                val msg = if (cancelFlag.get()) {
                    "Importation annulée ($count contacts déjà enregistrés)"
                } else {
                    "$count contact(s) importé(s) avec succès"
                }
                _snackbarEvents.tryEmit(msg)
            } catch (t: Throwable) {
                _snackbarEvents.tryEmit("Erreur lors de l'importation : ${t.localizedMessage}")
            } finally {
                _operationProgress.value = null
            }
        }
    }

    fun cancelActiveOperation() {
        cancelFlag.set(true)
        activeOperationJob?.cancel()
        _operationProgress.value = null
        _snackbarEvents.tryEmit("Opération annulée")
    }

    // Export
    fun openExportSheet(selectedOnlyIds: List<Long>? = null) {
        _contactsState.update {
            it.copy(
                showExportSheet = true,
                pendingExportContactIds = selectedOnlyIds
            )
        }
    }

    fun closeExportSheet() {
        _contactsState.update { it.copy(showExportSheet = false, pendingExportContactIds = null) }
    }

    fun exportToUri(uri: Uri, format: ExportFormat, selectedIds: List<Long>?, groupId: Long?) {
        cancelFlag.set(false)
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            _operationProgress.value = OperationProgress(
                title = "Exportation ${format.label}…",
                current = 0,
                total = 0,
                indeterminate = true
            )
            try {
                val written = repository.exportContactsToUri(
                    outputUri = uri,
                    format = format,
                    selectedIds = selectedIds,
                    groupId = groupId,
                    onProgress = { cur, tot ->
                        _operationProgress.value = OperationProgress(
                            title = "Exportation en cours…",
                            current = cur,
                            total = tot,
                            indeterminate = tot <= 0,
                            detail = "$cur / $tot contacts"
                        )
                    },
                    isCancelled = { cancelFlag.get() }
                )
                _snackbarEvents.tryEmit("$written contact(s) exporté(s) avec succès")
            } catch (t: Throwable) {
                _snackbarEvents.tryEmit("Erreur lors de l'exportation : ${t.localizedMessage}")
            } finally {
                _operationProgress.value = null
            }
        }
    }

    fun exportByGroupToFolder(treeUri: Uri) {
        cancelFlag.set(false)
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            _operationProgress.value = OperationProgress(
                title = "Exportation VCF par groupe…",
                current = 0,
                total = 0,
                indeterminate = true
            )
            try {
                val written = repository.exportVcfByGroupToFolder(
                    treeUri = treeUri,
                    onProgress = { cur, tot ->
                        _operationProgress.value = OperationProgress(
                            title = "Exportation par groupe…",
                            current = cur,
                            total = tot,
                            indeterminate = tot <= 0,
                            detail = "$cur / $tot contacts"
                        )
                    },
                    isCancelled = { cancelFlag.get() }
                )
                _snackbarEvents.tryEmit("Export par groupe terminé ($written contacts)")
            } catch (t: Throwable) {
                _snackbarEvents.tryEmit("Erreur d'export par groupe : ${t.localizedMessage}")
            } finally {
                _operationProgress.value = null
            }
        }
    }

    // Merge VCF files
    fun addMergeVcfFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _mergeState.update { it.copy(isAnalyzing = true) }
            val newSources = uris.map { uri ->
                val name = resolveDisplayFileName(uri)
                repository.parseVcfForMergePreview(uri, name)
            }
            _mergeState.update { st ->
                val mergedList = st.sourceFiles + newSources
                st.copy(
                    sourceFiles = mergedList,
                    preview = repository.analyzeMergeCandidates(mergedList),
                    isAnalyzing = false
                )
            }
        }
    }

    fun removeMergeSource(id: String) {
        _mergeState.update { st ->
            val updated = st.sourceFiles.filterNot { it.id == id }
            st.copy(
                sourceFiles = updated,
                preview = if (updated.isEmpty()) null else repository.analyzeMergeCandidates(updated)
            )
        }
    }

    fun clearMergeSources() {
        _mergeState.value = MergeUiState()
    }

    fun toggleMergeContact(fileId: String, index: Int) {
        _mergeState.update { st ->
            val updated = st.sourceFiles.map { src ->
                if (src.id != fileId) src
                else {
                    val nextSet = src.selectedIndices.toMutableSet()
                    if (!nextSet.add(index)) nextSet.remove(index)
                    src.copy(selectedIndices = nextSet)
                }
            }
            st.copy(sourceFiles = updated, preview = repository.analyzeMergeCandidates(updated))
        }
    }

    fun setMergeFileSelectAll(fileId: String, selectAll: Boolean) {
        _mergeState.update { st ->
            val updated = st.sourceFiles.map { src ->
                if (src.id != fileId) src
                else src.copy(selectedIndices = if (selectAll) src.contacts.indices.toSet() else emptySet())
            }
            st.copy(sourceFiles = updated, preview = repository.analyzeMergeCandidates(updated))
        }
    }

    fun setMergeStrategy(strategy: MergeStrategy) {
        _mergeState.update { it.copy(strategy = strategy) }
    }

    fun executeMergeToDatabase() {
        val st = _mergeState.value
        if (st.sourceFiles.isEmpty()) return
        cancelFlag.set(false)
        activeOperationJob = viewModelScope.launch {
            _operationProgress.value = OperationProgress(
                title = "Fusion et importation…",
                current = 0,
                total = 0,
                indeterminate = true
            )
            try {
                val prepared = repository.mergeAndPrepareContacts(st.sourceFiles, st.strategy)
                val total = prepared.size
                val inserted = repository.importParsedContactsToDb(
                    contacts = prepared,
                    onProgress = { n ->
                        _operationProgress.value = OperationProgress(
                            title = "Enregistrement des contacts fusionnés…",
                            current = n,
                            total = total,
                            detail = "$n / $total"
                        )
                    },
                    isCancelled = { cancelFlag.get() }
                )
                refreshContactsPage()
                _snackbarEvents.tryEmit("$inserted contacts fusionnés et ajoutés à votre carnet")
            } finally {
                _operationProgress.value = null
            }
        }
    }

    fun executeMergeToVcfFile(outputUri: Uri) {
        val st = _mergeState.value
        if (st.sourceFiles.isEmpty()) return
        cancelFlag.set(false)
        activeOperationJob = viewModelScope.launch {
            _operationProgress.value = OperationProgress(
                title = "Création du fichier VCF fusionné…",
                current = 0,
                total = 0,
                indeterminate = true
            )
            try {
                val prepared = repository.mergeAndPrepareContacts(st.sourceFiles, st.strategy)
                val total = prepared.size
                val written = repository.writeParsedContactsToVcfUri(
                    contacts = prepared,
                    outputUri = outputUri,
                    onProgress = { n ->
                        _operationProgress.value = OperationProgress(
                            title = "Écriture Majid_Aoudi_Contacts.vcf…",
                            current = n,
                            total = total,
                            detail = "$n / $total contacts"
                        )
                    },
                    isCancelled = { cancelFlag.get() }
                )
                _snackbarEvents.tryEmit("Fichier VCF fusionné créé ($written contacts)")
            } catch (t: Throwable) {
                _snackbarEvents.tryEmit("Erreur lors de la fusion : ${t.localizedMessage}")
            } finally {
                _operationProgress.value = null
            }
        }
    }

    // Duplicate scanner
    fun scanDuplicates() {
        viewModelScope.launch {
            _duplicatesState.update { it.copy(isScanning = true) }
            val clusters = repository.scanDuplicateClusters()
            _duplicatesState.value = DuplicatesUiState(
                clusters = clusters,
                isScanning = false,
                hasScannedOnce = true
            )
        }
    }

    fun mergeDuplicateCluster(cluster: DuplicateCluster) {
        viewModelScope.launch {
            repository.mergeDuplicateCluster(cluster)
            refreshContactsPage()
            refreshBackups()
            scanDuplicates()
            _snackbarEvents.tryEmit("Doublons fusionnés avec succès")
        }
    }

    fun deleteSingleFromCluster(contactId: Long) {
        viewModelScope.launch {
            repository.deleteContacts(listOf(contactId), createSafetyBackup = true)
            refreshContactsPage()
            refreshBackups()
            scanDuplicates()
            _snackbarEvents.tryEmit("Contact doublon supprimé")
        }
    }

    // Backups & Restore
    fun refreshBackups() {
        viewModelScope.launch {
            _backups.value = repository.listLocalBackups()
        }
    }

    fun createManualBackup() {
        viewModelScope.launch {
            _operationProgress.value = OperationProgress(
                title = "Création d'une sauvegarde locale…",
                current = 0,
                total = 0,
                indeterminate = true
            )
            val record = repository.createLocalBackup(isAutomatic = false)
            _operationProgress.value = null
            refreshBackups()
            if (record != null) {
                _snackbarEvents.tryEmit("Sauvegarde créée : ${record.fileName}")
            } else {
                _snackbarEvents.tryEmit("Échec de la création de sauvegarde")
            }
        }
    }

    fun restoreFromBackup(record: BackupRecord, replaceExisting: Boolean) {
        viewModelScope.launch {
            _operationProgress.value = OperationProgress(
                title = "Restauration de la sauvegarde…",
                current = 0,
                total = 0,
                indeterminate = true
            )
            val count = repository.restoreFromBackupFile(record.filePath, replaceExisting)
            _operationProgress.value = null
            refreshContactsPage()
            _snackbarEvents.tryEmit("$count contacts restaurés")
        }
    }

    fun restoreFromBackupUri(uri: Uri, replaceExisting: Boolean) {
        viewModelScope.launch {
            _operationProgress.value = OperationProgress(
                title = "Restauration depuis fichier JSON…",
                current = 0,
                total = 0,
                indeterminate = true
            )
            val count = repository.restoreFromBackupUri(uri, replaceExisting)
            _operationProgress.value = null
            refreshContactsPage()
            _snackbarEvents.tryEmit("$count contacts restaurés")
        }
    }

    fun runPerformanceBenchmark(count: Int) {
        cancelFlag.set(false)
        activeOperationJob = viewModelScope.launch {
            _operationProgress.value = OperationProgress(
                title = "Génération de $count contacts de test…",
                current = 0,
                total = count,
                indeterminate = false
            )
            try {
                var inserted = 0
                val insertMs = measureTimeMillis {
                    inserted = repository.generateSyntheticContacts(
                        count = count,
                        onProgress = { cur, tot ->
                            _operationProgress.value = OperationProgress(
                                title = "Génération locale ($cur / $tot)…",
                                current = cur,
                                total = tot,
                                detail = "Insertion SQLite optimisée par lots"
                            )
                        },
                        isCancelled = { cancelFlag.get() }
                    )
                }
                val totalDb = repository.getTotalCountNow()
                var pageResultCount = 0
                val searchMs = measureTimeMillis {
                    val (items, _) = repository.queryContactsPage(
                        searchQuery = "Majid",
                        filter = ContactFilter.ALL,
                        groupId = null,
                        firstLetter = null,
                        sortOrder = SortOrder.ALPHA_ASC,
                        limit = 60,
                        offset = 0
                    )
                    pageResultCount = items.size
                }
                val pageMs = measureTimeMillis {
                    repository.queryContactsPage(
                        searchQuery = "",
                        filter = ContactFilter.ALL,
                        groupId = null,
                        firstLetter = null,
                        sortOrder = SortOrder.ALPHA_ASC,
                        limit = 60,
                        offset = (totalDb / 2).coerceAtLeast(0)
                    )
                }
                _benchmarkReport.value =
                    "Test terminé : $inserted contacts générés en ${insertMs} ms • Total en base : $totalDb • Recherche FTS4 « Majid » : ${searchMs} ms ($pageResultCount affichés) • Pagination milieu de base : ${pageMs} ms."
                refreshContactsPage()
            } finally {
                _operationProgress.value = null
            }
        }
    }

    fun clearAllContacts() {
        viewModelScope.launch {
            repository.clearAllContacts()
            refreshContactsPage()
            refreshBackups()
            _snackbarEvents.tryEmit("Tous les contacts ont été supprimés (sauvegarde automatique créée)")
        }
    }

    private fun resolveDisplayFileName(uri: Uri): String {
        return try {
            getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            } ?: uri.lastPathSegment ?: "contacts.vcf"
        } catch (_: Throwable) {
            uri.lastPathSegment ?: "contacts.vcf"
        }
    }
}

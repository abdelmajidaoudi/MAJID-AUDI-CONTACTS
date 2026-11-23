package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.ContactDetail
import com.example.domain.model.ContactFilter
import com.example.domain.model.ContactSummary
import com.example.domain.model.GroupItem
import com.example.domain.model.SortOrder
import com.example.domain.util.AlphaHelper
import com.example.ui.ContactsUiState
import com.example.ui.components.ContactAvatar
import com.example.ui.theme.BrandHeaderGradient

@Composable
fun ContactsScreen(
    state: ContactsUiState,
    totalContactsCount: Int,
    noPhoneCount: Int,
    groups: List<GroupItem>,
    useSplitPane: Boolean,
    onSearchChanged: (String) -> Unit,
    onFilterChanged: (ContactFilter) -> Unit,
    onGroupSelected: (Long?) -> Unit,
    onLetterSelected: (String?) -> Unit,
    onSortChanged: (SortOrder) -> Unit,
    onPageChanged: (Int) -> Unit,
    onImportVcf: () -> Unit,
    onNavigateToMerge: () -> Unit,
    onOpenExportAll: () -> Unit,
    onAddContact: () -> Unit,
    onInspectContact: (Long) -> Unit,
    onEditContact: (Long) -> Unit,
    onDuplicateContact: (Long) -> Unit,
    onDeleteContacts: (List<Long>) -> Unit,
    onToggleSelect: (Long) -> Unit,
    onSelectRangeTo: (Long) -> Unit,
    onSelectAllPage: () -> Unit,
    onClearSelection: () -> Unit,
    onOpenBatchEdit: () -> Unit,
    onExportSelection: (List<Long>) -> Unit,
    onAssignToGroup: (contactId: Long, groupId: Long) -> Unit,
    detailPaneContent: @Composable (ContactDetail) -> Unit
) {
    var arabicScriptMode by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var pendingDeleteIds by remember { mutableStateOf<List<Long>?>(null) }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Top Gradient Brand Bar + Quick Actions
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BrandHeaderGradient)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            shadowElevation = 4.dp,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_brand_logo),
                                contentDescription = stringResource(R.string.cd_brand_logo),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Majid Aoudi Contact",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$totalContactsCount contact(s) • Base locale hors ligne",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        Button(
                            onClick = onAddContact,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("add_contact_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ajouter", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    // Quick Action Buttons Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        HeaderActionPill(
                            icon = Icons.Rounded.FileUpload,
                            label = "Importer VCF",
                            onClick = onImportVcf,
                            testTag = "import_vcf_button"
                        )
                        HeaderActionPill(
                            icon = Icons.AutoMirrored.Rounded.MergeType,
                            label = "Fusionner VCF",
                            onClick = onNavigateToMerge,
                            testTag = "merge_vcf_shortcut_button"
                        )
                        HeaderActionPill(
                            icon = Icons.Rounded.Download,
                            label = "Exporter",
                            onClick = onOpenExportAll,
                            testTag = "export_all_button"
                        )
                    }
                }
            }

            // Search + Sort Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchChanged,
                    placeholder = {
                        Text(
                            stringResource(R.string.search_hint),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChanged("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Effacer la recherche")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_contacts_input")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showSortMenu = true }
                            .testTag("sort_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Sort,
                            contentDescription = "Trier les contacts",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        order.label,
                                        fontWeight = if (state.sortOrder == order) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    onSortChanged(order)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Filter Chips + Group Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(ContactFilter.entries) { filter ->
                    val label = if (filter == ContactFilter.NO_PHONE && noPhoneCount > 0) {
                        "${filter.label} ($noPhoneCount)"
                    } else filter.label
                    FilterChip(
                        selected = state.filter == filter && state.selectedGroupId == null,
                        onClick = {
                            onGroupSelected(null)
                            onFilterChanged(filter)
                        },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
                items(groups, key = { "grp_${it.id}" }) { grp ->
                    FilterChip(
                        selected = state.selectedGroupId == grp.id,
                        onClick = {
                            onFilterChanged(ContactFilter.ALL)
                            onGroupSelected(if (state.selectedGroupId == grp.id) null else grp.id)
                        },
                        label = { Text("${grp.name} (${grp.contactCount})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Alphabetical Quick-Jump Bar (French A-Z & Arabic أ-ي)
            val letters = remember(arabicScriptMode) {
                (if (arabicScriptMode) AlphaHelper.ARABIC_ALPHABET else AlphaHelper.LATIN_ALPHABET) + "#"
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                AssistChip(
                    onClick = { arabicScriptMode = !arabicScriptMode },
                    label = {
                        Text(
                            if (arabicScriptMode) "A-Z" else "أ-ي",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    modifier = Modifier.testTag("toggle_alphabet_script_button")
                )
                Spacer(modifier = Modifier.width(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(letters) { letter ->
                        val selected = state.selectedLetter == letter
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onLetterSelected(letter) }
                        ) {
                            Text(
                                text = letter,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Multi-select Action Bar
            AnimatedVisibility(visible = state.isSelectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "${state.selectedIds.size} sélectionné(s)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onSelectAllPage) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = "Tout sélectionner sur la page")
                        }
                        IconButton(onClick = onOpenBatchEdit) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Modifier la sélection")
                        }
                        IconButton(onClick = { onExportSelection(state.selectedIds.toList()) }) {
                            Icon(Icons.Rounded.Download, contentDescription = "Exporter la sélection")
                        }
                        IconButton(onClick = { pendingDeleteIds = state.selectedIds.toList() }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Supprimer la sélection",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Rounded.Close, contentDescription = "Annuler la sélection")
                        }
                    }
                }
            }

            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            // Main Contact List or Empty State
            if (state.contacts.isEmpty() && !state.isLoading) {
                EmptyContactsState(
                    hasActiveFilter = state.searchQuery.isNotBlank() ||
                        state.filter != ContactFilter.ALL ||
                        state.selectedGroupId != null ||
                        state.selectedLetter != null,
                    onResetFilters = {
                        onSearchChanged("")
                        onFilterChanged(ContactFilter.ALL)
                        onGroupSelected(null)
                        onLetterSelected(null)
                    },
                    onImportVcf = onImportVcf,
                    onAddContact = onAddContact,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(state.contacts, key = { it.id }) { contact ->
                        ContactRowCard(
                            contact = contact,
                            isSelected = state.selectedIds.contains(contact.id),
                            isSelectionMode = state.isSelectionMode,
                            groups = groups,
                            onClick = {
                                if (state.isSelectionMode) onToggleSelect(contact.id)
                                else onInspectContact(contact.id)
                            },
                            onLongClick = { onToggleSelect(contact.id) },
                            onEdit = { onEditContact(contact.id) },
                            onDuplicate = { onDuplicateContact(contact.id) },
                            onSelectRange = { onSelectRangeTo(contact.id) },
                            onAssignGroup = { grpId -> onAssignToGroup(contact.id, grpId) },
                            onExportSingle = { onExportSelection(listOf(contact.id)) },
                            onDelete = { pendingDeleteIds = listOf(contact.id) }
                        )
                    }
                }

                // Pagination Bar (supports 100k+ contacts smoothly)
                Surface(
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onPageChanged(state.currentPage - 1) },
                            enabled = state.currentPage > 0,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Page précédente")
                            Text("Préc.")
                        }
                        Text(
                            text = "Page ${state.currentPage + 1}/${state.totalPages} • ${state.totalFilteredCount} résultat(s)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { onPageChanged(state.currentPage + 1) },
                            enabled = state.currentPage + 1 < state.totalPages,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Suiv.")
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Page suivante")
                        }
                    }
                }
            }
        }

        // Tablet / Expanded Split-Pane Detail View
        if (useSplitPane && state.inspectedContact != null) {
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier
                    .width(380.dp)
                    .fillMaxHeight()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                detailPaneContent(state.inspectedContact)
            }
        }
    }

    pendingDeleteIds?.let { ids ->
        AlertDialog(
            onDismissRequest = { pendingDeleteIds = null },
            title = { Text("Confirmer la suppression") },
            text = {
                Text(
                    if (ids.size == 1) "Voulez-vous vraiment supprimer ce contact ?"
                    else "Voulez-vous vraiment supprimer ces ${ids.size} contacts ? Une sauvegarde automatique locale sera créée au préalable."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteContacts(ids)
                        pendingDeleteIds = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteIds = null }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun HeaderActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.18f),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRowCard(
    contact: ContactSummary,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    groups: List<GroupItem>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onSelectRange: () -> Unit,
    onAssignGroup: (Long) -> Unit,
    onExportSingle: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showGroupSubmenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("contact_card_${contact.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                ContactAvatar(
                    displayName = contact.displayName,
                    photoPath = contact.photoPath,
                    colorSeed = contact.avatarColorSeed,
                    size = 48.dp
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Sélectionné",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(20.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (contact.primaryPhone.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (contact.phoneCount > 1) "${contact.primaryPhone} (+${contact.phoneCount - 1})" else contact.primaryPhone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (contact.primaryEmail.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Email,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = contact.primaryEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (contact.organization.isNotBlank() || contact.groupNames.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (contact.organization.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = contact.organization,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (contact.groupNames.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = contact.groupNames,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Menu contextuel du contact")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Modifier") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Dupliquer / Copier") },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onDuplicate()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isSelectionMode) "Sélectionner jusqu'ici (Shift)" else "Sélectionner") },
                        leadingIcon = { Icon(Icons.Rounded.SelectAll, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            if (isSelectionMode) onSelectRange() else onLongClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Ajouter à un groupe") },
                        leadingIcon = { Icon(Icons.Rounded.GroupAdd, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            showGroupSubmenu = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Exporter en VCF") },
                        leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onExportSingle()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }

                DropdownMenu(expanded = showGroupSubmenu, onDismissRequest = { showGroupSubmenu = false }) {
                    groups.forEach { g ->
                        DropdownMenuItem(
                            text = { Text(g.name) },
                            onClick = {
                                showGroupSubmenu = false
                                onAssignGroup(g.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyContactsState(
    hasActiveFilter: Boolean,
    onResetFilters: () -> Unit,
    onImportVcf: () -> Unit,
    onAddContact: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_contacts),
            contentDescription = stringResource(R.string.cd_empty_illustration),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(220.dp)
                .height(165.dp)
                .clip(RoundedCornerShape(22.dp))
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = if (hasActiveFilter) "Aucun contact trouvé" else "Votre carnet est prêt",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (hasActiveFilter) {
                "Essayez de modifier votre recherche ou réinitialisez les filtres actifs."
            } else {
                "Importez un ou plusieurs fichiers VCF (vCard 2.1, 3.0, 4.0) ou créez votre premier contact."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(18.dp))
        if (hasActiveFilter) {
            Button(onClick = onResetFilters) {
                Text("Réinitialiser les filtres")
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onImportVcf, modifier = Modifier.testTag("empty_import_vcf_button")) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Importer VCF")
                }
                OutlinedButton(onClick = onAddContact, modifier = Modifier.testTag("empty_add_contact_button")) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Nouveau contact")
                }
            }
        }
    }
}

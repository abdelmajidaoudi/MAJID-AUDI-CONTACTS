package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.DuplicateCluster
import com.example.domain.model.ExportFormat
import com.example.domain.model.MergeSourceFile
import com.example.domain.model.MergeStrategy
import com.example.ui.DuplicatesUiState
import com.example.ui.MergeUiState
import com.example.ui.components.ContactAvatar
import com.example.ui.theme.BrandHeaderGradient
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeAndToolsScreen(
    mergeState: MergeUiState,
    duplicatesState: DuplicatesUiState,
    totalContactsCount: Int,
    onPickMergeFiles: () -> Unit,
    onRemoveMergeFile: (String) -> Unit,
    onClearMergeFiles: () -> Unit,
    onToggleMergeContact: (fileId: String, index: Int) -> Unit,
    onSetMergeFileSelectAll: (fileId: String, selectAll: Boolean) -> Unit,
    onSetMergeStrategy: (MergeStrategy) -> Unit,
    onSaveMergedToDatabase: () -> Unit,
    onExportMergedVcf: () -> Unit,
    onScanDuplicates: () -> Unit,
    onMergeDuplicateCluster: (DuplicateCluster) -> Unit,
    onDeleteDuplicateContact: (Long) -> Unit,
    onTriggerExport: (ExportFormat) -> Unit
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }
    val subTabs = listOf("Fusion VCF", "Doublons", "Exportation")

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrandHeaderGradient)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column {
                Text(
                    "Fusion, Doublons & Export",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Text(
                    "Combinez plusieurs VCF, nettoyez les doublons et exportez en VCF/CSV/JSON",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }

        SecondaryTabRow(selectedTabIndex = selectedSubTab) {
            subTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedSubTab == index,
                    onClick = { selectedSubTab = index },
                    text = { Text(title, maxLines = 1) },
                    modifier = Modifier.testTag("tools_tab_$index")
                )
            }
        }

        when (selectedSubTab) {
            0 -> VcfMergePane(
                state = mergeState,
                onPickMergeFiles = onPickMergeFiles,
                onRemoveMergeFile = onRemoveMergeFile,
                onClearMergeFiles = onClearMergeFiles,
                onToggleMergeContact = onToggleMergeContact,
                onSetMergeFileSelectAll = onSetMergeFileSelectAll,
                onSetMergeStrategy = onSetMergeStrategy,
                onSaveMergedToDatabase = onSaveMergedToDatabase,
                onExportMergedVcf = onExportMergedVcf
            )
            1 -> DuplicatesPane(
                state = duplicatesState,
                onScanDuplicates = onScanDuplicates,
                onMergeCluster = onMergeDuplicateCluster,
                onDeleteContact = onDeleteDuplicateContact
            )
            2 -> ExportCenterPane(
                totalContactsCount = totalContactsCount,
                onTriggerExport = onTriggerExport
            )
        }
    }
}

@Composable
private fun VcfMergePane(
    state: MergeUiState,
    onPickMergeFiles: () -> Unit,
    onRemoveMergeFile: (String) -> Unit,
    onClearMergeFiles: () -> Unit,
    onToggleMergeContact: (fileId: String, index: Int) -> Unit,
    onSetMergeFileSelectAll: (fileId: String, selectAll: Boolean) -> Unit,
    onSetMergeStrategy: (MergeStrategy) -> Unit,
    onSaveMergedToDatabase: () -> Unit,
    onExportMergedVcf: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Fusionner plusieurs fichiers VCF",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        "Sélectionnez plusieurs fichiers (ex : Famille.vcf, Travail.vcf, Amis.vcf), vérifiez les doublons et générez Majid_Aoudi_Contacts.vcf.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onPickMergeFiles,
                            modifier = Modifier.testTag("pick_merge_files_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ajouter fichiers VCF")
                        }
                        if (state.sourceFiles.isNotEmpty()) {
                            OutlinedButton(onClick = onClearMergeFiles) {
                                Text("Tout vider")
                            }
                        }
                    }
                    if (state.isAnalyzing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        items(state.sourceFiles, key = { it.id }) { src ->
            MergeSourceFileCard(
                source = src,
                onRemove = { onRemoveMergeFile(src.id) },
                onToggleContact = { idx -> onToggleMergeContact(src.id, idx) },
                onSelectAll = { all -> onSetMergeFileSelectAll(src.id, all) }
            )
        }

        state.preview?.let { preview ->
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Analyse avant fusion", style = MaterialTheme.typography.titleMedium)
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatPill("Avant fusion", "${preview.totalBefore}")
                            StatPill("Doublons détectés", "${preview.duplicatesDetected}")
                            val afterCount = when (state.strategy) {
                                MergeStrategy.KEEP_BOTH -> preview.totalBefore
                                MergeStrategy.SMART_MERGE, MergeStrategy.SKIP_DUPLICATE -> preview.estimatedAfter
                            }
                            StatPill("Après fusion", "$afterCount")
                        }

                        HorizontalDivider()
                        Text("Traitement des doublons :", style = MaterialTheme.typography.labelLarge)
                        MergeStrategy.entries.forEach { strat ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSetMergeStrategy(strat) }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = state.strategy == strat,
                                    onClick = { onSetMergeStrategy(strat) }
                                )
                                Column {
                                    Text(strat.label, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        strat.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onExportMergedVcf,
                                enabled = preview.totalBefore > 0,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("export_merged_vcf_button")
                            ) {
                                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Enregistrer Majid_Aoudi_Contacts.vcf")
                            }
                            OutlinedButton(
                                onClick = onSaveMergedToDatabase,
                                enabled = preview.totalBefore > 0,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("import_merged_db_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.MergeType, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Fusionner dans mon carnet local")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MergeSourceFileCard(
    source: MergeSourceFile,
    onRemove: () -> Unit,
    onToggleContact: (Int) -> Unit,
    onSelectAll: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FileOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(source.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${source.selectedCount} / ${source.contacts.size} contact(s) sélectionné(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = "Afficher les contacts du fichier"
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Retirer le fichier", tint = MaterialTheme.colorScheme.error)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onSelectAll(true) }) { Text("Tout cocher") }
                        TextButton(onClick = { onSelectAll(false) }) { Text("Tout décocher") }
                    }
                    source.contacts.take(150).forEachIndexed { idx, c ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleContact(idx) }
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = idx in source.selectedIndices,
                                onCheckedChange = { onToggleContact(idx) }
                            )
                            Column {
                                Text(c.resolvedDisplayName(), style = MaterialTheme.typography.bodyMedium)
                                c.phones.firstOrNull()?.let { p ->
                                    Text(p.number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (source.contacts.size > 150) {
                        Text(
                            "Affichage limité aux 150 premiers contacts pour l'aperçu (${source.contacts.size} au total).",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicatesPane(
    state: DuplicatesUiState,
    onScanDuplicates: () -> Unit,
    onMergeCluster: (DuplicateCluster) -> Unit,
    onDeleteContact: (Long) -> Unit
) {
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Détection intelligente des doublons", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Compare les numéros de téléphone normalisés, noms et adresses e-mail. Aucune suppression n'est effectuée sans votre confirmation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onScanDuplicates,
                        enabled = !state.isScanning,
                        modifier = Modifier.testTag("scan_duplicates_button")
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (state.isScanning) "Analyse en cours…" else "Analyser mon carnet")
                    }
                    if (state.isScanning) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        if (state.hasScannedOnce && state.clusters.isEmpty() && !state.isScanning) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Aucun doublon détecté dans votre base locale !", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }

        items(state.clusters, key = { it.clusterKey }) { cluster ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cluster.reason.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                            Text(cluster.matchValue, style = MaterialTheme.typography.titleSmall)
                        }
                        Button(
                            onClick = { onMergeCluster(cluster) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Fusionner")
                        }
                    }

                    HorizontalDivider()

                    cluster.contacts.forEach { c ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ContactAvatar(
                                displayName = c.displayName,
                                photoPath = c.photoPath,
                                colorSeed = c.displayName.hashCode().absoluteValue,
                                size = 40.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    c.phones.joinToString(", ") { it.number }.ifBlank { "Sans numéro" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (c.emails.isNotEmpty()) {
                                    Text(
                                        c.emails.joinToString(", ") { it.email },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { pendingDeleteId = c.id }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Supprimer ce doublon", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Supprimer ce doublon ?") },
            text = { Text("Cette action supprimera uniquement cette fiche (une sauvegarde automatique sera créée).") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteContact(id)
                        pendingDeleteId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirmer")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun ExportCenterPane(
    totalContactsCount: Int,
    onTriggerExport: (ExportFormat) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                "Exporter $totalContactsCount contact(s)",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "Préserve les numéros multiples, e-mails, photos, notes, groupes et champs personnalisés.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            ExportBigCard(
                icon = Icons.Rounded.Download,
                title = "Fichier VCF unique (vCard 3.0)",
                description = "Regroupe tous vos contacts dans un seul fichier .vcf universel.",
                buttonLabel = "Exporter en VCF",
                onClick = { onTriggerExport(ExportFormat.VCF_SINGLE) }
            )
        }
        item {
            ExportBigCard(
                icon = Icons.Rounded.FolderCopy,
                title = "Fichiers VCF séparés par groupe",
                description = "Crée automatiquement un fichier .vcf par groupe (Famille.vcf, Travail.vcf…) dans le dossier de votre choix.",
                buttonLabel = "Choisir le dossier",
                onClick = { onTriggerExport(ExportFormat.VCF_BY_GROUP) }
            )
        }
        item {
            ExportBigCard(
                icon = Icons.Rounded.TableChart,
                title = "Tableau CSV compatible Excel",
                description = "Encodé en UTF-8 avec BOM pour afficher parfaitement l'arabe, le français et l'amazigh dans Excel.",
                buttonLabel = "Exporter en CSV",
                onClick = { onTriggerExport(ExportFormat.CSV) }
            )
        }
        item {
            ExportBigCard(
                icon = Icons.Rounded.Code,
                title = "Sauvegarde complète JSON",
                description = "Archive structurée complète incluant photos encodées, groupes et champs personnalisés.",
                buttonLabel = "Exporter en JSON",
                onClick = { onTriggerExport(ExportFormat.JSON) }
            )
        }
    }
}

@Composable
private fun ExportBigCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onClick) {
                Text(buttonLabel)
            }
        }
    }
}

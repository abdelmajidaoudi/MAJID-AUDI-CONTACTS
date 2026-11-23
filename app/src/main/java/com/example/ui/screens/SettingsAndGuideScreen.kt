package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.BackupRecord
import com.example.domain.model.ThemeMode
import com.example.ui.theme.BrandHeaderGradient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsAndGuideScreen(
    themeMode: ThemeMode,
    totalContactsCount: Int,
    backups: List<BackupRecord>,
    benchmarkReport: String?,
    onThemeChanged: (ThemeMode) -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackupFile: (BackupRecord, Boolean) -> Unit,
    onRestoreFromExternalJson: () -> Unit,
    onRunBenchmark: (Int) -> Unit,
    onClearAllContacts: () -> Unit
) {
    var pendingRestore by remember { mutableStateOf<BackupRecord?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }

    val guideSections = remember {
        listOf(
            "1. Importer et lire des fichiers VCF (2.1 / 3.0 / 4.0)" to
                "Appuyez sur « Importer VCF » dans l'onglet Contacts. Vous pouvez sélectionner un ou plusieurs fichiers simultanément, contenant des milliers ou des centaines de milliers de fiches. Les noms en français, arabe et amazigh ainsi que les photos, notes, entreprises et numéros multiples sont pris en charge sans bloquer l'interface.",
            "2. Fusionner plusieurs fichiers VCF" to
                "Ouvrez l'onglet « Fusion & Outils » → « Fusion VCF », ajoutez plusieurs fichiers (ex : Famille.vcf, Travail.vcf, Amis.vcf), cochez tous les contacts ou faites une sélection précise, choisissez la stratégie de doublons puis enregistrez le fichier unifié Majid_Aoudi_Contacts.vcf.",
            "3. Recherche rapide & Classement alphabétique" to
                "La recherche instantanée s'appuie sur un index SQLite FTS4 : tapez un nom, un morceau de numéro, un e-mail, une entreprise ou une note. Utilisez la barre alphabétique en haut pour sauter directement aux lettres latines (A–Z) ou arabes (أ–ي).",
            "4. Groupes personnalisés & Édition multiple" to
                "Dans « Groupes », créez, renommez ou supprimez vos catégories (Famille, Travail, Clients, Université…). Maintenez le doigt sur un contact pour activer la sélection multiple, puis modifiez en lot, exportez ou supprimez avec sauvegarde automatique.",
            "5. Détection et nettoyage des doublons" to
                "Dans « Doublons », lancez l'analyse : le moteur normalise les numéros (ex : 06 12… et +212 612…) et compare noms et e-mails. Comparez les fiches côte à côte avant de confirmer une fusion.",
            "6. Exportation (VCF, CSV Excel, JSON) & Sauvegardes" to
                "Exportez en VCF unique, en fichiers VCF séparés par groupe, en CSV UTF-8 BOM lisible directement dans Excel, ou en JSON complet. Vos données restent 100 % hors ligne sur votre appareil."
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BrandHeaderGradient)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_brand_logo),
                            contentDescription = stringResource(R.string.cd_brand_logo),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Majid Aoudi Contact", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(
                            "Paramètres, Sauvegardes & Guide d'utilisation",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // Theme Mode Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apparence (Clair / Sombre)", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { onThemeChanged(mode) },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("theme_${mode.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        // Security & Privacy Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("100 % Hors ligne & Confidentiel", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Aucune donnée personnelle n'est envoyée vers un serveur externe. Tous vos contacts ($totalContactsCount) sont stockés localement dans votre base SQLite sécurisée avec index FTS4.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Backup & Restore Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sauvegarde & Restauration", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onCreateBackup, modifier = Modifier.testTag("create_backup_button")) {
                            Text("Créer sauvegarde")
                        }
                        OutlinedButton(onClick = onRestoreFromExternalJson) {
                            Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Importer JSON")
                        }
                    }
                    if (backups.isNotEmpty()) {
                        Text("Sauvegardes locales récentes :", style = MaterialTheme.typography.labelLarge)
                        val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE) }
                        backups.take(5).forEach { b ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            if (b.isAutomatic) "Sauvegarde auto" else "Sauvegarde manuelle",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            "${fmt.format(Date(b.timestamp))} • ${(b.sizeBytes / 1024).coerceAtLeast(1)} Ko",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(onClick = { pendingRestore = b }) {
                                        Text("Restaurer")
                                    }
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { confirmClearAll = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Vider le carnet (avec sauvegarde auto)")
                    }
                }
            }
        }

        // Performance Benchmark 100k Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test de performance (jusqu'à 100 000 contacts)", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "Vérifiez la rapidité d'insertion par lots, la recherche FTS4 et la pagination SQLite directement sur cet appareil.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onRunBenchmark(5_000) }) {
                            Text("Tester 5 000")
                        }
                        Button(onClick = { onRunBenchmark(100_000) }) {
                            Text("Tester 100 000")
                        }
                    }
                    if (!benchmarkReport.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = benchmarkReport,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // French User Guide Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Rounded.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Guide d'utilisation en français", style = MaterialTheme.typography.titleLarge)
            }
        }

        items(guideSections) { (title, body) ->
            var expanded by remember { mutableStateOf(false) }
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { expanded = !expanded }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
                    }
                    AnimatedVisibility(visible = expanded) {
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }

    pendingRestore?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restaurer la sauvegarde") },
            text = { Text("Voulez-vous remplacer votre carnet actuel par cette sauvegarde ou fusionner les contacts restaurés ?") },
            confirmButton = {
                Button(onClick = {
                    onRestoreBackupFile(record, true)
                    pendingRestore = null
                }) {
                    Text("Remplacer")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingRestore = null }) { Text("Annuler") }
                    TextButton(onClick = {
                        onRestoreBackupFile(record, false)
                        pendingRestore = null
                    }) { Text("Ajouter") }
                }
            }
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Vider tout le carnet ?") },
            text = { Text("Une sauvegarde automatique locale sera créée avant la suppression de tous les contacts.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllContacts()
                        confirmClearAll = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Vider")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("Annuler") }
            }
        )
    }
}

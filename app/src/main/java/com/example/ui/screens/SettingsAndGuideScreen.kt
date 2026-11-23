package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.BackupRecord
import com.example.domain.model.ThemeMode
import com.example.domain.util.WhatsAppHelper
import com.example.ui.DriveSyncAction
import com.example.ui.theme.BrandHeaderGradient
import com.example.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsAndGuideScreen(
    themeMode: ThemeMode,
    totalContactsCount: Int,
    backups: List<BackupRecord>,
    benchmarkReport: String?,
    defaultCountryCode: String,
    driveAccountEmail: String?,
    lastDriveSyncTimestamp: Long,
    lastDriveSyncInfo: String,
    isDriveSyncing: Boolean,
    onThemeChanged: (ThemeMode) -> Unit,
    onDefaultCountryCodeChanged: (String) -> Unit,
    onTriggerDriveSync: (DriveSyncAction) -> Unit,
    onDisconnectDrive: () -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackupFile: (BackupRecord, Boolean) -> Unit,
    onRestoreFromExternalJson: () -> Unit,
    onRunBenchmark: (Int) -> Unit,
    onClearAllContacts: () -> Unit
) {
    var pendingRestore by remember { mutableStateOf<BackupRecord?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var confirmDriveReplace by remember { mutableStateOf(false) }

    val countryPresets = remember {
        listOf(
            "212" to "+212 Maroc",
            "33" to "+33 France",
            "213" to "+213 Algérie",
            "216" to "+216 Tunisie",
            "34" to "+34 Espagne",
            "1" to "+1 USA/CA"
        )
    }

    val guideSections = remember {
        listOf(
            "1. Importer et lire des fichiers VCF (2.1 / 3.0 / 4.0)" to
                "Appuyez sur « Importer VCF » dans l'onglet Contacts. Vous pouvez sélectionner un ou plusieurs fichiers simultanément, contenant des milliers ou des centaines de milliers de fiches. Les noms en français, arabe et amazigh ainsi que les photos, notes, entreprises et numéros multiples sont pris en charge sans bloquer l'interface.",
            "2. Fusionner plusieurs fichiers VCF" to
                "Ouvrez l'onglet « Fusion & Outils » → « Fusion VCF », ajoutez plusieurs fichiers (ex : Famille.vcf, Travail.vcf, Amis.vcf), cochez tous les contacts ou faites une sélection précise, choisissez la stratégie de doublons puis enregistrez le fichier unifié AA_Contacts.vcf.",
            "3. Liens directs WhatsApp (https://wa.me/212…) & Partage" to
                "Sur chaque fiche ou directement dans la liste des contacts, appuyez sur l'icône WhatsApp ou choisissez « Message WhatsApp (wa.me) ». Les numéros locaux (ex : 06 12 34 56 78) sont automatiquement convertis avec votre indicatif configuré (+212 par défaut → https://wa.me/212612345678). Vous pouvez aussi envoyer un contact ou toute une sélection en fichier .vcf sur WhatsApp.",
            "4. Synchronisation Google Drive" to
                "Connectez votre compte Google ci-dessus pour synchroniser vos contacts en toute sécurité sur votre Google Drive personnel (fichier AA_Contacts_Sync.json.gz et copie AA_Contacts_Backup.vcf). Seuls les fichiers créés par AA contacts sont accessibles grâce au scope restreint drive.file.",
            "5. Sauvegarde sur le téléphone & Exportation" to
                "Enregistrez un contact individuel ou toute une sélection directement dans le répertoire natif Android de votre téléphone, ou exportez en VCF unique, VCF par groupe, CSV compatible Excel (UTF-8 BOM) ou JSON complet.",
            "6. Recherche rapide, Groupes & Doublons" to
                "La recherche instantanée SQLite FTS4 retrouve nom, numéro, e-mail, entreprise ou note même sur 100 000 contacts. L'analyseur de doublons normalise les numéros locaux et internationaux avant toute fusion."
        )
    }

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE) }

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
                        Text("AA contacts", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(
                            "Drive Sync, WhatsApp, Sauvegardes & Guide",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // Google Drive Sync Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (driveAccountEmail != null) Icons.Rounded.CloudDone else Icons.Rounded.CloudSync,
                            contentDescription = null,
                            tint = if (driveAccountEmail != null) SuccessGreen else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Synchronisation Google Drive", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = driveAccountEmail?.let { "Connecté : $it" } ?: "Non connecté à Google Drive",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (driveAccountEmail != null) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (lastDriveSyncTimestamp > 0L) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    "Dernière synchro : ${dateFormatter.format(Date(lastDriveSyncTimestamp))}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (lastDriveSyncInfo.isNotBlank()) {
                                    Text(
                                        lastDriveSyncInfo,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            "Sauvegardez et synchronisez votre carnet ($totalContactsCount contacts) dans votre espace Google Drive privé (fichiers AA_Contacts_Sync.json.gz et AA_Contacts_Backup.vcf).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isDriveSyncing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    Button(
                        onClick = { onTriggerDriveSync(DriveSyncAction.TWO_WAY_SYNC) },
                        enabled = !isDriveSyncing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("drive_sync_now_button")
                    ) {
                        Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (driveAccountEmail == null) "Connecter Google Drive & Synchroniser"
                            else "Synchroniser maintenant"
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onTriggerDriveSync(DriveSyncAction.BACKUP_ONLY) },
                            enabled = !isDriveSyncing,
                            modifier = Modifier.testTag("drive_backup_only_button")
                        ) {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Envoyer sur Drive")
                        }
                        OutlinedButton(
                            onClick = { onTriggerDriveSync(DriveSyncAction.RESTORE_MERGE) },
                            enabled = !isDriveSyncing,
                            modifier = Modifier.testTag("drive_restore_merge_button")
                        ) {
                            Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restaurer (Fusion)")
                        }
                        OutlinedButton(
                            onClick = { confirmDriveReplace = true },
                            enabled = !isDriveSyncing,
                            modifier = Modifier.testTag("drive_restore_replace_button")
                        ) {
                            Text("Remplacer depuis Drive")
                        }
                        if (driveAccountEmail != null) {
                            TextButton(
                                onClick = onDisconnectDrive,
                                enabled = !isDriveSyncing,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.testTag("drive_disconnect_button")
                            ) {
                                Icon(Icons.Rounded.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Déconnecter")
                            }
                        }
                    }
                }
            }
        }

        // WhatsApp & Country Code Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, tint = SuccessGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("WhatsApp & Indicatif pays (wa.me)", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "Utilisé pour transformer automatiquement les numéros locaux (ex : 06…) en lien direct WhatsApp.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        countryPresets.forEach { (code, label) ->
                            FilterChip(
                                selected = defaultCountryCode == code,
                                onClick = { onDefaultCountryCodeChanged(code) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("country_preset_$code")
                            )
                        }
                    }
                    OutlinedTextField(
                        value = defaultCountryCode,
                        onValueChange = { input ->
                            val clean = input.filter { it.isDigit() }.take(4)
                            if (clean.isNotEmpty()) onDefaultCountryCodeChanged(clean)
                        },
                        label = { Text("Indicatif personnalisé (sans +)") },
                        leadingIcon = { Icon(Icons.Rounded.Public, contentDescription = null) },
                        prefix = { Text("+") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_country_code_input")
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SuccessGreen.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Aperçu : 06 12 34 56 78 → ${WhatsAppHelper.buildWaMeUrl("06 12 34 56 78", defaultCountryCode)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SuccessGreen,
                            modifier = Modifier.padding(10.dp)
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
                        Text("Stockage local sécurisé & Drive privé", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Vos $totalContactsCount contacts restent stockés localement dans votre base SQLite FTS4. Lorsque vous activez Google Drive, seules vos sauvegardes AA contacts sont synchronisées via l'autorisation restreinte drive.file.",
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
                        Text("Sauvegarde & Restauration locale", style = MaterialTheme.typography.titleMedium)
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
                        HorizontalDivider()
                        Text("Sauvegardes locales récentes :", style = MaterialTheme.typography.labelLarge)
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
                                            "${dateFormatter.format(Date(b.timestamp))} • ${(b.sizeBytes / 1024).coerceAtLeast(1)} Ko",
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

    if (confirmDriveReplace) {
        AlertDialog(
            onDismissRequest = { confirmDriveReplace = false },
            title = { Text("Remplacer depuis Google Drive ?") },
            text = { Text("Votre carnet local actuel sera remplacé par les contacts enregistrés dans votre sauvegarde Google Drive.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDriveReplace = false
                        onTriggerDriveSync(DriveSyncAction.RESTORE_REPLACE)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remplacer")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDriveReplace = false }) { Text("Annuler") }
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

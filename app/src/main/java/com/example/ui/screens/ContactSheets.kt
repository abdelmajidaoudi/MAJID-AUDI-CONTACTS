package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
import com.example.data.local.PhonebookSyncHelper
import com.example.domain.util.WhatsAppHelper
import com.example.ui.theme.SuccessGreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.ContactDetail
import com.example.domain.model.EmailEntry
import com.example.domain.model.GroupItem
import com.example.domain.model.PhoneEntry
import com.example.ui.components.ContactAvatar
import com.example.ui.theme.BrandHeaderGradient
import kotlin.math.absoluteValue

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContactDetailContent(
    contact: ContactDetail,
    defaultCountryCode: String,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onExportSingle: () -> Unit,
    onShareWhatsAppVcf: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    BackHandler { onClose() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Hero Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrandHeaderGradient)
                .padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fermer", tint = Color.White)
                    }
                    Row {
                        IconButton(onClick = onEdit, modifier = Modifier.testTag("detail_edit_button")) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Modifier", tint = Color.White)
                        }
                        IconButton(onClick = onDuplicate) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Dupliquer", tint = Color.White)
                        }
                        IconButton(onClick = onExportSingle) {
                            Icon(Icons.Rounded.Download, contentDescription = "Exporter VCF", tint = Color.White)
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Supprimer", tint = Color.White)
                        }
                    }
                }
                ContactAvatar(
                    displayName = contact.displayName,
                    photoPath = contact.photoPath,
                    colorSeed = contact.displayName.hashCode().absoluteValue,
                    size = 84.dp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = contact.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                if (contact.jobTitle.isNotBlank() || contact.organization.isNotBlank()) {
                    Text(
                        text = listOf(contact.jobTitle, contact.organization).filter { it.isNotBlank() }.joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
                if (contact.groups.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        contact.groups.forEach { g ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = g.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            // Quick Share & Save on Phone row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(PhonebookSyncHelper.buildInsertContactIntent(contact))
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("detail_save_phonebook_button")
                ) {
                    Icon(Icons.Rounded.Contacts, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sauver sur tél.", maxLines = 1)
                }
                Button(
                    onClick = onShareWhatsAppVcf,
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("detail_share_whatsapp_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Partager VCF", maxLines = 1)
                }
            }

            if (contact.phones.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Téléphones & WhatsApp", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        contact.phones.forEachIndexed { idx, phone ->
                            val waUrl = WhatsAppHelper.buildWaMeUrl(phone.number, defaultCountryCode)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(phone.number, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "${phone.typeLabel} • $waUrl",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)))
                                        }
                                    },
                                    modifier = Modifier.testTag("whatsapp_direct_button_$idx")
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.Chat,
                                        contentDescription = "Envoyer un message sur WhatsApp",
                                        tint = SuccessGreen
                                    )
                                }
                                IconButton(onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.number}")))
                                    }
                                }) {
                                    Icon(Icons.Rounded.Call, contentDescription = "Appeler", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${phone.number}")))
                                    }
                                }) {
                                    Icon(Icons.Rounded.Sms, contentDescription = "Envoyer SMS", tint = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                        TextButton(
                            onClick = {
                                val text = WhatsAppHelper.formatContactAsShareText(contact, defaultCountryCode)
                                val url = WhatsAppHelper.buildWaMeTextUrl(text)
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            },
                            modifier = Modifier.testTag("share_contact_text_whatsapp")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Envoyer cette fiche en message WhatsApp", color = SuccessGreen)
                        }
                    }
                }
            }

            if (contact.emails.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Adresses e-mail", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary)
                        contact.emails.forEach { mail ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(mail.email, style = MaterialTheme.typography.bodyLarge)
                                    Text(mail.typeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${mail.email}")))
                                    }
                                }) {
                                    Icon(Icons.Rounded.Email, contentDescription = "Envoyer un e-mail", tint = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                        }
                    }
                }
            }

            if (contact.organization.isNotBlank() || contact.birthday.isNotBlank() || contact.postalAddress.isNotBlank() || contact.notes.isNotBlank()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (contact.organization.isNotBlank()) {
                            InfoFieldRow(Icons.Rounded.Business, "Entreprise", "${contact.organization} ${contact.jobTitle}".trim())
                        }
                        if (contact.birthday.isNotBlank()) {
                            InfoFieldRow(Icons.Rounded.Cake, "Anniversaire", contact.birthday)
                        }
                        if (contact.postalAddress.isNotBlank()) {
                            InfoFieldRow(Icons.Rounded.LocationOn, "Adresse", contact.postalAddress)
                        }
                        if (contact.notes.isNotBlank()) {
                            InfoFieldRow(Icons.Rounded.Notes, "Notes personnelles", contact.notes)
                        }
                    }
                }
            }

            if (contact.customFields.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Champs personnalisés vCard", style = MaterialTheme.typography.titleSmall)
                        contact.customFields.forEach { (k, v) ->
                            Text("$k : $v", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce contact ?") },
            text = { Text("Voulez-vous supprimer « ${contact.displayName} » de votre carnet ?") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun InfoFieldRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContactEditorDialog(
    initial: ContactDetail,
    allGroups: List<GroupItem>,
    onDismiss: () -> Unit,
    onSave: (ContactDetail, Uri?, Boolean) -> Unit
) {
    var firstName by remember(initial) { mutableStateOf(initial.firstName) }
    var lastName by remember(initial) { mutableStateOf(initial.lastName) }
    var displayName by remember(initial) { mutableStateOf(initial.displayName) }
    var organization by remember(initial) { mutableStateOf(initial.organization) }
    var jobTitle by remember(initial) { mutableStateOf(initial.jobTitle) }
    var birthday by remember(initial) { mutableStateOf(initial.birthday) }
    var postalAddress by remember(initial) { mutableStateOf(initial.postalAddress) }
    var notes by remember(initial) { mutableStateOf(initial.notes) }

    val phones = remember(initial) {
        mutableStateListOf<PhoneEntry>().apply {
            if (initial.phones.isEmpty()) add(PhoneEntry("", "Mobile")) else addAll(initial.phones)
        }
    }
    val emails = remember(initial) {
        mutableStateListOf<EmailEntry>().apply {
            if (initial.emails.isEmpty()) add(EmailEntry("", "Personnel")) else addAll(initial.emails)
        }
    }
    var selectedGroupIds by remember(initial) { mutableStateOf(initial.groups.map { it.id }.toSet()) }
    var pickedPhotoUri by remember(initial) { mutableStateOf<Uri?>(null) }
    var removePhoto by remember(initial) { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedPhotoUri = uri
            removePhoto = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler { onDismiss() }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(if (initial.id == 0L) "Nouveau contact" else "Modifier le contact")
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Fermer")
                        }
                    },
                    actions = {
                        Button(
                            onClick = {
                                val chosenGroups = allGroups.filter { it.id in selectedGroupIds }
                                val updated = initial.copy(
                                    firstName = firstName,
                                    lastName = lastName,
                                    displayName = displayName,
                                    phones = phones.filter { it.number.isNotBlank() },
                                    emails = emails.filter { it.email.isNotBlank() },
                                    organization = organization,
                                    jobTitle = jobTitle,
                                    birthday = birthday,
                                    postalAddress = postalAddress,
                                    notes = notes,
                                    groups = chosenGroups
                                )
                                onSave(updated, pickedPhotoUri, removePhoto)
                            },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("save_contact_button")
                        ) {
                            Text("Enregistrer")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Photo section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ContactAvatar(
                        displayName = displayName.ifBlank { "$firstName $lastName".trim().ifBlank { "MC" } },
                        photoPath = if (removePhoto) null else initial.photoPath,
                        colorSeed = displayName.hashCode().absoluteValue,
                        size = 68.dp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        ) {
                            Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (pickedPhotoUri != null) "Photo sélectionnée" else "Choisir une photo")
                        }
                        if (initial.photoPath != null || pickedPhotoUri != null) {
                            TextButton(onClick = {
                                pickedPhotoUri = null
                                removePhoto = true
                            }) {
                                Text("Retirer la photo", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                HorizontalDivider()

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text("Prénom") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("editor_first_name")
                    )
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text("Nom") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("editor_last_name")
                    )
                }

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Nom complet affiché (optionnel)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_display_name")
                )

                // Phones
                Text("Numéros de téléphone", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                phones.forEachIndexed { index, phone ->
                    var typeMenu by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = phone.number,
                            onValueChange = { phones[index] = phone.copy(number = it) },
                            label = { Text("Téléphone ${index + 1}") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("editor_phone_$index")
                        )
                        Box {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { typeMenu = true }
                            ) {
                                Text(phone.typeLabel, modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp), style = MaterialTheme.typography.labelMedium)
                            }
                            DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                                listOf("Mobile", "Travail", "Domicile", "Fax", "Autre").forEach { t ->
                                    DropdownMenuItem(text = { Text(t) }, onClick = {
                                        phones[index] = phone.copy(typeLabel = t)
                                        typeMenu = false
                                    })
                                }
                            }
                        }
                        if (phones.size > 1) {
                            IconButton(onClick = { phones.removeAt(index) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Supprimer ce numéro")
                            }
                        }
                    }
                }
                TextButton(onClick = { phones.add(PhoneEntry("", "Mobile")) }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ajouter un numéro")
                }

                // Emails
                Text("Adresses e-mail", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary)
                emails.forEachIndexed { index, mail ->
                    var typeMenu by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = mail.email,
                            onValueChange = { emails[index] = mail.copy(email = it) },
                            label = { Text("E-mail ${index + 1}") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("editor_email_$index")
                        )
                        Box {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { typeMenu = true }
                            ) {
                                Text(mail.typeLabel, modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp), style = MaterialTheme.typography.labelMedium)
                            }
                            DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                                listOf("Personnel", "Travail", "Autre").forEach { t ->
                                    DropdownMenuItem(text = { Text(t) }, onClick = {
                                        emails[index] = mail.copy(typeLabel = t)
                                        typeMenu = false
                                    })
                                }
                            }
                        }
                        if (emails.size > 1) {
                            IconButton(onClick = { emails.removeAt(index) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Supprimer cet e-mail")
                            }
                        }
                    }
                }
                TextButton(onClick = { emails.add(EmailEntry("", "Personnel")) }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ajouter un e-mail")
                }

                // Organization & Info
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = organization,
                        onValueChange = { organization = it },
                        label = { Text("Entreprise") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = jobTitle,
                        onValueChange = { jobTitle = it },
                        label = { Text("Fonction") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = birthday,
                    onValueChange = { birthday = it },
                    label = { Text("Date d'anniversaire (ex: 1990-05-14)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = postalAddress,
                    onValueChange = { postalAddress = it },
                    label = { Text("Adresse postale") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes personnelles") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                // Groups Multi-select
                Text("Groupes", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    allGroups.forEach { g ->
                        val selected = g.id in selectedGroupIds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedGroupIds = if (selected) selectedGroupIds - g.id else selectedGroupIds + g.id
                            },
                            label = { Text(g.name) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

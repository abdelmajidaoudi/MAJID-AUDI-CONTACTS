package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.domain.model.ExportFormat
import com.example.domain.model.GroupItem
import com.example.domain.model.OperationProgress
import com.example.ui.theme.AvatarPalette
import com.example.ui.theme.BrandHeaderGradient
import com.example.ui.theme.RoyalBluePrimary
import com.example.ui.theme.TurquoiseBright
import com.example.ui.theme.VioletSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ContactAvatar(
    displayName: String,
    photoPath: String?,
    colorSeed: Int,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = photoPath) {
        value = if (!photoPath.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(photoPath)
                    if (file.exists()) {
                        BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                    } else null
                }.getOrNull()
            }
        } else null
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = displayName,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        val initials = remember(displayName) { computeInitials(displayName) }
        val baseColor = AvatarPalette[colorSeed % AvatarPalette.size]
        val secondColor = AvatarPalette[(colorSeed + 2) % AvatarPalette.size]
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(baseColor, secondColor)))
        ) {
            Text(
                text = initials,
                color = Color.White,
                style = if (size >= 64.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun computeInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> "${words.first().take(1)}${words.last().take(1)}".uppercase()
    }
}

@Composable
fun SplashOverlay(
    visible: Boolean,
    onFinished: () -> Unit
) {
    val scale = remember { Animatable(0.82f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(420, easing = FastOutSlowInEasing))
        scale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        delay(950)
        onFinished()
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(350))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BrandHeaderGradient),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(32.dp)
                    .scale(scale.value)
                    .alpha(alpha.value)
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    shadowElevation = 18.dp,
                    modifier = Modifier.size(124.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_brand_logo),
                        contentDescription = stringResource(id = R.string.cd_brand_logo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Majid Aoudi Contact",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Gestionnaire de contacts professionnel • VCF • Hors ligne",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.88f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(28.dp))
                LinearProgressIndicator(
                    color = TurquoiseBright,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier
                        .width(180.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                )
            }
        }
    }
}

@Composable
fun OperationProgressDialog(
    progress: OperationProgress,
    onCancel: () -> Unit
) {
    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = progress.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (progress.detail.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = progress.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (progress.indeterminate || progress.total <= 0) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(CircleShape),
                        color = RoyalBluePrimary
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(CircleShape),
                        color = VioletSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${(progress.fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("cancel_operation_button")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Annuler")
                    }
                }
            }
        }
    }
}

@Composable
fun ExportOptionsDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onSelectFormat: (ExportFormat) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (selectedCount > 0) "Exporter $selectedCount contact(s)" else "Exporter les contacts",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Choisissez le format d'exportation :",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ExportFormatRow(
                    icon = Icons.Rounded.Download,
                    title = ExportFormat.VCF_SINGLE.label,
                    subtitle = "Compatible Android, iOS, Outlook et vCard 3.0",
                    onClick = { onSelectFormat(ExportFormat.VCF_SINGLE) },
                    testTag = "export_vcf_single"
                )
                if (selectedCount == 0) {
                    ExportFormatRow(
                        icon = Icons.Rounded.FolderCopy,
                        title = ExportFormat.VCF_BY_GROUP.label,
                        subtitle = "Crée un fichier .vcf distinct par groupe dans un dossier",
                        onClick = { onSelectFormat(ExportFormat.VCF_BY_GROUP) },
                        testTag = "export_vcf_by_group"
                    )
                }
                ExportFormatRow(
                    icon = Icons.Rounded.TableChart,
                    title = ExportFormat.CSV.label,
                    subtitle = "UTF-8 avec BOM pour ouverture directe dans Excel",
                    onClick = { onSelectFormat(ExportFormat.CSV) },
                    testTag = "export_csv"
                )
                ExportFormatRow(
                    icon = Icons.Rounded.Code,
                    title = ExportFormat.JSON.label,
                    subtitle = "Sauvegarde structurée complète (photos et champs perso)",
                    onClick = { onSelectFormat(ExportFormat.JSON) },
                    testTag = "export_json"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )
}

@Composable
private fun ExportFormatRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun BatchEditDialog(
    selectedCount: Int,
    groups: List<GroupItem>,
    onDismiss: () -> Unit,
    onApply: (groupId: Long?, organization: String?, noteToAppend: String?) -> Unit
) {
    var selectedGroupId by remember { mutableStateOf<Long?>(null) }
    var organization by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier $selectedCount contact(s)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Ajouter au groupe (optionnel) :",
                    style = MaterialTheme.typography.labelLarge
                )
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedGroupId = null }
                    ) {
                        RadioButton(selected = selectedGroupId == null, onClick = { selectedGroupId = null })
                        Text("Aucun changement de groupe", style = MaterialTheme.typography.bodyMedium)
                    }
                    groups.take(6).forEach { g ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedGroupId = g.id }
                        ) {
                            RadioButton(selected = selectedGroupId == g.id, onClick = { selectedGroupId = g.id })
                            Text("${g.name} (${g.contactCount})", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                OutlinedTextField(
                    value = organization,
                    onValueChange = { organization = it },
                    label = { Text("Définir l'entreprise (optionnel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Ajouter une note (optionnel)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(
                        selectedGroupId,
                        organization.takeIf { it.isNotBlank() },
                        note.takeIf { it.isNotBlank() }
                    )
                },
                modifier = Modifier.testTag("apply_batch_edit_button")
            ) {
                Text("Appliquer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

package com.example

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.PhonebookSyncHelper
import com.example.domain.model.ExportFormat
import com.example.ui.ContactViewModel
import com.example.ui.DriveSyncAction
import com.example.ui.components.BatchEditDialog
import com.example.ui.components.ExportOptionsDialog
import com.example.ui.components.OperationProgressDialog
import com.example.ui.components.SplashOverlay
import com.example.ui.screens.ContactDetailContent
import com.example.ui.screens.ContactEditorDialog
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.GroupsScreen
import com.example.ui.screens.MergeAndToolsScreen
import com.example.ui.screens.SettingsAndGuideScreen
import com.example.ui.theme.MajidAoudiContactTheme
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object AppRoutes {
    const val CONTACTS = "contacts"
    const val GROUPS = "groups"
    const val MERGE_TOOLS = "merge_tools"
    const val SETTINGS = "settings"
}

class MainActivity : ComponentActivity() {

    private val viewModel: ContactViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            MajidAoudiContactTheme(themeMode = themeMode) {
                AAContactsApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri: Uri? = intent.data
            if (uri != null) {
                viewModel.importVcfFiles(listOf(uri))
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        if (cont.isActive) cont.resume(result)
    }
    addOnFailureListener { exc ->
        if (cont.isActive) cont.resumeWithException(exc)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AAContactsApp(viewModel: ContactViewModel) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val coroutineScope = rememberCoroutineScope()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppRoutes.CONTACTS

    val contactsState by viewModel.contactsState.collectAsStateWithLifecycle()
    val mergeState by viewModel.mergeState.collectAsStateWithLifecycle()
    val duplicatesState by viewModel.duplicatesState.collectAsStateWithLifecycle()
    val operationProgress by viewModel.operationProgress.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalContactsCount.collectAsStateWithLifecycle()
    val noPhoneCount by viewModel.noPhoneCount.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val backups by viewModel.backups.collectAsStateWithLifecycle()
    val benchmarkReport by viewModel.benchmarkReport.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val defaultCountryCode by viewModel.defaultCountryCode.collectAsStateWithLifecycle()
    val driveAccountEmail by viewModel.driveAccountEmail.collectAsStateWithLifecycle()
    val lastDriveSyncTimestamp by viewModel.lastDriveSyncTimestamp.collectAsStateWithLifecycle()
    val lastDriveSyncInfo by viewModel.lastDriveSyncInfo.collectAsStateWithLifecycle()
    val isDriveSyncing by viewModel.isDriveSyncing.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showSplash by rememberSaveable { mutableStateOf(true) }

    var pendingExportFormat by remember { mutableStateOf<ExportFormat?>(null) }
    var pendingExportSelectedIds by remember { mutableStateOf<List<Long>?>(null) }
    var pendingExportGroupId by remember { mutableStateOf<Long?>(null) }

    var pendingPhonebookIds by remember { mutableStateOf<List<Long>?>(null) }
    var pendingPhonebookAll by remember { mutableStateOf(false) }

    var pendingDriveAction by remember { mutableStateOf<DriveSyncAction?>(null) }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.shareVcfEvents.collect { req ->
            if (req.preferWhatsApp) {
                try {
                    context.startActivity(PhonebookSyncHelper.buildShareVcfIntent(req.uri, "com.whatsapp"))
                } catch (_: ActivityNotFoundException) {
                    try {
                        context.startActivity(PhonebookSyncHelper.buildShareVcfIntent(req.uri, "com.whatsapp.w4b"))
                    } catch (_: ActivityNotFoundException) {
                        context.startActivity(
                            Intent.createChooser(
                                PhonebookSyncHelper.buildShareVcfIntent(req.uri, null),
                                "Envoyer les contacts sur WhatsApp"
                            )
                        )
                    }
                }
            } else {
                context.startActivity(
                    Intent.createChooser(
                        PhonebookSyncHelper.buildShareVcfIntent(req.uri, null),
                        "Partager les contacts (.vcf)"
                    )
                )
            }
        }
    }

    val writeContactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val ids = if (pendingPhonebookAll) null else pendingPhonebookIds
        pendingPhonebookIds = null
        pendingPhonebookAll = false
        if (granted) {
            viewModel.saveContactsToDevicePhonebook(ids)
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Permission Contacts requise pour enregistrer dans le répertoire du téléphone")
            }
        }
    }

    fun triggerSaveToPhonebook(selectedIds: List<Long>? = null) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.saveContactsToDevicePhonebook(selectedIds)
        } else {
            pendingPhonebookIds = selectedIds
            pendingPhonebookAll = selectedIds == null
            writeContactsPermissionLauncher.launch(Manifest.permission.WRITE_CONTACTS)
        }
    }

    val driveAuthScope = remember { Scope("https://www.googleapis.com/auth/drive.file") }

    val driveAuthResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val action = pendingDriveAction
        pendingDriveAction = null
        if (result.resultCode == Activity.RESULT_OK && result.data != null && activity != null && action != null) {
            runCatching {
                val authResult = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(result.data)
                val token = authResult.accessToken
                if (!token.isNullOrBlank()) {
                    if (driveAccountEmail == null) {
                        viewModel.setDriveAccountEmail("Compte Google connecté")
                    }
                    viewModel.executeDriveAction(token, action)
                } else {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Jeton d'accès Google Drive indisponible")
                    }
                }
            }.onFailure { err ->
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Erreur d'autorisation Drive : ${err.localizedMessage ?: "annulée"}")
                }
            }
        } else if (action != null) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Autorisation Google Drive annulée")
            }
        }
    }

    fun requestDriveSync(action: DriveSyncAction) {
        if (activity == null) return
        coroutineScope.launch {
            try {
                if (driveAccountEmail == null) {
                    val webClientId = context.getString(R.string.google_oauth_web_client_id)
                    if (webClientId.isNotBlank()) {
                        try {
                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(webClientId)
                                .build()
                            val credentialRequest = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()
                            val credManager = CredentialManager.create(context)
                            val credResult = credManager.getCredential(activity, credentialRequest)
                            val googleCred = GoogleIdTokenCredential.createFrom(credResult.credential.data)
                            viewModel.setDriveAccountEmail(googleCred.id)
                        } catch (cancel: GetCredentialCancellationException) {
                            snackbarHostState.showSnackbar("Connexion Google annulée")
                            return@launch
                        } catch (_: Throwable) {
                            // Continue to Identity AuthorizationClient account selector as fallback
                        }
                    }
                }

                val authRequest = AuthorizationRequest.builder()
                    .setRequestedScopes(listOf(driveAuthScope))
                    .build()
                val authResult = Identity.getAuthorizationClient(activity)
                    .authorize(authRequest)
                    .awaitResult()

                val pendingIntent = authResult.pendingIntent
                if (authResult.hasResolution() && pendingIntent != null) {
                    pendingDriveAction = action
                    driveAuthResolutionLauncher.launch(
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    )
                } else {
                    val token = authResult.accessToken
                    if (!token.isNullOrBlank()) {
                        if (driveAccountEmail == null) {
                            viewModel.setDriveAccountEmail("Compte Google connecté")
                        }
                        viewModel.executeDriveAction(token, action)
                    } else {
                        snackbarHostState.showSnackbar("Impossible d'obtenir l'accès Google Drive")
                    }
                }
            } catch (t: Throwable) {
                snackbarHostState.showSnackbar(
                    "Google Drive indisponible : ${t.localizedMessage ?: "vérifiez Google Play Services"}"
                )
            }
        }
    }

    // SAF Document Launchers (Zero storage permissions needed!)
    val importVcfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importVcfFiles(uris)
    }

    val mergeVcfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addMergeVcfFiles(uris)
    }

    val saveMergedVcfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-vcard")
    ) { uri ->
        if (uri != null) viewModel.executeMergeToVcfFile(uri)
    }

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val fmt = pendingExportFormat
        if (uri != null && fmt != null) {
            viewModel.exportToUri(uri, fmt, pendingExportSelectedIds, pendingExportGroupId)
        }
        pendingExportFormat = null
        pendingExportSelectedIds = null
        pendingExportGroupId = null
    }

    val exportFolderTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.exportByGroupToFolder(uri)
    }

    val restoreJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.restoreFromBackupUri(uri, replaceExisting = false)
    }

    fun launchExportFlow(format: ExportFormat, selectedIds: List<Long>? = null, groupId: Long? = null) {
        if (format == ExportFormat.VCF_BY_GROUP) {
            exportFolderTreeLauncher.launch(null)
            return
        }
        pendingExportFormat = format
        pendingExportSelectedIds = selectedIds
        pendingExportGroupId = groupId
        val defaultName = when (format) {
            ExportFormat.VCF_SINGLE -> "AA_Contacts.vcf"
            ExportFormat.CSV -> "AA_Contacts.csv"
            ExportFormat.JSON -> "AA_Contacts_Sauvegarde.json"
            ExportFormat.VCF_BY_GROUP -> "AA_Contacts.vcf"
        }
        exportFileLauncher.launch(defaultName)
    }

    val vcfMimeTypes = arrayOf("text/x-vcard", "text/vcard", "*/*")

    val navItems = listOf(
        Triple(AppRoutes.CONTACTS, stringResource(R.string.nav_contacts), Icons.Rounded.Contacts to Icons.Outlined.Contacts),
        Triple(AppRoutes.GROUPS, stringResource(R.string.nav_groups), Icons.Rounded.FolderShared to Icons.Outlined.FolderShared),
        Triple(AppRoutes.MERGE_TOOLS, stringResource(R.string.nav_merge), Icons.AutoMirrored.Rounded.MergeType to Icons.AutoMirrored.Outlined.MergeType),
        Triple(AppRoutes.SETTINGS, stringResource(R.string.nav_settings), Icons.Rounded.Settings to Icons.Outlined.Settings)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 640.dp
            val useSplitPane = maxWidth >= 880.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useNavRail) {
                        NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                            navItems.forEach { (route, label, icons) ->
                                val selected = currentRoute == route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(route) {
                                            popUpTo(AppRoutes.CONTACTS) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            if (selected) icons.first else icons.second,
                                            contentDescription = label
                                        )
                                    },
                                    label = { Text(label, maxLines = 1) },
                                    modifier = Modifier.testTag("nav_$route")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavRail) {
                        NavigationRail(
                            header = {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_brand_logo),
                                        contentDescription = stringResource(R.string.cd_brand_logo),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))
                            navItems.forEach { (route, label, icons) ->
                                val selected = currentRoute == route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(route) {
                                            popUpTo(AppRoutes.CONTACTS) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            if (selected) icons.first else icons.second,
                                            contentDescription = label
                                        )
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("rail_$route")
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = AppRoutes.CONTACTS,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        composable(AppRoutes.CONTACTS) {
                            ContactsScreen(
                                state = contactsState,
                                totalContactsCount = totalCount,
                                noPhoneCount = noPhoneCount,
                                defaultCountryCode = defaultCountryCode,
                                groups = groups,
                                useSplitPane = useSplitPane,
                                onSearchChanged = viewModel::onSearchQueryChanged,
                                onFilterChanged = viewModel::onFilterChanged,
                                onGroupSelected = viewModel::onGroupFilterChanged,
                                onLetterSelected = viewModel::onLetterSelected,
                                onSortChanged = viewModel::onSortOrderChanged,
                                onPageChanged = viewModel::goToPage,
                                onImportVcf = { importVcfLauncher.launch(vcfMimeTypes) },
                                onNavigateToMerge = {
                                    navController.navigate(AppRoutes.MERGE_TOOLS) {
                                        popUpTo(AppRoutes.CONTACTS) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onOpenExportAll = { viewModel.openExportSheet(null) },
                                onAddContact = viewModel::openNewContactEditor,
                                onInspectContact = viewModel::inspectContact,
                                onEditContact = viewModel::openEditContactEditor,
                                onDuplicateContact = viewModel::duplicateContact,
                                onDeleteContacts = viewModel::deleteContacts,
                                onToggleSelect = viewModel::toggleSelectContact,
                                onSelectRangeTo = viewModel::selectRangeTo,
                                onSelectAllPage = viewModel::selectAllOnCurrentPage,
                                onClearSelection = viewModel::clearSelection,
                                onOpenBatchEdit = { viewModel.setShowBatchEditDialog(true) },
                                onExportSelection = { ids -> viewModel.openExportSheet(ids) },
                                onShareWhatsAppSelection = { ids ->
                                    viewModel.shareContactsVcf(selectedIds = ids, preferWhatsApp = true)
                                },
                                onSavePhonebookSelection = { ids -> triggerSaveToPhonebook(ids) },
                                onAssignToGroup = viewModel::assignSingleContactToGroup,
                                detailPaneContent = { detail ->
                                    ContactDetailContent(
                                        contact = detail,
                                        defaultCountryCode = defaultCountryCode,
                                        onClose = viewModel::closeInspection,
                                        onEdit = { viewModel.openEditContactEditor(detail.id) },
                                        onDuplicate = { viewModel.duplicateContact(detail.id) },
                                        onExportSingle = { viewModel.openExportSheet(listOf(detail.id)) },
                                        onShareWhatsAppVcf = {
                                            viewModel.shareContactsVcf(
                                                selectedIds = listOf(detail.id),
                                                preferWhatsApp = true,
                                                fileName = "${detail.displayName.ifBlank { "contact" }}.vcf"
                                            )
                                        },
                                        onDelete = { viewModel.deleteContacts(listOf(detail.id)) }
                                    )
                                }
                            )

                            if (!useSplitPane && contactsState.inspectedContact != null) {
                                val detail = contactsState.inspectedContact!!
                                ModalBottomSheet(
                                    onDismissRequest = viewModel::closeInspection,
                                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                                ) {
                                    ContactDetailContent(
                                        contact = detail,
                                        defaultCountryCode = defaultCountryCode,
                                        onClose = viewModel::closeInspection,
                                        onEdit = { viewModel.openEditContactEditor(detail.id) },
                                        onDuplicate = { viewModel.duplicateContact(detail.id) },
                                        onExportSingle = { viewModel.openExportSheet(listOf(detail.id)) },
                                        onShareWhatsAppVcf = {
                                            viewModel.shareContactsVcf(
                                                selectedIds = listOf(detail.id),
                                                preferWhatsApp = true,
                                                fileName = "${detail.displayName.ifBlank { "contact" }}.vcf"
                                            )
                                        },
                                        onDelete = { viewModel.deleteContacts(listOf(detail.id)) }
                                    )
                                }
                            }
                        }

                        composable(AppRoutes.GROUPS) {
                            GroupsScreen(
                                groups = groups,
                                onOpenGroupContacts = { grpId ->
                                    viewModel.onGroupFilterChanged(grpId)
                                    navController.navigate(AppRoutes.CONTACTS) {
                                        popUpTo(AppRoutes.CONTACTS) { saveState = true }
                                        launchSingleTop = true
                                    }
                                },
                                onCreateGroup = viewModel::createGroup,
                                onRenameGroup = viewModel::renameGroup,
                                onDeleteGroup = viewModel::deleteGroup,
                                onExportGroupVcf = { grpId ->
                                    launchExportFlow(ExportFormat.VCF_SINGLE, selectedIds = null, groupId = grpId)
                                }
                            )
                        }

                        composable(AppRoutes.MERGE_TOOLS) {
                            MergeAndToolsScreen(
                                mergeState = mergeState,
                                duplicatesState = duplicatesState,
                                totalContactsCount = totalCount,
                                onPickMergeFiles = { mergeVcfPickerLauncher.launch(vcfMimeTypes) },
                                onRemoveMergeFile = viewModel::removeMergeSource,
                                onClearMergeFiles = viewModel::clearMergeSources,
                                onToggleMergeContact = viewModel::toggleMergeContact,
                                onSetMergeFileSelectAll = viewModel::setMergeFileSelectAll,
                                onSetMergeStrategy = viewModel::setMergeStrategy,
                                onSaveMergedToDatabase = viewModel::executeMergeToDatabase,
                                onExportMergedVcf = { saveMergedVcfLauncher.launch("AA_Contacts.vcf") },
                                onScanDuplicates = viewModel::scanDuplicates,
                                onMergeDuplicateCluster = viewModel::mergeDuplicateCluster,
                                onDeleteDuplicateContact = viewModel::deleteSingleFromCluster,
                                onTriggerExport = { format -> launchExportFlow(format) },
                                onShareWhatsApp = { viewModel.shareContactsVcf(selectedIds = null, preferWhatsApp = true) },
                                onSaveToPhonebook = { triggerSaveToPhonebook(null) }
                            )
                        }

                        composable(AppRoutes.SETTINGS) {
                            SettingsAndGuideScreen(
                                themeMode = themeMode,
                                totalContactsCount = totalCount,
                                backups = backups,
                                benchmarkReport = benchmarkReport,
                                defaultCountryCode = defaultCountryCode,
                                driveAccountEmail = driveAccountEmail,
                                lastDriveSyncTimestamp = lastDriveSyncTimestamp,
                                lastDriveSyncInfo = lastDriveSyncInfo,
                                isDriveSyncing = isDriveSyncing,
                                onThemeChanged = viewModel::setThemeMode,
                                onDefaultCountryCodeChanged = viewModel::setDefaultCountryCode,
                                onTriggerDriveSync = ::requestDriveSync,
                                onDisconnectDrive = viewModel::disconnectDrive,
                                onCreateBackup = viewModel::createManualBackup,
                                onRestoreBackupFile = viewModel::restoreFromBackup,
                                onRestoreFromExternalJson = {
                                    restoreJsonLauncher.launch(arrayOf("application/json", "*/*"))
                                },
                                onRunBenchmark = viewModel::runPerformanceBenchmark,
                                onClearAllContacts = viewModel::clearAllContacts
                            )
                        }
                    }
                }
            }
        }

        // Modals & Overlays
        contactsState.editingContact?.let { editing ->
            ContactEditorDialog(
                initial = editing,
                allGroups = groups,
                onDismiss = viewModel::closeEditor,
                onSave = viewModel::saveContact
            )
        }

        if (contactsState.showBatchEditDialog) {
            BatchEditDialog(
                selectedCount = contactsState.selectedIds.size,
                groups = groups,
                onDismiss = { viewModel.setShowBatchEditDialog(false) },
                onApply = viewModel::applyBatchEdit
            )
        }

        if (contactsState.showExportSheet) {
            val selIds = contactsState.pendingExportContactIds
            ExportOptionsDialog(
                selectedCount = selIds?.size ?: 0,
                onDismiss = viewModel::closeExportSheet,
                onSelectFormat = { format ->
                    viewModel.closeExportSheet()
                    launchExportFlow(format, selectedIds = selIds)
                },
                onShareWhatsApp = {
                    viewModel.closeExportSheet()
                    viewModel.shareContactsVcf(selectedIds = selIds, preferWhatsApp = true)
                },
                onSaveToPhonebook = {
                    viewModel.closeExportSheet()
                    triggerSaveToPhonebook(selIds)
                }
            )
        }

        operationProgress?.let { prog ->
            OperationProgressDialog(
                progress = prog,
                onCancel = viewModel::cancelActiveOperation
            )
        }

        SplashOverlay(
            visible = showSplash,
            onFinished = { showSplash = false }
        )
    }
}

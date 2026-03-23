package com.jasper.app.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jasper.app.JasperApplication
import com.jasper.app.R
import com.jasper.app.ui.state.HomeUiState
import com.jasper.app.ui.state.UserWithStats
import com.jasper.app.viewmodel.HomeViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToRegister: () -> Unit,
    onNavigateToRegisterAddCaptures: (Int) -> Unit,
    onNavigateToRecognize: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToKiosk: () -> Unit,
    onNavigateToAttendance: () -> Unit,
    onNavigateToAnalytics: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as JasperApplication
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(app.repository))
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNavigateToAnalytics) {
                        Icon(Icons.Default.Analytics, contentDescription = stringResource(R.string.analytics))
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = stringResource(R.string.history))
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Primary action buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onNavigateToRegister, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.register_face))
                }
                OutlinedButton(onClick = onNavigateToRecognize, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.recognize))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onNavigateToAttendance, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.attendance))
                }
                OutlinedButton(onClick = onNavigateToKiosk, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.kiosk_mode))
                }
            }

            Spacer(Modifier.height(16.dp))

            when (val state = uiState) {
                HomeUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                is HomeUiState.Error -> Text(
                    state.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(8.dp)
                )
                is HomeUiState.Ready -> {
                    if (state.users.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Face, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.no_users_registered),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Text(
                            stringResource(R.string.registered_users, state.users.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.users, key = { it.user.id }) { uws ->
                                UserRow(
                                    uws = uws,
                                    photoFile = app.repository.profilePhotoFile(uws.user.id),
                                    onDelete = { vm.deleteUser(uws.user.id) },
                                    onRename = { newName -> vm.renameUser(uws.user.id, newName) },
                                    onAddCaptures = { onNavigateToRegisterAddCaptures(uws.user.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRow(
    uws: UserWithStats,
    photoFile: File,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onAddCaptures: () -> Unit
) {
    val context = LocalContext.current
    var showRenameDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile photo (circular thumbnail)
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(photoFile)
                    .crossfade(true)
                    .build(),
                contentDescription = uws.user.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                fallback = null,
                placeholder = null,
                error = null
            )
            if (!photoFile.exists()) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Face, contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(uws.user.name, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                val registeredDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    .format(Date(uws.user.createdAt))
                val statsText = if (uws.recognitionCount > 0) {
                    val lastSeen = uws.lastSeenAt?.let { formatRelativeTime(it) } ?: ""
                    "Seen ${uws.recognitionCount}× · Last: $lastSeen"
                } else {
                    "Registered $registeredDate"
                }
                Text(statsText, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Add captures button
            IconButton(onClick = onAddCaptures) {
                Icon(Icons.Default.AddCircleOutline,
                    contentDescription = stringResource(R.string.add_captures),
                    tint = MaterialTheme.colorScheme.primary)
            }
            // Rename button
            IconButton(onClick = { showRenameDialog = true }) {
                Icon(Icons.Default.Edit,
                    contentDescription = stringResource(R.string.rename_user),
                    tint = MaterialTheme.colorScheme.secondary)
            }
            // Delete button
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_user, uws.user.name),
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showRenameDialog) {
        RenameDialog(
            currentName = uws.user.name,
            onConfirm = { newName -> onRename(newName); showRenameDialog = false },
            onDismiss = { showRenameDialog = false }
        )
    }
}

@Composable
private fun RenameDialog(currentName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_user_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name_label)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

private fun formatRelativeTime(ms: Long): String {
    val diff = System.currentTimeMillis() - ms
    return when {
        diff < TimeUnit.MINUTES.toMillis(1)  -> "just now"
        diff < TimeUnit.HOURS.toMillis(1)    -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
        diff < TimeUnit.DAYS.toMillis(1)     -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
        diff < TimeUnit.DAYS.toMillis(7)     -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
        else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(ms))
    }
}

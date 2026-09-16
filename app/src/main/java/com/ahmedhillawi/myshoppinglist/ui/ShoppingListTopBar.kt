package com.ahmedhillawi.myshoppinglist.ui

import android.app.LocaleManager
import android.os.LocaleList
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import com.ahmedhillawi.myshoppinglist.BuildConfig
import com.ahmedhillawi.myshoppinglist.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

// Extracted out of ShoppingListScreen to keep that composable's own cognitive complexity down
// (see CLAUDE.md's Testing/architecture notes on why this file exists). Owns its own
// menuExpanded state -- nothing else in ShoppingListScreen needs to read it.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListTopBar(
    householdName: String,
    onCopyList: () -> Unit,
    onShareList: () -> Unit,
    onCopyInviteCode: () -> Unit,
    onShowAccountScreen: () -> Unit,
    onLogout: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Column {
                Text(stringResource(R.string.app_name))
                Text(householdName, style = MaterialTheme.typography.labelMedium)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        actions = {
            IconButton(onClick = onCopyList) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy_list_description))
            }
            IconButton(onClick = onShareList) {
                Icon(imageVector = Icons.Default.Share, contentDescription = stringResource(R.string.share_list_description))
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options_description))
                }
                ShoppingListOverflowMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    onCopyInviteCode = onCopyInviteCode,
                    onShowAccountScreen = onShowAccountScreen,
                    onLogout = onLogout
                )
            }
        }
    )
}

@Composable
private fun ShoppingListOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCopyInviteCode: () -> Unit,
    onShowAccountScreen: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val localeManager = context.getSystemService(LocaleManager::class.java)
    val currentTag = if (!localeManager.applicationLocales.isEmpty) {
        localeManager.applicationLocales[0].toLanguageTag()
    } else "en"
    val targetLanguageLabel = if (currentTag.contains("ar")) "English" else "العربية"

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.copy_invite_code)) },
            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
            onClick = {
                onCopyInviteCode()
                onDismiss()
            }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(text = targetLanguageLabel, style = MaterialTheme.typography.labelLarge) },
            leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
            onClick = {
                val newTag = if (currentTag.contains("ar")) "en" else "ar"
                // Applying the new locale triggers Activity recreation automatically.
                localeManager.applicationLocales = LocaleList.forLanguageTags(newTag)
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.account_menu_item)) },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            onClick = {
                onShowAccountScreen()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.logout)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Default.ExitToApp, contentDescription = null) },
            onClick = onLogout
        )
        HorizontalDivider()
        DropdownMenuItem(
            enabled = false,
            text = {
                Text(
                    text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TIMESTAMP})",
                    style = MaterialTheme.typography.labelSmall
                )
            },
            onClick = {}
        )
    }
}

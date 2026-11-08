package com.cj.tapblok

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cj.tapblok.database.AppDatabase
import com.cj.tapblok.database.BlockedSite
import com.cj.tapblok.ui.theme.TapBlokTheme
import kotlinx.coroutines.launch

class BlockedSitesActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TapBlokTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Blocked Websites") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    }
                ) { padding ->
                    BlockedSitesScreen(modifier = Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
fun BlockedSitesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { AppSettings.prefs(context) }
    val dao = remember { AppDatabase.getDatabase(context).blockedSiteDao() }
    val scope = rememberCoroutineScope()

    val sitesFlow = remember { dao.getAllBlockedSites() }
    val sites by sitesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var adultFilter by remember { mutableStateOf(prefs.getBoolean(AppSettings.KEY_ADULT_FILTER_ENABLED, true)) }
    var input by remember { mutableStateOf("") }
    var isServiceActive by remember { mutableStateOf(isServiceRunning(context, AppMonitoringService::class.java)) }
    var blockerEnabled by remember { mutableStateOf(WebsiteBlockerService.isEnabled(context)) }

    // Rules can't be loosened mid-session, same as Settings
    val editable = !isServiceActive

    fun addPattern() {
        val pattern = WebsiteFilter.normalize(input).trimEnd('/')
        if (pattern.isBlank()) return
        scope.launch { dao.insert(BlockedSite(pattern)) }
        input = ""
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        if (!blockerEnabled) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Website blocking is off", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Turn on \"TapBlok website blocker\" in Accessibility settings. " +
                                    "It only reads the browser's address bar during a session.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Text(
                                text = "If the option is greyed out, open App info › ⋮ › " +
                                        "\"Allow restricted settings\" first.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }) { Text("Accessibility") }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                OutlinedButton(onClick = {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null)
                                        )
                                    )
                                }) { Text("App info") }
                            }
                        }
                    }
                }
            }
        }

        if (isServiceActive) {
            item {
                Text(
                    text = "Website rules are locked while a session is active.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Block adult content", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Built-in list of adult sites and keywords",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = adultFilter,
                        enabled = editable,
                        onCheckedChange = {
                            adultFilter = it
                            prefs.edit { putBoolean(AppSettings.KEY_ADULT_FILTER_ENABLED, it) }
                        }
                    )
                }
            }
        }

        item {
            Text(
                text = "Your rules",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    enabled = editable,
                    singleLine = true,
                    label = { Text("Domain or keyword") },
                    placeholder = { Text("example.com") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addPattern() }),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { addPattern() }, enabled = editable && input.isNotBlank()) {
                    Text("Add")
                }
            }
            Text(
                text = "A domain (example.com) blocks that site and its subdomains. " +
                        "A word without a dot blocks any address that contains it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (sites.isEmpty()) {
            item {
                Text(
                    text = "No custom rules yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(sites, key = { it.pattern }) { site ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = site.pattern,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { scope.launch { dao.delete(site) } },
                        enabled = editable
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove ${site.pattern}")
                    }
                }
            }
        }
    }

    // Re-check session lock and accessibility state when returning from system settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceActive = isServiceRunning(context, AppMonitoringService::class.java)
                blockerEnabled = WebsiteBlockerService.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

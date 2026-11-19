package me.paliyo.launcher

import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.UserHandle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color as C
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val Dim = C(0xFF8A8A8A)
private val Accent = C(0xFFFF6B4A)

class HomeActivity : ComponentActivity() {
    private var listOpen by mutableStateOf(false)
    private var query by mutableStateOf("")
    private var sheetFor by mutableStateOf<AppEntry?>(null)
    private var appsVersion by mutableIntStateOf(0)
    private val appsCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(p: String, u: UserHandle) { appsVersion++ }
        override fun onPackageAdded(p: String, u: UserHandle) { appsVersion++ }
        override fun onPackageChanged(p: String, u: UserHandle) { appsVersion++ }
        override fun onPackagesAvailable(p: Array<String>, u: UserHandle, r: Boolean) { appsVersion++ }
        override fun onPackagesUnavailable(p: Array<String>, u: UserHandle, r: Boolean) { appsVersion++ }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        getSystemService(LauncherApps::class.java).registerCallback(appsCallback)
        val settings = AppSettings(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = C.Black, surface = C(0xFF111111), primary = Accent, onPrimary = C.Black)) {
                var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
                LaunchedEffect(appsVersion) { apps = withContext(Dispatchers.Default) { loadApps(this@HomeActivity) } }

                Box(Modifier.fillMaxSize().background(C.Black).systemBarsPadding().imePadding()) {
                    if (listOpen) AppList(apps, settings, onLong = { sheetFor = it })
                    else Home(apps, settings, onLong = { sheetFor = it })
                }
                sheetFor?.let { OptionsSheet(it, settings) { sheetFor = null } }
            }
        }
    }

    // Home pressed while already home: back to the home view, search cleared.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        listOpen = false
        query = ""
        sheetFor = null
    }

    override fun onDestroy() {
        getSystemService(LauncherApps::class.java).unregisterCallback(appsCallback)
        super.onDestroy()
    }

    private fun open(app: AppEntry, settings: AppSettings) {
        launch(this, app, app.key in settings.distracting)
        listOpen = false
        query = ""
    }

    @Composable
    private fun Home(apps: List<AppEntry>, settings: AppSettings, onLong: (AppEntry) -> Unit) {
        BackHandler {} // home swallows Back
        val ctx = LocalContext.current
        val now by produceState(LocalDateTime.now()) {
            while (true) {
                value = LocalDateTime.now()
                delay(60_000 - System.currentTimeMillis() % 60_000)
            }
        }
        val byKey = apps.associateBy { it.key }
        var drag = 0f
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp).pointerInput(Unit) {
                detectVerticalDragGestures(onDragStart = { drag = 0f }, onDragEnd = { if (drag < -120f) listOpen = true }) { _, dy -> drag += dy }
            }
        ) {
            Spacer(Modifier.height(32.dp))
            Text(
                now.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm")),
                fontSize = 88.sp, fontWeight = FontWeight.Light, color = C.White,
            )
            Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), fontSize = 16.sp, color = Dim)
            Spacer(Modifier.weight(1f))
            settings.favorites.mapNotNull { byKey[it] }.forEach { AppRow(it, settings, 28, onLong) }
            if (settings.favorites.isEmpty()) Text("Long-press an app in the list to add it here.", color = Dim, fontSize = 14.sp)
            Spacer(Modifier.height(32.dp))
            Text(
                "search",
                Modifier.fillMaxWidth().combinedClickable(onClick = { listOpen = true }).padding(vertical = 12.dp),
                color = Dim, fontSize = 16.sp,
            )
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun AppList(apps: List<AppEntry>, settings: AppSettings, onLong: (AppEntry) -> Unit) {
        BackHandler { listOpen = false; query = "" }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        // Hidden apps leave the list but still show up in search.
        val shown = if (query.isBlank()) apps.filter { it.key !in settings.hidden } else search(apps, query)
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
            TextField(
                query, { query = it },
                Modifier.fillMaxWidth().focusRequester(focus).padding(vertical = 12.dp),
                placeholder = { Text("Search apps") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { shown.firstOrNull()?.let { open(it, settings) } }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = C.Transparent, unfocusedContainerColor = C.Transparent,
                    focusedIndicatorColor = Dim, unfocusedIndicatorColor = Dim,
                ),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                if (query.isBlank()) {
                    shown.groupBy { it.label.firstOrNull()?.uppercaseChar()?.takeIf(Char::isLetter) ?: '#' }.forEach { (letter, group) ->
                        stickyHeader {
                            Text(letter.toString(), Modifier.fillMaxWidth().background(C.Black).padding(top = 12.dp), color = Dim, fontSize = 12.sp)
                        }
                        items(group, key = { it.key }) { AppRow(it, settings, 22, onLong) }
                    }
                } else {
                    items(shown, key = { it.key }) { AppRow(it, settings, 22, onLong) }
                }
            }
        }
    }

    @Composable
    private fun AppRow(app: AppEntry, settings: AppSettings, size: Int, onLong: (AppEntry) -> Unit) {
        Text(
            app.label,
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = { open(app, settings) }, onLongClick = { onLong(app) })
                .padding(vertical = 8.dp),
            fontSize = size.sp,
            color = if (app.key in settings.distracting) Dim else C.White,
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun OptionsSheet(app: AppEntry, settings: AppSettings, onDismiss: () -> Unit) {
        val la = getSystemService(LauncherApps::class.java)
        val isFav = app.key in settings.favorites
        val homeFull = !isFav && settings.favorites.size >= AppSettings.MAX_FAVORITES
        ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C(0xFF111111)) {
            Column(Modifier.padding(horizontal = 28.dp).padding(bottom = 24.dp)) {
                Text(app.label, fontSize = 30.sp, fontWeight = FontWeight.Medium, color = C.White)
                Spacer(Modifier.height(12.dp))
                Toggle(if (homeFull) "Show on home (home is full)" else "Show on home", isFav, enabled = !homeFull) { settings.toggleFavorite(app.key) }
                Toggle("Hide from app list", app.key in settings.hidden) { settings.toggleHidden(app.key) }
                Toggle("Mark as distracting", app.key in settings.distracting) { settings.toggleDistracting(app.key) }
                Action("App info") { la.startAppDetailsActivity(app.component, app.user, null, null); onDismiss() }
                Action("Uninstall") {
                    startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.component.packageName}")))
                    onDismiss()
                }
            }
        }
    }

    @Composable
    private fun Toggle(label: String, checked: Boolean, enabled: Boolean = true, onToggle: () -> Unit) {
        Row(
            Modifier.fillMaxWidth()
                .toggleable(checked, enabled = enabled, role = Role.Switch) { onToggle() }
                .padding(vertical = 4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f), fontSize = 18.sp, color = if (enabled) C.White else Dim)
            Switch(checked, null, enabled = enabled)
        }
    }

    @Composable
    private fun Action(label: String, onClick: () -> Unit) {
        TextButton(onClick, Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            Text(label, Modifier.fillMaxWidth(), fontSize = 18.sp, color = C.White)
        }
    }
}

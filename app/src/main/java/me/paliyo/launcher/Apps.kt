package me.paliyo.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.Collator
import java.text.Normalizer

data class AppEntry(
    val label: String,
    val component: ComponentName,
    val user: UserHandle, // work profile apps share package names
    val key: String,
)

fun loadApps(ctx: Context): List<AppEntry> {
    val la = ctx.getSystemService(LauncherApps::class.java)
    val um = ctx.getSystemService(UserManager::class.java)
    val collator = Collator.getInstance()
    return la.profiles.flatMap { user ->
        val serial = um.getSerialNumberForUser(user)
        la.getActivityList(null, user).map {
            AppEntry(it.label.toString(), it.componentName, user, "${it.componentName.flattenToString()}#$serial")
        }
    }.filter { it.component.packageName != ctx.packageName }
        .sortedWith(compareBy(collator) { it.label })
}

fun normalize(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

/** 0 = prefix, 1 = word start ("maps" in "google maps"), 2 = contains, null = no match. */
fun matchRank(label: String, query: String): Int? {
    val l = normalize(label)
    val q = normalize(query).trim()
    return when {
        l.startsWith(q) -> 0
        l.split(Regex("[^\\p{L}\\p{N}]+")).any { it.startsWith(q) } -> 1
        q in l -> 2
        else -> null
    }
}

fun search(apps: List<AppEntry>, query: String): List<AppEntry> =
    apps.mapNotNull { a -> matchRank(a.label, query)?.let { it to a } }
        .sortedBy { it.first } // stable: keeps alphabetical order within a rank
        .map { it.second }

// ponytail: SharedPreferences instead of Room; move to Room when phase 4 adds the intent log and limits.
class AppSettings(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var favorites by mutableStateOf(p.getString("favorites", "")!!.split('\n').filter { it.isNotEmpty() })
        private set
    var hidden by mutableStateOf(p.getStringSet("hidden", emptySet())!!.toSet())
        private set
    var distracting by mutableStateOf(p.getStringSet("distracting", emptySet())!!.toSet())
        private set

    fun toggleFavorite(key: String) {
        favorites = if (key in favorites) favorites - key else (favorites + key).take(MAX_FAVORITES)
        p.edit().putString("favorites", favorites.joinToString("\n")).apply()
    }

    fun toggleHidden(key: String) {
        hidden = hidden.toggle(key)
        p.edit().putStringSet("hidden", hidden).apply()
    }

    fun toggleDistracting(key: String) {
        distracting = distracting.toggle(key)
        p.edit().putStringSet("distracting", distracting).apply()
    }

    private fun Set<String>.toggle(k: String) = if (k in this) this - k else this + k

    companion object { const val MAX_FAVORITES = 5 }
}

fun launch(ctx: Context, app: AppEntry, distracting: Boolean) {
    if (distracting) {
        ctx.startActivity(
            Intent(ctx, FrictionActivity::class.java)
                .putExtra("component", app.component)
                .putExtra("user", app.user)
                .putExtra("label", app.label)
        )
    } else {
        ctx.getSystemService(LauncherApps::class.java).startMainActivity(app.component, app.user, null, null)
    }
}

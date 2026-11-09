package dev.minimal.launcher

import android.Manifest
import android.content.ComponentName
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.telephony.TelephonyManager
import android.location.LocationManager
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import android.os.Bundle
import android.os.Environment
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.util.Patterns
import androidx.compose.ui.text.font.FontFamily
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import org.json.JSONObject

// ---------- Apps ----------

data class App(val label: String, val component: ComponentName) {
    val key: String get() = component.flattenToString()
}

@Suppress("DEPRECATION")
fun loadApps(ctx: Context): List<App> {
    val pm = ctx.packageManager
    val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(query, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map {
            App(
                it.loadLabel(pm).toString(),
                ComponentName(it.activityInfo.packageName, it.activityInfo.name),
            )
        }
        .sortedBy { it.label.lowercase() }
}

fun defaultFavorites(apps: List<App>): List<App> =
    listOf("phone", "messages", "camera", "notes")
        .mapNotNull { name -> apps.firstOrNull { it.label.contains(name, ignoreCase = true) } }
        .distinct()

// ---------- Settings model ----------

/** Fonts that ship with Android, so nothing needs to be bundled. */
enum class AppFont(val label: String, val family: FontFamily) {
    SANS("Sans", FontFamily.SansSerif),
    GOOGLE_SANS("Google Sans", FontFamily(Typeface.create("google-sans", Typeface.NORMAL))),
    CONDENSED("Condensed", FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))),
    SERIF("Serif", FontFamily.Serif),
    MONO("Monospace", FontFamily.Monospace),
    CASUAL("Casual", FontFamily(Typeface.create("casual", Typeface.NORMAL))),
}

/**
 * Many phones (Pixels included) ship a system font with no real Thin weight, so asking for "thin" looks the
 * same as light. This is the Roboto Thin that Android keeps under its own name.
 */
val ThinFamily: FontFamily = FontFamily(Typeface.create("sans-serif-thin", Typeface.NORMAL))

/**
 * Google Sans is Google's own font and isn't licensed for bundling in other apps, so this uses the copy the phone
 * already has (Pixels and some other Android phones ship it). Elsewhere Android quietly falls back to its normal sans.
 */
fun googleSansInstalled(): Boolean =
    listOf("/system/fonts", "/product/fonts", "/system_ext/fonts", "/vendor/fonts").any { dir ->
        java.io.File(dir).listFiles()?.any { it.name.startsWith("GoogleSans", ignoreCase = true) } == true
    }

fun Config.fontFamily(): FontFamily = font.family

enum class ClockStyle(val label: String) {
    LARGE("Large"), THIN("Thin"), BOLD("Bold"), TWO_TONE("Two-tone"), COMPACT("Compact"), MATERIAL("Material"),
}

enum class HomeAlign(val label: String) {
    LEFT("Left"), CENTER("Center"), RIGHT("Right"),
}

enum class HourFormat(val label: String) {
    SYSTEM("Follow system"), H12("12-hour"), H24("24-hour"),
}

/** [pattern] is a java.time pattern; null hides the date. Labels are previews of today's date. */
enum class DateStyle(val pattern: String?) {
    LONG("EEEE, d MMMM"),
    LONG_US("EEEE, MMMM d"),
    SHORT("EEE, MMM d"),
    DAY_MONTH("d MMM"),
    NUMERIC_US("M/d/yyyy"),
    NUMERIC_DMY("d/M/yyyy"),
    ISO("yyyy-MM-dd"),
    OFF(null),
}

enum class Engine(val label: String, val base: String) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    DUCK("DuckDuckGo", "https://duckduckgo.com/?q="),
    BRAVE("Brave", "https://search.brave.com/search?q="),
    BING("Bing", "https://www.bing.com/search?q="),
}

data class Config(
    val font: AppFont = AppFont.SANS,
    val clock: ClockStyle = ClockStyle.LARGE,
    val hours: HourFormat = HourFormat.SYSTEM,
    val date: DateStyle = DateStyle.LONG,
    val weatherOn: Boolean = false,
    val city: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val fahrenheit: Boolean = false,
    val calendarOn: Boolean = false,
    val searchApps: Boolean = true,
    val searchContacts: Boolean = false,
    val searchFiles: Boolean = false,
    val searchWeb: Boolean = true,
    val engine: Engine = Engine.GOOGLE,
    val clockApp: String = "",
    val calendarApp: String = "",
    val weatherApp: String = "",
    val weatherLocation: Boolean = false,
    val widgetsOn: Boolean = false,
    val notifDots: Boolean = false,
    val hideStatusBar: Boolean = false,
    val showBattery: Boolean = true,
    val showClock: Boolean = true,
    val homeAlign: HomeAlign = HomeAlign.LEFT,
    val mediaPlayer: Boolean = true,
    val lowercaseApps: Boolean = false,
    val pitchBlack: Boolean = false,
    val centerApps: Boolean = false,
    val animations: Boolean = true,
    val drawerRight: Boolean = false,
    val swipeUpSearch: Boolean = false,
    val noHaptics: Boolean = false,
    val hidden: Set<String> = emptySet(),
)

fun appPrefs(ctx: Context): SharedPreferences =
    ctx.getSharedPreferences("minimal", Context.MODE_PRIVATE)

private inline fun <reified E : Enum<E>> SharedPreferences.readEnum(key: String, default: E): E {
    val saved = getString(key, null)
    return enumValues<E>().firstOrNull { it.name == saved } ?: default
}

fun loadConfig(ctx: Context): Config {
    val p = appPrefs(ctx)
    return Config(
        font = p.readEnum("font", AppFont.SANS),
        clock = p.readEnum("clock", ClockStyle.LARGE),
        hours = p.readEnum("hours", HourFormat.SYSTEM),
        date = p.readEnum("date", DateStyle.LONG),
        weatherOn = p.getBoolean("weatherOn", false),
        city = p.getString("city", "") ?: "",
        lat = p.getString("lat", null)?.toDoubleOrNull() ?: 0.0,
        lon = p.getString("lon", null)?.toDoubleOrNull() ?: 0.0,
        fahrenheit = p.getBoolean("fahrenheit", Locale.getDefault().country == "US"),
        calendarOn = p.getBoolean("calendarOn", false),
        searchApps = p.getBoolean("searchApps", true),
        searchContacts = p.getBoolean("searchContacts", false),
        searchFiles = p.getBoolean("searchFiles", false),
        searchWeb = p.getBoolean("searchWeb", true),
        engine = p.readEnum("engine", Engine.GOOGLE),
        clockApp = p.getString("clockApp", "") ?: "",
        calendarApp = p.getString("calendarApp", "") ?: "",
        weatherApp = p.getString("weatherApp", "") ?: "",
        weatherLocation = p.getBoolean("weatherLocation", false),
        widgetsOn = p.getBoolean("widgetsOn", false),
        notifDots = p.getBoolean("notifDots2", false),
        hideStatusBar = p.getBoolean("hideStatusBar", false),
        showBattery = p.getBoolean("showBattery", true),
        showClock = p.getBoolean("showClock", true),
        homeAlign = p.readEnum("homeAlign", HomeAlign.LEFT),
        mediaPlayer = p.getBoolean("mediaPlayer", true),
        lowercaseApps = p.getBoolean("lowercaseApps", false),
        pitchBlack = p.getBoolean("pitchBlack", false),
        centerApps = p.getBoolean("centerApps", false),
        animations = p.getBoolean("animations", true),
        drawerRight = p.getBoolean("drawerRight", false),
        swipeUpSearch = p.getBoolean("swipeUpSearch", false),
        noHaptics = p.getBoolean("noHaptics", false),
        hidden = p.getStringSet("hidden", emptySet())?.toSet() ?: emptySet(),
    )
}

fun saveConfig(ctx: Context, c: Config) {
    appPrefs(ctx).edit()
        .putString("font", c.font.name)
        .putString("clock", c.clock.name)
        .putString("hours", c.hours.name)
        .putString("date", c.date.name)
        .putBoolean("weatherOn", c.weatherOn)
        .putString("city", c.city)
        .putString("lat", c.lat.toString())
        .putString("lon", c.lon.toString())
        .putBoolean("fahrenheit", c.fahrenheit)
        .putBoolean("calendarOn", c.calendarOn)
        .putBoolean("searchApps", c.searchApps)
        .putBoolean("searchContacts", c.searchContacts)
        .putBoolean("searchFiles", c.searchFiles)
        .putBoolean("searchWeb", c.searchWeb)
        .putString("engine", c.engine.name)
        .putString("clockApp", c.clockApp)
        .putString("calendarApp", c.calendarApp)
        .putString("weatherApp", c.weatherApp)
        .putBoolean("weatherLocation", c.weatherLocation)
        .putBoolean("widgetsOn", c.widgetsOn)
        .putBoolean("notifDots2", c.notifDots)
        .putBoolean("hideStatusBar", c.hideStatusBar)
        .putBoolean("showBattery", c.showBattery)
        .putBoolean("showClock", c.showClock)
        .putString("homeAlign", c.homeAlign.name)
        .putBoolean("mediaPlayer", c.mediaPlayer)
        .putBoolean("lowercaseApps", c.lowercaseApps)
        .putBoolean("pitchBlack", c.pitchBlack)
        .putBoolean("centerApps", c.centerApps)
        .putBoolean("animations", c.animations)
        .putBoolean("drawerRight", c.drawerRight)
        .putBoolean("swipeUpSearch", c.swipeUpSearch)
        .putBoolean("noHaptics", c.noHaptics)
        .putStringSet("hidden", c.hidden)
        .apply()
}

fun readFavs(ctx: Context): List<String>? =
    appPrefs(ctx).getString("favs", null)?.split("\n")?.filter { it.isNotBlank() }

fun writeFavs(ctx: Context, keys: List<String>) {
    appPrefs(ctx).edit().putString("favs", keys.joinToString("\n")).apply()
}

// ---------- Weather (Open-Meteo, no account or key needed) ----------

data class Weather(val temp: Int, val code: Int)

private fun httpGet(url: String): String? = try {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 8000
    conn.readTimeout = 8000
    conn.setRequestProperty("User-Agent", "MinimalLauncher/1.0")
    try {
        if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
    } finally {
        conn.disconnect()
    }
} catch (e: Exception) {
    null
}

/** Returns (name, latitude, longitude) for the best match, or null. */
fun geocode(name: String): Triple<String, Double, Double>? {
    val body = httpGet(
        "https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name=" +
            URLEncoder.encode(name, "UTF-8"),
    ) ?: return null
    return try {
        val results = JSONObject(body).optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val o = results.getJSONObject(0)
        Triple(o.optString("name", name), o.getDouble("latitude"), o.getDouble("longitude"))
    } catch (e: Exception) {
        null
    }
}

/** Approximate device location, or null if there is no permission or no fix. Never leaves the phone except as coordinates sent to Open-Meteo. */
suspend fun currentLatLon(ctx: Context): Pair<Double, Double>? {
    if (ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = try { lm.getProviders(true) } catch (e: Exception) { emptyList() }
    var best: android.location.Location? = null
    for (provider in providers) {
        val loc = try { lm.getLastKnownLocation(provider) } catch (e: Exception) { null } ?: continue
        val cur = best
        if (cur == null || loc.time > cur.time) best = loc
    }
    val found = best
    if (found != null) return found.latitude to found.longitude
    val provider = providers.firstOrNull() ?: return null
    return withTimeoutOrNull(10_000L) {
        suspendCancellableCoroutine<Pair<Double, Double>?> { cont ->
            try {
                lm.getCurrentLocation(provider, null, ctx.mainExecutor) { loc ->
                    if (cont.isActive) cont.resume(loc?.let { it.latitude to it.longitude })
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }
}

data class SysStatus(
    val battery: Int = 100,
    val charging: Boolean = false,
)

fun readStatus(ctx: Context): SysStatus {
    val bi = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val state = bi?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val pct = if (level >= 0 && scale > 0) level * 100 / scale else 100
    val charging = state == BatteryManager.BATTERY_STATUS_CHARGING || state == BatteryManager.BATTERY_STATUS_FULL
    return SysStatus(pct, charging)
}

fun fetchWeather(lat: Double, lon: Double, fahrenheit: Boolean): Weather? {
    val unit = if (fahrenheit) "fahrenheit" else "celsius"
    val body = httpGet(
        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code&temperature_unit=$unit",
    ) ?: return null
    return try {
        val cur = JSONObject(body).getJSONObject("current")
        Weather(cur.getDouble("temperature_2m").roundToInt(), cur.getInt("weather_code"))
    } catch (e: Exception) {
        null
    }
}

fun describe(code: Int): String = when (code) {
    0 -> "Clear"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    in 51..57 -> "Drizzle"
    in 61..67 -> "Rain"
    in 71..77 -> "Snow"
    in 80..82 -> "Showers"
    85, 86 -> "Snow showers"
    in 95..99 -> "Thunderstorm"
    else -> ""
}

// ---------- Calendar ----------

data class Event(val title: String, val begin: Long, val allDay: Boolean)

/** The current or next event in the coming week; timed events win over all-day ones. */
fun queryNextEvent(ctx: Context): Event? {
    if (ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return null
    val now = System.currentTimeMillis()
    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(builder, now)
    ContentUris.appendId(builder, now + 7L * 24 * 60 * 60 * 1000)
    return try {
        ctx.contentResolver.query(
            builder.build(),
            arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.ALL_DAY,
            ),
            null, null,
            CalendarContract.Instances.BEGIN + " ASC",
        )?.use { c ->
            var firstAllDay: Event? = null
            var timed: Event? = null
            while (timed == null && c.moveToNext()) {
                val e = Event(c.getString(0) ?: "Event", c.getLong(1), c.getInt(2) == 1)
                if (e.allDay) { if (firstAllDay == null) firstAllDay = e } else timed = e
            }
            timed ?: firstAllDay
        }
    } catch (e: Exception) {
        null
    }
}

fun formatEvent(e: Event, is24: Boolean): String {
    val zone = if (e.allDay) ZoneOffset.UTC else ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(e.begin).atZone(zone)
    val today = LocalDate.now()
    val day = start.toLocalDate()
    val dayPart = when (day) {
        today -> ""
        today.plusDays(1) -> "Tomorrow "
        else -> start.format(DateTimeFormatter.ofPattern("EEE "))
    }
    val timePart = if (e.allDay) "All Day" else start.format(DateTimeFormatter.ofPattern(if (is24) "H:mm" else "h:mm a"))
    return "${e.title} · $dayPart$timePart"
}

// ---------- Search backends ----------

data class ContactHit(val name: String, val uri: Uri)
data class FileHit(val name: String, val uri: Uri, val mime: String?)

fun searchContacts(ctx: Context, q: String): List<ContactHit> {
    if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val out = mutableListOf<ContactHit>()
    try {
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(q))
        ctx.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ),
            null, null,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC",
        )?.use { c ->
            while (c.moveToNext() && out.size < 6) {
                val name = c.getString(2) ?: ""
                if (name.isNotBlank()) {
                    out.add(ContactHit(name, ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1))))
                }
            }
        }
    } catch (e: Exception) {
        // ignore: results simply stay empty
    }
    return out
}

/** Needs "All files access". Searches everything Android has indexed, including documents. */
fun searchFiles(ctx: Context, q: String): List<FileHit> {
    if (!Environment.isExternalStorageManager()) return emptyList()
    val out = mutableListOf<FileHit>()
    try {
        val base = MediaStore.Files.getContentUri("external")
        val args = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE ? AND " +
                    MediaStore.Files.FileColumns.MIME_TYPE + " IS NOT NULL",
            )
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf("%$q%"))
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC")
            putInt(ContentResolver.QUERY_ARG_LIMIT, 6)
        }
        ctx.contentResolver.query(
            base,
            arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,
            ),
            args, null,
        )?.use { c ->
            while (c.moveToNext()) {
                out.add(FileHit(c.getString(1) ?: "File", ContentUris.withAppendedId(base, c.getLong(0)), c.getString(2)))
            }
        }
    } catch (e: Exception) {
        // ignore: results simply stay empty
    }
    return out
}

fun isUrl(q: String): Boolean = Patterns.WEB_URL.matcher(q).matches()

fun normalizeUrl(q: String): String =
    if (q.startsWith("http://", true) || q.startsWith("https://", true)) q else "https://$q"


// ---------- Search ranking ----------

/** Counts how often an app is opened so search can put the ones you use first. */
fun recordLaunch(ctx: Context, key: String) {
    val p = appPrefs(ctx)
    p.edit().putInt("use_$key", p.getInt("use_$key", 0) + 1).apply()
}

/** Names that start with the query first, then words that start with it, then anywhere; ties go to the most used. */
fun rankApps(ctx: Context, apps: List<App>, query: String, limit: Int): List<App> {
    val p = appPrefs(ctx)
    val s = query.lowercase()
    fun score(a: App): Int {
        val l = a.label.lowercase()
        return when {
            l.startsWith(s) -> 0
            l.split(' ', '-', '_', '.').any { it.startsWith(s) } -> 1
            l.contains(s) -> 2
            else -> 3
        }
    }
    return apps.filter { score(it) < 3 }
        .sortedWith(compareBy<App>({ score(it) }, { -p.getInt("use_${it.key}", 0) }, { it.label.lowercase() }))
        .take(limit)
}

// ---------- Backup ----------

private fun skipInBackup(key: String) = key.startsWith("widget_") || key.startsWith("use_") || key == "wp_seed"

/** Every setting and the favorites as one JSON file. Widgets are left out: their ids only mean something on this phone. */
fun exportSettings(ctx: Context): String {
    val prefs = org.json.JSONObject()
    for ((k, v) in appPrefs(ctx).all) {
        if (skipInBackup(k)) continue
        val e = org.json.JSONObject()
        when (v) {
            is Boolean -> e.put("t", "b").put("v", v)
            is Int -> e.put("t", "i").put("v", v)
            is Long -> e.put("t", "l").put("v", v)
            is Float -> e.put("t", "f").put("v", v.toDouble())
            is String -> e.put("t", "s").put("v", v)
            is Set<*> -> e.put("t", "ss").put("v", org.json.JSONArray(v.map { it.toString() }))
            else -> continue
        }
        prefs.put(k, e)
    }
    return org.json.JSONObject().put("app", "MinimalYou").put("version", 1).put("prefs", prefs).toString(2)
}

/** Returns false (and changes nothing) if the text isn't a MinimalYou backup. */
fun importSettings(ctx: Context, text: String): Boolean {
    try {
        val root = org.json.JSONObject(text)
        if (root.optString("app") != "MinimalYou") return false
        val prefs = root.getJSONObject("prefs")
        val ed = appPrefs(ctx).edit()
        val keys = prefs.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (skipInBackup(k)) continue
            val e = prefs.getJSONObject(k)
            when (e.getString("t")) {
                "b" -> ed.putBoolean(k, e.getBoolean("v"))
                "i" -> ed.putInt(k, e.getInt("v"))
                "l" -> ed.putLong(k, e.getLong("v"))
                "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                "s" -> ed.putString(k, e.getString("v"))
                "ss" -> {
                    val arr = e.getJSONArray("v")
                    ed.putStringSet(k, (0 until arr.length()).map { arr.getString(it) }.toSet())
                }
            }
        }
        ed.apply()
        return true
    } catch (e: Exception) {
        return false
    }
}

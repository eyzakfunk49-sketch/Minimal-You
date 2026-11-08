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
    CONDENSED("Condensed", FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))),
    SERIF("Serif", FontFamily.Serif),
    MONO("Monospace", FontFamily.Monospace),
    CASUAL("Casual", FontFamily(Typeface.create("casual", Typeface.NORMAL))),
}

enum class ClockStyle(val label: String) {
    LARGE("Large"), BOLD("Bold"), COMPACT("Compact"), STACKED("Stacked"),
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
    val timePart = if (e.allDay) "All day" else start.format(DateTimeFormatter.ofPattern(if (is24) "H:mm" else "h:mm a"))
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

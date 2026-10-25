package com.kirox.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.kirox.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A GitHub release that carries an APK. */
data class AppRelease(
    val tag: String,
    val version: String,
    val title: String,
    val notes: String,
    val prerelease: Boolean,
    val apkUrl: String,
    val apkSize: Long,
    val sha256: String?,
    val pageUrl: String
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState
    data class Ready(val release: AppRelease, val file: File) : UpdateState
    data class Failed(val message: String, val release: AppRelease? = null) : UpdateState
}

/**
 * Updates from the project's GitHub releases. Anonymous HTTPS to api.github.com with ETag caching;
 * nothing about the Telegram account is sent. A download is installed only after its size, SHA-256,
 * package name, signing certificate and version code are checked, and the user confirms the install.
 */
class UpdateCenter(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("kirox_updates", Context.MODE_PRIVATE)
    private var job: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Beta builds include pre-releases; it can be turned off in the updates screen. */
    var betaChannel: Boolean
        get() = prefs.getBoolean(KEY_BETA, BuildConfig.VERSION_NAME.contains('-'))
        set(value) { prefs.edit().putBoolean(KEY_BETA, value).apply() }

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** At most once a day, silently. */
    fun checkDaily() {
        if (System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0) < DAY_MS) return
        check()
    }

    fun check() {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Checking
            _state.value = runCatching { findRelease() }.fold(
                onSuccess = { release -> if (release != null) UpdateState.Available(release) else UpdateState.UpToDate },
                onFailure = { UpdateState.Failed(it.message ?: "Не удалось проверить обновления") }
            )
        }
    }

    fun download(release: AppRelease) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Downloading(release, 0f)
            _state.value = runCatching { fetch(release) }.fold(
                onSuccess = { UpdateState.Ready(release, it) },
                onFailure = { UpdateState.Failed(it.message ?: "Не удалось скачать обновление", release) }
            )
        }
    }

    /** Opens the system installer; asks for the "install unknown apps" permission first if needed. */
    fun install(file: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        // Go straight to the system installer instead of an "open with" chooser (file managers, editors).
        context.packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .firstOrNull { it.endsWith("packageinstaller") }
            ?.let(intent::setPackage)
        context.startActivity(intent)
    }

    private fun findRelease(): AppRelease? {
        val connection = URL("https://api.github.com/repos/$REPO/releases?per_page=15").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "KiroX-Android")
            prefs.getString(KEY_ETAG, null)?.takeIf { prefs.contains(KEY_BODY) }?.let { connection.setRequestProperty("If-None-Match", it) }
            val body = when (val code = connection.responseCode) {
                304 -> prefs.getString(KEY_BODY, null) ?: "[]"
                200 -> connection.inputStream.bufferedReader().use { it.readText() }.also {
                    prefs.edit().putString(KEY_ETAG, connection.getHeaderField("ETag")).putString(KEY_BODY, it).apply()
                }
                403, 429 -> error("GitHub временно ограничил проверки, попробуйте позже")
                else -> error("GitHub ответил $code")
            }
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            val list = JSONArray(body)
            // GitHub orders releases by the tagged commit's date, which is wrong when a tag points at an
            // older commit, so pick the highest version rather than the first entry.
            return (0 until list.length()).map { list.getJSONObject(it) }
                .filterNot { it.optBoolean("draft") || (it.optBoolean("prerelease") && !betaChannel) }
                .filter { AppVersion.isNewer(it.optString("tag_name").removePrefix("v"), currentVersion) }
                .sortedWith { a, b -> if (AppVersion.isNewer(a.optString("tag_name").removePrefix("v"), b.optString("tag_name").removePrefix("v"))) -1 else 1 }
                .firstNotNullOfOrNull(::parse)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(json: JSONObject): AppRelease? {
        val assets = json.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") } ?: return null
        val tag = json.optString("tag_name")
        var sha256 = apk.optString("digest").takeIf { it.startsWith("sha256:") }?.substring(7)?.lowercase()
        if (sha256 == null) {
            // Fallback: a "<apk>.sha256" asset published next to the APK.
            val shaAsset = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name").equals(apk.optString("name") + ".sha256", true) }
            sha256 = shaAsset?.optString("browser_download_url")?.let(::fetchText)?.trim()?.take(64)?.lowercase()
        }
        return AppRelease(
            tag = tag,
            version = tag.removePrefix("v"),
            title = json.optString("name").ifBlank { tag },
            notes = json.optString("body"),
            prerelease = json.optBoolean("prerelease"),
            apkUrl = apk.optString("browser_download_url"),
            apkSize = apk.optLong("size"),
            sha256 = sha256?.takeIf { it.length == 64 && it.all { c -> c in "0123456789abcdef" } },
            pageUrl = json.optString("html_url")
        )
    }

    private fun fetch(release: AppRelease): File {
        require(release.apkUrl.startsWith("https://")) { "Небезопасная ссылка на файл" }
        val expectedHash = release.sha256 ?: error("У релиза нет контрольной суммы SHA-256, установка небезопасна")
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "kirox-${release.version}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "KiroX-Android")
            if (connection.responseCode != 200) error("Сервер ответил ${connection.responseCode}")
            val total = release.apkSize.takeIf { it > 0 } ?: connection.contentLength.toLong()
            var done = 0L
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (scope.isActive) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        if (total > 0) _state.value = UpdateState.Downloading(release, done.toFloat() / total)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (release.apkSize > 0 && file.length() != release.apkSize) fail(file, "Файл скачался не полностью")
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (hash != expectedHash) fail(file, "Контрольная сумма не совпала — файл повреждён или подменён")
        verifyPackage(file)
        return file
    }

    /** Same package, same signing certificate and a higher version code than the installed app. */
    private fun verifyPackage(file: File) {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = pm.getPackageArchiveInfo(file.path, flags) ?: fail(file, "Файл не является приложением Android")
        val installed = pm.getPackageInfo(context.packageName, flags)
        if (archive.packageName != context.packageName) fail(file, "Это другое приложение")
        if (versionCode(archive) <= versionCode(installed)) fail(file, "Версия в файле не новее установленной")
        if (certificates(archive) != certificates(installed)) fail(file, "Подпись не совпадает с установленной версией")
    }

    private fun versionCode(info: PackageInfo): Long = info.longVersionCode

    private fun certificates(info: PackageInfo): Set<String> {
        val signatures = info.signingInfo?.apkContentsSigners
        return signatures.orEmpty().map { sig -> MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    private fun fail(file: File, message: String): Nothing {
        file.delete()
        error(message)
    }

    private fun fetchText(url: String): String? = runCatching {
        if (!url.startsWith("https://")) return null
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "KiroX-Android")
            try { inputStream.bufferedReader().use { it.readText() } } finally { disconnect() }
        }
    }.getOrNull()

    companion object {
        const val REPO = "kirdev07/KiroX-Client-TG"
        const val RELEASES_URL = "https://github.com/$REPO/releases"
        private const val KEY_BETA = "beta_channel"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_ETAG = "etag"
        private const val KEY_BODY = "body"
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

/** Semantic versions such as "1.0.0-beta.1": a release is newer than its pre-releases. */
object AppVersion {
    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    fun compare(a: String, b: String): Int {
        val (coreA, preA) = split(a)
        val (coreB, preB) = split(b)
        for (i in 0 until maxOf(coreA.size, coreB.size)) {
            val diff = coreA.getOrElse(i) { 0 }.compareTo(coreB.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        when {
            preA.isEmpty() && preB.isEmpty() -> return 0
            preA.isEmpty() -> return 1
            preB.isEmpty() -> return -1
        }
        for (i in 0 until maxOf(preA.size, preB.size)) {
            val x = preA.getOrNull(i) ?: return -1
            val y = preB.getOrNull(i) ?: return 1
            val diff = when {
                x.toIntOrNull() != null && y.toIntOrNull() != null -> x.toInt().compareTo(y.toInt())
                x.toIntOrNull() != null -> -1
                y.toIntOrNull() != null -> 1
                else -> x.compareTo(y)
            }
            if (diff != 0) return diff
        }
        return 0
    }

    private fun split(version: String): Pair<List<Int>, List<String>> {
        val clean = version.trim().removePrefix("v").substringBefore('+')
        val core = clean.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val pre = if ('-' in clean) clean.substringAfter('-').split('.').filter { it.isNotEmpty() } else emptyList()
        return core to pre
    }
}

package com.wifihavoc.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.topjohnwu.superuser.Shell
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest

/**
 * KaliManager — управление Kali-окружением внутри приложения.
 *
 * Источник rootfs (по приоритету):
 *  1) Bundled assets — `assets/kali-rootfs-arm64.tar.gz` (если вшили)
 *  2) Удалённый GitHub Release (LFS) — основной путь, если assets пуст
 *  3) Локальный файл на SDCard — `/sdcard/kali-rootfs-arm64.tar.gz`
 *
 * При первом запуске: скачать/распаковать → отметить готовность → chroot.
 */
class KaliManager(private val context: Context) {

    private val rootfsDir: File = File(context.filesDir, "kali-rootfs")
    private val tarballPath: File = File(context.filesDir, "kali-rootfs.tar.gz")
    private val markerFile: File = File(context.filesDir, ".kali_installed")
    private val bindDirs = listOf("/dev", "/proc", "/sys", "/sdcard")

    companion object {
        private const val TAG = "KaliManager"
        private const val ASSET_NAME = "kali-rootfs-arm64.tar.gz"

        /**
         * Прямая ссылка на rootfs в твоём GitHub-репо через LFS.
         * Если используешь свой репозиторий — поменяй USERNAME/REPO/TAG.
         */
        const val ROOTFS_URL = "https://github.com/22Chestii22/wifihavoc/releases/download/v0.1.0/kali-rootfs-arm64.tar.gz"

        /** SHA256 перепакованного rootfs (tar.gz). Проверяется при скачивании. */
        const val ROOTFS_SHA256 = "52df42d2e08aa70f53afbe5aa5867afdab5b3af5a7cdbd9e9039882e4194aae7"
    }

    fun isInstalled(): Boolean = markerFile.exists() && rootfsDir.exists() && rootfsDir.list()?.isNotEmpty() == true

    fun getRootfsPath(): String = rootfsDir.absolutePath

    suspend fun install(progressCallback: (Int, String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            progressCallback(2, "Подготовка...")

            // 1. Достать tar.gz из приоритетного источника
            val got = obtainTarball { pct, msg ->
                progressCallback(2 + (pct * 0.3f).toInt(), msg)
            }
            if (!got) {
                progressCallback(0, "Не удалось получить rootfs (assets/url/sdcard)")
                return@withContext false
            }

            // 2. Проверить SHA256 (сверяем с тем, что заявлен в ROOTFS_SHA256)
            progressCallback(35, "Проверка целостности...")
            val actual = sha256Of(tarballPath)
            if (actual.equals(ROOTFS_SHA256, ignoreCase = true).not()) {
                Log.e(TAG, "SHA256 mismatch! expected=$ROOTFS_SHA256 actual=$actual")
                progressCallback(0, "Ошибка: повреждён архив (sha256 не совпал)")
                tarballPath.delete()
                return@withContext false
            }

            // 3. Распаковать
            progressCallback(40, "Распаковка...")
            extractRootfs { current, total ->
                val pct = 40 + (current * 50 / total).coerceIn(0, 50)
                progressCallback(pct.toInt(), String.format("Распаковано: %d / %d файлов", current, total))
            }

            // 4. Bind-монтирования
            setupBindMounts()

            // 5. Маркер
            markerFile.writeText("1")
            progressCallback(100, "Готово!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            progressCallback(0, "Ошибка: ${e.message}")
            false
        }
    }

    /**
     * Достать tar.gz: сначала assets, иначе скачать по URL, иначе SDCard.
     * Возвращает true если файл лежит в tarballPath и проходит по размеру.
     */
    private suspend fun obtainTarball(progress: (Int, String) -> Unit): Boolean {
        // 1. Assets (если вшит)
        try {
            val assetSize = context.assets.openFd(ASSET_NAME).use { it.length }
            if (assetSize > 0) {
                progress(5, "Копирую rootfs из assets...")
                context.assets.open(ASSET_NAME).use { input ->
                    FileOutputStream(tarballPath).use { output ->
                        val total = input.copyWithProgress(output) { read, _ ->
                            val pct = (read * 100 / assetSize).toInt().coerceIn(0, 100)
                            progress(pct, "Копирую из assets: ${read / 1024 / 1024} МБ")
                        }
                        Log.d(TAG, "Copied $total bytes from assets")
                    }
                }
                return true
            }
        } catch (e: Exception) {
            Log.d(TAG, "Asset $ASSET_NAME not found, falling back to URL")
        }

        // 2. Скачать по URL
        if (ROOTFS_URL.isNotBlank()) {
            try {
                progress(5, "Скачиваю rootfs (~241 МБ)...")
                downloadRootfs(ROOTFS_URL) { downloaded, total ->
                    val pct = (downloaded * 100 / total).toInt().coerceIn(0, 100)
                    progress(pct, String.format("Скачано: %.1f / %.1f МБ", downloaded / 1024f / 1024f, total / 1024f / 1024f))
                }
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Download from $ROOTFS_URL failed", e)
            }
        }

        // 3. Локальный файл на SDCard
        val sdcard = File("/sdcard/$ASSET_NAME")
        if (sdcard.exists() && sdcard.length() > 0) {
            progress(50, "Копирую rootfs с /sdcard...")
            sdcard.copyTo(tarballPath, overwrite = true)
            return true
        }

        return false
    }

    private suspend fun downloadRootfs(url: String, progress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection()
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        connection.connect()
        val fileSize = connection.contentLengthLong
        val input: InputStream = connection.getInputStream()
        val output = FileOutputStream(tarballPath)
        val buffer = ByteArray(64 * 1024)
        var downloaded = 0L
        var len: Int
        while (input.read(buffer).also { len = it } != -1) {
            output.write(buffer, 0, len)
            downloaded += len
            progress(downloaded, fileSize)
        }
        output.close()
        input.close()
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun extractRootfs(progress: (Int, Int) -> Unit) = withContext(Dispatchers.IO) {
        val cmd = "tar -xzf ${tarballPath.absolutePath} -C ${rootfsDir.absolutePath}"
        val result = RootManager.run("sh", "-c", cmd)
        if (!result.isSuccess) {
            throw RuntimeException("Extract failed: ${result.err.joinToString()}")
        }
        // Приблизительный счётчик файлов
        progress(rootfsDir.walkTopDown().count(), 10000)
    }

    private suspend fun setupBindMounts() {
        bindDirs.forEach { dir ->
            val target = File(rootfsDir, dir.substring(1))
            if (!target.exists()) target.mkdirs()
            RootManager.run("mount", "--bind", dir, target.absolutePath)
        }
    }

    suspend fun execInKali(vararg cmd: String): ShellOut = withContext(Dispatchers.IO) {
        val fullCmd = buildChrootCommand(cmd)
        val result = RootManager.run(*fullCmd)
        ShellOut(result.out.joinToString("\n"), result.err.joinToString("\n"), result.isSuccess)
    }

    suspend fun execInKaliStream(vararg cmd: String, onLine: (String) -> Unit): ShellOut = withContext(Dispatchers.IO) {
        val fullCmd = buildChrootCommand(cmd)
        val result = RootManager.runStream(*fullCmd, onLine = onLine)
        ShellOut(result.out.joinToString("\n"), result.err.joinToString("\n"), result.isSuccess)
    }

    private fun buildChrootCommand(cmd: Array<out String>): Array<String> {
        val chrootPath = rootfsDir.absolutePath
        return arrayOf(
            "chroot", chrootPath,
            "/usr/bin/env", "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        ) + cmd
    }

    suspend fun getAvailableTools(): List<String> = withContext(Dispatchers.IO) {
        val tools = listOf("aircrack-ng", "aireplay-ng", "airodump-ng", "iw", "john", "hashcat", "tcpdump")
        tools.filter { tool -> execInKali("which", tool).ok }
    }

    suspend fun uninstall() {
        RootManager.run("sh", "-c", "rm -rf ${rootfsDir.absolutePath} ${tarballPath.absolutePath} ${markerFile.absolutePath}")
    }
}

/** Утилита: InputStream.copyTo с прогрессом. */
private inline fun InputStream.copyWithProgress(
    out: FileOutputStream,
    onProgress: (read: Long, total: Long) -> Unit
): Long {
    val buffer = ByteArray(64 * 1024)
    var total: Long = 0
    var n = read(buffer)
    while (n >= 0) {
        out.write(buffer, 0, n)
        total += n
        onProgress(total, -1L)
        n = read(buffer)
    }
    return total
}

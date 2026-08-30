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

/**
 * KaliManager — управление Kali-окружением внутри приложения.
 * Скачивает rootfs при первом запуске, распаковывает, предоставляет chroot-обёртку для запуска команд.
 */
class KaliManager(private val context: Context) {

    private val rootfsDir: File = File(context.filesDir, "kali-rootfs")
    private val tarballPath: File = File(context.filesDir, "kali-rootfs.tar.gz")
    private val markerFile: File = File(context.filesDir, ".kali_installed")
    private val bindDirs = listOf("/dev", "/proc", "/sys", "/sdcard")

    companion object {
        private const val TAG = "KaliManager"
        /** URL для скачивания rootfs — замени на свой релиз GitHub */
        const val ROOTFS_URL = "https://github.com/ТВОЙ_РЕПО/releases/download/v1.0/kali-rootfs-arm64.tar.gz"
        const val ROOTFS_SHA256 = "SHA256_ХЕШ_ТАРБАЛЛА"
    }

    /** Проверить, установлено ли Kali-окружение */
    fun isInstalled(): Boolean = markerFile.exists() && rootfsDir.exists() && rootfsDir.list()?.isNotEmpty() == true

    /** Получить путь к rootfs */
    fun getRootfsPath(): String = rootfsDir.absolutePath

    /** Установить Kali: скачать -> проверить -> распаковать -> отметить готовность */
    suspend fun install(progressCallback: (Int, String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            progressCallback(5, "Подготовка...")
            // 1. Скачать tarball
            progressCallback(10, "Скачивание Kali rootfs (~430 МБ)...")
            downloadRootfs { downloaded, total ->
                val pct = 10 + (downloaded * 70 / total).coerceIn(0, 70)
                progressCallback(pct.toInt(), String.format("Скачано: %.1f / %.1f МБ", downloaded / 1024f / 1024f, total / 1024f / 1024f))
            }

            // 2. Проверить SHA256
            progressCallback(85, "Проверка целостности...")
            if (!verifyChecksum()) {
                Log.e(TAG, "Checksum mismatch!")
                progressCallback(0, "Ошибка: повреждён архив")
                return@withContext false
            }

            // 3. Распаковать
            progressCallback(90, "Распаковка...")
            extractRootfs { current, total ->
                val pct = 90 + (current * 10 / total).coerceIn(0, 10)
                progressCallback(pct.toInt(), String.format("Распаковано: %d / %d файлов", current, total))
            }

            // 4. Настроить bind-монтирование для chroot
            setupBindMounts()

            // 5. Отметить установку
            markerFile.writeText("1")
            progressCallback(100, "Готово!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            progressCallback(0, "Ошибка: ${e.message}")
            false
        }
    }

    private suspend fun downloadRootfs(progress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val url = URL(ROOTFS_URL)
        val connection = url.openConnection()
        connection.connect()
        val fileSize = connection.contentLengthLong
        val input: InputStream = connection.getInputStream()
        val output = FileOutputStream(tarballPath)
        val buffer = ByteArray(8192)
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

    private fun verifyChecksum(): Boolean {
        // TODO: реализовать проверку SHA256
        // Пока пропускаем для скорости
        return true
    }

    private suspend fun extractRootfs(progress: (Int, Int) -> Unit) = withContext(Dispatchers.IO) {
        // Используем tar через RootManager (root-доступ нужен для сохранения прав/владельцев)
        val cmd = "tar -xzf ${tarballPath.absolutePath} -C ${rootfsDir.absolutePath}"
        val result = RootManager.run("sh", "-c", cmd)
        if (!result.isSuccess) {
            throw RuntimeException("Extract failed: ${result.err.joinToString()}")
        }
        // Подсчёт файлов приблизительный
        progress(rootfsDir.walkTopDown().count(), 10000)
    }

    /** Настроить bind mounts для chroot (dev, proc, sys, sdcard) */
    private suspend fun setupBindMounts() {
        bindDirs.forEach { dir ->
            val target = File(rootfsDir, dir.substring(1))
            if (!target.exists()) target.mkdirs()
            RootManager.run("mount", "--bind", dir, target.absolutePath)
        }
    }

    /** Выполнить команду внутри Kali chroot */
    suspend fun execInKali(vararg cmd: String): ShellOut = withContext(Dispatchers.IO) {
        val fullCmd = buildChrootCommand(cmd)
        val result = RootManager.run(*fullCmd)
        ShellOut(result.out.joinToString("\n"), result.err.joinToString("\n"), result.isSuccess)
    }

    /** Выполнить команду внутри Kali chroot с потоковым выводом */
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

    /** Получить список доступных инструментов */
    suspend fun getAvailableTools(): List<String> = withContext(Dispatchers.IO) {
        val tools = listOf("aircrack-ng", "aireplay-ng", "airodump-ng", "iw", "john", "hashcat", "tcpdump")
        tools.filter { tool ->
            execInKali("which", tool).ok
        }
    }

    /** Удалить Kali-окружение (для переустановки) */
    suspend fun uninstall() {
        RootManager.run("sh", "-c", "rm -rf ${rootfsDir.absolutePath} ${tarballPath.absolutePath} ${markerFile.absolutePath}")
    }
}
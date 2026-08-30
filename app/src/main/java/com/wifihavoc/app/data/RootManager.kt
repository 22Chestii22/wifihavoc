package com.wifihavoc.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.topjohnwu.superuser.Shell

/**
 * RootManager — обёртка над root-оболочкой (libsu / Magisk).
 * Выполняет команды с root-правами; управляет monitor mode
 * через QCACLD-параметр con_mode на встроенном Wi-Fi чипе Qualcomm.
 */
object RootManager {

    val isRootAvailable: Boolean
        get() = Shell.isAppGrantedRoot() == true || Shell.rootAccess()

    suspend fun run(vararg cmd: String): Shell.Result = withContext(Dispatchers.IO) {
        Shell.cmd(*cmd).exec()
    }

    /** Запуск команды с потоковым выводом (для терминала/долгих процессов). */
    suspend fun runStream(vararg cmd: String, onLine: (String) -> Unit): Shell.Result = withContext(Dispatchers.IO) {
        val process = Shell.cmd(*cmd).exec()
        process.out.forEach { onLine(it) }
        process.err.forEach { onLine("[stderr] $it") }
        process
    }

    /** Прочитать значение sysfs-параметра через root. */
    suspend fun readSysfs(path: String): String {
        val r = Shell.cmd("cat $path").exec()
        return r.out.firstOrNull() ?: ""
    }

    suspend fun writeSysfs(path: String, value: String): Boolean {
        val r = Shell.cmd("echo $value > $path").exec()
        return r.isSuccess
    }

    /** Текущий режим con_mode: 0 = managed (обычный), 4 = monitor. -1 если нет. */
    suspend fun currentConMode(): Int {
        val v = readSysfs(CON_MODE)
        return v.trim().toIntOrNull() ?: -1
    }

    /**
     * Включить monitor mode. ВНИМАНИЕ: телефон отключится от Wi-Fi.
     * Возвращает true при успехе. Вызывающий обязан затем вызвать disableMonitorMode().
     */
    suspend fun enableMonitorMode(): Boolean {
        var ok = Shell.cmd("ip link set wlan0 down").exec().isSuccess
        ok = Shell.cmd("echo 4 > $CON_MODE").exec().isSuccess && ok
        ok = Shell.cmd("ip link set wlan0 up").exec().isSuccess && ok
        return ok
    }

    /** Выключить monitor mode, вернуть телефон в обычный режим и восстановить Wi-Fi. */
    suspend fun disableMonitorMode(): Boolean {
        var ok = Shell.cmd("ip link set wlan0 down").exec().isSuccess
        ok = Shell.cmd("echo 0 > $CON_MODE").exec().isSuccess && ok
        ok = Shell.cmd("ip link set wlan0 up").exec().isSuccess && ok
        return ok
    }

    const val CON_MODE = "/sys/module/wlan/parameters/con_mode"

    /** Путь sysfs канала QCACLD, если драйвер его выставляет. */
    fun currentChannelSysfs(): String? {
        val candidates = listOf(
            "/sys/module/wlan/parameters/channel",
            "/sys/module/wlan/parameters/channel_switch",
        )
        return candidates.firstOrNull { java.io.File(it).exists() }
    }

    /**
     * Реальное состояние радио: не полагается только на con_mode, считывает
     * фактические флаги интерфейса (monitor/radiotap, up/down) через ip.
     */
    suspend fun radioState(): RadioState {
        val mode = currentConMode()
        val link = Shell.cmd("ip -d link show wlan0").exec().out.joinToString("\n")
        val linkFlags = Shell.cmd("ip link show wlan0").exec().out.joinToString("\n")
        val isUp = linkFlags.contains("<") && Regex("<[^>]*>").find(linkFlags)?.value?.contains("UP") == true
        val isMonitor = link.contains("monitor") || link.contains("ieee80211/radiotap")
        return RadioState(
            conMode = mode,
            ifaceUp = isUp,
            ifaceMonitor = isMonitor,
            reported = mode == 4 || isMonitor
        )
    }
}

data class RadioState(
    val conMode: Int,
    val ifaceUp: Boolean,
    val ifaceMonitor: Boolean,
    val reported: Boolean,
)

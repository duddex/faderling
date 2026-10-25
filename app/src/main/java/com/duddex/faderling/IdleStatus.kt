package com.duddex.faderling

/** What the top bar cycles through while no Session is connected: "Not connected", IP and port, announced name. */
object IdleStatus {
    const val STEP_COUNT = 3
    const val STEP_MS = 3000L

    private const val PREFIX = "Faderling"

    /** Announced Bonjour name without the leading "Faderling", e.g. "KFAUWI", "Debug KFAUWI" or "KFAUWI (2)". */
    fun shortName(advertisedName: String): String {
        if (!advertisedName.startsWith("$PREFIX ")) return advertisedName
        return advertisedName.removePrefix(PREFIX).trim().ifEmpty { advertisedName }
    }

    /** First IPv4 address of a Wi-Fi interface ("wlan…") from (interface name, address) pairs, or null. */
    fun wifiAddress(addresses: List<Pair<String, String>>): String? =
        addresses.firstOrNull { (name, _) -> name.startsWith("wlan") }?.second

    /** "192.168.2.123:5004", or the address alone while the port is not known yet. */
    fun endpoint(address: String, port: Int?): String = if (port == null) address else "$address:$port"
}

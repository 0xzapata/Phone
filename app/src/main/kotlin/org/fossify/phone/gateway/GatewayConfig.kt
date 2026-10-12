package org.fossify.phone.gateway

import android.content.Context
import androidx.core.content.edit

fun isGatewayUrl(url: String) = url.startsWith("https://")

fun isAllowed(number: String, allowlist: Set<String>) = allowlist.isEmpty() || number in allowlist

class GatewayConfig(context: Context) {
    private val prefs = context.getSharedPreferences("gateway_prefs", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit { putBoolean("enabled", value) }

    var gatewayUrl: String
        get() = getString("gateway_url")
        set(value) = putString("gateway_url", value)

    var deviceToken: String
        get() = getString("device_token")
        set(value) = putString("device_token", value)

    var allowlist: Set<String>
        get() = getString("allowlist").lines().filter { it.isNotBlank() }.toSet()
        set(value) = putString("allowlist", value.joinToString("\n"))

    private fun getString(key: String) = prefs.getString(key, null).orEmpty()

    private fun putString(key: String, value: String) = prefs.edit { putString(key, value.trim()) }
}

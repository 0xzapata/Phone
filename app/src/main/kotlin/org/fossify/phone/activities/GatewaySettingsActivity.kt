package org.fossify.phone.activities

import android.os.Bundle
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.R
import org.fossify.phone.databinding.ActivityGatewaySettingsBinding
import org.fossify.phone.gateway.CALL_STARTED
import org.fossify.phone.gateway.GatewayConfig
import org.fossify.phone.gateway.GatewayEvent
import org.fossify.phone.gateway.GatewayWorker
import org.fossify.phone.gateway.isGatewayUrl
import java.util.UUID

class GatewaySettingsActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityGatewaySettingsBinding::inflate)
    private val gatewayConfig by lazy { GatewayConfig(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.gatewayScrollview))
        setupMaterialScrollListener(scrollingView = binding.gatewayScrollview, topAppBar = binding.gatewayAppbar)

        binding.apply {
            gatewayEnabled.isChecked = gatewayConfig.enabled
            gatewayEnabledHolder.setOnClickListener { gatewayEnabled.toggle() }
            gatewayUrl.setText(gatewayConfig.gatewayUrl)
            gatewayDeviceToken.setText(gatewayConfig.deviceToken)
            gatewayAllowlist.setText(gatewayConfig.allowlist.joinToString("\n"))
            gatewaySave.setOnClickListener {
                if (save()) toast(R.string.gateway_saved)
            }
            gatewaySendTest.setOnClickListener { sendTestEvent() }
        }
        updateTextColors(binding.gatewayHolder)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.gatewayAppbar, NavigationIcon.Arrow)
    }

    private fun save(): Boolean {
        val url = binding.gatewayUrl.text.toString().trim()
        if (!isGatewayUrl(url)) {
            toast(R.string.gateway_url_invalid)
            return false
        }
        val token = binding.gatewayDeviceToken.text.toString().trim()
        if (binding.gatewayEnabled.isChecked && token.isEmpty()) {
            toast(R.string.gateway_token_required)
            return false
        }
        binding.apply {
            gatewayConfig.enabled = gatewayEnabled.isChecked
            gatewayConfig.gatewayUrl = url
            gatewayConfig.deviceToken = token
            gatewayConfig.allowlist = gatewayAllowlist.text.toString().lines()
                .map { it.trim().normalizePhoneNumber() }
                .filter { it.isNotBlank() }
                .toSet()
            gatewayAllowlist.setText(gatewayConfig.allowlist.joinToString("\n"))
        }
        return true
    }

    private fun sendTestEvent() {
        if (!save()) return
        if (!gatewayConfig.enabled) {
            toast(R.string.gateway_enable_first)
            return
        }
        // The first allowlist number keeps the test inside the allowlist, so the worker does not drop it.
        val event = GatewayEvent(
            id = UUID.randomUUID().toString(),
            kind = CALL_STARTED,
            occurredAtMs = System.currentTimeMillis(),
            number = gatewayConfig.allowlist.firstOrNull().orEmpty(),
            displayName = "test",
            direction = "incoming"
        )
        ensureBackgroundThread {
            GatewayWorker.enqueue(this, event)
            toast(R.string.gateway_test_queued)
        }
    }
}

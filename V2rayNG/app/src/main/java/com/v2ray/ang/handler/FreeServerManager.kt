package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils

/**
 * zixora Free VPN — built-in free server list (one-time bootstrap).
 *
 * Ownership note: this object owns only the FIRST-LAUNCH bootstrap lifetime
 * (register the free-servers subscription once, fetch it once).
 * Ongoing periodic updates stay with SubscriptionUpdater; server storage
 * stays with MmkvManager; config parsing stays with AngConfigManager.
 *
 * On first launch, registers a subscription pointing at free-servers.txt
 * in the GitHub repo. The file contains plain share links
 * (vless://, vmess://, trojan://, ss://), one per line.
 *
 * To update the server list, just edit free-servers.txt in the repo —
 * no app rebuild needed. The app re-fetches it every 24 hours automatically.
 *
 * Must be called on a background thread (e.g. from MainViewModel.initialize,
 * which runs on preloadDispatcher) because it performs a blocking fetch.
 */
object FreeServerManager {

    const val FREE_SERVERS_URL =
        "https://raw.githubusercontent.com/SHO101234/zixora-vpn-free/main/free-servers.txt"

    private const val PREF_FREE_SERVERS_INIT = "pref_free_servers_init"
    private const val REMARKS = "zixora Free Servers"

    fun initIfNeeded() {
        try {
            if (MmkvManager.decodeSettingsBool(PREF_FREE_SERVERS_INIT, false)) {
                return
            }
            val exists = MmkvManager.decodeSubscriptions()
                .any { it.subscription.url == FREE_SERVERS_URL }
            if (!exists) {
                val subItem = SubscriptionItem(
                    remarks = REMARKS,
                    url = FREE_SERVERS_URL,
                    enabled = true,
                    autoUpdate = true,
                    updateInterval = 1440 // minutes = 24h
                )
                MmkvManager.encodeSubscription(Utils.getUuid(), subItem)
                LogUtil.i(AppConfig.TAG, "FreeServerManager: free-servers subscription added")
            }
            // Mark bootstrap done before the fetch: the periodic updater
            // covers fetch failures, and this must never re-register.
            MmkvManager.encodeSettings(PREF_FREE_SERVERS_INIT, true)

            // Fetch right away so servers appear on first launch (best effort).
            val sub = MmkvManager.decodeSubscriptions()
                .firstOrNull { it.subscription.url == FREE_SERVERS_URL }
            if (sub != null) {
                val result = AngConfigManager.updateConfigViaSub(sub)
                LogUtil.i(
                    AppConfig.TAG,
                    "FreeServerManager: initial fetch done, configs=${result.configCount}"
                )
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "FreeServerManager: init failed", e)
        }
    }
}

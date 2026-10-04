package com.longlifeio.fineprint.egress

import android.content.pm.PackageInfo

/**
 * Capabilities an app registers beyond ordinary permissions, read from its manifest (ids match
 * bundle.json's device_reach boilerplate). Registered, not necessarily enabled: an accessibility
 * service, for instance, still has to be switched on by the user.
 */
internal fun PackageInfo.deviceReach(requested: Set<String>): List<String> {
    val reach = LinkedHashSet<String>()
    services?.forEach {
        when (it.permission) {
            "android.permission.BIND_ACCESSIBILITY_SERVICE" -> reach += "accessibility_service"
            "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" -> reach += "notification_listener"
            "android.permission.BIND_VPN_SERVICE" -> reach += "vpn_service"
        }
    }
    receivers?.forEach { if (it.permission == "android.permission.BIND_DEVICE_ADMIN") reach += "device_admin" }
    if ("android.permission.SYSTEM_ALERT_WINDOW" in requested) reach += "overlay"
    if ("android.permission.PACKAGE_USAGE_STATS" in requested) reach += "usage_stats"
    if ("android.permission.RECEIVE_BOOT_COMPLETED" in requested) reach += "autostart"
    if ("android.permission.BLUETOOTH_SCAN" in requested && "android.permission.ACCESS_BACKGROUND_LOCATION" in requested) {
        reach += "background_ble_scan"
    }
    return reach.toList()
}

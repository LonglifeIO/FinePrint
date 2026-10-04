package com.longlifeio.fineprint.egress

import android.app.AppOpsManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build

/** A permission an app declares in its manifest (`<uses-permission>`), with its state on this device. */
data class RequestedPermission(
    val name: String,
    val granted: Boolean,
    /** Protection level "dangerous": a runtime permission the user is asked about. */
    val dangerous: Boolean,
)

/** An installed app: who it is, where its code lives, and what it asked for. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val lastUpdateTime: Long,
    val isSystem: Boolean,
    /** False for resource-only packages (e.g. overlays): there is no code to scan. */
    val hasCode: Boolean,
    /** base.apk first, then any split APKs. */
    val apkPaths: List<String>,
    /** Dangerous first, then granted before denied, then by name. */
    val permissions: List<RequestedPermission>,
) {
    /** Changes when the app is updated, so a cached scan of the old version is not reused. */
    val scanKey: String get() = "$packageName@$lastUpdateTime"
}

/**
 * Lists every installed package with its requested permissions, sorted by label. Sees everything
 * only because the manifest declares QUERY_ALL_PACKAGES (Android 11+). Call off the main thread.
 */
fun scanInstalledApps(pm: PackageManager, appOps: AppOpsManager): List<InstalledApp> {
    val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
    } else {
        @Suppress("DEPRECATION")
        pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
    }
    val kinds = HashMap<String, PermissionKind>() // many apps share permissions; look each up once
    return packages
        .mapNotNull { it.toInstalledApp(pm, appOps, kinds) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}

private fun PackageInfo.toInstalledApp(
    pm: PackageManager,
    appOps: AppOpsManager,
    kinds: MutableMap<String, PermissionKind>,
): InstalledApp? {
    val app: ApplicationInfo = applicationInfo ?: return null
    return InstalledApp(
        packageName = packageName,
        label = app.loadLabel(pm).toString(),
        versionName = versionName,
        versionCode = longVersionCode,
        lastUpdateTime = lastUpdateTime,
        isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
        hasCode = app.flags and ApplicationInfo.FLAG_HAS_CODE != 0,
        apkPaths = listOfNotNull(app.sourceDir) + app.splitSourceDirs.orEmpty(),
        permissions = requestedPermissionList(app, pm, appOps, kinds),
    )
}

private fun PackageInfo.requestedPermissionList(
    app: ApplicationInfo,
    pm: PackageManager,
    appOps: AppOpsManager,
    kinds: MutableMap<String, PermissionKind>,
): List<RequestedPermission> {
    val names = requestedPermissions ?: return emptyList()
    val flags = requestedPermissionsFlags
    val requestedKinds = names.map { name -> kinds.getOrPut(name) { kindOf(pm, name) } }
    val grants = Grants(app, appOps, names.toHashSet())
    return names.indices
        .map { i ->
            val bit = (flags?.getOrNull(i) ?: 0) and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
            RequestedPermission(names[i], grants.isGranted(names[i], bit, requestedKinds[i]), requestedKinds[i].dangerous)
        }
        .distinctBy { it.name }
        .sortedWith(
            compareByDescending<RequestedPermission> { it.dangerous }
                .thenByDescending { it.granted }
                .thenBy { it.name },
        )
}

/** How a permission is protected. Unknown permissions (no app defines them here) are never granted. */
private class PermissionKind(
    val dangerous: Boolean,
    /** Special access (`appop` protection), e.g. SYSTEM_ALERT_WINDOW: the user toggles an app-op. */
    val special: Boolean,
    val op: String?,
)

private fun kindOf(pm: PackageManager, name: String): PermissionKind = try {
    val info = pm.getPermissionInfo(name, 0)
    PermissionKind(
        dangerous = info.protection == PermissionInfo.PROTECTION_DANGEROUS,
        special = info.protectionFlags and PermissionInfo.PROTECTION_FLAG_APPOP != 0,
        op = AppOpsManager.permissionToOp(name),
    )
} catch (e: PackageManager.NameNotFoundException) {
    PermissionKind(dangerous = false, special = false, op = null)
}

/**
 * Background permissions, which have no app-op of their own, and the foreground permissions whose
 * op carries their state ("allowed" = all the time). PermissionInfo.backgroundPermission would say
 * this, but it is a system API.
 */
private val FOREGROUND_OF = mapOf(
    "android.permission.ACCESS_BACKGROUND_LOCATION" to
        listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"),
    "android.permission.BODY_SENSORS_BACKGROUND" to listOf("android.permission.BODY_SENSORS"),
)

/**
 * Decides whether a permission is in effect for [app]. The GRANTED bit alone misleads: special
 * access lives entirely in an app-op; and a runtime permission the user denied stays "granted" in
 * the bit when the app targets pre-Android-6 or got it implicitly by a platform split (Android 17's
 * ACCESS_LOCAL_NETWORK, Bluetooth for old apps) -- the denial is kept as the app-op instead. Raw
 * modes are read, because the evaluated mode turns "allowed in foreground" into "ignored" whenever
 * the scanned app is not on screen.
 */
private class Grants(
    private val app: ApplicationInfo,
    private val appOps: AppOpsManager,
    private val requested: Set<String>,
) {
    fun isGranted(name: String, bit: Boolean, kind: PermissionKind): Boolean {
        val op = kind.op
        return when {
            kind.special && op != null -> when (rawMode(op)) {
                AppOpsManager.MODE_ALLOWED, AppOpsManager.MODE_FOREGROUND -> true
                AppOpsManager.MODE_DEFAULT -> bit || (name == SCHEDULE_EXACT_ALARM && exactAlarmsAllowedByDefault(app))
                else -> false
            }
            kind.dangerous && op != null -> bit && rawMode(op).let {
                it != AppOpsManager.MODE_IGNORED && it != AppOpsManager.MODE_ERRORED
            }
            kind.dangerous && app.targetSdkVersion < Build.VERSION_CODES.M -> // e.g. ACCESS_BACKGROUND_LOCATION
                FOREGROUND_OF[name].orEmpty().filter { it in requested }.mapNotNull { AppOpsManager.permissionToOp(it) }
                    .takeIf { it.isNotEmpty() }?.any { rawMode(it) == AppOpsManager.MODE_ALLOWED } ?: bit
            else -> bit
        }
    }

    private fun rawMode(op: String): Int = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            appOps.checkOpRawNoThrow(op, app.uid, app.packageName, null)
        } else {
            @Suppress("DEPRECATION")
            appOps.unsafeCheckOpRawNoThrow(op, app.uid, app.packageName)
        }
    } catch (e: SecurityException) {
        AppOpsManager.MODE_DEFAULT // falls back to the GRANTED bit
    }
}

/** Manifest.permission.SCHEDULE_EXACT_ALARM is API 31; as a name to compare it works on 29-30 too. */
private const val SCHEDULE_EXACT_ALARM = "android.permission.SCHEDULE_EXACT_ALARM"

/** Exact alarms are allowed by default except for apps targeting 33+ on Android 14+ (AlarmManagerService). */
private fun exactAlarmsAllowedByDefault(app: ApplicationInfo): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || app.targetSdkVersion < Build.VERSION_CODES.TIRAMISU

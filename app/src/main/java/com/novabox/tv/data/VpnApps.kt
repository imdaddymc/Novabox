package com.novabox.tv.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * The user's own VPN app (Surfshark, NordVPN…). NovaBox doesn't run or require a VPN: it only
 * remembers which app the user picked, opens it with one press, and shows whether a VPN is on.
 */
object VpnApps {
    data class VpnApp(val pkg: String, val name: String)

    /**
     * Apps on the TV that provide a VPN: they have a service guarded by BIND_VPN_SERVICE. VPN apps
     * usually keep that service private, so it is found from each app's declared services.
     */
    fun installed(ctx: Context): List<VpnApp> {
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        val pkgs = runCatching { pm.getInstalledPackages(PackageManager.GET_SERVICES) }.getOrDefault(emptyList())
        return pkgs.asSequence()
            .filter { it.packageName != ctx.packageName }
            .filter { p -> p.services?.any { it.permission == android.Manifest.permission.BIND_VPN_SERVICE } == true }
            .filter { launchIntent(ctx, it.packageName) != null }
            .mapNotNull { p -> p.applicationInfo?.let { VpnApp(p.packageName, it.loadLabel(pm).toString()) } }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    private fun launchIntent(ctx: Context, pkg: String): Intent? =
        ctx.packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: ctx.packageManager.getLaunchIntentForPackage(pkg)

    /** Opens the VPN app; false if it isn't installed any more. */
    fun open(ctx: Context, pkg: String): Boolean {
        val i = launchIntent(ctx, pkg) ?: return false
        return runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    /** Whether NovaBox's connection goes through a VPN right now. */
    fun active(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val net = cm.activeNetwork ?: return false
        return cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }
}

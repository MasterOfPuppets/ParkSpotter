package com.masterofpuppets.parkspotter.domain.service.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.masterofpuppets.parkspotter.R
import com.masterofpuppets.parkspotter.domain.model.InstalledNavApp
import java.util.Locale

class NavigationServiceImpl : NavigationService {

    override fun getInstalledNavigationApps(context: Context): List<InstalledNavApp> {
        val pm = context.packageManager
        val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=0,0"))
        val resolveInfos = pm.queryIntentActivities(geoIntent, PackageManager.MATCH_DEFAULT_ONLY)

        val appList = mutableListOf<InstalledNavApp>()
        // 1. System Default / Ask
        appList.add(
            InstalledNavApp(
                packageName = "system_default",
                appName = context.getString(R.string.settings_nav_app_system_default),
                isSystemDefault = true
            )
        )

        // 2. Discover all installed navigation/map apps
        val knownOrDiscovered = resolveInfos.mapNotNull { ri ->
            val pkg = ri.activityInfo.packageName
            val label = ri.loadLabel(pm).toString()
            if (pkg.isNotBlank()) InstalledNavApp(pkg, label) else null
        }.distinctBy { it.packageName }

        appList.addAll(knownOrDiscovered)
        return appList
    }

    override fun navigateDirect(
        context: Context,
        destLat: Double,
        destLon: Double,
        destName: String?,
        targetPackageName: String?
    ): Boolean {
        val latStr = String.format(Locale.US, "%.6f", destLat)
        val lonStr = String.format(Locale.US, "%.6f", destLon)
        val label = destName?.takeIf { it.isNotBlank() } ?: "ParkSpotter Spot"

        val intentsToTry = mutableListOf<Intent>()

        if (!targetPackageName.isNullOrBlank() && targetPackageName != "system_default") {
            intentsToTry.add(createAppSpecificDirectIntent(targetPackageName, latStr, lonStr, label))
        }

        // Generic Geo fallback & Chooser fallback
        intentsToTry.add(createGenericGeoIntent(latStr, lonStr, label))

        val isSystemDefault = targetPackageName.isNullOrBlank() || targetPackageName == "system_default"
        return tryLaunchIntents(context, intentsToTry, isSystemDefault)
    }

    override fun navigateTrip(
        context: Context,
        originLat: Double,
        originLon: Double,
        stops: List<Pair<Double, Double>>,
        destLat: Double,
        destLon: Double,
        destName: String?,
        targetPackageName: String?
    ): Boolean {
        val origLatStr = String.format(Locale.US, "%.6f", originLat)
        val origLonStr = String.format(Locale.US, "%.6f", originLon)
        val destLatStr = String.format(Locale.US, "%.6f", destLat)
        val destLonStr = String.format(Locale.US, "%.6f", destLon)

        val waypointsFormatted = stops.joinToString("|") {
            "${String.format(Locale.US, "%.6f", it.first)},${String.format(Locale.US, "%.6f", it.second)}"
        }

        val intentsToTry = mutableListOf<Intent>()

        if (!targetPackageName.isNullOrBlank() && targetPackageName != "system_default") {
            intentsToTry.add(createAppSpecificTripIntent(targetPackageName, origLatStr, origLonStr, destLatStr, destLonStr, waypointsFormatted, destName))
        }

        // Universal Google Maps Trip URL fallback (works in any browser or maps app)
        intentsToTry.add(createGoogleMapsTripIntent(origLatStr, origLonStr, destLatStr, destLonStr, waypointsFormatted, null))
        // Generic Geo fallback
        intentsToTry.add(createGenericGeoIntent(destLatStr, destLonStr, destName))

        val isSystemDefault = targetPackageName.isNullOrBlank() || targetPackageName == "system_default"
        return tryLaunchIntents(context, intentsToTry, isSystemDefault)
    }

    override fun copyCoordinatesToClipboard(
        context: Context,
        lat: Double,
        lon: Double
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val coordsText = String.format(Locale.US, "%.6f, %.6f", lat, lon)
        val clip = ClipData.newPlainText("Coordinates", coordsText)
        clipboard.setPrimaryClip(clip)
    }

    private fun tryLaunchIntents(context: Context, intents: List<Intent>, isSystemDefault: Boolean): Boolean {
        for (intent in intents) {
            try {
                if (isSystemDefault && intent.`package` == null) {
                    val chooser = Intent.createChooser(intent, "Navigate with")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                    return true
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Exception) {
                // Try next fallback intent
            }
        }
        return false
    }

    private fun createAppSpecificDirectIntent(pkg: String, lat: String, lon: String, label: String): Intent {
        return when {
            pkg.contains("waze", ignoreCase = true) -> {
                Intent(Intent.ACTION_VIEW, Uri.parse("waze://?ll=$lat,$lon&navigate=yes")).apply {
                    setPackage(pkg)
                }
            }
            pkg.contains("osmand", ignoreCase = true) -> {
                Intent(Intent.ACTION_VIEW, Uri.parse("osmand.navigation:q=$lat,$lon&name=${Uri.encode(label)}")).apply {
                    setPackage(pkg)
                }
            }
            else -> {
                // Google Maps, Kurviger, Sygic, Here WeGo, TomTom, etc. all respond to standard geo: URI
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(label)})")).apply {
                    setPackage(pkg)
                }
            }
        }
    }

    private fun createAppSpecificTripIntent(
        pkg: String,
        origLat: String,
        origLon: String,
        destLat: String,
        destLon: String,
        waypoints: String,
        label: String?
    ): Intent {
        return when {
            pkg.contains("google.android.apps.maps", ignoreCase = true) -> {
                createGoogleMapsTripIntent(origLat, origLon, destLat, destLon, waypoints, "com.google.android.apps.maps")
            }
            pkg.contains("osmand", ignoreCase = true) -> {
                Intent(Intent.ACTION_VIEW, Uri.parse("osmand.navigation:s_lat=$origLat&s_lon=$origLon&d_lat=$destLat&d_lon=$destLon")).apply {
                    setPackage(pkg)
                }
            }
            pkg.contains("waze", ignoreCase = true) -> {
                // Waze does not support multi-stop URL API natively -> navigate directly to final destination
                Intent(Intent.ACTION_VIEW, Uri.parse("waze://?ll=$destLat,$destLon&navigate=yes")).apply {
                    setPackage(pkg)
                }
            }
            else -> {
                // Kurviger / other apps: send intent targeting package
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:$destLat,$destLon?q=$destLat,$destLon(${Uri.encode(label ?: "Destination")})")).apply {
                    setPackage(pkg)
                }
            }
        }
    }

    private fun createGoogleMapsTripIntent(
        origLat: String,
        origLon: String,
        destLat: String,
        destLon: String,
        waypoints: String,
        targetPackage: String?
    ): Intent {
        val uriBuilder = Uri.parse("https://www.google.com/maps/dir/").buildUpon()
            .appendQueryParameter("api", "1")
            .appendQueryParameter("origin", "$origLat,$origLon")
            .appendQueryParameter("destination", "$destLat,$destLon")
            .appendQueryParameter("travelmode", "driving")

        if (waypoints.isNotBlank()) {
            uriBuilder.appendQueryParameter("waypoints", waypoints)
        }

        return Intent(Intent.ACTION_VIEW, uriBuilder.build()).apply {
            if (!targetPackage.isNullOrBlank()) {
                setPackage(targetPackage)
            }
        }
    }

    private fun createGenericGeoIntent(lat: String, lon: String, label: String?): Intent {
        val encodedLabel = Uri.encode(label ?: "Destination")
        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon($encodedLabel)")
        return Intent(Intent.ACTION_VIEW, uri)
    }
}

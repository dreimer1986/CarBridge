package org.carbridge.core

/** Choices for [SplitScreenPatch]. */
data class SplitScreenOptions(
    /** Show the app under Android Auto's button strip instead of below it. */
    val fullHeight: Boolean = false,
)

/**
 * Shows the app in Android Auto's main split-screen pane instead of full screen: a navigation-
 * category car app whose map surface shows the app's own launcher activity on a virtual display,
 * started through Shizuku. An alternative to [CarPatch], never combined with it.
 *
 * Only the manifest is changed here. Each host also adds the split-screen code: the host dex
 * (classes named under [EXTENSION]) to the app's own classes, and the payload dex plus its Car
 * App Library's API level as [PAYLOAD_ASSETS].
 */
object SplitScreenPatch {
    const val EXTENSION = "org.carbridge.extension.splitscreen"
    const val SERVICE = "$EXTENSION.SplitScreenCarAppService"
    const val DESCRIPTOR_NAME = "carbridge_split_screen_app_desc"

    // Read by the payload's AppDisplay
    const val FULL_HEIGHT_META = "$EXTENSION.FULL_HEIGHT"
    const val SHIZUKU_PROVIDER = "rikka.shizuku.ShizukuProvider"

    /** APK paths of the files the payload loader reads, see the host extension's PayloadClassLoader. */
    const val PAYLOAD_DEX_ASSET = "assets/carbridge/splitscreen.dex"
    const val CAR_APP_API_LEVEL_ASSET = "assets/carbridge/car-app-api.level"
    val PAYLOAD_ASSETS = listOf(PAYLOAD_DEX_ASSET, CAR_APP_API_LEVEL_ASSET)

    private const val NAVIGATION_CATEGORY = "androidx.car.app.category.NAVIGATION"
    private const val CAR_LAUNCHER_CATEGORY = "android.intent.category.CAR_LAUNCHER"

    // android.R.attr ids, verified against android-36 android.jar
    private val ALLOW_EMBEDDED = AndroidAttr("allowEmbedded", 0x010103f5)
    private val EXPORTED = AndroidAttr("exported", 0x01010010)
    private val AUTHORITIES = AndroidAttr("authorities", 0x01010018)
    private val ENABLED = AndroidAttr("enabled", 0x0101000e)
    private val MULTIPROCESS = AndroidAttr("multiprocess", 0x01010013)
    private val PERMISSION = AndroidAttr("permission", 0x01010006)

    val PERMISSIONS = listOf(
        "androidx.car.app.ACCESS_SURFACE",
        "androidx.car.app.NAVIGATION_TEMPLATES",
        // Shizuku only sends its binder to apps that request this
        "moe.shizuku.manager.permission.API_V23",
    )

    /** [packageName] is the app's package as installed; it names the Shizuku provider. */
    fun apply(host: ManifestHost, packageName: String, options: SplitScreenOptions = SplitScreenOptions()) {
        val app = host.application
        if (app.children("service").any { it.getString(CarPatch.NAME) == SERVICE }) return
        check(CarPatch.launcherActivities(app).isNotEmpty()) { "no launcher activity found" }
        check(!hasCarPatch(app)) { "the app already has the full-screen Android Auto changes" }

        for (perm in PERMISSIONS) {
            if (!host.hasPermission(perm)) host.addPermission(perm)
        }

        // Keeps Android Auto's media player for apps that already declared one
        val keepMedia = findMeta(app, CarPatch.CAR_APP_META) != null
        host.addAutomotiveDescriptor(DESCRIPTOR_NAME, *listOfNotNull("template", "media".takeIf { keepMedia }).toTypedArray())
        setMeta(app, CarPatch.CAR_APP_META) { it.setResource(CarPatch.RESOURCE, "xml", DESCRIPTOR_NAME) }
        setMeta(app, "androidx.car.app.minCarApiLevel") { it.setInt(CarPatch.VALUE, 5) }
        setMeta(app, "moe.shizuku.client.V3_SUPPORT") { it.setBoolean(CarPatch.VALUE, true) }

        stripCarAppServices(app)
        addService(app, options)
        addShizukuProvider(app, packageName)

        for (activity in app.children("activity")) {
            // Activities may only start on an app-owned virtual display when embeddable
            activity.setBoolean(ALLOW_EMBEDDED, true)
            activity.setBoolean(CarPatch.RESIZEABLE, true)
            if (activity.getInt(CarPatch.SCREEN_ORIENTATION) in CarPatch.PORTRAIT_ORIENTATIONS) {
                activity.remove(CarPatch.SCREEN_ORIENTATION)
            }
        }
        host.log("Split screen: added $SERVICE")
    }

    /** Points the Shizuku provider at [packageName], e.g. after another patch renamed the app. */
    fun updateShizukuAuthority(app: ManifestElement, packageName: String) {
        app.children("provider").firstOrNull { it.getString(CarPatch.NAME) == SHIZUKU_PROVIDER }
            ?.setString(AUTHORITIES, "$packageName.shizuku")
    }

    /**
     * True when [CarPatch] changed the app: it adds the car launcher category to launcher
     * activities, which apps don't declare themselves.
     */
    fun hasCarPatch(app: ManifestElement) =
        CarPatch.launcherActivities(app).any { activity ->
            activity.children("intent-filter").any { filter ->
                filter.children("category").any { it.getString(CarPatch.NAME) == CAR_LAUNCHER_CATEGORY }
            }
        }

    /** The app's own template screens would compete with the split-screen service. */
    private fun stripCarAppServices(app: ManifestElement) {
        for (service in app.children("service")) {
            for (filter in service.children("intent-filter")) {
                val actions = filter.children("action").mapNotNull { it.getString(CarPatch.NAME) }
                if (CarPatch.CAR_APP_SERVICE_ACTION in actions) service.removeChild(filter)
            }
        }
    }

    private fun addService(app: ManifestElement, options: SplitScreenOptions) {
        val service = app.append("service")
        service.setString(CarPatch.NAME, SERVICE)
        service.setBoolean(EXPORTED, true)
        setMeta(service, FULL_HEIGHT_META) { it.setBoolean(CarPatch.VALUE, options.fullHeight) }
        val filter = service.append("intent-filter")
        filter.append("action").setString(CarPatch.NAME, CarPatch.CAR_APP_SERVICE_ACTION)
        filter.append("category").setString(CarPatch.NAME, NAVIGATION_CATEGORY)
    }

    /** Shizuku delivers its binder through this provider; only system and shell may call it. */
    private fun addShizukuProvider(app: ManifestElement, packageName: String) {
        if (app.children("provider").any { it.getString(CarPatch.NAME) == SHIZUKU_PROVIDER }) return
        val provider = app.append("provider")
        provider.setString(CarPatch.NAME, SHIZUKU_PROVIDER)
        provider.setString(AUTHORITIES, "$packageName.shizuku")
        provider.setBoolean(MULTIPROCESS, false)
        provider.setBoolean(ENABLED, true)
        provider.setBoolean(EXPORTED, true)
        provider.setString(PERMISSION, "android.permission.INTERACT_ACROSS_USERS_FULL")
    }

    private fun findMeta(parent: ManifestElement, name: String) =
        parent.children("meta-data").firstOrNull { it.getString(CarPatch.NAME) == name }

    private inline fun setMeta(parent: ManifestElement, name: String, setValue: (ManifestElement) -> Unit) {
        val meta = findMeta(parent, name) ?: parent.append("meta-data").also { it.setString(CarPatch.NAME, name) }
        setValue(meta)
    }
}

package org.carbridge

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModelProvider
import org.carbridge.patch.KeyBackup
import org.carbridge.ui.CarBridgeTheme
import org.carbridge.ui.HomeActions
import org.carbridge.ui.HomeScreen

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[HomeModel::class.java] }
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    // A cancelled picker keeps the code dialog open, so the user can try again
    private val saveBackup = registerForActivityResult(ActivityResultContracts.CreateDocument(KeyBackup.MIME_TYPE)) { uri ->
        uri?.let(model::saveBackup)
    }
    private val pickApks = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        model.addFromFiles(uris)
    }
    private val pickBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::openRestore)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The header is always guide-sign green, so status bar icons stay light in both themes
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val actions = HomeActions(
            allowInstalls = {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            },
            openAndroidAuto = ::openAndroidAuto,
            markDeveloperMode = model::markDeveloperMode,
            markUnknownSources = model::markUnknownSources,
            add = model::requestAdd,
            update = { copy -> copy.original?.let { model.add(it, update = true) } },
            updateAll = model::updateAll,
            openMorphe = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MORPHE_SOURCE))) },
            setFillScreen = model::setFillScreen,
            setKeepMedia = model::setKeepMedia,
            setCarLayout = model::setCarLayout,
            remove = { copy -> startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${copy.packageName}"))) },
            open = { copy -> packageManager.getLaunchIntentForPackage(copy.packageName)?.let(::startActivity) },
            saveBackup = saveBackup::launch,
            // Files apps rarely know the .cbkey type, so show every file
            pickBackup = { pickBackup.launch(arrayOf("*/*")) },
            // Bundles (.apks, .xapk) have no registered type, so show every file and check the contents
            pickApks = { copy -> model.expectFilesFor(copy); pickApks.launch(arrayOf("*/*")) },
        )
        UpdateCheck.schedule(this)
        if (savedInstanceState == null) handleIntent(intent)
        lifecycleScope.launch {
            model.notificationPrompt.collect { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
        setContent {
            CarBridgeTheme {
                HomeScreen(model, actions)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            UpdateCheck.ACTION_UPDATE -> {
                model.updateFromNotification(intent.getStringExtra(UpdateCheck.EXTRA_PACKAGE))
                UpdateCheck.dismiss(this)
            }
            // An APK opened or shared from another app; a newer version updates its existing copy
            Intent.ACTION_VIEW, Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                model.expectFilesFor(null)
                model.addFromFiles(sharedFiles(intent))
            }
        }
    }

    private fun sharedFiles(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_VIEW -> listOfNotNull(intent.data)
        Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        else -> intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    }

    override fun onResume() {
        super.onResume()
        // Settings, uninstall and install prompts all return here
        model.refresh()
        UpdateCheck.dismissIfResolved(this)
    }

    private fun openAndroidAuto() {
        val settings = Intent(Intent.ACTION_APPLICATION_PREFERENCES).setPackage(ANDROID_AUTO)
        try {
            startActivity(settings)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$ANDROID_AUTO")))
        }
    }

    private companion object {
        const val ANDROID_AUTO = "com.google.android.projection.gearhead"
        const val MORPHE_SOURCE = "https://morphe.software/add-source?github=jmwerk/carbridge-patches"
    }
}

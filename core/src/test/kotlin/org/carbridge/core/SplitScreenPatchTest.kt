package org.carbridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitScreenPatchTest {

    @Test
    fun addsNavigationService() {
        val host = DomHost.parse(manifest())
        SplitScreenPatch.apply(host, "a.b")

        val app = host.application
        val service = app.children("service").single { it.getString(CarPatch.NAME) == SplitScreenPatch.SERVICE }
        assertEquals("true", service.getString(EXPORTED))
        val filter = service.children("intent-filter").single()
        assertEquals(listOf(CarPatch.CAR_APP_SERVICE_ACTION), filter.children("action").map { it.getString(CarPatch.NAME) })
        assertEquals(listOf("androidx.car.app.category.NAVIGATION"), filter.children("category").map { it.getString(CarPatch.NAME) })
        assertEquals("false", meta(service, SplitScreenPatch.FULL_HEIGHT_META)?.getString(CarPatch.VALUE))

        assertEquals(listOf("template"), host.descriptorUses[SplitScreenPatch.DESCRIPTOR_NAME])
        assertEquals("@xml/${SplitScreenPatch.DESCRIPTOR_NAME}", meta(app, CarPatch.CAR_APP_META)?.getString(CarPatch.RESOURCE))
        assertEquals("5", meta(app, "androidx.car.app.minCarApiLevel")?.getString(CarPatch.VALUE))
        for (perm in SplitScreenPatch.PERMISSIONS) assertTrue(host.hasPermission(perm))
    }

    @Test
    fun addsShizukuProviderForPackage() {
        val host = DomHost.parse(manifest())
        SplitScreenPatch.apply(host, "a.b")
        assertEquals("a.b.shizuku", provider(host)?.getString(AUTHORITIES))

        SplitScreenPatch.updateShizukuAuthority(host.application, "a.b.car")
        assertEquals("a.b.car.shizuku", provider(host)?.getString(AUTHORITIES))
    }

    @Test
    fun makesActivitiesEmbeddable() {
        val host = DomHost.parse(manifest())
        SplitScreenPatch.apply(host, "a.b")
        for (activity in host.application.children("activity")) {
            assertEquals("true", activity.getString(ALLOW_EMBEDDED))
            assertEquals("true", activity.getString(CarPatch.RESIZEABLE))
        }
        val main = host.application.children("activity").single { it.getString(CarPatch.NAME) == ".Main" }
        assertNull(main.getString(CarPatch.SCREEN_ORIENTATION))
    }

    @Test
    fun stripsCarAppServiceButKeepsMedia() {
        val host = DomHost.parse(manifest())
        SplitScreenPatch.apply(host, "a.b")
        assertTrue(serviceFilters(host, ".CarApp").isEmpty())
        assertEquals(1, serviceFilters(host, ".Media").size)
    }

    @Test
    fun keepsMediaPlayerOfCarApps() {
        val xml = manifest().replace(
            "<application>",
            "<application><meta-data android:name=\"${CarPatch.CAR_APP_META}\" android:resource=\"@xml/own\"/>",
        )
        val host = DomHost.parse(xml)
        SplitScreenPatch.apply(host, "a.b")
        assertEquals(listOf("template", "media"), host.descriptorUses[SplitScreenPatch.DESCRIPTOR_NAME])
        assertEquals(1, host.application.children("meta-data").count { it.getString(CarPatch.NAME) == CarPatch.CAR_APP_META })
    }

    @Test
    fun fullHeightOption() {
        val host = DomHost.parse(manifest())
        SplitScreenPatch.apply(host, "a.b", SplitScreenOptions(fullHeight = true))
        val service = host.application.children("service").single { it.getString(CarPatch.NAME) == SplitScreenPatch.SERVICE }
        assertEquals("true", meta(service, SplitScreenPatch.FULL_HEIGHT_META)?.getString(CarPatch.VALUE))
    }

    @Test
    fun isIdempotent() {
        val host = DomHost.parse(manifest())
        SplitScreenPatch.apply(host, "a.b")
        SplitScreenPatch.apply(host, "a.b")
        assertEquals(1, host.application.children("service").count { it.getString(CarPatch.NAME) == SplitScreenPatch.SERVICE })
        assertEquals(1, host.application.children("provider").size)
    }

    @Test
    fun detectsCarPatch() {
        val host = DomHost.parse(manifest())
        assertFalse(SplitScreenPatch.hasCarPatch(host.application))
        CarPatch.apply(host)
        assertTrue(SplitScreenPatch.hasCarPatch(host.application))
    }

    @Test(expected = IllegalStateException::class)
    fun refusesCarPatchedApp() {
        val host = DomHost.parse(manifest())
        CarPatch.apply(host)
        SplitScreenPatch.apply(host, "a.b")
    }

    private fun manifest() = """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="a.b">
          <application>
            <activity android:name=".Main" android:screenOrientation="portrait">
              <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
              </intent-filter>
            </activity>
            <activity android:name=".Settings"/>
            <service android:name=".Media">
              <intent-filter><action android:name="android.media.browse.MediaBrowserService"/></intent-filter>
            </service>
            <service android:name=".CarApp">
              <intent-filter><action android:name="androidx.car.app.CarAppService"/></intent-filter>
            </service>
          </application>
        </manifest>
    """.trimIndent()

    private fun meta(parent: ManifestElement, name: String) =
        parent.children("meta-data").firstOrNull { it.getString(CarPatch.NAME) == name }

    private fun provider(host: DomHost) =
        host.application.children("provider").singleOrNull { it.getString(CarPatch.NAME) == SplitScreenPatch.SHIZUKU_PROVIDER }

    private fun serviceFilters(host: DomHost, name: String) =
        host.application.children("service").single { it.getString(CarPatch.NAME) == name }.children("intent-filter")

    private companion object {
        val EXPORTED = AndroidAttr("exported", 0x01010010)
        val AUTHORITIES = AndroidAttr("authorities", 0x01010018)
        val ALLOW_EMBEDDED = AndroidAttr("allowEmbedded", 0x010103f5)
    }
}

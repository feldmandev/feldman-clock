package com.feldman.clock.app.actions

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.assistant.appactions.testing.aatl.AppActionsTestManager
import com.google.assistant.appactions.testing.aatl.fulfillment.AppActionsFulfillmentIntentResult
import com.google.assistant.appactions.testing.aatl.fulfillment.FulfillmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppActionsTest {

    private lateinit var aatl: AppActionsTestManager

    @Before
    fun init() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        aatl = AppActionsTestManager(appContext)
    }

    @Test
    fun testCreateAlarm_resolvesToDeepLink() {
        val intentName = "actions.intent.CREATE_ALARM"
        val params = mapOf(
            "alarm.time" to "2026-01-22T08:00:00",
            "alarm.message" to "Test Alarm"
        )

        val result = aatl.fulfill(intentName, params)

        assertEquals("Action should resolve to an Intent", FulfillmentType.INTENT, result.fulfillmentType)

        val intentResult = result as AppActionsFulfillmentIntentResult
        val intent = intentResult.intent

        assertNotNull("Intent should not be null", intent)

        val data = intent.data
        assertNotNull("Intent data (Deep Link) should not be null", data)

        assertEquals("Scheme should be feldmanclock", "feldmanclock", data?.scheme)
        assertEquals("Host should be alarm", "alarm", data?.host)
        assertEquals("Path should be /create", "/create", data?.path)
        assertEquals("Time parameter incorrect", "2026-01-22T08:00:00", data?.getQueryParameter("time"))
        assertEquals("Message parameter incorrect", "Test Alarm", data?.getQueryParameter("message"))
        assertEquals(Intent.ACTION_VIEW, intent.action)
    }
}

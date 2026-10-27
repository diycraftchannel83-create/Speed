package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.TripEntity
import com.example.export.TripExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Alangmotor", appName)
    }

    @Test
    fun `export to csv produces valid telemetry file`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testTrip = TripEntity(
            id = 1,
            title = "Morning Test Sprint",
            startTime = System.currentTimeMillis() - 60000,
            endTime = System.currentTimeMillis(),
            maxSpeedKmh = 112.5f,
            avgSpeedKmh = 64.2f,
            distanceMeters = 3450.0,
            durationSeconds = 60,
            race0to60Seconds = 4.12f,
            race0to100Seconds = 9.85f,
            snapshotCount = 2,
            routePointsJson = """[{"latitude":-6.2088,"longitude":106.8456,"speedKmh":45.0,"acceleration":1.2,"altitude":28.0,"timestamp":1700000000000}]""",
            weatherCondition = "Clear",
            temperatureCelsius = 28
        )

        val csvFile = TripExporter.exportToCsv(context, testTrip)
        assertTrue(csvFile.exists())
        assertTrue(csvFile.length() > 0)
        val content = csvFile.readText()
        assertTrue(content.contains("ALANGMOTOR SPEED RECORDER"))
        assertTrue(content.contains("112.5"))
    }
}

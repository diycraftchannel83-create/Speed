package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.model.RoutePoint
import com.example.data.model.TripEntity
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TripExporter {

    fun parsePoints(json: String): List<RoutePoint> {
        return try {
            val list = mutableListOf<RoutePoint>()
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    RoutePoint(
                        latitude = obj.optDouble("latitude", 0.0),
                        longitude = obj.optDouble("longitude", 0.0),
                        speedKmh = obj.optDouble("speedKmh", 0.0).toFloat(),
                        acceleration = obj.optDouble("acceleration", 0.0).toFloat(),
                        altitude = obj.optDouble("altitude", 0.0),
                        timestamp = obj.optLong("timestamp", 0L)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun exportToCsv(context: Context, trip: TripEntity): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = "Alangmotor_Trip_${trip.id}_${System.currentTimeMillis()}.csv"
        val file = File(exportDir, fileName)

        val points = parsePoints(trip.routePointsJson)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        file.printWriter().use { out ->
            out.println("# ALANGMOTOR SPEED RECORDER TELEMETRY LOG")
            out.println("# Trip ID,${trip.id}")
            out.println("# Title,${trip.title.replace(",", ";")}")
            out.println("# Start Time,${dateFormat.format(Date(trip.startTime))}")
            out.println("# End Time,${dateFormat.format(Date(trip.endTime))}")
            out.println("# Duration (sec),${trip.durationSeconds}")
            out.println("# Total Distance (km),${String.format(Locale.US, "%.2f", trip.distanceMeters / 1000.0)}")
            out.println("# Max Speed (km/h),${String.format(Locale.US, "%.1f", trip.maxSpeedKmh)}")
            out.println("# Avg Speed (km/h),${String.format(Locale.US, "%.1f", trip.avgSpeedKmh)}")
            out.println("# 0-60 km/h (s),${trip.race0to60Seconds?.let { String.format(Locale.US, "%.2f", it) } ?: "--"}")
            out.println("# 0-100 km/h (s),${trip.race0to100Seconds?.let { String.format(Locale.US, "%.2f", it) } ?: "--"}")
            out.println("# Snapshots Captured,${trip.snapshotCount}")
            out.println("# Weather,${trip.weatherCondition} (${trip.temperatureCelsius} C)")
            out.println("")
            out.println("Index,Timestamp,DateTime,Latitude,Longitude,Altitude_m,Speed_kmh,Acceleration_ms2")

            points.forEachIndexed { index, pt ->
                val timeStr = dateFormat.format(Date(pt.timestamp))
                out.println(
                    "$index,${pt.timestamp},$timeStr,${pt.latitude},${pt.longitude}," +
                            "${String.format(Locale.US, "%.1f", pt.altitude)}," +
                            "${String.format(Locale.US, "%.1f", pt.speedKmh)}," +
                            "${String.format(Locale.US, "%.2f", pt.acceleration)}"
                )
            }
        }
        return file
    }

    fun exportToPdf(context: Context, trip: TripEntity): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = "Alangmotor_Trip_${trip.id}_${System.currentTimeMillis()}.pdf"
        val file = File(exportDir, fileName)

        val points = parsePoints(trip.routePointsJson)
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 at 72dpi
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Background
        val bgPaint = Paint().apply { color = Color.rgb(10, 20, 32) }
        canvas.drawRect(0f, 0f, 595f, 842f, bgPaint)

        // Header Banner
        val headerPaint = Paint().apply { color = Color.rgb(4, 30, 56) }
        canvas.drawRect(0f, 0f, 595f, 90f, headerPaint)

        val cyanPaint = Paint().apply {
            color = Color.rgb(0, 229, 255)
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText("ALANGMOTOR SPEED RECORDER", 30f, 42f, cyanPaint)

        val subheaderPaint = Paint().apply {
            color = Color.rgb(180, 215, 235)
            textSize = 11f
            isAntiAlias = true
        }
        canvas.drawText("Vehicle Telemetry & Route Performance Report", 30f, 65f, subheaderPaint)

        val dateHeaderPaint = Paint().apply {
            color = Color.rgb(255, 145, 0)
            textSize = 11f
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        canvas.drawText(dateFormat.format(Date(trip.startTime)), 565f, 55f, dateHeaderPaint)

        // Summary Cards
        val cardPaint = Paint().apply { color = Color.rgb(15, 34, 55) }
        val strokePaint = Paint().apply {
            color = Color.rgb(0, 176, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }

        val cardRect = RectF(30f, 110f, 565f, 260f)
        canvas.drawRoundRect(cardRect, 10f, 10f, cardPaint)
        canvas.drawRoundRect(cardRect, 10f, 10f, strokePaint)

        val cardTitlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText("TRIP METRICS: ${trip.title}", 50f, 140f, cardTitlePaint)

        val statLabelPaint = Paint().apply {
            color = Color.rgb(140, 175, 195)
            textSize = 10f
            isAntiAlias = true
        }
        val statValuePaint = Paint().apply {
            color = Color.rgb(0, 229, 255)
            textSize = 16f
            isFakeBoldText = true
            isAntiAlias = true
        }

        // Row 1
        canvas.drawText("TOTAL DISTANCE", 50f, 170f, statLabelPaint)
        canvas.drawText(String.format(Locale.US, "%.2f km", trip.distanceMeters / 1000.0), 50f, 192f, statValuePaint)

        canvas.drawText("TOP SPEED", 180f, 170f, statLabelPaint)
        val redValuePaint = Paint(statValuePaint).apply { color = Color.rgb(255, 23, 68) }
        canvas.drawText(String.format(Locale.US, "%.1f km/h", trip.maxSpeedKmh), 180f, 192f, redValuePaint)

        canvas.drawText("AVERAGE SPEED", 310f, 170f, statLabelPaint)
        canvas.drawText(String.format(Locale.US, "%.1f km/h", trip.avgSpeedKmh), 310f, 192f, statValuePaint)

        canvas.drawText("DURATION", 440f, 170f, statLabelPaint)
        val hours = trip.durationSeconds / 3600
        val mins = (trip.durationSeconds % 3600) / 60
        val secs = trip.durationSeconds % 60
        val durationStr = String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
        canvas.drawText(durationStr, 440f, 192f, statValuePaint)

        // Row 2
        val orangeValuePaint = Paint(statValuePaint).apply { color = Color.rgb(255, 145, 0) }
        canvas.drawText("0-60 KM/H TIME", 50f, 222f, statLabelPaint)
        canvas.drawText(trip.race0to60Seconds?.let { String.format(Locale.US, "%.2f s", it) } ?: "N/A", 50f, 242f, orangeValuePaint)

        canvas.drawText("0-100 KM/H TIME", 180f, 222f, statLabelPaint)
        canvas.drawText(trip.race0to100Seconds?.let { String.format(Locale.US, "%.2f s", it) } ?: "N/A", 180f, 242f, orangeValuePaint)

        canvas.drawText("WEATHER", 310f, 222f, statLabelPaint)
        canvas.drawText("${trip.weatherCondition} (${trip.temperatureCelsius}°C)", 310f, 242f, statValuePaint)

        canvas.drawText("SNAPSHOTS", 440f, 222f, statLabelPaint)
        canvas.drawText("${trip.snapshotCount} captures", 440f, 242f, statValuePaint)

        // Speed Graph Section
        val graphCardRect = RectF(30f, 280f, 565f, 500f)
        canvas.drawRoundRect(graphCardRect, 10f, 10f, cardPaint)
        canvas.drawRoundRect(graphCardRect, 10f, 10f, strokePaint)

        canvas.drawText("SPEED TELEMETRY PROFILE", 50f, 310f, cardTitlePaint)

        val graphLeft = 60f
        val graphRight = 535f
        val graphTop = 330f
        val graphBottom = 480f

        val gridLinePaint = Paint().apply {
            color = Color.rgb(30, 55, 80)
            strokeWidth = 1f
        }
        for (i in 0..4) {
            val y = graphTop + (graphBottom - graphTop) * (i / 4f)
            canvas.drawLine(graphLeft, y, graphRight, y, gridLinePaint)
            val speedLabel = String.format(Locale.US, "%.0f", (trip.maxSpeedKmh.coerceAtLeast(60f)) * (1f - i / 4f))
            canvas.drawText(speedLabel, graphLeft - 22f, y + 4f, statLabelPaint)
        }

        if (points.isNotEmpty()) {
            val maxSpeed = (points.maxOfOrNull { it.speedKmh } ?: 60f).coerceAtLeast(40f)
            val linePaint = Paint().apply {
                color = Color.rgb(0, 229, 255)
                strokeWidth = 2.5f
                style = Paint.Style.STROKE
                isAntiAlias = true
            }

            var prevX = graphLeft
            var prevY = graphBottom - (points.first().speedKmh / maxSpeed) * (graphBottom - graphTop)

            for (i in 1 until points.size) {
                val pt = points[i]
                val currentX = graphLeft + (i.toFloat() / (points.size - 1)) * (graphRight - graphLeft)
                val currentY = graphBottom - (pt.speedKmh / maxSpeed) * (graphBottom - graphTop)
                canvas.drawLine(prevX, prevY, currentX, currentY, linePaint)
                prevX = currentX
                prevY = currentY
            }
        } else {
            canvas.drawText("No granular speed telemetry points recorded", graphLeft + 40f, (graphTop + graphBottom) / 2, statLabelPaint)
        }

        // Data Table Preview (First 8 points)
        val tableCardRect = RectF(30f, 520f, 565f, 780f)
        canvas.drawRoundRect(tableCardRect, 10f, 10f, cardPaint)
        canvas.drawRoundRect(tableCardRect, 10f, 10f, strokePaint)

        canvas.drawText("ROUTE WAYPOINTS SAMPLE (${points.size} Total Samples)", 50f, 550f, cardTitlePaint)

        var rowY = 580f
        canvas.drawText("Pt", 50f, rowY, statLabelPaint)
        canvas.drawText("Time", 90f, rowY, statLabelPaint)
        canvas.drawText("Latitude", 200f, rowY, statLabelPaint)
        canvas.drawText("Longitude", 310f, rowY, statLabelPaint)
        canvas.drawText("Speed", 420f, rowY, statLabelPaint)
        canvas.drawText("Alt (m)", 500f, rowY, statLabelPaint)

        val tableRowPaint = Paint().apply {
            color = Color.WHITE
            textSize = 9f
            isAntiAlias = true
        }

        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val samplePoints = if (points.size <= 8) points else {
            val step = points.size / 8
            (0 until 8).mapNotNull { points.getOrNull(it * step) }
        }

        samplePoints.forEachIndexed { idx, pt ->
            rowY += 22f
            canvas.drawText("${idx + 1}", 50f, rowY, tableRowPaint)
            canvas.drawText(timeFormat.format(Date(pt.timestamp)), 90f, rowY, tableRowPaint)
            canvas.drawText(String.format(Locale.US, "%.5f", pt.latitude), 200f, rowY, tableRowPaint)
            canvas.drawText(String.format(Locale.US, "%.5f", pt.longitude), 310f, rowY, tableRowPaint)
            canvas.drawText(String.format(Locale.US, "%.1f km/h", pt.speedKmh), 420f, rowY, tableRowPaint)
            canvas.drawText(String.format(Locale.US, "%.0f", pt.altitude), 500f, rowY, tableRowPaint)
        }

        // Footer
        val footerPaint = Paint().apply {
            color = Color.rgb(100, 130, 150)
            textSize = 9f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("Generated by Alangmotor Speed Recorder • Precision Telemetry for Enthusiasts", 595f / 2, 815f, footerPaint)

        document.finishPage(page)

        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()

        return file
    }

    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Telemetry File"))
    }
}

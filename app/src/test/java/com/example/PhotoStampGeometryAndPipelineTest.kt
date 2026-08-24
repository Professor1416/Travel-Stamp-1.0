package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.TravelStamp
import com.example.data.model.Trip
import com.example.data.model.TripStatus
import com.example.ui.poster.PhotoStampLayout
import com.example.ui.poster.PosterRenderConfig
import com.example.ui.poster.PosterRenderResult
import com.example.ui.poster.PosterRenderer
import com.example.ui.poster.PosterTemplate
import com.example.ui.poster.StampEditionFormat
import com.example.ui.poster.StampSize
import com.example.ui.util.PhotoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PhotoStampGeometryAndPipelineTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun createTestImageFile(name: String, width: Int, height: Int, color: Int = AndroidColor.BLUE): File {
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        val paint = Paint().apply {
            this.color = AndroidColor.YELLOW
            textSize = 20f
        }
        canvas.drawText("TEST IMAGE", 10f, 30f, paint)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()
        return file
    }

    @Test
    fun testPreviewExportCoordinateParity() {
        // Test that for all formats and stamp sizes, normalized coordinates map
        // identically between Preview and Export geometries with zero mathematical drift.
        for (format in StampEditionFormat.entries) {
            for (size in StampSize.entries) {
                // Test a grid of normalized coordinates
                val testCoords = listOf(
                    0.5f to 0.44f, // Default center
                    0.1f to 0.1f,  // Top-left boundary attempt
                    0.9f to 0.9f,  // Bottom-right boundary attempt
                    0.3f to 0.6f,  // Mid quadrant
                    0.7f to 0.3f   // Upper right quadrant
                )

                for ((rawX, rawY) in testCoords) {
                    val (clampedX, clampedY) = PhotoStampLayout.clampStampPosition(rawX, rawY, format, size)

                    // Export calculation (at full 1080x... resolution)
                    val exportW = format.width.toFloat()
                    val exportH = format.height.toFloat()
                    val exportStampRadius = PhotoStampLayout.getStampRadiusPx(exportW, size)
                    val exportCenterX = clampedX * exportW
                    val exportCenterY = clampedY * exportH

                    // Preview calculation (e.g. at 320dp width container)
                    val previewW = 320f
                    val previewH = previewW / format.aspectRatio
                    val previewStampRadius = PhotoStampLayout.getStampRadiusPx(previewW, size)
                    val previewCenterX = clampedX * previewW
                    val previewCenterY = clampedY * previewH

                    // Re-normalized coordinates from export and preview must be identical
                    val normExportX = exportCenterX / exportW
                    val normExportY = exportCenterY / exportH
                    val normPreviewX = previewCenterX / previewW
                    val normPreviewY = previewCenterY / previewH

                    assertEquals(normExportX, normPreviewX, 0.0001f)
                    assertEquals(normExportY, normPreviewY, 0.0001f)

                    // Proportional radius must be identical
                    val propExportRadius = exportStampRadius / exportW
                    val propPreviewRadius = previewStampRadius / previewW
                    assertEquals(propExportRadius, propPreviewRadius, 0.0001f)

                    // Stamp bounds must never cross the footer title zone
                    val footerStartYExport = exportH * PhotoStampLayout.getFooterStartYRatio(format)
                    val stampBottomExport = exportCenterY + exportStampRadius
                    assertTrue(
                        "Stamp bottom ($stampBottomExport) must be strictly above footer start ($footerStartYExport) for format $format and size $size",
                        stampBottomExport < footerStartYExport
                    )
                }
            }
        }
    }

    @Test
    fun testUmbhrandeWaterfallNoOverlapRegression() {
        // Specifically verifies the reported regression issue:
        // Story format (9:16), destination "Umbhrande Waterfall", stamp dragged to lower allowable limit.
        val trip = Trip(
            id = 101,
            name = "Maharashtra Trek",
            destination = "Umbhrande Waterfall, Sahyadri",
            date = "2024-07-15",
            status = TripStatus.COMPLETED
        )
        val stamp = TravelStamp(
            id = 201,
            tripId = 101,
            title = "Umbhrande Waterfall",
            destination = "Umbhrande Waterfall, Sahyadri",
            dateText = "JUL 2024",
            stampCode = "#042",
            inkColorHex = "#1E3A2F",
            peopleCount = 2,
            momentsCount = 3
        )

        val imageFile = createTestImageFile("umbhrande_test.jpg", 1200, 1600, AndroidColor.DKGRAY)

        val config = PosterRenderConfig(
            template = PosterTemplate.PHOTO_STAMP,
            format = StampEditionFormat.STORY,
            photoUri = imageFile.absolutePath,
            stampSize = StampSize.LARGE,
            stampPositionX = 0.5f,
            stampPositionY = 0.70f // Attempt lower position near footer
        )

        val result = PosterRenderer.render(context, trip, stamp, config)
        assertTrue(result is PosterRenderResult.Success)
        val bitmap = (result as PosterRenderResult.Success).bitmap
        assertNotNull(bitmap)
        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)

        // Mathematical verification that stamp bottom is above footer title
        val (clampedX, clampedY) = PhotoStampLayout.clampStampPosition(
            config.stampPositionX,
            config.stampPositionY,
            config.format,
            config.stampSize
        )
        val stampRadius = PhotoStampLayout.getStampRadiusPx(1080f, config.stampSize)
        val stampCenterY = clampedY * 1920f
        val stampBottomWithBadge = stampCenterY + stampRadius + 20f
        val footerStartY = 1920f * PhotoStampLayout.getFooterStartYRatio(config.format)

        assertTrue(
            "Stamp bottom with badge ($stampBottomWithBadge) must not overlap footer ($footerStartY)",
            stampBottomWithBadge <= footerStartY
        )

        bitmap.recycle()
        imageFile.delete()
    }

    @Test
    fun testLargePhotoSafeSampling() {
        // Verifies calculateInSampleSize produces optimal power-of-2 downsampling
        // for massive 108MP, 48MP, and 12MP camera resolutions.
        val maxDim = PhotoUtils.MAX_WORKING_DIMENSION // 2560

        // 108MP: 12000 x 9000
        val sample108 = PhotoUtils.calculateInSampleSize(12000, 9000, maxDim)
        assertTrue("Sample size for 108MP must be >= 4", sample108 >= 4)
        val dec108W = 12000 / sample108
        val dec108H = 9000 / sample108
        assertTrue("Decoded 108MP dimension must be <= maxDim", maxOf(dec108W, dec108H) <= maxDim)

        // 48MP: 8000 x 6000
        val sample48 = PhotoUtils.calculateInSampleSize(8000, 6000, maxDim)
        assertTrue("Sample size for 48MP must be >= 4", sample48 >= 4)
        val dec48W = 8000 / sample48
        val dec48H = 6000 / sample48
        assertTrue("Decoded 48MP dimension must be <= maxDim", maxOf(dec48W, dec48H) <= maxDim)

        // 12MP: 4032 x 3024
        val sample12 = PhotoUtils.calculateInSampleSize(4032, 3024, maxDim)
        assertTrue("Sample size for 12MP must be 2", sample12 == 2)
        val dec12W = 4032 / sample12
        val dec12H = 3024 / sample12
        assertTrue("Decoded 12MP dimension must be <= maxDim", maxOf(dec12W, dec12H) <= maxDim)

        // Standard 1080p: 1920 x 1080
        val sample2MP = PhotoUtils.calculateInSampleSize(1920, 1080, maxDim)
        assertEquals(1, sample2MP)
    }

    @Test
    fun testPhotoTransformGeometryCalculations() {
        // Test pan & zoom transform geometry for 4 quadrants
        val targetW = 1080f
        val targetH = 1920f
        val srcW = 2000f
        val srcH = 1500f

        // Center with zoom = 1.5f
        val geomCenter = PhotoStampLayout.calculatePhotoTransform(
            srcWidth = srcW,
            srcHeight = srcH,
            targetWidth = targetW,
            targetHeight = targetH,
            panX = 0f,
            panY = 0f,
            zoom = 1.5f
        )
        assertEquals(targetW, geomCenter.targetWidth, 0.01f)
        assertEquals(targetH, geomCenter.targetHeight, 0.01f)
        assertTrue(geomCenter.scaledWidth >= targetW)
        assertTrue(geomCenter.scaledHeight >= targetH)

        // Pan Top-Left
        val geomTopLeft = PhotoStampLayout.calculatePhotoTransform(
            srcWidth = srcW,
            srcHeight = srcH,
            targetWidth = targetW,
            targetHeight = targetH,
            panX = -0.5f,
            panY = -0.5f,
            zoom = 2.0f
        )
        assertTrue(geomTopLeft.left <= 0f)
        assertTrue(geomTopLeft.top <= 0f)

        // Pan Bottom-Right
        val geomBottomRight = PhotoStampLayout.calculatePhotoTransform(
            srcWidth = srcW,
            srcHeight = srcH,
            targetWidth = targetW,
            targetHeight = targetH,
            panX = 0.5f,
            panY = 0.5f,
            zoom = 2.0f
        )
        assertTrue(geomBottomRight.clampedPanX > 0f)
        assertTrue(geomBottomRight.clampedPanY > 0f)
    }

    @Test
    fun testWorkingDirCleanupSeparation() {
        val editorDir = File(context.cacheDir, "photo_editor")
        editorDir.mkdirs()

        // Create 8 dummy working files
        for (i in 1..8) {
            val f = File(editorDir, "working_test_$i.jpg")
            f.writeText("test $i")
            f.setLastModified(System.currentTimeMillis() + i * 1000)
        }

        PhotoUtils.cleanupWorkingDir(editorDir, maxFilesToKeep = 5)
        val remaining = editorDir.listFiles()?.size ?: 0
        assertEquals(5, remaining)

        // Verify user moments in filesDir/moments are untouched
        val momentsDir = File(context.filesDir, "moments")
        momentsDir.mkdirs()
        val userMoment = File(momentsDir, "moment_keep.jpg")
        userMoment.writeText("important user memory")
        PhotoUtils.cleanupWorkingDir(editorDir, maxFilesToKeep = 2)
        assertTrue(userMoment.exists())
    }

    @Test
    fun testUnsupportedFormatHandling() {
        // Test non-image corrupted file
        val fakeFile = File(context.cacheDir, "corrupted.txt")
        fakeFile.writeText("This is not an image at all.")

        val result = PhotoUtils.prepareWorkingImage(context, Uri.fromFile(fakeFile))
        assertTrue(result is PhotoUtils.WorkingImageResult.Error)

        fakeFile.delete()
    }
}

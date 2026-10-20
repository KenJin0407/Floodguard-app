package com.example.floodguard

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.util.Base64
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Image processing and validation utility for flood reports and avatars.
 * Handles bitmap downscaling, heuristic image quality checks, circular avatar cropping,
 * and base64 persistence.
 */
object ReportPhotoHelper {

    data class PhotoValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )

    fun validateFloodPhoto(bitmap: Bitmap?): PhotoValidationResult {
        if (bitmap == null) {
            return PhotoValidationResult(
                isValid = false,
                errorMessage = "Hindi mabasa ang litrato o corrupted ang file. Mangyaring mag-attach ng panibagong litrato ng baha."
            )
        }

        if (bitmap.width < 50 || bitmap.height < 50) {
            return PhotoValidationResult(
                isValid = false,
                errorMessage = "Masyadong maliit ang resolution ng larawan. Mangyaring mag-attach ng malinaw na litrato ng binabahang kalsada."
            )
        }

        val sampleGrid = 16
        val stepX = (bitmap.width / sampleGrid).coerceAtLeast(1)
        val stepY = (bitmap.height / sampleGrid).coerceAtLeast(1)

        val pixels = ArrayList<Int>(sampleGrid * sampleGrid)
        var totalR = 0L
        var totalG = 0L
        var totalB = 0L

        for (x in 0 until sampleGrid) {
            for (y in 0 until sampleGrid) {
                val px = (x * stepX).coerceIn(0, bitmap.width - 1)
                val py = (y * stepY).coerceIn(0, bitmap.height - 1)
                val pixel = bitmap.getPixel(px, py)
                pixels.add(pixel)
                totalR += Color.red(pixel)
                totalG += Color.green(pixel)
                totalB += Color.blue(pixel)
            }
        }

        val count = pixels.size
        val avgR = totalR.toDouble() / count
        val avgG = totalG.toDouble() / count
        val avgB = totalB.toDouble() / count

        var varianceSum = 0.0
        for (p in pixels) {
            val dr = Color.red(p) - avgR
            val dg = Color.green(p) - avgG
            val db = Color.blue(p) - avgB
            varianceSum += (dr * dr + dg * dg + db * db) / 3.0
        }
        val stdDev = Math.sqrt(varianceSum / count)

        if (stdDev < 6.0) {
            return PhotoValidationResult(
                isValid = false,
                errorMessage = "Ang napiling larawan ay tila blangko o iisang kulay lamang. Kinakailangan mag-attach ng totoong litrato ng binabahang kalsada o lugar."
            )
        }

        return PhotoValidationResult(isValid = true, errorMessage = null)
    }

    fun persistPickedPhoto(context: Context, sourceUri: Uri): String? {
        return try {
            val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                ?: return null
            val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val scaled = scaleDownBitmap(original, 720)

            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, output)
            val jpegBytes = output.toByteArray()

            // Also save a local file copy in app-private storage
            try {
                val dir = File(context.filesDir, "flood_photos")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "flood_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { it.write(jpegBytes) }
            } catch (_: Exception) {
            }

            "data:image/jpeg;base64," + Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    fun encodeBitmapToDataUri(context: Context, bitmap: Bitmap): String {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
        val jpegBytes = output.toByteArray()
        try {
            val dir = File(context.filesDir, "avatar_photos")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { it.write(jpegBytes) }
        } catch (_: Exception) {
        }
        return "data:image/jpeg;base64," + Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
    }

    private fun scaleDownBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val longest = max(width, height)
        if (longest <= maxDimension) return bitmap

        val ratio = maxDimension.toFloat() / longest.toFloat()
        val newWidth = (width * ratio).roundToInt().coerceAtLeast(1)
        val newHeight = (height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    fun loadBitmap(context: Context, photoUriStr: String?): Bitmap? {
        if (photoUriStr.isNullOrBlank()) return null

        return try {
            when {
                photoUriStr.startsWith("data:image") || photoUriStr.contains("base64,") -> {
                    val base64Part = photoUriStr.substringAfter("base64,", photoUriStr)
                    val decoded = Base64.decode(base64Part, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                }
                photoUriStr.startsWith("/") -> {
                    BitmapFactory.decodeFile(photoUriStr)
                }
                else -> {
                    val uri = Uri.parse(photoUriStr)
                    if (uri.scheme == "file" && uri.path != null) {
                        BitmapFactory.decodeFile(uri.path)
                    } else {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun createCircularAvatarBitmap(source: Bitmap, sizePx: Int = 256): Bitmap {
        val minEdge = min(source.width, source.height).coerceAtLeast(1)
        val xOffset = (source.width - minEdge) / 2
        val yOffset = (source.height - minEdge) / 2
        val square = Bitmap.createBitmap(source, xOffset, yOffset, minEdge, minEdge)
        val scaledSquare = if (minEdge != sizePx) {
            Bitmap.createScaledBitmap(square, sizePx, sizePx, true)
        } else {
            square
        }

        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val radius = sizePx / 2f
        val strokeWidth = sizePx * 0.03f

        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(scaledSquare, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(radius, radius, radius - strokeWidth / 2f, imagePaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#475569")
            this.strokeWidth = strokeWidth
        }
        canvas.drawCircle(radius, radius, radius - strokeWidth / 2f, borderPaint)

        return output
    }

    fun showAdjustAvatarDialog(
        context: Context,
        sourceBitmap: Bitmap,
        onConfirmed: (Bitmap, String) -> Unit
    ) {
        val density = context.resources.displayMetrics.density
        val pad = (18 * density).toInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.dropdown_menu_bg)
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.dialog_header_bg)
            setPadding(pad, pad, pad, pad)
        }

        val titleView = TextView(context).apply {
            text = "Adjust Profile Photo"
            setTextColor(Color.WHITE)
            textSize = 18f
            paint.isFakeBoldText = true
        }

        val subtitleView = TextView(context).apply {
            text = "Drag photo to reposition and use slider or pinch to zoom"
            setTextColor(Color.parseColor("#FFA500"))
            textSize = 12f
            setPadding(0, (3 * density).toInt(), 0, 0)
        }

        header.addView(titleView)
        header.addView(subtitleView)
        root.addView(header)

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad, pad, pad)
        }

        val cropViewSizePx = (260 * density).toInt()
        lateinit var zoomSeekBar: SeekBar
        lateinit var zoomValueText: TextView
        var updatingSliderFromPinch = false

        val cropView = AvatarCropView(context, sourceBitmap) { currentZoom ->
            updatingSliderFromPinch = true
            val progress = ((currentZoom - 1.0f) / 3.0f * 100f).roundToInt().coerceIn(0, 100)
            zoomSeekBar.progress = progress
            zoomValueText.text = String.format("%.1fx", currentZoom)
            updatingSliderFromPinch = false
        }.apply {
            layoutParams = LinearLayout.LayoutParams(cropViewSizePx, cropViewSizePx).apply {
                bottomMargin = (14 * density).toInt()
            }
        }
        body.addView(cropView)

        val zoomRowHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (4 * density).toInt()
            }
        }

        val zoomLabel = TextView(context).apply {
            text = "ZOOM"
            setTextColor(Color.parseColor("#0A192F"))
            textSize = 11f
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        zoomValueText = TextView(context).apply {
            text = "1.0x"
            setTextColor(Color.parseColor("#6B7280"))
            textSize = 12f
            paint.isFakeBoldText = true
        }

        val resetBtn = TextView(context).apply {
            text = "Reset"
            setTextColor(Color.parseColor("#FFA500"))
            textSize = 12f
            paint.isFakeBoldText = true
            setPadding((12 * density).toInt(), 0, 0, 0)
            setOnClickListener {
                cropView.resetTransform()
                zoomSeekBar.progress = 0
                zoomValueText.text = "1.0x"
            }
        }

        zoomRowHeader.addView(zoomLabel)
        zoomRowHeader.addView(zoomValueText)
        zoomRowHeader.addView(resetBtn)
        body.addView(zoomRowHeader)

        zoomSeekBar = SeekBar(context).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (18 * density).toInt()
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!updatingSliderFromPinch) {
                        val zoom = 1.0f + (progress / 100f) * 3.0f
                        cropView.setZoomFactor(zoom)
                        zoomValueText.text = String.format("%.1fx", zoom)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        body.addView(zoomSeekBar)

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val cancelBtn = Button(context).apply {
            text = "Cancel"
            isAllCaps = false
            setTextColor(Color.parseColor("#374151"))
            textSize = 14f
            paint.isFakeBoldText = true
            setBackgroundResource(R.drawable.bg_dialog_cancel_btn)
            layoutParams = LinearLayout.LayoutParams(0, (46 * density).toInt(), 1f).apply {
                marginEnd = (10 * density).toInt()
            }
        }

        val saveBtn = Button(context).apply {
            text = "Save Photo"
            isAllCaps = false
            setTextColor(Color.parseColor("#0A192F"))
            textSize = 14f
            paint.isFakeBoldText = true
            setBackgroundResource(R.drawable.btn_yellow)
            layoutParams = LinearLayout.LayoutParams(0, (46 * density).toInt(), 1f)
        }

        buttonRow.addView(cancelBtn)
        buttonRow.addView(saveBtn)
        body.addView(buttonRow)
        root.addView(body)

        val dialog = AlertDialog.Builder(context)
            .setView(root)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        cancelBtn.setOnClickListener {
            dialog.dismiss()
        }

        saveBtn.setOnClickListener {
            val croppedSquare = cropView.exportAdjustedSquareBitmap(320)
            val dataUri = encodeBitmapToDataUri(context, croppedSquare)
            dialog.dismiss()
            onConfirmed(croppedSquare, dataUri)
        }

        dialog.show()
    }

    private class AvatarCropView(
        context: Context,
        private val bitmap: Bitmap,
        private val onZoomChanged: (Float) -> Unit
    ) : View(context) {

        private val drawMatrix = Matrix()
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val bgPaint = Paint().apply {
            color = Color.parseColor("#0A192F")
            style = Paint.Style.FILL
        }
        private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#990A192F")
            style = Paint.Style.FILL
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFA500")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * context.resources.displayMetrics.density
        }
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#44FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 1f * context.resources.displayMetrics.density
        }
        private val overlayPath = Path()
        private val circlePath = Path()

        private var baseScale = 1f
        private var userZoom = 1f
        private var offsetX = 0f
        private var offsetY = 0f

        private var lastTouchX = 0f
        private var lastTouchY = 0f
        private var activePointerId = MotionEvent.INVALID_POINTER_ID

        private val scaleDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val newZoom = (userZoom * detector.scaleFactor).coerceIn(1.0f, 4.0f)
                    userZoom = newZoom
                    clampOffsets()
                    onZoomChanged(userZoom)
                    invalidate()
                    return true
                }
            }
        )

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            computeBaseScale(w, h)
        }

        private fun computeBaseScale(w: Int, h: Int) {
            if (w <= 0 || h <= 0 || bitmap.width <= 0 || bitmap.height <= 0) return
            val cropDiameter = getCropRadius(w, h) * 2f
            val scaleX = cropDiameter / bitmap.width.toFloat()
            val scaleY = cropDiameter / bitmap.height.toFloat()
            // Ensure the image covers the entire circular crop area at 1.0x zoom
            baseScale = max(scaleX, scaleY)
            clampOffsets()
        }

        private fun getCropRadius(w: Int = width, h: Int = height): Float {
            return min(w, h) * 0.44f
        }

        fun setZoomFactor(zoom: Float) {
            userZoom = zoom.coerceIn(1.0f, 4.0f)
            clampOffsets()
            invalidate()
        }

        fun resetTransform() {
            userZoom = 1.0f
            offsetX = 0f
            offsetY = 0f
            invalidate()
        }

        private fun clampOffsets() {
            if (width <= 0 || height <= 0) return
            val cropRadius = getCropRadius()
            val totalScale = baseScale * userZoom
            val scaledHalfW = (bitmap.width * totalScale) / 2f
            val scaledHalfH = (bitmap.height * totalScale) / 2f

            val maxDx = max(0f, scaledHalfW - cropRadius)
            val maxDy = max(0f, scaledHalfH - cropRadius)

            offsetX = offsetX.coerceIn(-maxDx, maxDx)
            offsetY = offsetY.coerceIn(-maxDy, maxDy)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            parent?.requestDisallowInterceptTouchEvent(true)
            scaleDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    activePointerId = event.getPointerId(0)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!scaleDetector.isInProgress) {
                        val pointerIndex = event.findPointerIndex(activePointerId)
                        if (pointerIndex != -1) {
                            val x = event.getX(pointerIndex)
                            val y = event.getY(pointerIndex)
                            offsetX += (x - lastTouchX)
                            offsetY += (y - lastTouchY)
                            clampOffsets()
                            invalidate()
                            lastTouchX = x
                            lastTouchY = y
                        }
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    val pointerIndex = event.actionIndex
                    val pointerId = event.getPointerId(pointerIndex)
                    if (pointerId == activePointerId) {
                        val newPointerIndex = if (pointerIndex == 0) 1 else 0
                        lastTouchX = event.getX(newPointerIndex)
                        lastTouchY = event.getY(newPointerIndex)
                        activePointerId = event.getPointerId(newPointerIndex)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val cx = w / 2f
            val cy = h / 2f
            val radius = getCropRadius()

            // Background fill
            canvas.drawRect(0f, 0f, w, h, bgPaint)

            // Draw transformed bitmap centered + user pan/zoom
            val totalScale = baseScale * userZoom
            drawMatrix.reset()
            drawMatrix.postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
            drawMatrix.postScale(totalScale, totalScale)
            drawMatrix.postTranslate(cx + offsetX, cy + offsetY)
            canvas.drawBitmap(bitmap, drawMatrix, bitmapPaint)

            // Draw darkened overlay outside the circular crop region
            overlayPath.reset()
            overlayPath.addRect(0f, 0f, w, h, Path.Direction.CW)
            circlePath.reset()
            circlePath.addCircle(cx, cy, radius, Path.Direction.CW)
            overlayPath.op(circlePath, Path.Op.DIFFERENCE)
            canvas.drawPath(overlayPath, overlayPaint)

            // Subtle alignment crosshairs inside the circle
            canvas.save()
            canvas.clipPath(circlePath)
            canvas.drawLine(cx - radius, cy, cx + radius, cy, gridPaint)
            canvas.drawLine(cx, cy - radius, cx, cy + radius, gridPaint)
            canvas.restore()

            // Circular border
            canvas.drawCircle(cx, cy, radius, borderPaint)
        }

        fun exportAdjustedSquareBitmap(outputSize: Int = 320): Bitmap {
            val out = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawColor(Color.parseColor("#0A192F"))

            val radius = getCropRadius()
            val cropDiameter = (radius * 2f).coerceAtLeast(1f)
            val exportRatio = outputSize.toFloat() / cropDiameter
            val totalScale = baseScale * userZoom * exportRatio

            val exportMatrix = Matrix()
            exportMatrix.postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
            exportMatrix.postScale(totalScale, totalScale)
            exportMatrix.postTranslate(
                outputSize / 2f + offsetX * exportRatio,
                outputSize / 2f + offsetY * exportRatio
            )

            canvas.drawBitmap(bitmap, exportMatrix, bitmapPaint)
            return out
        }
    }

    fun showFloodPhotoDialog(context: Context, photoUriStr: String, location: String) {
        val density = context.resources.displayMetrics.density
        val pad = (18 * density).toInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.dropdown_menu_bg)
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.dialog_header_bg)
            setPadding(pad, pad, pad, pad)
        }

        val titleView = TextView(context).apply {
            text = "Flood Report Photo"
            setTextColor(Color.WHITE)
            textSize = 17f
            paint.isFakeBoldText = true
        }

        val subtitleView = TextView(context).apply {
            text = location
            setTextColor(Color.parseColor("#FFA500"))
            textSize = 12f
            setPadding(0, (2 * density).toInt(), 0, 0)
        }

        header.addView(titleView)
        header.addView(subtitleView)
        root.addView(header)

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }

        val bitmap = loadBitmap(context, photoUriStr)
        if (bitmap != null) {
            val imageView = ImageView(context).apply {
                setImageBitmap(bitmap)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                maxHeight = (360 * density).toInt()
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (16 * density).toInt()
                }
                layoutParams = params
            }
            body.addView(imageView)
        } else {
            val fallbackMsg = TextView(context).apply {
                text = "Unable to load this photo (the temporary device permission for this older photo has expired). Please re-upload the photo in a new report."
                setTextColor(Color.parseColor("#4B5563"))
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, (16 * density).toInt(), 0, (20 * density).toInt())
            }
            body.addView(fallbackMsg)
        }

        val closeButton = Button(context).apply {
            text = "Close"
            isAllCaps = false
            setTextColor(Color.parseColor("#0A192F"))
            textSize = 14f
            paint.isFakeBoldText = true
            setBackgroundResource(R.drawable.btn_yellow)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (44 * density).toInt()
            )
        }

        body.addView(closeButton)
        root.addView(body)

        val dialog = AlertDialog.Builder(context)
            .setView(root)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}


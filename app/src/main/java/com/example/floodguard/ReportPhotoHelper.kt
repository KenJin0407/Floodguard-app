package com.example.floodguard

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.util.Base64
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object ReportPhotoHelper {

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

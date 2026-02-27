package gov.lbl.crucible.scanner.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.caverock.androidsvg.SVG
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import gov.lbl.crucible.scanner.data.model.CrucibleResource
import gov.lbl.crucible.scanner.data.model.Dataset
import gov.lbl.crucible.scanner.data.model.Sample
import java.io.File

object ShareCardGenerator {

    private const val W = 600
    private const val H = 320

    fun generate(
        context: Context,
        resource: CrucibleResource,
        url: String,
        bannerColorInt: Int,
        darkTheme: Boolean = false
    ): Uri? = runCatching {
        val bitmap = drawCard(context, resource, bannerColorInt, darkTheme)
        val file = File(context.cacheDir, "share_card.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 95, it) }
        bitmap.recycle()
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()

    /** Returns a slightly darkened version of the given color for contrast strips. */
    private fun darkenColor(color: Int, factor: Float = 0.75f): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun drawCard(
        context: Context,
        resource: CrucibleResource,
        bannerColorInt: Int,
        darkTheme: Boolean
    ): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        val bannerDark = darkenColor(bannerColorInt, 0.70f)
        val colorBody  = if (darkTheme) Color.parseColor("#1E1E1E") else Color.parseColor("#F8F9FA")
        val colorText  = if (darkTheme) Color.parseColor("#EEEEEE") else Color.parseColor("#212121")
        val colorMuted = if (darkTheme) Color.parseColor("#90A4AE") else Color.parseColor("#78909C")
        val colorWhite = Color.WHITE

        // Body: 24 (top pad) + 72 (icon) + 24 (bottom pad) = 120 px
        val iconSz  = 72
        val bodyH   = 24 + iconSz + 24      // 120 px
        val headerH = H - bodyH             // 200 px banner
        val pad     = 20

        // ── Banner background ──────────────────────────────────────────────
        p.style = Paint.Style.FILL
        p.color = bannerColorInt
        canvas.drawRect(0f, 0f, W.toFloat(), headerH.toFloat(), p)

        p.color = bannerDark
        canvas.drawRect(0f, (headerH - 6).toFloat(), W.toFloat(), headerH.toFloat(), p)

        // ── Body background ────────────────────────────────────────────────
        p.color = colorBody
        canvas.drawRect(0f, headerH.toFloat(), W.toFloat(), H.toFloat(), p)

        // ── QR code — right side of banner, vertically centred ────────────
        val qrSz   = 160
        val qrLeft = W - qrSz - pad                // 540
        val qrTop  = (headerH - qrSz) / 2          // 20 px

        val qrBmp = generateQrBitmap(resource.uniqueId, qrSz)
        p.color = colorWhite
        canvas.drawRoundRect(
            RectF((qrLeft - 10).toFloat(), (qrTop - 10).toFloat(),
                  (qrLeft + qrSz + 10).toFloat(), (qrTop + qrSz + 10).toFloat()),
            12f, 12f, p
        )
        canvas.drawBitmap(qrBmp, qrLeft.toFloat(), qrTop.toFloat(), null)
        qrBmp.recycle()

        // ── Resource name — top-left of banner (no icon) ──────────────────
        val textX        = pad.toFloat()
        val textMaxW     = (qrLeft - textX - 16).toFloat()
        val nameFontSize = 34f
        p.color    = colorWhite
        p.textSize = nameFontSize
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val nameY = pad + nameFontSize   // baseline ≈ 54 px from top
        canvas.drawText(truncate(resource.name, p, textMaxW), textX, nameY, p)

        // ── Resource type — semi-transparent badge below name ──────────────
        val typeText = when (resource) {
            is Sample  -> (resource.sampleType ?: "Sample").uppercase()
            is Dataset -> (resource.measurement ?: "Dataset").uppercase()
            else       -> ""
        }
        if (typeText.isNotBlank()) {
            val typeFontSize = 17f
            p.textSize = typeFontSize
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val badgePadH  = 12f
            val badgePadV  = 7f
            val badgeTop   = nameY + nameFontSize * 0.28f + 8f
            val badgeLeft  = textX
            val badgeRight = minOf(badgeLeft + p.measureText(typeText) + badgePadH * 2f, qrLeft - 8f)
            val badgeBot   = badgeTop + typeFontSize + badgePadV * 2f
            val badgeRect  = RectF(badgeLeft, badgeTop, badgeRight, badgeBot)

            p.color = Color.argb(60, 255, 255, 255)
            canvas.drawRoundRect(badgeRect, 12f, 12f, p)

            val fm   = p.fontMetrics
            val txtY = badgeRect.centerY() - (fm.ascent + fm.descent) / 2f
            p.color  = colorWhite
            canvas.drawText(
                truncate(typeText, p, badgeRect.width() - badgePadH * 2f),
                badgeLeft + badgePadH, txtY, p
            )
        }

        // ── Body: icon bottom-right, 24 px top pad ────────────────────────
        val iconAsset = if (darkTheme) "crucible_icon_72px_dark.svg" else "crucible_icon_72px_light.svg"
        val iconBodyX = (W - iconSz - pad).toFloat()   // 628
        val iconBodyY = (headerH + 24).toFloat()        // icon top Y

        runCatching {
            val svg = SVG.getFromAsset(context.assets, iconAsset)
            canvas.save()
            canvas.translate(iconBodyX, iconBodyY)
            svg.renderToCanvas(canvas)
            canvas.restore()
        }

        // "Shared via Crucible Lens" — left of icon, baseline at icon bottom
        p.color    = colorMuted
        p.textSize = 16f
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        val brand     = "Shared via Crucible Lens"
        val brandMaxW = iconBodyX - pad - 12f
        val brandY    = iconBodyY + iconSz   // baseline at bottom of icon
        canvas.drawText(truncate(brand, p, brandMaxW), pad.toFloat(), brandY, p)

        // MFID — left side, top aligned with icon top
        p.color    = colorText
        p.textSize = 18f
        p.typeface = Typeface.MONOSPACE
        val mfidMaxW = iconBodyX - pad - 12f
        val mfidFm   = p.fontMetrics
        val mfidY    = iconBodyY - mfidFm.ascent   // text top aligns with icon top
        canvas.drawText(truncate(resource.uniqueId, p, mfidMaxW), pad.toFloat(), mfidY, p)

        return bmp
    }

    private fun generateQrBitmap(content: String, size: Int): Bitmap {
        val matrix = MultiFormatWriter().encode(
            content, BarcodeFormat.QR_CODE, size, size,
            mapOf(EncodeHintType.MARGIN to 1)
        )
        return Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
            for (x in 0 until size) for (y in 0 until size)
                setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }

    private fun truncate(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        val ew = paint.measureText(ellipsis)
        var s = text
        while (s.isNotEmpty() && paint.measureText(s) + ew > maxWidth) s = s.dropLast(1)
        return "$s$ellipsis"
    }
}

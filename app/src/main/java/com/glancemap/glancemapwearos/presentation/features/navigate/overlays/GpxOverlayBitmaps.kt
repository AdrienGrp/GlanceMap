package com.glancemap.glancemapwearos.presentation.features.navigate

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

internal fun makeLabeledDotBitmap(
    label: String,
    sizePx: Int,
    strokePx: Int,
    fillColorArgb: Int
): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val r = sizePx / 2f - 1f

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColorArgb
        style = Paint.Style.FILL
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = strokePx.toFloat()
    }

    canvas.drawCircle(cx, cy, r, fill)
    canvas.drawCircle(cx, cy, r - strokePx / 2f, stroke)

    val textSize = sizePx * 0.60f

    val textStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = (sizePx * 0.10f).coerceAtLeast(2f)
        this.textSize = textSize
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val textFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
        this.textSize = textSize
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val fm = textFill.fontMetrics
    val textY = cy - (fm.ascent + fm.descent) / 2f

    canvas.drawText(label, cx, textY, textStroke)
    canvas.drawText(label, cx, textY, textFill)

    return bmp
}

internal fun makeLabeledYellowDotBitmap(
    label: String,
    sizePx: Int,
    strokePx: Int,
    fillAlpha: Int
): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val r = (sizePx / 2f) - 1f

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(fillAlpha, 255, 235, 59)
        style = Paint.Style.FILL
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = strokePx.toFloat()
    }

    canvas.drawCircle(cx, cy, r, fill)
    canvas.drawCircle(cx, cy, r - strokePx / 2f, stroke)

    val textSize = sizePx * 0.52f

    val textStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = (sizePx * 0.08f).coerceAtLeast(2f)
        this.textSize = textSize
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val textFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
        this.textSize = textSize
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val fm = textFill.fontMetrics
    val textY = cy - (fm.ascent + fm.descent) / 2f

    canvas.drawText(label, cx, textY, textStroke)
    canvas.drawText(label, cx, textY, textFill)

    return bmp
}

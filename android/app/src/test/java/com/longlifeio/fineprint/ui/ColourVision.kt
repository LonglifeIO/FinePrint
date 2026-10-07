package com.longlifeio.fineprint.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Colour science for PaletteContrastTest: WCAG contrast, colour-blind simulation (Machado, Oliveira and
 * Fernandes 2009, severity 1.0, applied in linear sRGB) and the CIEDE2000 colour difference.
 */

enum class Vision(val matrix: List<Double>?) {
    NORMAL(null),
    PROTANOPIA(listOf(0.152286, 1.052583, -0.204868, 0.114503, 0.786281, 0.099216, -0.003882, -0.048116, 1.051998)),
    DEUTERANOPIA(listOf(0.367322, 0.860646, -0.227968, 0.280085, 0.672501, 0.047413, -0.011820, 0.042940, 0.968881)),
}

private fun linear(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

private fun linearRgb(c: Color): List<Double> = listOf(linear(c.red), linear(c.green), linear(c.blue))

fun luminance(c: Color): Double = linearRgb(c).let { (r, g, b) -> 0.2126 * r + 0.7152 * g + 0.0722 * b }

fun contrast(a: Color, b: Color): Double {
    val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
    return (hi + 0.05) / (lo + 0.05)
}

/** The colour as someone with [vision] sees it, in linear sRGB. */
private fun seen(c: Color, vision: Vision): List<Double> {
    val v = linearRgb(c)
    val m = vision.matrix ?: return v
    return List(3) { i -> (m[3 * i] * v[0] + m[3 * i + 1] * v[1] + m[3 * i + 2] * v[2]).coerceIn(0.0, 1.0) }
}

private fun lab(v: List<Double>): Triple<Double, Double, Double> {
    val (r, g, b) = v
    val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
    val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883
    fun f(t: Double) = if (t > 216.0 / 24389) cbrt(t) else (24389.0 / 27 * t + 16) / 116
    return Triple(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
}

private fun Double.rad() = Math.toRadians(this)

/** CIEDE2000 difference between two colours as someone with [vision] sees them. */
fun difference(a: Color, b: Color, vision: Vision): Double {
    val (l1, a1, b1) = lab(seen(a, vision))
    val (l2, a2, b2) = lab(seen(b, vision))
    val cBar = (hypot(a1, b1) + hypot(a2, b2)) / 2
    val g = 0.5 * (1 - sqrt(cBar.pow(7) / (cBar.pow(7) + 25.0.pow(7))))
    val a1p = (1 + g) * a1
    val a2p = (1 + g) * a2
    val c1 = hypot(a1p, b1)
    val c2 = hypot(a2p, b2)
    val h1 = (Math.toDegrees(atan2(b1, a1p)) + 360) % 360
    val h2 = (Math.toDegrees(atan2(b2, a2p)) + 360) % 360
    val dh = when {
        c1 * c2 == 0.0 -> 0.0
        h2 - h1 > 180 -> h2 - h1 - 360
        h2 - h1 < -180 -> h2 - h1 + 360
        else -> h2 - h1
    }
    val dH = 2 * sqrt(c1 * c2) * sin((dh / 2).rad())
    val lBar = (l1 + l2) / 2
    val cpBar = (c1 + c2) / 2
    val hBar = when {
        c1 * c2 == 0.0 -> h1 + h2
        kotlin.math.abs(h1 - h2) <= 180 -> (h1 + h2) / 2
        h1 + h2 < 360 -> (h1 + h2 + 360) / 2
        else -> (h1 + h2 - 360) / 2
    }
    val t = 1 - 0.17 * cos((hBar - 30).rad()) + 0.24 * cos((2 * hBar).rad()) + 0.32 * cos((3 * hBar + 6).rad()) - 0.20 * cos((4 * hBar - 63).rad())
    val dTheta = 30 * exp(-((hBar - 275) / 25).pow(2))
    val rc = 2 * sqrt(cpBar.pow(7) / (cpBar.pow(7) + 25.0.pow(7)))
    val sl = 1 + 0.015 * (lBar - 50).pow(2) / sqrt(20 + (lBar - 50).pow(2))
    val sc = 1 + 0.045 * cpBar
    val sh = 1 + 0.015 * cpBar * t
    val rt = -sin((2 * dTheta).rad()) * rc
    val (x, y, z) = Triple((l2 - l1) / sl, (c2 - c1) / sc, dH / sh)
    return sqrt(x * x + y * y + z * z + rt * y * z)
}

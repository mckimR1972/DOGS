package com.teamdogs.dogs.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Line icons used across DOGS (24dp grid, 1.75 stroke, round caps), drawn as path data so the app needs no
 * icon-font dependency. Tint them with Icon(tint = ...).
 */
object DogsIcons {
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"

    private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r},${y}h${w - 2 * r}a$r,$r 0 0,1 $r,${r}v${h - 2 * r}a$r,$r 0 0,1 ${-r},${r}" +
            "h${-(w - 2 * r)}a$r,$r 0 0,1 ${-r},${-r}v${-(h - 2 * r)}a$r,$r 0 0,1 $r,${-r}z"

    private fun icon(name: String, stroke: Float = 1.75f, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d), fill = null, stroke = SolidColor(Color.Black),
                    strokeLineWidth = stroke, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Home = icon("home", 1.75f, "M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z")
    val Todo = icon("todo", 1.75f, rrect(4f, 4f, 16f, 17f, 2.5f), "m8.5 12 2.5 2.5 4.5-5")
    val Photo = icon("photo", 1.75f, rrect(3f, 4f, 18f, 16f, 2.5f), circle(9f, 10f, 1.8f), "m21 16-5-5-9 9")
    val Group = icon("group", 1.75f, circle(9f, 8f, 3.5f), "M2.5 20a6.5 6.5 0 0 1 13 0M16 4.5a3.5 3.5 0 0 1 0 7M18 14.5a6.5 6.5 0 0 1 3.5 5.5")
    val Bell = icon("bell", 1.75f, "M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0")
    val ChevronDown = icon("chevronDown", 2f, "m6 9 6 6 6-6")
    val ChevronRight = icon("chevronRight", 2f, "m9 6 6 6-6 6")
    val ChevronLeft = icon("chevronLeft", 2f, "m15 6-6 6 6 6")
    val Back = icon("back", 2f, "M19 12H5", "m12 19-7-7 7-7")
    val Check = icon("check", 2.25f, "M20 6 9 17l-5-5")
    val Plus = icon("plus", 2f, "M12 5v14M5 12h14")
    val Close = icon("close", 2f, "M18 6 6 18M6 6l12 12")
    val Clock = icon("clock", 2f, circle(12f, 12f, 9f), "M12 7v5l3 2")
    val Paw = icon("paw", 1.5f, circle(7f, 9f, 1.6f), circle(11f, 6f, 1.6f), circle(15f, 6f, 1.6f), circle(18f, 10f, 1.6f),
        "M8.5 17.5c0-2.5 2-5 4-5s4 2.5 4 5c0 1.5-1.5 2-4 2s-4-.5-4-2Z")
    val Eye = icon("eye", 1.75f, "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z", circle(12f, 12f, 3f))
    val EyeOff = icon("eyeOff", 1.75f, "M10.6 5.1A10.4 10.4 0 0 1 12 5c6.5 0 10 7 10 7a17 17 0 0 1-2.2 3.2",
        "M6.6 6.6A17 17 0 0 0 2 12s3.5 7 10 7a9.7 9.7 0 0 0 5.4-1.6", "M9.9 9.9a3 3 0 0 0 4.2 4.2", "M3 3l18 18")
    val Info = icon("info", 2f, circle(12f, 12f, 9f), "M12 11v5M12 8h.01")
    val Bulb = icon("bulb", 1.75f, "M9 18h6M10 21h4M12 3a6 6 0 0 0-3.6 10.8c.6.5 1 1.2 1 2V16h5.2v-.2c0-.8.4-1.5 1-2A6 6 0 0 0 12 3Z")
    val Camera = icon("camera", 2f, "M14.5 4h-5L7.5 7H4a1 1 0 0 0-1 1v11a1 1 0 0 0 1 1h16a1 1 0 0 0 1-1V8a1 1 0 0 0-1-1h-3.5z", circle(12f, 13f, 3.5f))
    val Edit = icon("edit", 2f, "M4 20h4L19 9l-4-4L4 16z", "m13.5 6.5 4 4")
    val Trash = icon("trash", 2f, "M3 6h18", "M8 6V4h8v2", "M19 6l-1 14H6L5 6")
    val Logout = icon("logout", 2f, "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4", "m16 17 5-5-5-5", "M21 12H9")
    val Refresh = icon("refresh", 2f, "M21 12a9 9 0 1 1-2.6-6.4", "M21 3v6h-6")
    val Hand = icon("hand", 1.75f, "M18 11V6a2 2 0 0 0-4 0v5M14 10V4a2 2 0 0 0-4 0v6M10 10.5V6a2 2 0 0 0-4 0v8",
        "M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.9-6-2.4l-3.6-3.6a2 2 0 0 1 2.8-2.8L7 15")
    val Calendar = icon("calendar", 1.75f, rrect(3f, 5f, 18f, 16f, 2.5f), "M16 3v4M8 3v4M3 10h18")
}

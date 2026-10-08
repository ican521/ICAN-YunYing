package com.ican.tvplay.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 全部图标手写矢量路径，不依赖 material-icons 与任何 Leanback 资源。
 */
object AppIcons {

    private fun vector(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(pathData),
            fill = SolidColor(Color.Black),
        ).build()

    val Home: ImageVector = vector(
        "home",
        "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z",
    )

    val Search: ImageVector = vector(
        "search",
        "M15.5 14h-.79l-.28-.27a6.5 6.5 0 1 0-.7.7l.27.28v.79l5 4.99L20.49 19z" +
            "M9.5 14A4.5 4.5 0 1 1 14 9.5 4.5 4.5 0 0 1 9.5 14z",
    )

    val Favorite: ImageVector = vector(
        "favorite",
        "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3" +
            "c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5" +
            "c0 3.78-3.4 6.86-8.55 11.54z",
    )

    val History: ImageVector = vector(
        "history",
        "M13 3a9 9 0 0 0-9 9H1l3.99 4.01L9 12H6a7 7 0 1 1 7 7c-1.93 0-3.68-.79-4.94-2.06" +
            "l-1.42 1.42A8.95 8.95 0 0 0 13 21a9 9 0 0 0 0-18z" +
            "M12 8v5l4.28 2.54.72-1.21-3.5-2.08V8z",
    )

    val Settings: ImageVector = vector(
        "settings",
        "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61" +
            "l-1.92-3.32a.49.49 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54" +
            "a.484.484 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54" +
            "c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87" +
            "c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58" +
            "a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96" +
            "c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41" +
            "l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32" +
            "c.12-.22.07-.47-.12-.61z" +
            "M12 15.6A3.6 3.6 0 1 1 12 8.4a3.6 3.6 0 0 1 0 7.2z",
    )

    val Back: ImageVector = vector(
        "back",
        "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20z",
    )

    val Play: ImageVector = vector("play", "M8 5v14l11-7z")

    val Pause: ImageVector = vector(
        "pause",
        "M6 19h4V5H6z M14 5v14h4V5z",
    )

    val Check: ImageVector = vector(
        "check",
        "M9 16.17 4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z",
    )

    val Close: ImageVector = vector(
        "close",
        "M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 " +
            "17.59 19 19 17.59 13.41 12z",
    )

    val Delete: ImageVector = vector(
        "delete",
        "M6 19a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V7H6z" +
            "M19 4h-3.5l-1-1h-5l-1 1H5v2h14z",
    )

    val Star: ImageVector = vector(
        "star",
        "M12 17.27 18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z",
    )

    val Site: ImageVector = vector(
        "site",
        "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 " +
            "11.99 2zm6.93 6h-2.95c-.32-1.25-.78-2.45-1.38-3.56 1.84.63 3.37 1.91 4.33 " +
            "3.56zM12 4.04c.83 1.2 1.48 2.53 1.91 3.96h-3.82c.43-1.43 1.08-2.76 " +
            "1.91-3.96zM4.26 14C4.1 13.36 4 12.69 4 12s.1-1.36.26-2h3.38c-.08.66-.14 " +
            "1.32-.14 2 0 .68.06 1.34.14 2H4.26zm.82 2h2.95c.32 1.25.78 2.45 1.38 " +
            "3.56-1.84-.63-3.37-1.9-4.33-3.56zm2.95-8H5.08c.96-1.66 2.49-2.93 " +
            "4.33-3.56C8.81 5.55 8.35 6.75 8.03 8zM12 19.96c-.83-1.2-1.48-2.53-1.91-3.96" +
            "h3.82c-.43 1.43-1.08 2.76-1.91 3.96zM14.34 14H9.66c-.09-.66-.16-1.32-.16-2 " +
            "0-.68.07-1.35.16-2h4.68c.09.65.16 1.32.16 2 0 .68-.07 1.34-.16 2zm.25 " +
            "5.56c.6-1.11 1.06-2.31 1.38-3.56h2.95c-.96 1.65-2.49 2.93-4.33 " +
            "3.56zM16.36 14c.08-.66.14-1.32.14-2 0-.68-.06-1.34-.14-2h3.38c.16.64.26 " +
            "1.31.26 2s-.1 1.36-.26 2h-3.38z",
    )

    val FavoriteBorder: ImageVector = vector(
        "favorite_border",
        "M16.5 5c-1.74 0-3.41.81-4.5 2.09C10.91 5.81 9.24 5 7.5 5 4.42 5 2 7.42 2 10.5" +
            "c0 3.78 3.4 6.86 8.55 11.54L12 23.55l1.45-1.32C18.6 17.36 22 14.28 22 10.5 " +
            "22 7.42 19.58 5 16.5 5z" +
            "M12 19.55l-.1.1-.1-.1C7.14 15.24 4 12.39 4 10.5 4 8.5 5.5 7 7.5 7" +
            "c1.54 0 3.04.99 3.57 2.36h1.87C13.46 7.99 14.96 7 16.5 7c2 0 3.5 1.5 3.5 3.5 " +
            "0 1.89-3.14 4.74-7.9 9.05z",
    )
}

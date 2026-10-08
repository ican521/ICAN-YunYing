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

    // ---- 播放器控制 ----

    val Speed: ImageVector = vector(
        "speed",
        "M20.38 8.57l-1.23 1.85a8 8 0 0 1-.22 7.58H5.07A8 8 0 0 1 15.58 6.85l1.85-1.23" +
            "A10 10 0 0 0 3.35 19a2 2 0 0 0 1.72 1h13.85a2 2 0 0 0 1.74-1 10 10 0 0 0-.27-10.43" +
            "zm-9.79 6.84a2 2 0 0 0 2.83 0l5.66-8.49-8.49 5.66a2 2 0 0 0 0 2.83z",
    )

    val AspectRatio: ImageVector = vector(
        "aspect_ratio",
        "M19 12h-2v3h-3v2h5zM7 9h3V7H5v5h2zm14-6H3a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h18" +
            "a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2zm0 16.01H3V4.99h18z",
    )

    val Memory: ImageVector = vector(
        "memory",
        "M15 9H9v6h6zm-2 4h-2v-2h2zm8-2V9h-2V7a2 2 0 0 0-2-2h-2V3h-2v2h-2V3H9v2H7a2 2 0 0 " +
            "0-2 2v2H3v2h2v2H3v2h2v2a2 2 0 0 0 2 2h2v2h2v-2h2v2h2v-2h2a2 2 0 0 0 2-2v-2h2v-2h-2v-2z" +
            "m-4 6H7V7h10z",
    )

    val Buffer: ImageVector = vector(
        "buffer",
        "M4 6h16v2H4zm0 5h16v2H4zm0 5h16v2H4z",
    )

    val AudioTrack: ImageVector = vector(
        "audio_track",
        "M12 3v10.55A4 4 0 1 0 14 17V7h4V3z",
    )

    val Subtitle: ImageVector = vector(
        "subtitle",
        "M20 4H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2zM4 12h4v2H4z" +
            "m14 6H4v-2h14zm6-4h-4v-2h4zm0 4h-4v-2h4z",
    )

    // ---- FongMi 播放器控制 ----

    val Cast: ImageVector = vector(
        "cast",
        "M21 3H3c-1.1 0-2 .9-2 2v3h2V5h18v14h-7v2h7a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2zM1 15v3h3a3 3 0 0 0-3-3zm0 4v3h3a3 3 0 0 0-3-3zm0-8v2a7 7 0 0 1 7 7h2a9 9 0 0 0-9-9z",
    )

    val Info: ImageVector = vector(
        "info",
        "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm1 15h-2v-6h2zm0-8h-2V7h2z",
    )

    val Fullscreen: ImageVector = vector(
        "fullscreen",
        "M7 14H5v5h5v-2H7zm-2-4h2V7h3V5H5zm12 7h-3v2h5v-5h-2zM14 5v2h3v3h2V5z",
    )

    val Lock: ImageVector = vector(
        "lock",
        "M18 8h-1V6a5 5 0 0 0-10 0v2H6a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V10a2 2 0 0 0-2-2zm-6 9a2 2 0 1 1 0-4 2 2 0 0 1 0 4zm3-9H9V6a3 3 0 0 1 6 0z",
    )

    val LockOpen: ImageVector = vector(
        "lock_open",
        "M12 2a5 5 0 0 0-5 5h2a3 3 0 1 1 6 0v2H6a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V11a2 2 0 0 0-2-2h-1V7a5 5 0 0 0-5-5zm0 15a2 2 0 1 1 0-4 2 2 0 0 1 0 4z",
    )

    val Refresh: ImageVector = vector(
        "refresh",
        "M17.65 6.35A7.958 7.958 0 0 0 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08" +
            "A5.99 5.99 0 0 1 12 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4z",
    )

    val Loop: ImageVector = vector(
        "loop",
        "M12 4V1L8 5l4 4V6a6 6 0 0 1 6 6 6 6 0 0 1-6 6 6 6 0 0 1-6-6H4a8 8 0 0 0 8 8 8 8 0 0 0 8-8 8 8 0 0 0-8-8z",
    )

    val SkipPrev: ImageVector = vector(
        "skip_prev",
        "M6 6h2v12H6zm3.5 6 8.5 6.5V5.5z",
    )

    val SkipNext: ImageVector = vector(
        "skip_next",
        "M6 18l8.5-6L6 6v12zM16 6v12h2V6z",
    )

    val Rewind: ImageVector = vector(
        "rewind",
        "M11 18V6l-8.5 6zM12.5 8.5V15l8.5-6.5z",
    )

    val Forward: ImageVector = vector(
        "forward",
        "M4 18l8.5-6L4 6v12zM16 6v12l8.5-6z",
    )
}

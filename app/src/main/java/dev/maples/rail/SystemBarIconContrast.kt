package dev.maples.rail

import android.app.WallpaperColors

/**
 * Whether the system bar icons should be dark so they stay legible against the
 * content behind the transparent bars.
 *
 * A wallpaper-backed destination follows the wallpaper's color hints:
 * [WallpaperColors.HINT_SUPPORTS_DARK_TEXT] means the wallpaper is light enough
 * for dark icons, and any other hint means light icons. Dark text support wins
 * when both hints are set, because the wallpaper is then explicitly legible
 * with dark text. Without any color information, the system night mode is the
 * best available signal. Destinations that draw an opaque surface ignore the
 * wallpaper and follow that surface's tone instead.
 *
 * @param isWallpaperBacked whether the destination shows the wallpaper behind
 * the bars
 * @param isSurfaceDark whether the opaque surface drawn behind the bars is dark
 * @param wallpaperColorHints the hints from [WallpaperColors.getColorHints], or
 * null when the wallpaper colors are unknown
 * @param isNightMode whether the system is currently in night mode
 */
internal fun shouldUseDarkSystemBarIcons(
    isWallpaperBacked: Boolean,
    isSurfaceDark: Boolean,
    wallpaperColorHints: Int?,
    isNightMode: Boolean
): Boolean = when {
    !isWallpaperBacked -> !isSurfaceDark
    wallpaperColorHints == null -> !isNightMode
    else -> (wallpaperColorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
}

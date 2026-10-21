package dev.maples.rail

import android.app.WallpaperColors
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemBarIconContrastTest {
    @Test
    fun `uses dark icons on a light wallpaper`() {
        val inputIsWallpaperBacked: Boolean = true
        val inputIsSurfaceDark: Boolean = false
        val inputColorHints: Int = WallpaperColors.HINT_SUPPORTS_DARK_TEXT
        val inputIsNightMode: Boolean = true

        val actualUseDarkIcons: Boolean = shouldUseDarkSystemBarIcons(
            isWallpaperBacked = inputIsWallpaperBacked,
            isSurfaceDark = inputIsSurfaceDark,
            wallpaperColorHints = inputColorHints,
            isNightMode = inputIsNightMode
        )

        assertTrue(actualUseDarkIcons)
    }

    @Test
    fun `uses light icons on a dark wallpaper`() {
        val inputIsWallpaperBacked: Boolean = true
        val inputIsSurfaceDark: Boolean = false
        val inputColorHints: Int = WallpaperColors.HINT_SUPPORTS_DARK_THEME
        val inputIsNightMode: Boolean = false

        val actualUseDarkIcons: Boolean = shouldUseDarkSystemBarIcons(
            isWallpaperBacked = inputIsWallpaperBacked,
            isSurfaceDark = inputIsSurfaceDark,
            wallpaperColorHints = inputColorHints,
            isNightMode = inputIsNightMode
        )

        assertFalse(actualUseDarkIcons)
    }

    @Test
    fun `uses dark icons when the wallpaper supports both dark text and a dark theme`() {
        val inputIsWallpaperBacked: Boolean = true
        val inputIsSurfaceDark: Boolean = false
        val inputColorHints: Int =
            WallpaperColors.HINT_SUPPORTS_DARK_TEXT or WallpaperColors.HINT_SUPPORTS_DARK_THEME
        val inputIsNightMode: Boolean = true

        val actualUseDarkIcons: Boolean = shouldUseDarkSystemBarIcons(
            isWallpaperBacked = inputIsWallpaperBacked,
            isSurfaceDark = inputIsSurfaceDark,
            wallpaperColorHints = inputColorHints,
            isNightMode = inputIsNightMode
        )

        assertTrue(actualUseDarkIcons)
    }

    @Test
    fun `uses light icons when the wallpaper has no color hints`() {
        val inputIsWallpaperBacked: Boolean = true
        val inputIsSurfaceDark: Boolean = false
        val inputColorHints: Int = 0
        val inputIsNightMode: Boolean = false

        val actualUseDarkIcons: Boolean = shouldUseDarkSystemBarIcons(
            isWallpaperBacked = inputIsWallpaperBacked,
            isSurfaceDark = inputIsSurfaceDark,
            wallpaperColorHints = inputColorHints,
            isNightMode = inputIsNightMode
        )

        assertFalse(actualUseDarkIcons)
    }

    @Test
    fun `falls back to dark icons on the launcher in light mode when the wallpaper colors are unknown`() {
        val inputIsWallpaperBacked: Boolean = true
        val inputIsSurfaceDark: Boolean = false
        val inputColorHints: Int? = null
        val inputIsNightMode: Boolean = false

        val actualUseDarkIcons: Boolean = shouldUseDarkSystemBarIcons(
            isWallpaperBacked = inputIsWallpaperBacked,
            isSurfaceDark = inputIsSurfaceDark,
            wallpaperColorHints = inputColorHints,
            isNightMode = inputIsNightMode
        )

        assertTrue(actualUseDarkIcons)
    }

    @Test
    fun `falls back to light icons on the launcher in night mode when the wallpaper colors are unknown`() {
        val inputIsWallpaperBacked: Boolean = true
        val inputIsSurfaceDark: Boolean = false
        val inputColorHints: Int? = null
        val inputIsNightMode: Boolean = true

        val actualUseDarkIcons: Boolean = shouldUseDarkSystemBarIcons(
            isWallpaperBacked = inputIsWallpaperBacked,
            isSurfaceDark = inputIsSurfaceDark,
            wallpaperColorHints = inputColorHints,
            isNightMode = inputIsNightMode
        )

        assertFalse(actualUseDarkIcons)
    }

    @Test
    fun `uses light icons over an opaque dark surface`() {
        val inputIsWallpaperBacked: Boolean = false
        val inputIsSurfaceDark: Boolean = true
        val inputColorHints: Int = WallpaperColors.HINT_SUPPORTS_DARK_TEXT
        val inputIsNightMode: Boolean = false

        val actualUseDarkIcons: Boolean = shouldUseDarkSystemBarIcons(
            isWallpaperBacked = inputIsWallpaperBacked,
            isSurfaceDark = inputIsSurfaceDark,
            wallpaperColorHints = inputColorHints,
            isNightMode = inputIsNightMode
        )

        assertFalse(actualUseDarkIcons)
    }

    @Test
    fun `uses dark icons over an opaque light surface`() {
        val inputIsWallpaperBacked: Boolean = false
        val inputIsSurfaceDark: Boolean = false
        val inputColorHints: Int = WallpaperColors.HINT_SUPPORTS_DARK_THEME
        val inputIsNightMode: Boolean = true

        val actualUseDarkIcons: Boolean = shouldUseDarkSystemBarIcons(
            isWallpaperBacked = inputIsWallpaperBacked,
            isSurfaceDark = inputIsSurfaceDark,
            wallpaperColorHints = inputColorHints,
            isNightMode = inputIsNightMode
        )

        assertTrue(actualUseDarkIcons)
    }
}

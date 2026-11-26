package id.perusahaan.asetga.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Ikon status bar putih selama composable ini tampil (dipakai di atas header biru),
 * lalu dikembalikan sesuai tema saat layar ditinggalkan.
 */
@Composable
fun LightStatusBarIcons() {
    val view = LocalView.current
    val isDark = AppTheme.isDark
    if (view.isInEditMode) return
    DisposableEffect(view, isDark) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = !isDark }
    }
}

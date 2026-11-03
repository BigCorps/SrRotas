package com.srrotas.app

import android.app.AlertDialog
import android.content.Context

/** vc109 — lugares marcados (particulares/compartilháveis) acessíveis também no Radar contextual. */
object RadarPlacesDialog109 {
    fun show(context: Context) {
        val dialog = AlertDialog.Builder(context)
            .setView(RadarPanel027035(context, placesOnly = true))
            .setPositiveButton("Fechar", null)
            .create()
        val p = UiKit.palette(context)
        dialog.window?.setBackgroundDrawable(UiKit.rounded(context, p.background, 20, p.line, 1))
        dialog.show()
    }
}

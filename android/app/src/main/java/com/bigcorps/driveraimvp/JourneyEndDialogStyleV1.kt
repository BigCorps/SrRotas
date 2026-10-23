package com.srrotas.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface

/** End-of-journey dialog only: primary save, cancel, skip odometer. */
internal object JourneyEndDialogStyleV1 {
    fun apply(context: Context, dialog: AlertDialog) {
        val p=SrUi023.palette(context)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(Color.WHITE)
            background=SrUi023.rounded(p.userGreen,12,p.userGreen,1,context)
            isAllCaps=false
            minHeight=SrUi023.dp(context,40)
            setPadding(SrUi023.dp(context,10),0,SrUi023.dp(context,10),0)
            setTypeface(typeface,Typeface.BOLD)
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(p.red)
            isAllCaps=false
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.apply {
            setTextColor(p.muted)
            isAllCaps=false
        }
    }
}

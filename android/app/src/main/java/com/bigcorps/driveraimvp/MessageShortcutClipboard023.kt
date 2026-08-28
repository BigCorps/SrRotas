package com.srrotas.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

object MessageShortcutClipboard023 {
    fun copy(context: Context, shortcut: MessageShortcut023): Boolean {
        if (!shortcut.enabled || shortcut.text.isBlank()) return false
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val label = shortcut.accessibilityLabel
            ?.takeIf(String::isNotBlank)
            ?: "Mensagem ${shortcut.shortLabel}"
        clipboard.setPrimaryClip(ClipData.newPlainText(label, shortcut.text))
        Toast.makeText(context, "Mensagem copiada", Toast.LENGTH_SHORT).show()
        return true
    }
}

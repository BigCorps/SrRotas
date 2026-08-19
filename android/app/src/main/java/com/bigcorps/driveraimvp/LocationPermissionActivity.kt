package com.srrotas.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast

class LocationPermissionActivity : Activity() {
    companion object { private const val REQ_LOCATION = 4515 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (JourneyLocationRuntime.hasPermission(this)) {
            JourneyLocationService.dispatch(this, JourneyLocationService.ACTION_START)
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Localização aproximada durante a jornada")
            .setMessage(
                "O Sr. Rotas usa sua localização aproximada enquanto a jornada estiver ativa para medir quanto tempo você fica disponível por região. " +
                    "O servidor recebe intervalos por região, não uma trilha contínua de GPS. Se você não permitir, o OCR e o HUD continuam funcionando normalmente, mas a estatística regional ficará incompleta.",
            )
            .setNegativeButton("Agora não") { _, _ ->
                LocalLog.append(this, "Localização regional recusada no aviso 0.15; OCR continua normalmente.")
                finish()
            }
            .setPositiveButton("Continuar") { _, _ ->
                requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
            }
            .setOnCancelListener { finish() }
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                JourneyLocationService.dispatch(this, JourneyLocationService.ACTION_START)
                Toast.makeText(this, "Localização regional ativada para esta jornada.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Jornada continua sem estatística de exposição regional.", Toast.LENGTH_LONG).show()
                LocalLog.append(this, "Localização regional não autorizada; OCR continua normalmente.")
            }
            finish()
        }
    }
}

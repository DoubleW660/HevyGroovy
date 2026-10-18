package com.example.hevygroovy.resttimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RestTimerController.ACTION_EXPIRED) return
        val token = intent.getStringExtra(RestTimerController.EXTRA_TOKEN) ?: return
        RestTimerController.get(context).onAlarm(token)
    }
}

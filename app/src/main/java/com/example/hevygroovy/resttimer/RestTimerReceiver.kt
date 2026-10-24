package com.example.hevygroovy.resttimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RestTimerController.ACTION_EXPIRED) return
        val token = intent.getStringExtra(RestTimerController.EXTRA_TOKEN) ?: return
        val pending = goAsync()
        try {
            RestTimerController.get(context).onAlarm(token, pending::finish)
        } catch (failure: RuntimeException) {
            pending.finish()
            throw failure
        }
    }
}

package dev.mobilehermes

import android.content.Context
import android.content.Intent
import android.net.Uri

object TermuxCommand {
    fun openTermux(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.termux")
        if (launchIntent != null) {
            context.startActivity(launchIntent)
            return
        }

        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://f-droid.org/packages/com.termux/")
            )
        )
    }
}


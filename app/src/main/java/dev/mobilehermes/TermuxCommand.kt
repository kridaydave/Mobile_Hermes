package dev.mobilehermes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

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

    fun runMobileHermesScript(context: Context, scriptName: String, argument: String? = null) {
        val command = buildString {
            append("cd ~/Mobile_Hermes/termux && sh ")
            append(scriptName)
            if (!argument.isNullOrBlank()) {
                append(' ')
                append(argument)
            }
        }

        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setPackage("com.termux")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", command))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
            putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
        }

        try {
            context.startActivity(intent)
        } catch (exception: Exception) {
            Toast.makeText(
                context,
                "Open Termux and run: $command",
                Toast.LENGTH_LONG
            ).show()
            openTermux(context)
        }
    }
}

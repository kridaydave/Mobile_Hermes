package dev.mobilehermes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object TermuxCommand {
    private const val TERMUX_PACKAGE = "com.termux"
    private const val RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
    private const val REPO_URL = "https://github.com/kridaydave/Mobile_Hermes.git"

    fun isTermuxInstalled(context: Context): Boolean {
        return context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE) != null
    }

    fun openTermux(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
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

    fun runSetup(context: Context, mode: String) {
        runShell(context, setupCommand(mode), "Setup command copied")
    }

    fun runScript(context: Context, scriptName: String, argument: String? = null) {
        val command = buildString {
            append(ensureRepoCommand())
            append(" && cd ~/Mobile_Hermes/termux && sh ")
            append(shellQuote(scriptName))
            if (!argument.isNullOrBlank()) {
                append(' ')
                append(shellQuote(argument))
            }
        }
        runShell(context, command, "Command copied")
    }

    fun copyExternalAppsFix(context: Context) {
        copyToClipboard(
            context,
            "Enable Termux external apps",
            "mkdir -p ~/.termux && grep -qxF 'allow-external-apps=true' ~/.termux/termux.properties 2>/dev/null || printf '\\nallow-external-apps=true\\n' >> ~/.termux/termux.properties"
        )
        Toast.makeText(context, "Copied Termux permission command", Toast.LENGTH_LONG).show()
    }

    fun copySetupCommand(context: Context, mode: String) {
        copyToClipboard(context, "Mobile Hermes setup", setupCommand(mode))
        Toast.makeText(context, "Copied setup command", Toast.LENGTH_LONG).show()
    }

    fun copyCleanupCommand(context: Context) {
        copyToClipboard(
            context,
            "Mobile Hermes cleanup",
            "${ensureRepoCommand()} && cd ~/Mobile_Hermes/termux && sh mobile-hermes-cleanup.sh"
        )
        Toast.makeText(context, "Copied cleanup command", Toast.LENGTH_LONG).show()
    }

    private fun runShell(context: Context, command: String, copiedToast: String) {
        val intent = Intent(RUN_COMMAND_ACTION).apply {
            setPackage(TERMUX_PACKAGE)
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", command))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
            putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
        }

        try {
            context.startActivity(intent)
        } catch (exception: Exception) {
            copyToClipboard(context, "Mobile Hermes Termux command", command)
            Toast.makeText(
                context,
                "$copiedToast. Enable allow-external-apps=true, then paste in Termux.",
                Toast.LENGTH_LONG
            ).show()
            openTermux(context)
        }
    }

    private fun setupCommand(mode: String): String {
        return "${ensureRepoCommand()} && cd ~/Mobile_Hermes/termux && sh mobile-hermes-bootstrap.sh ${shellQuote(mode)} && sh mobile-hermes-start.sh"
    }

    private fun ensureRepoCommand(): String {
        return "pkg install -y git >/dev/null 2>&1 || true; mkdir -p ~/.mobile-hermes; " +
            "if [ -d ~/Mobile_Hermes/.git ]; then git -C ~/Mobile_Hermes pull --ff-only; " +
            "else git clone ${shellQuote(REPO_URL)} ~/Mobile_Hermes && touch ~/.mobile-hermes/repo-cloned-by-app; fi"
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun copyToClipboard(context: Context, label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    }
}

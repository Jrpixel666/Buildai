package dev.ashdock.launcher

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.BroadcastReceiver
import java.util.concurrent.atomic.AtomicInteger

object TermuxBridge {
    private val sequence = AtomicInteger(15000)
    private const val PACKAGE = "com.termux"
    const val RESULT_ACTION = "dev.ashdock.launcher.RESULT"

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }

    fun permissionGranted(context: Context): Boolean =
        context.checkSelfPermission("com.termux.permission.RUN_COMMAND") == PackageManager.PERMISSION_GRANTED

    fun run(context: Context, command: String, label: String, fullTerminal: Boolean = false): String? {
        if (!isInstalled(context)) return "Termux is not installed. Install the F-Droid/GitHub edition first."
        if (!permissionGranted(context)) return "Grant AshDock 'Run commands in Termux environment' in Android app permissions."
        if (command.length > 65000) return "Command is too long."
        return try {
            val reqId = sequence.incrementAndGet()
            val callback = Intent(context, CommandResultReceiver::class.java)
                .setAction(RESULT_ACTION)
                .putExtra("ash_command", label)
            val flags = PendingIntent.FLAG_ONE_SHOT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, reqId, callback, flags)
            val intent = Intent("com.termux.RUN_COMMAND").apply {
                setClassName(PACKAGE, "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", command))
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", !fullTerminal)
                putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", label.take(60))
                if (fullTerminal) {
                    putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
                } else {
                    putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pending)
                }
            }
            context.startService(intent) ?: return "Termux rejected the service request. Check Termux setup."
            null
        } catch (e: SecurityException) {
            "Permission denied by Termux. Check allow-external-apps=true and Android permissions: ${e.message}"
        } catch (e: Exception) {
            "Unable to run command: ${e.message}"
        }
    }
}

class CommandResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TermuxBridge.RESULT_ACTION) return
        val data: Bundle? = intent.getBundleExtra("result")
        val label = intent.getStringExtra("ash_command") ?: "Command"
        val code = data?.getInt("exitCode", -999) ?: -999
        val out = data?.getString("stdout").orEmpty().trimEnd()
        val err = data?.getString("stderr").orEmpty().trimEnd()
        val internal = data?.getString("errmsg").orEmpty()
        val info = buildString {
            append("\n[${label}] exit=$code\n")
            if (out.isNotBlank()) append(out).append('\n')
            if (err.isNotBlank()) append(err).append('\n')
            if (internal.isNotBlank()) append("Termux: ").append(internal).append('\n')
            if (data == null) append("No result bundle returned. Check Termux version and permissions.\n")
        }.takeLast(18000)
        val store = SettingsStore(context)
        store.appendConsole(info)
        if (label == "Discover Linux applications" && code == 0) {
            val imports = out.lines().mapNotNull { line ->
                val parts = line.split('\t', limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val id = parts[0].trim()
                val name = parts[1].trim().take(70)
                if (!id.matches(Regex("[A-Za-z0-9._+-]{1,100}")) || name.isEmpty()) null
                else LinuxShortcut(name, "gtk-launch ${LinuxCommands.quote(id)}", true)
            }.take(100)
            imports.forEach { store.addShortcut(it) }
            store.appendConsole("Imported ${imports.size} desktop shortcuts; Linux GUI display requires a separate X server.\n")
        }
        MainActivity.current?.get()?.refreshConsoleIfVisible()
    }
}

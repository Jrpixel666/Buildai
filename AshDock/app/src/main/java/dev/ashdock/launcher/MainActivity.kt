package dev.ashdock.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ResolveInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.*
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class AppEntry(val label: String, val info: ResolveInfo, val key: String)
private data class Palette(val accent: Int, val backdrop: Int, val panel: Int, val foreground: Int, val muted: Int)

class MainActivity : Activity() {
    companion object { var current: WeakReference<MainActivity>? = null }

    private lateinit var store: SettingsStore
    private lateinit var palette: Palette
    private lateinit var root: LinearLayout
    private lateinit var workspace: LinearLayout
    private var consoleView: TextView? = null
    private var currentPage = "HOME"
    private var clockView: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val clockUpdater = object : Runnable {
        override fun run() {
            if (currentPage == "HOME") updateClock()
            handler.postDelayed(this, 30000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(this)
        current = WeakReference(this)
        showPage("HOME")
        if (!getPreferences(MODE_PRIVATE).getBoolean("welcome", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("welcome", true).apply()
            AlertDialog.Builder(this)
                .setTitle("Welcome to AshDock")
                .setMessage("A minimalist Omarchy-inspired home for Android. Android apps work straight away. Arch Linux ARM is optional and runs rootlessly through Termux and PRoot-Distro. The command console is built into this launcher.")
                .setPositiveButton("Set as home") { _, _ -> requestHomeRole() }
                .setNegativeButton("Explore") { _, _ -> }
                .show()
        }
    }
    override fun onResume() { super.onResume(); current = WeakReference(this); handler.removeCallbacks(clockUpdater); handler.post(clockUpdater) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(clockUpdater) }
    override fun onDestroy() { super.onDestroy(); if (current?.get() === this) current = null }
    override fun onBackPressed() { if (currentPage != "HOME") showPage("HOME") }

    private fun p(): Palette {
        val accent = when (store.theme) {
            "GLACIER" -> Color.rgb(100, 176, 255)
            "FOREST" -> Color.rgb(137, 201, 121)
            "VIOLET" -> Color.rgb(192, 144, 255)
            "SAND" -> Color.rgb(237, 189, 115)
            "CUSTOM" -> try { Color.parseColor(store.accentHex) } catch (_: Exception) { Color.rgb(255, 139, 43) }
            else -> Color.rgb(255, 139, 43)
        }
        return Palette(accent, Color.rgb(10, 12, 16), Color.rgb(24, 27, 33), Color.rgb(240, 240, 238), Color.rgb(152, 157, 166))
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun size(sp: Float): Float = sp * store.textScale / 100f
    private fun rounded(color: Int, radius: Int = 12, stroke: Int? = null) = GradientDrawable().also {
        it.setColor(color); it.cornerRadius = dp(radius).toFloat()
        if (stroke != null) it.setStroke(dp(1), stroke)
    }
    private fun text(value: String, sp: Float = 14f, color: Int = palette.foreground, mono: Boolean = false, bold: Boolean = false): TextView =
        TextView(this).also {
            it.text = value; it.setTextColor(color); it.textSize = size(sp)
            it.typeface = if (mono) Typeface.create("monospace", if (bold) Typeface.BOLD else Typeface.NORMAL)
                else Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            it.setLineSpacing(dp(2).toFloat(), 1.03f)
        }
    private fun vertical(): LinearLayout = LinearLayout(this).also { it.orientation = LinearLayout.VERTICAL }
    private fun horizontal(): LinearLayout = LinearLayout(this).also { it.orientation = LinearLayout.HORIZONTAL; it.gravity = Gravity.CENTER_VERTICAL }
    private fun pad(v: View, x: Int = 16, y: Int = 12) { v.setPadding(dp(x), dp(y), dp(x), dp(y)) }
    private fun line(parent: LinearLayout, h: Int = 12) { parent.addView(View(this), LinearLayout.LayoutParams(1, dp(h))) }
    private fun card(parent: LinearLayout, title: String, subtitle: String? = null, onClick: (() -> Unit)? = null): LinearLayout {
        val box = vertical()
        box.background = rounded(palette.panel, 15, Color.rgb(48, 51, 58)); pad(box, 16, 14)
        box.addView(text(title, 15f, palette.foreground, bold = true))
        if (subtitle != null) { line(box, 5); box.addView(text(subtitle, 12f, palette.muted)) }
        if (onClick != null) { box.isClickable = true; box.setOnClickListener { onClick() } }
        parent.addView(box, LinearLayout.LayoutParams(-1, -2))
        line(parent, 9)
        return box
    }
    private fun button(label: String, action: () -> Unit, primary: Boolean = false): TextView = text(label, 12f,
        if (primary) palette.backdrop else palette.foreground, mono = true, bold = true).also {
        pad(it, 12, 12); it.gravity = Gravity.CENTER; it.background = rounded(if (primary) palette.accent else palette.panel, 11,
            if (primary) null else Color.rgb(58, 60, 68))
        it.setOnClickListener { _ -> action() }
    }
    private fun rowButtons(parent: LinearLayout, vararg items: Pair<String, () -> Unit>) {
        val r = horizontal()
        items.forEachIndexed { index, item ->
            if (index > 0) r.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
            val b = button(item.first, item.second, index == 0)
            r.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
        }
        parent.addView(r, LinearLayout.LayoutParams(-1, -2)); line(parent)
    }
    private fun section(parent: LinearLayout, name: String) {
        val t = text(name.uppercase(Locale.ROOT), 11f, palette.accent, mono = true, bold = true)
        t.letterSpacing = .14f; parent.addView(t)
        line(parent, 10)
    }
    private fun begin(title: String, subtitle: String): LinearLayout {
        palette = p()
        window.statusBarColor = palette.backdrop
        window.navigationBarColor = palette.backdrop
        if (store.showStatusBar) window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        else window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        val frame = android.widget.FrameLayout(this)
        frame.addView(GridWallpaper(this, palette), android.widget.FrameLayout.LayoutParams(-1, -1))
        root = vertical()
        root.setPadding(dp(16), dp(12), dp(16), dp(3))
        frame.addView(root, android.widget.FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)
        val top = horizontal()
        val brand = text("▦  ASHDOCK", 14f, palette.accent, mono = true, bold = true)
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val tag = text("ANDROID  /  ARCH", 10f, palette.muted, mono = true)
        top.addView(tag)
        root.addView(top)
        line(root, 22)
        val header = text(title, 26f, palette.foreground, bold = true)
        root.addView(header)
        line(root, 2)
        root.addView(text(subtitle, 12f, palette.muted))
        line(root, 18)
        val scroll = ScrollView(this)
        scroll.isFillViewport = false
        scroll.clipToPadding = false
        workspace = vertical()
        workspace.setPadding(0, 0, 0, dp(28))
        scroll.addView(workspace)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        line(root, 4)
        makeDock()
        return workspace
    }
    private fun makeDock() {
        val nav = horizontal()
        nav.background = rounded(palette.panel, 15, Color.rgb(51, 54, 62))
        pad(nav, 5, 5)
        val destinations = if (store.leftHanded) listOf("SETUP", "LINUX", "APPS", "HOME")
            else listOf("HOME", "APPS", "LINUX", "SETUP")
        destinations.forEach { page ->
            val selected = currentPage == page
            val chip = text(when (page) { "HOME" -> "⌂  HOME"; "APPS" -> "▦  APPS";
                "LINUX" -> "›_ LINUX"; else -> "⚙  TWEAK" }, 10f,
                if (selected) palette.backdrop else palette.muted, mono = true, bold = selected)
            chip.gravity = Gravity.CENTER
            pad(chip, 2, 12)
            if (selected) chip.background = rounded(palette.accent, 10)
            chip.setOnClickListener { showPage(page) }
            nav.addView(chip, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, -2))
    }
    private fun showPage(page: String) {
        currentPage = page
        consoleView = null; clockView = null
        when (page) {
            "HOME" -> showHome()
            "APPS" -> showApps()
            "LINUX" -> showLinux()
            "SETUP" -> showSettings()
        }
    }
    private fun installedApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION") val matches = packageManager.queryIntentActivities(intent, 0)
        return matches.mapNotNull {
            val activity = it.activityInfo ?: return@mapNotNull null
            if (activity.packageName == packageName) return@mapNotNull null
            val label = it.loadLabel(packageManager).toString()
            val key = ComponentName(activity.packageName, activity.name).flattenToString()
            AppEntry(label, it, key)
        }.distinctBy { it.key }.sortedBy { it.label.lowercase(Locale.ROOT) }
    }
    private fun openApp(entry: AppEntry) {
        val a = entry.info.activityInfo
        try {
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(a.packageName, a.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            startActivity(intent)
        } catch (e: Exception) { alert("App could not open", e.localizedMessage ?: "Unknown error") }
    }
    private fun appChoices(entry: AppEntry) {
        val pinned = store.pinned().contains(entry.key)
        val hidden = store.hidden().contains(entry.key)
        val actions = arrayOf(if (pinned) "Unpin from home" else "Pin to home",
            if (hidden) "Unhide app" else "Hide from drawer", "App details")
        AlertDialog.Builder(this).setTitle(entry.label).setItems(actions) { _, index ->
            when (index) {
                0 -> store.togglePinned(entry.key)
                1 -> store.toggleHidden(entry.key)
                2 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${entry.info.activityInfo.packageName}")))
            }
            showPage(currentPage)
        }.show()
    }
    private fun appGrid(parent: LinearLayout, apps: List<AppEntry>, max: Int = Int.MAX_VALUE) {
        val columns = store.gridColumns
        val shown = apps.take(max)
        if (shown.isEmpty()) { parent.addView(text("No apps here yet. Long-press an app to pin it.", 13f, palette.muted)); return }
        shown.chunked(columns).forEach { batch ->
            val row = horizontal()
            row.gravity = Gravity.TOP
            batch.forEach { app ->
                val item = vertical()
                item.gravity = Gravity.CENTER_HORIZONTAL
                pad(item, 2, if (store.compactApps) 5 else 10)
                val img = ImageView(this)
                try { img.setImageDrawable(app.info.loadIcon(packageManager)) } catch (_: Exception) { img.setImageResource(android.R.drawable.sym_def_app_icon) }
                val px = dp(if (store.compactApps) 34 else 44)
                item.addView(img, LinearLayout.LayoutParams(px, px))
                line(item, 8)
                val title = text(app.label, 10f, palette.foreground)
                title.gravity = Gravity.CENTER; title.maxLines = 2; title.ellipsize = android.text.TextUtils.TruncateAt.END
                item.addView(title, LinearLayout.LayoutParams(-1, dp(35)))
                item.setOnClickListener { openApp(app) }
                item.setOnLongClickListener { appChoices(app); true }
                row.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
            }
            repeat(columns - batch.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            parent.addView(row)
        }
    }
    private fun showHome() {
        val body = begin("Your workspace", "NO ROOT  ·  MINIMAL  ·  CUSTOMISABLE")
        if (store.showClock) {
            line(body, 14)
            clockView = text("", 62f, palette.foreground, mono = true, bold = true)
            clockView?.setSingleLine(true)
            clockView?.setAutoSizeTextTypeUniformWithConfiguration(30, 64, 2, android.util.TypedValue.COMPLEX_UNIT_SP)
            body.addView(clockView)
            line(body, 5)
            updateClock()
        }
        line(body, 18)
        section(body, "Quick actions")
        rowButtons(body,
            "▦  ALL APPS" to { showPage("APPS") },
            "›_  TERMINAL" to { showPage("LINUX") })
        val pinned = installedApps().filter { store.pinned().contains(it.key) }
        section(body, "Pinned Android apps")
        appGrid(body, pinned, 18)
        line(body, 20)
        if (store.showLinuxHome) {
            section(body, "Linux shortcuts")
            val shortcuts = store.shortcuts()
            if (shortcuts.isEmpty()) card(body, "+  Add Linux application", "Create a shortcut to an Arch program or import desktop entries") { addShortcutDialog() }
            else shortcuts.take(10).forEach { shortcut ->
                card(body, "  ›  ${shortcut.label}", if (shortcut.graphical) "GUI · X11 needed" else "COMMAND · ${shortcut.command.take(60)}") { runShortcut(shortcut) }
            }
        }
        line(body, 4)
        section(body, "Workspace health")
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val pct = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        card(body, "ANDROID  ${Build.VERSION.RELEASE}     BATTERY  ${if (pct >= 0) "$pct%" else "—"}",
            "Architecture ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"} · Arch is optional")
        if (store.showHints) card(body, "TIP  /  PRESS & HOLD", "Long-press an Android app to pin, hide, or inspect it.")
    }
    private fun updateClock() {
        val pattern = if (store.use24Hour) "HH:mm" else "h:mm a"
        clockView?.text = SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
    }
    private fun showApps() {
        val body = begin("Applications", "INSTALLED  /  PIN  /  HIDE")
        val search = EditText(this)
        search.setTextColor(palette.foreground); search.setHintTextColor(palette.muted)
        search.setSingleLine(true); search.hint = "Search Android apps..."
        search.textSize = size(15f); search.background = rounded(palette.panel, 12, Color.rgb(55, 57, 64)); pad(search)
        body.addView(search, LinearLayout.LayoutParams(-1, -2)); line(body, 16)
        val counter = text("", 11f, palette.accent, mono = true)
        body.addView(counter); line(body, 8)
        val grid = vertical()
        body.addView(grid)
        val all = installedApps()
        fun filter(query: String) {
            grid.removeAllViews()
            val hidden = store.hidden()
            val results = all.filter { !hidden.contains(it.key) && it.label.contains(query, ignoreCase = true) }
            counter.text = "${results.size} APPS  /  ${store.pinned().size} PINNED"
            appGrid(grid, results)
        }
        filter("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { filter(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })
        line(body, 15)
        card(body, "Hidden apps", "${store.hidden().size} hidden · restore from Settings") { showPage("SETUP") }
    }
    private fun launch(command: String, title: String, full: Boolean = false) {
        if (!full) store.appendConsole("\nashdock$ $title\n")
        val error = TermuxBridge.run(this, command, title, full)
        if (error != null) { store.appendConsole("$error\n"); alert("Linux bridge", error) }
        else if (!full) Toast.makeText(this, "Command sent to Termux", Toast.LENGTH_SHORT).show()
        refreshConsoleIfVisible()
    }
    fun refreshConsoleIfVisible() {
        runOnUiThread { consoleView?.let { it.text = store.console().ifEmpty { "No commands yet. Type help to begin." } } }
    }
    private fun showLinux() {
        val body = begin("Linux workspace", "ARCH LINUX ARM  /  PROOT  /  NO ROOT")
        section(body, "Runtime")
        card(body, if (TermuxBridge.isInstalled(this)) "● Termux detected" else "○ Termux required",
            "The launcher uses Termux and PRoot-Distro as its Linux execution backend. The console below stays inside AshDock.")
        rowButtons(body,
            "INSTALL ARCH" to { installArchDialog() },
            "CHECK STATUS" to { launch(LinuxCommands.detection(), "Check Linux status") })
        rowButtons(body,
            "ADD APP" to { addShortcutDialog() },
            "SCAN .DESKTOP" to { scanDesktopDialog() })
        section(body, "Command console")
        val console = vertical()
        console.background = rounded(Color.rgb(7, 9, 12), 14, Color.rgb(55, 58, 67)); pad(console, 14, 14)
        val bar = text("●  ash-arch@ashdock  ·  output", 11f, palette.accent, mono = true, bold = true)
        console.addView(bar); line(console, 8)
        consoleView = text(store.console().ifEmpty { "Welcome to AshDock terminal.\nType help for commands." }, 12f,
            Color.rgb(207, 214, 211), mono = true)
        consoleView?.setTextIsSelectable(true)
        console.addView(consoleView)
        body.addView(console)
        line(body, 10)
        val promptRow = horizontal()
        promptRow.addView(text("❯ ", 18f, palette.accent, mono = true, bold = true))
        val entry = EditText(this)
        entry.setTextColor(palette.foreground); entry.setHintTextColor(palette.muted)
        entry.textSize = size(13f); entry.typeface = Typeface.MONOSPACE
        entry.hint = "arch uname -a"; entry.setSingleLine(true)
        entry.background = rounded(palette.panel, 10); pad(entry, 10, 11)
        entry.imeOptions = EditorInfo.IME_ACTION_GO
        promptRow.addView(entry, LinearLayout.LayoutParams(0, -2, 1f))
        promptRow.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        val go = button("RUN", { val cmd = entry.text.toString(); entry.setText(""); handleConsole(cmd) }, true)
        promptRow.addView(go)
        entry.setOnEditorActionListener { _, _, _ -> val cmd = entry.text.toString(); entry.setText(""); handleConsole(cmd); true }
        body.addView(promptRow); line(body, 13)
        rowButtons(body,
            "HELP" to { handleConsole("help") },
            "FULL TERMINAL" to { openFullTerminal() })
        section(body, "Graphical Linux")
        rowButtons(body,
            "START X11" to { startX11() },
            "X11 GUIDE" to { x11Help() })
        section(body, "Arch applications")
        val shortcuts = store.shortcuts()
        if (shortcuts.isEmpty()) body.addView(text("No Linux apps pinned. Install a package, then add a command or scan desktop files.", 13f, palette.muted))
        else shortcuts.forEach { shortcut ->
            val c = card(body, "›  ${shortcut.label}", "${if (shortcut.graphical) "X11 GUI" else "CLI"}  ·  ${shortcut.command.take(80)}") { runShortcut(shortcut) }
            c.setOnLongClickListener { shortcutOptions(shortcut); true }
        }
        line(body, 15)
        card(body, "GUI apps: display required", "For XFCE, GTK, Qt and other windows, install and configure Termux:X11. PRoot is not a display server.") { x11Help() }
    }
    private fun handleConsole(input: String) {
        val cmd = input.trim()
        if (cmd.isEmpty()) return
        when {
            cmd == "help" -> { store.appendConsole("\n${LinuxCommands.quickHelp()}\n"); refreshConsoleIfVisible() }
            cmd == "clear" -> { store.clearConsole(); refreshConsoleIfVisible() }
            cmd == "add" -> addShortcutDialog()
            cmd == "discover" -> scanDesktopDialog()
            cmd == "arch" -> launch(LinuxCommands.detection(), "Check Linux status")
            cmd == "update" -> launch(LinuxCommands.login("pacman -Syu --noconfirm"), "Update Arch packages")
            cmd.startsWith("install ") -> try { launch(LinuxCommands.packageCommand(cmd.removePrefix("install ")), cmd) }
                catch (e: IllegalArgumentException) { alert("Package name", e.message ?: "Invalid") }
            cmd.startsWith("arch ") -> launch(LinuxCommands.login(cmd.removePrefix("arch ")), cmd)
            cmd.startsWith("termux ") -> launch(cmd.removePrefix("termux "), cmd)
            else -> {
                store.appendConsole("\nUnknown command. Prefix Arch commands with 'arch ' or type help.\n")
                refreshConsoleIfVisible()
            }
        }
    }
    private fun runShortcut(shortcut: LinuxShortcut) {
        val command = if (shortcut.graphical) "export DISPLAY=:0; ${shortcut.command}" else shortcut.command
        launch(LinuxCommands.shellCommand(command), shortcut.label)
    }
    private fun shortcutOptions(shortcut: LinuxShortcut) {
        AlertDialog.Builder(this).setTitle(shortcut.label)
            .setItems(arrayOf("Run", "Edit shortcut", "Remove shortcut")) { _, which ->
                when (which) {
                    0 -> runShortcut(shortcut)
                    1 -> addShortcutDialog(shortcut)
                    2 -> AlertDialog.Builder(this).setTitle("Remove ${shortcut.label}?")
                        .setMessage("Only removes the launcher shortcut. It does not uninstall the Arch package.")
                        .setPositiveButton("Remove") { _, _ ->
                            store.setShortcuts(store.shortcuts().filterNot { it.label == shortcut.label }); showPage(currentPage)
                        }.setNegativeButton("Cancel", null).show()
                }
            }.show()
    }
    private fun addShortcutDialog(existing: LinuxShortcut? = null) {
        val form = vertical(); pad(form, 22, 12)
        val name = EditText(this).apply { hint = "Name, e.g. Neovim"; setSingleLine(true); setText(existing?.label ?: "") }
        val command = EditText(this).apply { hint = "Arch command, e.g. nvim"; setSingleLine(true); setText(existing?.command ?: "") }
        val graphical = CheckBox(this).apply { text = "Graphical app (requires Termux:X11)"; isChecked = existing?.graphical ?: false }
        form.addView(name); form.addView(command); form.addView(graphical)
        AlertDialog.Builder(this).setTitle(if (existing == null) "Add Linux shortcut" else "Edit Linux shortcut")
            .setView(form)
            .setPositiveButton("Save") { _, _ ->
                val label = name.text.toString().trim().take(80)
                val cmd = command.text.toString().trim().take(2048)
                if (label.isNotEmpty() && cmd.isNotEmpty()) {
                    if (existing != null) store.setShortcuts(store.shortcuts().filterNot { it.label == existing.label })
                    store.addShortcut(LinuxShortcut(label, cmd, graphical.isChecked))
                    showPage(currentPage)
                } else alert("Invalid shortcut", "Name and command cannot be blank.")
            }.setNegativeButton("Cancel", null).show()
    }
    private fun scanDesktopDialog() {
        AlertDialog.Builder(this).setTitle("Import Linux applications")
            .setMessage("Scan Arch's .desktop entries and add them as Linux shortcuts? Launching GUI shortcuts also requires GTK and a configured Termux:X11 display.")
            .setPositiveButton("Scan") { _, _ -> launch(LinuxCommands.discoverDesktopApps(), "Discover Linux applications") }
            .setNegativeButton("Cancel", null).show()
    }
    private fun installArchDialog() {
        val freeGiB = try {
            val fs = android.os.StatFs(Environment.getDataDirectory().path)
            fs.availableBytes.toDouble() / (1024 * 1024 * 1024)
        } catch (_: Exception) { 0.0 }
        AlertDialog.Builder(this).setTitle("Install Arch Linux ARM")
            .setMessage("Downloads the official AArch64 root filesystem to Termux, verifies its signature, then installs it through PRoot-Distro as '${LinuxCommands.NAME}'. No root required.\n\nEnsure Termux is configured, Wi-Fi is available and you have several GB of free space (estimated free: ${"%.1f".format(freeGiB)} GiB). The download can be large.\n\nThe launcher will not overwrite an existing rootfs.")
            .setPositiveButton("Install") { _, _ -> launch(LinuxCommands.installer(), "Install Arch Linux ARM") }
            .setNegativeButton("Cancel", null).show()
    }
    private fun openFullTerminal() {
        launch(LinuxCommands.login("exec /bin/bash -l"), "Interactive Arch session", full = true)
    }
    private fun startX11() {
        AlertDialog.Builder(this).setTitle("Start Termux:X11 display :0")
            .setMessage("Requires the Termux:X11 Android app and termux-x11-nightly package. Starts the X server in Termux. Graphical windows appear in the separate Termux:X11 app, not inside AshDock.")
            .setPositiveButton("Start") { _, _ ->
                launch("if command -v termux-x11 >/dev/null 2>&1; then nohup termux-x11 :0 >/dev/null 2>&1 </dev/null & echo 'X11 start requested'; else echo 'Install x11-repo and termux-x11-nightly first'; fi", "Start Termux:X11")
            }.setNegativeButton("Cancel", null).show()
    }
    private fun x11Help() {
        alert("Graphical Linux applications", "Install Termux:X11 separately and run its X server. In Arch, install needed GUI packages with pacman. The one-shot command console does not implement a graphical display, sound server, window manager, or interactive TTY. See docs/LINUX_SETUP.md in the source package.")
    }
    private fun alert(title: String, message: String) { AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show() }
    private fun requestHomeRole() {
        if (Build.VERSION.SDK_INT >= 29) {
            val role = getSystemService(ROLE_SERVICE) as RoleManager
            if (role.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (!role.isRoleHeld(RoleManager.ROLE_HOME)) {
                    startActivityForResult(role.createRequestRoleIntent(RoleManager.ROLE_HOME), 102)
                } else Toast.makeText(this, "Already the default home", Toast.LENGTH_SHORT).show()
                return
            }
        }
        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
    }
    private fun showSettings() {
        val body = begin("Customise", "THEMES  /  LAYOUT  /  SYSTEM")
        section(body, "Appearance")
        card(body, "Colour scheme  ·  ${store.theme}", "Ember, Glacier, Forest, Violet, Sand or your custom accent") {
            val themes = arrayOf("EMBER", "GLACIER", "FOREST", "VIOLET", "SAND", "CUSTOM")
            AlertDialog.Builder(this).setTitle("Choose theme").setSingleChoiceItems(themes,
                themes.indexOf(store.theme)) { dialog, which -> store.theme = themes[which]; dialog.dismiss()
                if (themes[which] == "CUSTOM") customAccent() else showPage("SETUP")
            }.setNegativeButton("Cancel", null).show()
        }
        rowButtons(body,
            "GRID  ${store.gridColumns}  ▦" to { store.gridColumns = if (store.gridColumns == 6) 3 else store.gridColumns + 1; showPage("SETUP") },
            "FONT  ${store.textScale}%" to { scaleDialog() })
        toggle(body, "Big clock", "Show the large desktop-style clock", store.showClock) { store.showClock = it }
        toggle(body, "24-hour clock", "Use 24-hour time instead of AM/PM", store.use24Hour) { store.use24Hour = it }
        toggle(body, "Compact app grid", "Smaller icon tiles in the launcher", store.compactApps) { store.compactApps = it }
        toggle(body, "Visible status bar", "Show Android's status bar above the workspace", store.showStatusBar) { store.showStatusBar = it }
        line(body, 12)
        section(body, "Home screen")
        toggle(body, "Linux on home", "Show Linux shortcuts in the home workspace", store.showLinuxHome) { store.showLinuxHome = it }
        toggle(body, "Left-handed dock", "Reverse the dock layout", store.leftHanded) { store.leftHanded = it }
        toggle(body, "Tips and guidance", "Show launcher help on the home screen", store.showHints) { store.showHints = it }
        card(body, "Unhide Android apps", "${store.hidden().size} apps hidden from the launcher drawer") { unhideDialog() }
        line(body, 12)
        section(body, "Linux & integration")
        card(body, "Termux permission", if (TermuxBridge.permissionGranted(this)) "Granted" else "Not granted · allow external commands in Termux too") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        card(body, "Linux setup instructions", "Permissions, package manager, installing GUI apps, security and limitations") { setupHelp() }
        card(body, "Set as default home", "Use the standard Android launcher-role selection dialog") { requestHomeRole() }
        line(body, 12)
        section(body, "Data & maintenance")
        rowButtons(body,
            "EXPORT SETUP" to { exportConfig() },
            "IMPORT SETUP" to { importConfig() })
        card(body, "Clear terminal log", "Delete captured command output only, without touching Linux or apps") {
            AlertDialog.Builder(this).setTitle("Clear command log?").setPositiveButton("Clear") { _, _ -> store.clearConsole() }
                .setNegativeButton("Cancel", null).show()
        }
        card(body, "About AshDock", "Version 0.1 · A native Android launcher with an optional Termux / Arch bridge. Inspired by Omarchy, not affiliated.")
    }
    private fun toggle(parent: LinearLayout, title: String, subtitle: String, checked: Boolean, callback: (Boolean) -> Unit) {
        val row = horizontal(); pad(row, 10, 13)
        val desc = vertical()
        desc.addView(text(title, 15f, palette.foreground, bold = true))
        desc.addView(text(subtitle, 12f, palette.muted))
        row.addView(desc, LinearLayout.LayoutParams(0, -2, 1f))
        val switch = Switch(this)
        switch.isChecked = checked
        switch.setOnCheckedChangeListener { _, value -> callback(value); if (title == "Visible status bar" || title == "Left-handed dock") showPage("SETUP") }
        row.addView(switch)
        parent.addView(row)
        parent.addView(View(this).also { it.setBackgroundColor(Color.rgb(40, 42, 48)) }, LinearLayout.LayoutParams(-1, dp(1)))
    }
    private fun scaleDialog() {
        val options = arrayOf("80%", "90%", "100%", "110%", "120%", "130%")
        AlertDialog.Builder(this).setTitle("Interface text scaling").setItems(options) { _, which ->
            store.textScale = 80 + which * 10; showPage("SETUP")
        }.show()
    }
    private fun customAccent() {
        val input = EditText(this); input.setSingleLine(true); input.setText(store.accentHex); pad(input)
        AlertDialog.Builder(this).setTitle("Accent colour (hex)").setView(input)
            .setPositiveButton("Apply") { _, _ ->
                val s = input.text.toString().trim()
                if (s.matches(Regex("#[0-9a-fA-F]{6}"))) { store.accentHex = s; showPage("SETUP") }
                else alert("Invalid colour", "Use a 6-digit RGB colour such as #FF8B2B.")
            }.setNegativeButton("Cancel", null).show()
    }
    private fun unhideDialog() {
        val all = installedApps().filter { store.hidden().contains(it.key) }
        if (all.isEmpty()) { alert("No hidden apps", "All Android apps are visible."); return }
        AlertDialog.Builder(this).setTitle("Unhide apps")
            .setItems(all.map { it.label }.toTypedArray()) { _, which ->
                store.toggleHidden(all[which].key); showPage("SETUP")
            }.show()
    }
    private fun setupHelp() {
        alert("Termux setup", "1. Install current Termux from F-Droid or Termux GitHub releases (not the old Play Store edition).\n\n2. Open Termux once and run:\nmkdir -p ~/.termux\nprintf 'allow-external-apps = true\\n' >> ~/.termux/termux.properties\ntermux-reload-settings\n\n3. Grant AshDock 'Run commands in Termux environment' in Android Settings > Apps > AshDock > Permissions.\n\n4. Go to Linux > Install Arch.\n\n5. Install packages from the AshDock console: install nano git htop.\n\nNote: a standalone Linux GUI needs Termux:X11. Full interactive TTY apps open Termux.")
    }
    private fun exportConfig() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "ashdock-settings.json")
        }
        startActivityForResult(intent, 120)
    }
    private fun importConfig() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
        }
        startActivityForResult(intent, 121)
    }
    @Deprecated("Using Activity result for a dependency-free Android project")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        try {
            when (requestCode) {
                120 -> contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(store.exportJson()) }
                121 -> {
                    val str = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    if (str.length > 200000) throw IllegalArgumentException("Backup is too large")
                    AlertDialog.Builder(this).setTitle("Import configuration?")
                        .setMessage("A backup can add executable Linux shortcuts. Only import files you trust. This will replace current launcher settings.")
                        .setPositiveButton("Import") { _, _ -> try { store.importJson(str); showPage("SETUP") }
                            catch (e: Exception) { alert("Import error", e.localizedMessage ?: "Invalid backup") } }
                        .setNegativeButton("Cancel", null).show()
                }
            }
        } catch (e: Exception) { alert("File error", e.localizedMessage ?: "Unable to read/write file") }
    }
}

private class GridWallpaper(context: android.content.Context, val theme: Palette) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(theme.backdrop)
        val grid = 36f * resources.displayMetrics.density
        paint.color = Color.argb(13, 180, 186, 198); paint.strokeWidth = 1f
        var x = 0f; while (x < width) { canvas.drawLine(x, 0f, x, height.toFloat(), paint); x += grid }
        var y = 0f; while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, paint); y += grid }
        val path = Path().apply {
            moveTo(width * .58f, height * .08f)
            lineTo(width * 1.12f, height * .08f)
            lineTo(width * 1.12f, height * .37f)
            lineTo(width * .84f, height * .37f)
            lineTo(width * .84f, height * .22f)
            lineTo(width * .58f, height * .22f)
            close()
        }
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f * resources.displayMetrics.density
        paint.color = (theme.accent and 0x00FFFFFF) or (38 shl 24)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }
}

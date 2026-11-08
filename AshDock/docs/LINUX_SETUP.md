# Linux setup · AshDock

AshDock **is an Android home launcher**; Linux runs as an optional **user-space process**, not as a replacement Android kernel. No root or bootloader unlock is needed.

## 1. Install trusted dependencies

Install a current Termux APK from [F-Droid](https://f-droid.org/en/packages/com.termux/) or the [official GitHub releases](https://github.com/termux/termux-app/releases). Open Termux at least once. Do not mix signing channels for Termux and its plugins.

AshDock does **not** download executable PRoot binaries into its own Android private data; it delegates to Termux's maintained PRoot package. This avoids Android 10+ W^X execution restrictions and dramatically reduces the size of the launcher APK.

## 2. Allow explicit, permissioned commands

Run in the **Termux terminal** (not in AshDock):

```sh
mkdir -p ~/.termux
printf 'allow-external-apps = true\n' >> ~/.termux/termux.properties
termux-reload-settings
```

Then grant AshDock permission in **Android Settings → Apps → AshDock → Permissions → Additional permissions → Run commands in Termux environment**. The exact wording depends on your Android version. This is an elevated cross-app privilege; grant it only if you trust AshDock. Keep Termux itself updated.

## 3. Install the genuine Arch Linux ARM aarch64 rootfs

Open **AshDock → Linux → Install Arch**. It sends a setup script to Termux that:

1. Installs Termux `proot-distro`, `curl`, `gnupg`.
2. Downloads the AArch64 root filesystem and detached signature from the [official Arch Linux ARM mirror](https://archlinuxarm.org/about/downloads).
3. Fetches the published signing key, **checks the full fingerprint `68B3537F39A313B3E574D06777193F152BDBE6A6`**, then verifies the archive signature using `gpg`.
4. Installs via `proot-distro install -n ash-arch ./ArchLinuxARM-aarch64-latest.tar.gz`.
5. Keeps the rootfs under Termux's private app data. Does not overwrite an existing `ash-arch` container.

**Storage:** Leave several gigabytes free; compressed download size and installed package data change over time. Use reliable Wi-Fi. The setup may take a long time. Android may terminate long-lived background processes on some devices; if that happens, launch Termux to rerun or diagnose installation.

The key server is an external availability dependency. A failed key download or failed signature check **stops installation** rather than silently bypassing verification. Never disable signature verification to finish a failed install.

If the PRoot login produces an error, open **Full Terminal** and inspect it. As usual, Arch update commands may require initial keyring setup:

```sh
proot-distro login --shared-tmp ash-arch
pacman-key --init
pacman-key --populate archlinuxarm
pacman -Syu
```

## 4. Install Linux commands and add them to the menu

Use the AshDock in-launcher command console:

```text
install nano git htop
arch cat /etc/os-release
arch pacman -Q
```

Tap **ADD APP** and enter, for example, `Neovim` as a label and `nvim` as an Arch command. It now appears in the Linux app menu and, if enabled, on the home screen. Or tap **SCAN .DESKTOP** to import installed graphical `.desktop` shortcuts.

**Important:** AshDock's in-launcher console runs one-shot shell commands and shows captured output; it is **not** a full VT100/PTY terminal emulator. `vim`, `nano`, `htop`, TUI menus, interactive `pacman` prompts, and curses programs should run through **FULL TERMINAL**, which opens the interactive session in Termux. This distinction is deliberate and avoids pretending an output console implements terminal control sequences.

## 5. Optional graphical applications

Linux graphical windows require an X server separate from PRoot. Use the [official Termux:X11 project](https://github.com/termux/termux-x11) and install both its Android app and its companion Termux package:

```sh
# In Termux host, not Arch:
pkg install x11-repo
pkg install termux-x11-nightly
termux-x11 :0 &
```

Then in Termux:

```sh
proot-distro login --shared-tmp ash-arch
export DISPLAY=:0
pacman -Syu
pacman -S --needed gtk3 xorg-xeyes
xeyes
```

The display should appear in the **Termux:X11 app**. Alternatively, use **AshDock → Linux → START X11**, which starts display `:0` when the companion is installed. GUI shortcuts use `DISPLAY=:0`; the display number must match.

On devices with non-Qualcomm GPUs, accelerated Linux OpenGL/Vulkan is not automatically available. AshDock does not promise hardware acceleration, full Wayland/Hyprland, desktop-style window management of Android apps, or native window embedding. A graphical app may fail because of missing dependencies or X11, not because the launcher is broken.

## 6. Recover and maintain

- Open **Linux → CHECK STATUS** for the container list.
- Enter `arch pacman -Q` to list packages.
- Run `arch pacman -Syu` for upgrades.
- Use **Settings → EXPORT SETUP** to save your shortcuts and preferences. **This does not back up the Arch filesystem.**
- For rootfs backup, use the maintained `proot-distro backup`/`restore` commands in Termux. Check supported syntax using `proot-distro backup --help`.
- To remove Linux, follow `proot-distro remove ash-arch` in Termux, **after** backing up your data. Uninstalling AshDock alone does **not** remove Termux's Linux filesystem.

## 7. Security notes

AshDock neither requires root nor scans private files without user action. The Linux terminal executes exactly the command you type, including destructive shell commands. Imported backups may contain executable Linux shortcuts: import only files you trust. The installer downloads from official sources and verifies a detached signature. Termux RUN_COMMAND permission grants AshDock the ability to execute code in your Termux environment; revoke that permission at any time in Android settings to disable the bridge.

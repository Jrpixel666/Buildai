package dev.ashdock.launcher

object LinuxCommands {
    const val NAME = "ash-arch"
    const val ARCHIVE = "ArchLinuxARM-aarch64-latest.tar.gz"
    const val ROOTFS_URL = "https://os.archlinuxarm.org/os/$ARCHIVE"
    const val FINGERPRINT = "68B3537F39A313B3E574D06777193F152BDBE6A6"

    // POSIX-safe quoting for user-controlled labels, package names, and commands.
    fun quote(input: String): String = "'" + input.replace("'", "'\\''") + "'"
    fun login(cmd: String): String = "proot-distro login --shared-tmp $NAME -- /bin/bash -lc ${quote(cmd)}"
    fun shellCommand(cmd: String): String = login(cmd)
    fun packageCommand(input: String): String {
        val pkgs = input.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        require(pkgs.isNotEmpty() && pkgs.size <= 15) { "Choose 1 to 15 packages" }
        require(pkgs.all { it.matches(Regex("[A-Za-z0-9][A-Za-z0-9+_.@-]{0,99}")) }) { "Invalid package name" }
        return login("pacman -S --needed --noconfirm ${pkgs.joinToString(" ")}")
    }
    // Installer is deliberately non-destructive; existing installation is retained.
    fun installer(): String = """
        set -eu
        if ! command -v pkg >/dev/null 2>&1; then echo 'ERROR: Run in Termux.'; exit 1; fi
        pkg install -y proot-distro curl gnupg
        if proot-distro list --quiet 2>/dev/null | grep -qx 'ash-arch'; then
          echo 'An ash-arch installation may already exist. No overwrite performed.'
          exit 0
        fi
        cd "${'$'}HOME"
        mkdir -p "${'$'}HOME/.ashdock-gpg"
        chmod 700 "${'$'}HOME/.ashdock-gpg"
        curl --fail --location --retry 3 --continue-at - --output "$ARCHIVE" "$ROOTFS_URL"
        curl --fail --location --retry 3 --output "$ARCHIVE.sig" "$ROOTFS_URL.sig"
        gpg --batch --homedir "${'$'}HOME/.ashdock-gpg" --keyserver hkps://keyserver.ubuntu.com --recv-keys "$FINGERPRINT"
        actual="${'$'}(gpg --homedir "${'$'}HOME/.ashdock-gpg" --with-colons --fingerprint "$FINGERPRINT" | awk -F: '${'$'}1=="fpr" {print ${'$'}10; exit}')"
        [ "${'$'}actual" = "$FINGERPRINT" ] || { echo 'Signing key fingerprint mismatch'; exit 1; }
        gpg --homedir "${'$'}HOME/.ashdock-gpg" --verify "$ARCHIVE.sig" "$ARCHIVE"
        proot-distro install -n $NAME "./$ARCHIVE"
        proot-distro login $NAME -- /bin/sh -c 'echo "nameserver 1.1.1.1" > /etc/resolv.conf 2>/dev/null || true'
        echo 'ASHDOCK_ARCH_INSTALLED'
        echo 'Arch Linux ARM is installed. Run pacman -Syu in the console.'
    """.trimIndent()

    fun detection(): String = """
        if command -v proot-distro >/dev/null 2>&1; then
          proot-distro list
        else
          echo 'PRoot-Distro is not installed. Use Setup Arch.'
        fi
    """.trimIndent()

    // TSV output: desktop-id, name. No arbitrary commands are executed during discovery.
    fun discoverDesktopApps(): String = login("""
      for f in /usr/share/applications/*.desktop /root/.local/share/applications/*.desktop; do
        [ -f "${'$'}f" ] || continue
        grep -qi '^NoDisplay=true' "${'$'}f" && continue
        id=${'$'}{f##*/}; id=${'$'}{id%.desktop}
        title=${'$'}(grep -m1 '^Name=' "${'$'}f" | cut -d= -f2- | tr '\t\r\n' '   ')
        [ -n "${'$'}title" ] && printf '%s\t%s\n' "${'$'}id" "${'$'}title"
      done
    """.trimIndent())
    fun guiLaunch(desktopId: String): String {
        require(desktopId.matches(Regex("[A-Za-z0-9._+-]+")))
        return login("export DISPLAY=:0; gtk-launch ${quote(desktopId)}")
    }
    fun quickHelp() = """
        ASHDOCK • COMMAND REFERENCE
        help                       show this guide
        arch                       check Arch installation
        arch <command>             run command inside Arch
        install <pkg>              pacman -S package(s)
        update                     pacman -Syu
        add                        create a Linux menu shortcut
        discover                   scan .desktop files
        clear                      clear console output
        termux <command>           execute in Termux shell

        Examples:
          install htop nano git
          arch uname -a
          arch pacman -Q
          arch htop      (interactive commands need full Termux)

        App commands are one-shot with captured output.
        For interactive TTY applications, choose Open full terminal.
    """.trimIndent()
}

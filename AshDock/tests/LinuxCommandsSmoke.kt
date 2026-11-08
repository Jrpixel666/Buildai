import dev.ashdock.launcher.LinuxCommands
import java.io.File

fun main() {
    require(LinuxCommands.login("printf hello").contains("--shared-tmp"))
    require(LinuxCommands.installer().contains("gpg --homedir"))
    require(LinuxCommands.installer().contains("--verify"))
    require(LinuxCommands.installer().contains("proot-distro install -n ash-arch"))
    require(LinuxCommands.packageCommand("git nano").contains("git nano"))
    require(LinuxCommands.quote("x' && echo HACKED #") == "'x'\\'' && echo HACKED #'" )
    for (bad in listOf("nano;rm -rf /", "foo && bar", "foo/../bar")) {
        try { LinuxCommands.packageCommand(bad); error("Unsafe package allowed: $bad") }
        catch (_: IllegalArgumentException) { /* expected */ }
    }
    File(System.getProperty("java.io.tmpdir"), "ashdock-installer-smoke.sh")
        .writeText(LinuxCommands.installer())
    println("PASS: command quoting, package-name filter, signed installer template")
}

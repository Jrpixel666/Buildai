#!/usr/bin/env bash
set -eu
if ! command -v pkg >/dev/null 2>&1; then echo 'ERROR: Run in Termux.'; exit 1; fi
pkg install -y proot-distro curl gnupg
if proot-distro list --quiet 2>/dev/null | grep -qx 'ash-arch'; then
  echo 'An ash-arch installation may already exist. No overwrite performed.'
  exit 0
fi
cd "$HOME"
mkdir -p "$HOME/.ashdock-gpg"
chmod 700 "$HOME/.ashdock-gpg"
curl --fail --location --retry 3 --continue-at - --output "ArchLinuxARM-aarch64-latest.tar.gz" "https://os.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz"
curl --fail --location --retry 3 --output "ArchLinuxARM-aarch64-latest.tar.gz.sig" "https://os.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz.sig"
gpg --batch --homedir "$HOME/.ashdock-gpg" --keyserver hkps://keyserver.ubuntu.com --recv-keys "68B3537F39A313B3E574D06777193F152BDBE6A6"
actual="$(gpg --homedir "$HOME/.ashdock-gpg" --with-colons --fingerprint "68B3537F39A313B3E574D06777193F152BDBE6A6" | awk -F: '$1=="fpr" {print $10; exit}')"
[ "$actual" = "68B3537F39A313B3E574D06777193F152BDBE6A6" ] || { echo 'Signing key fingerprint mismatch'; exit 1; }
gpg --batch --homedir "$HOME/.ashdock-gpg" --verify "ArchLinuxARM-aarch64-latest.tar.gz.sig" "ArchLinuxARM-aarch64-latest.tar.gz"
proot-distro install -n ash-arch "./ArchLinuxARM-aarch64-latest.tar.gz"
proot-distro login ash-arch -- /bin/sh -c 'echo "nameserver 1.1.1.1" > /etc/resolv.conf 2>/dev/null || true'
echo 'ASHDOCK_ARCH_INSTALLED'
echo 'Arch Linux ARM is installed. Run pacman -Syu in the console.'
#!/bin/sh
# Packages build/linux/package (the jar from linuxAppJar and these scripts) as a .deb on a Linux machine.
# Usage: package-deb.sh <package directory> <version> <output directory>
set -eu
input="$1"; version="$2"; output="$3"
resources=$(mktemp -d)
cp "$input/prerm" "$resources/prerm"
cp "$input/postinst" "$resources/postinst"
chmod 0755 "$input/posato-helper-setup"
jpackage --type deb --name posato --app-version "$version" \
  --input "$input" --main-jar posato.jar --main-class app.posato.desktop.linux.LinuxMainKt \
  --java-options -XX:+DisableAttachMechanism \
  --jlink-options "--strip-debug --no-man-pages --no-header-files" \
  --add-modules java.base,java.desktop,java.sql,java.naming,java.logging,java.management,jdk.net,jdk.unsupported,jdk.accessibility,java.net.http,jdk.crypto.ec \
  --linux-package-name posato --linux-app-category utils --linux-shortcut --linux-menu-group Utility \
  --description "Pause the websites and apps you choose." --vendor "Posato" \
  --resource-dir "$resources" --dest "$output"
rm -rf "$resources"

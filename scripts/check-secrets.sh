#!/usr/bin/env bash
set -euo pipefail
version=8.30.1
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
curl --fail --silent --show-error --location --retry 3 "https://github.com/gitleaks/gitleaks/releases/download/v$version/gitleaks_${version}_linux_x64.tar.gz" -o "$work/scanner.tar.gz"
printf '%s  %s\n' "$(cat release/gitleaks-sha256.txt)" "$work/scanner.tar.gz" | sha256sum --check --status
tar -xzf "$work/scanner.tar.gz" -C "$work" gitleaks
"$work/gitleaks" git --redact --no-banner --log-opts=--all .
# Scan only publishable source, not ignored private signing recovery files.
mkdir "$work/source"
git ls-files -z | tar --null -T - -cf - | tar -xf - -C "$work/source"
"$work/gitleaks" dir --redact --no-banner "$work/source"

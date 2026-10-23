#!/bin/sh
# Installs the android CLI into $HOME/.local/bin and puts it on the job's PATH.
#
# The installer downloads a large archive, and a hosted runner drops that
# transfer now and then (curl 92, "HTTP/2 stream was not closed cleanly")
# before a single test has run. The whole install is retried a few times,
# and the installer is fetched to a file first so a failed fetch cannot pass
# as an empty script piped into bash.
set -u
url="${ANDROID_CLI_INSTALLER_URL:-https://dl.google.com/android/cli/latest/linux_x86_64/install.sh}"
attempts="${ANDROID_CLI_INSTALL_ATTEMPTS:-3}"
installer="$(mktemp)"
trap 'rm -f "$installer"' EXIT

i=1
while :; do
  if curl -fsSL "$url" -o "$installer" && bash "$installer"; then
    echo "$HOME/.local/bin" >> "${GITHUB_PATH:-/dev/null}"
    exit 0
  fi
  if [ "$i" -ge "$attempts" ]; then
    echo "ERROR: android CLI install failed after $attempts attempts"
    exit 1
  fi
  echo "android CLI install attempt $i failed, retrying in 15 s"
  i=$((i + 1))
  sleep 15
done

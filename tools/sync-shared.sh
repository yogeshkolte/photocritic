#!/bin/sh
# Copies shared/prompts into the iOS Core package, which can only bundle
# resources inside its own folder (design file 11). CI fails if the copy differs.
set -e
root=$(cd "$(dirname "$0")/.." && pwd)
dest="$root/ios/Core/Sources/PhotoCriticCore/Resources/prompts"
rm -rf "$dest"
mkdir -p "$dest"
cp "$root"/shared/prompts/* "$dest/"
echo "Copied shared/prompts to ${dest#$root/}"

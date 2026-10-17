#!/usr/bin/env bash
# Downloads the Xray core Android library (libv2ray.aar)
# Run this before building locally: ./scripts/fetch-core.sh
set -e

VERSION="v26.10.10"
mkdir -p app/libs
if [ -f app/libs/libv2ray.aar ] && [ "$(stat -c%s app/libs/libv2ray.aar)" -gt 1000000 ]; then
    echo "libv2ray.aar already exists, skipping download."
    exit 0
fi
echo "Downloading libv2ray.aar ${VERSION} from AndroidLibXrayLite..."
curl -fSL --retry 3 -o app/libs/libv2ray.aar \
    "https://github.com/2dust/AndroidLibXrayLite/releases/download/${VERSION}/libv2ray.aar"
echo "Done: $(du -h app/libs/libv2ray.aar | cut -f1) -> app/libs/libv2ray.aar"

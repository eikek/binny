#!/usr/bin/env bash

set -e

docker run \
  -p 9000:9001 \
  -p 9001:9002 \
  -e "MINIO_ROOT_USER=root" \
  -e "MINIO_ROOT_PASSWORD=d2Fscm/f" \
  quay.io/minio/minio server /data --console-address ":9002"

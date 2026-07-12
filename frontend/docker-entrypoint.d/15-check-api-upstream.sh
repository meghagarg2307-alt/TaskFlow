#!/bin/sh
# Fail fast if reverse-proxy upstream env is missing (Compose + Render).
# Runs before 20-envsubst-on-templates.sh in the official nginx entrypoint.
set -eu

ok=1

if [ -z "${API_UPSTREAM:-}" ]; then
  echo "error: required environment variable API_UPSTREAM is not set" >&2
  ok=0
fi
if [ -z "${API_HOST:-}" ]; then
  echo "error: required environment variable API_HOST is not set" >&2
  ok=0
fi
if [ -z "${NGINX_RESOLVER:-}" ]; then
  echo "error: required environment variable NGINX_RESOLVER is not set" >&2
  ok=0
fi

if [ "$ok" -ne 1 ]; then
  echo "hint: local Compose sets these on the web service;" >&2
  echo "      on Render set API_UPSTREAM / API_HOST / NGINX_RESOLVER" >&2
  exit 1
fi

echo "nginx upstream: API_UPSTREAM=${API_UPSTREAM} API_HOST=${API_HOST} NGINX_RESOLVER=${NGINX_RESOLVER}"

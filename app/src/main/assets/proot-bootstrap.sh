#!/system/bin/sh
# Lightweight stub daemon for PRoot bootstrap and lab verification
# Starts HTTP listener on 127.0.0.1:8787 returning health & status

PORT=8787
while true; do
  printf 'HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nConnection: close\r\n\r\n<!DOCTYPE html><html><head><title>Hermes Agent</title></head><body><h1>Hermes Agent Running</h1><p>Status: OK</p></body></html>\r\n' | nc -l -p $PORT 2>/dev/null || (
    # fallback busybox / toybox nc format
    printf 'HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nConnection: close\r\n\r\n<!DOCTYPE html><html><head><title>Hermes Agent</title></head><body><h1>Hermes Agent Running</h1><p>Status: OK</p></body></html>\r\n' | nc -l 127.0.0.1 $PORT 2>/dev/null
  ) || sleep 1
done

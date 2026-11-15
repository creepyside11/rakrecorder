#!/system/bin/sh
{ printf 'GET / HTTP/1.0\r\n\r\n'; sleep 2; } | nc -w 5 10.0.2.2 8765 2>&1

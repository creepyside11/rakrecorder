#!/system/bin/sh
{ printf 'rakrecorder-test'; sleep 2; } | nc -u -w 5 10.0.2.2 8766 2>&1

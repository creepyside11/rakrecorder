#!/system/bin/sh
{ printf 'rakrecorder-test'; sleep 2; } | timeout 8 nc -u -w 5 10.0.2.2 8766 2>&1

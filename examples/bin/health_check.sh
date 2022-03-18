#! /bin/bash
set -e

curl -f -s http://localhost:9090/status/health | grep -q "true"

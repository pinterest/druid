#! /bin/bash

# A monitor script to wait for changes to a managed data file containing a list mysql replica sets
# and write out the connection URI for the current master of the desired replica set to a file.
# Usage: ./monitor_mysql_master.sh OUTFILE ZKFILE

outfile=$1
zkfile=$2
uri=""
last_valid=""

validate_uri() {
    # Note that this only matches quad dotted IPv4 addresses, and does not validate all octects are  < 255
    [[ "$1" =~ ^jdbc:mysql://([0-9]{1,3}\.){3}[0-9]{1,3}:[0-9]{1,5}$ ]] && return 0 || return 1
}

# Read $OUTFILE if it exists
if [ -e $outfile ]; then
    uri=$(<$outfile)
fi

if validate_uri "$uri"; then
    last_valid=$uri
fi

# Construct the URI for replica set TELETRAAN_REPLICA_SET (REPLICA_SET script config variable in teletraan)
uri=$(cat $zkfile | jq ".${TELETRAAN_REPLICA_SET}.instances | map(select(.is_master)) | .[0] \
| \"jdbc:mysql://\"+.ip_address+\":\"+(.port|tostring)" | tr -d '"')

echo "URI: $uri"

if validate_uri "$uri"; then
    last_valid=$uri
fi

if ! validate_uri "$last_valid"; then
    echo "No valid URI found!"
    exit 1
fi

echo "${last_valid}" > $outfile

# TODO: Make this less brittle
# Set up watcher to pass changes to the container
inotifywait -qm $zkfile -e MOVED_TO --format '%f' \
| while read file; do
    uri=$(cat $file \
    | jq ".${TELETRAAN_REPLICA_SET}.instances | map(select(.is_master)) | .[0] \
    | \"jdbc:mysql://\"+.ip_address+\":\"+(.port|tostring)" | tr -d '"')
    if validate_uri "$uri"; then
        echo "$uri" > $outfile
    fi
done
#!/bin/bash

# Load segments into page cache starting from latest date until a specific min start date is reached or (memory used + cached) reaches a thresold percent
# Usage:
# ssh devapp
# fh -u
# fh -e 'sudo docker exec druid /bin/sh -c "SEGMENT_FOLDER_PREFIX='/mnt/druid/segment-cache' DATA_SOURCE='partner_metrics' MIN_START_DATE=2018-01-01 MEM_USAGE_THRESHOLD=95 bash /opt/druid/bin/load_segments_to_page_cache.sh"' druid-storage-insights-data

SEGMENT_FOLDER=$SEGMENT_FOLDER_PREFIX/$DATA_SOURCE
INTERVALS=($SEGMENT_FOLDER/*)
# Load segments in reverse chronological order
for ((i = ${#INTERVALS[@]} - 1;i >= 0;i--));
do
  interval=${INTERVALS[i]}
  echo "Processing interval: $interval"
  interval_start_date=`basename $interval`

  if [[ $MIN_START_DATE > $interval_start_date ]]
  then
    echo "Skip interval before $MIN_START_DATE: $interval"
    continue
  fi

  # pick latest version of the interval
  latest_version=""
  for version in $interval/*
  do
    if [ "$latest_version" = "" ] || [ `basename $version` >  `basename $latest_version` ]
    then
      latest_version=$version
    fi
  done

  # Load segment files into page cache
  for partition in $latest_version/*
  do
     for segment_file in $partition/*
     do
       echo "Load $segment_file"
       cat $segment_file > /dev/null
     done
  done

  mem_usage=`awk '/^Mem/ {printf("%u", 100*$3/$2);}' <(free -m)`
  if [[ mem_usage -gt $MEM_USAGE_THRESHOLD ]]
  then
    echo "Skip loading remaining segments because current memory usage is above $MEM_USAGE_THRESHOLD"
    break
  fi
done

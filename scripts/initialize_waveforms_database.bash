#!/bin/bash

COOKIE_JAR=`mktemp --suffix=-waveforms`
data_dir=/usr/opsdata/waveforms/data/rf/
server="waveformtest.acc.jlab.org"
login_query="/wfbrowser/login"
event_query="/wfbrowser/ajax/event"
login_url="https://${server}${login_query}"
event_url="https://${server}${event_query}"

curl -c $COOKIE_JAR -X POST -d username=waveformstest -d password='abAB12!@' -d requester='login' "$login_url"

#for location in 1L22 1L23 1L24 1L25 1L26
for location in 0L04
do
    for date in $(ls $data_dir/$location)
    do
        datef=$(echo $date | tr '_' '-')
        for time in $(ls $data_dir/$location/$date)
        do
            timef=$(echo $time | perl -ne 'while(m/(\d\d)(\d\d)(\d\d)(.\d)/g) { print "$1:$2:$3$4";}')
            event_time_utc=$(date -d @"$( expr 3600 \* 4 + $(date -d "$datef $timef" +%s) )" +"%F %T")$(echo $timef | cut -c9-10)
            echo -n "start POST "
            date
            curl -b $COOKIE_JAR -X POST -d datetime="$datef $timef" -d location="$location" -d system="rf" $event_url
            echo
            echo -n "end POST "
            date
        done
    done
done
rm -f $COOKIE_JAR

# Now pipe this to a mysql command
#echo $sql

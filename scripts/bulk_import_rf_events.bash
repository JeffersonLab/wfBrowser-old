#!/bin/bash

# Command Definitions (same on RHEL6 and RHEL7)
CURL='/usr/csite/pubtools/bin/curl'
GREP='/bin/grep'
MAILX='/bin/mailx'
MKTEMP='/bin/mktemp'
DATE='/bin/date'
CUT='/bin/cut'
PERL='/usr/csite/pubtools/bin/perl'
BC='/bin/bc'

COOKIE_JAR=`$MKTEMP --suffix=-waveforms`

data_dir=/usr/opsdata/waveforms/data/
config_file="./curl.cfg"
SERVER="waveformstest.acc.jlab.org"
SYSTEM_LIST='rf'

usage () {
    cat - <<EOF
Usage: $0 [-h] [-b <begin_time>] [-e <end_time>] [-H <server>[:port]] [-d]
-h                 Show this help message
-b <begin_time>    Earliest event time to import
-e <end_time>      Latest event time to import
-H <server[:port]> Upload server target
-d                 Don't try to authenticate to server
                   (This is useful for direct test server
                   uploads, e.g., glassfish)

Defaults are -b 1970-01-01 -e 2100-01-01 -H waveformstest.acc.jlab.org
EOF
}

# Converts a date/time from the local timezone (at the time of the timestampe) to UTC
# Expects two arguments, a well formated date (YYYY-mm-dd) and a well formated time (HH:MM:SS.S)
get_event_time_utc () {
    datef=$1
    timef=$2

    frac=$(echo "$($DATE -d "$datef $timef" +%N) / 1000000000" | $BC -l | $CUT -c2)

    # bc will return 0, not .0 and so frac will be empty
    if [ -z "$frac" ] ; then
        frac="0"
    fi

    timestamp=$($DATE -d "$datef $timef" +%s)
    echo $($DATE -d @"$timestamp" -u +"%F %T")".$frac"
}

#-----------------------------------------------------------------------
# Process Options
#-----------------------------------------------------------------------
# Use a very wide range for default timestamp filtering.  Should block anything
BEGIN=0        # 1970-01-01 UTC in unix time (1969-12-31 19:00:00 EST)
END=4102462800 # 2100-01-01 UTC give or take in Unix time 
AUTH=1
GROUPED=true
while getopts ":b:e:H:hd:" opt; do
    case $opt in
        h) usage
           exit 0
           ;;
        b) BEGIN=$($DATE -d "$OPTARG" +%s)
           ;;
        e) END=$($DATE -d "$OPTARG" +%s)
           ;;
        H) SERVER="$OPTARG"
           ;;
        d) AUTH=0
           ;;
       \?) echo "Unknown Option: $opt"
           usage
           exit 1
           ;;
        *) echo "Unknown Option: $opt"
           usage
           exit 1
           ;;
    esac
done

# The expression $(($OPTIND - 1)) is an arithmetic expression equal to $OPTIND minus 1.
# This value is used as the argument to shift. The result is that the correct number of
# arguments are shifted out of the way, leaving the real arguments as $1, $2, etc.
shift $(($OPTIND - 1))

if [ $# -ne 0 ] ; then
    usage
    exit 1
fi

login_url="https://${SERVER}/wfbrowser/login"
event_url="https://${SERVER}/wfbrowser/ajax/event"

if [ $AUTH == 1 ] ; then
    #curl -c $COOKIE_JAR -X POST -d username=waveformstest -d password='abAB12!@' -d requester='login' "$login_url"
    $CURL  -s -c $COOKIE_JAR -L -K $config_file "$login_url" > /dev/null
    exit_val=$?
    if [ $exit_val -ne 0 ] ; then
        echo "Error authenticating to $server.  Received error code $exit_val.  Exiting"
        exit 1
    else
        echo "Successfully authenticated to $SERVER"
    fi
else
    echo "Skipping authentication"
fi


for system in $SYSTEM_LIST
do
    for location in $(ls "$data_dir/$system")
    do
        for date in $(ls "$data_dir/$system/$location")
        do
            if [ "$date" == "older" ] ; then
                echo "Skipping folder $data_dir/$system/$location/$date"
                continue
            fi
            datef=$(echo $date | tr '_' '-')
            event_date=$($DATE -d "$datef" +%s)
            if [ $BEGIN -gt $event_date -o $END -lt $event_date ] ; then
                echo Skipping event folder $system/$location/$date
                continue
            fi
           
            for time in $(ls "$data_dir/$system/$location/$date")
            do
                echo "$data_dir/$system/$location/$date/$time"
                if [ ! -d "$data_dir/$system/$location/$date/$time" ] ; then
                    echo Skipping "$data_dir/$system/$location/$date/$time".  Not a directory
                    continue
                fi
                timef=$(echo $time | $PERL -ne 'while(m/(\d\d)(\d\d)(\d\d)(.\d)/g) { print "$1:$2:$3$4";}')

                # Filter out events that are not in our time range
                event_time=$($DATE -d "$datef $timef" +%s)  # Unix timestamp for comparison
                if [ "$BEGIN" -lt "$event_time" -a "$END" -gt "$event_time" ] ; then
                    
                    echo "POSTing $system/$location/$date/$time"

                    # Run the actual command - NOTE: grouped may need to be changed based on the system
                    $CURL -b $COOKIE_JAR -X POST \
                      -d datetime="$datef $timef" \
                      -d location="$location" \
                      -d system="$system" \
                      -d classification="" \
                      -d grouped="$GROUPED" \
                      $event_url

                    echo
                else
                    echo Skipping event $system/$location/$date/$time
                fi
            done
        done
    done
done
rm -f $COOKIE_JAR


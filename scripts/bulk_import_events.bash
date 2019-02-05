#!/bin/bash

# Command Definitions (same on RHEL6 and RHEL7)
CURL='/usr/csite/pubtools/bin/curl'
GREP='/bin/grep'
MAILX='/bin/mailx'
MKTEMP='/bin/mktemp'
DATE='/bin/date'

COOKIE_JAR=`$MKTEMP --suffix=-waveforms`

data_dir=/usr/opsdata/waveforms/data/
config_file="./curl.cfg"
SERVER="waveformstest.acc.jlab.org"
SYSTEM_LIST='rf'

usage () {
echo "Requires at least one argument, defaults are -b 1970-01-01 -e 2100-01-01 -H waveformstest.acc.jlab.org -s rf"
echo "Usage: $0 [-h] [-b <begin_time>] [-e <end_time>] [-H <server>[:port]] [-s <system>[ system2 ...]]"
}

#-----------------------------------------------------------------------
# Process Options
#-----------------------------------------------------------------------
if [ $# -eq 0 ] ; then
    usage
    exit 1
fi
# Use a very wide range for default timestamp filtering.  Should block anything
BEGIN=0        # 1970-01-01 UTC in unix time (1969-12-31 19:00:00 EST)
END=4102462800 # 2100-01-01 UTC give or take in Unix time 
while getopts ":b:e:H:s:h" opt; do
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
        s) SYSTEM_LIST="$OPTARG"
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

#login_url="https://${SERVER}/wfbrowser/login"
event_url="https://${SERVER}/wfbrowser/ajax/event"


#curl -c $COOKIE_JAR -X POST -d username=waveformstest -d password='abAB12!@' -d requester='login' "$login_url"
$CURL  -s -c $COOKIE_JAR -L -K $config_file "$login_url" -O > /dev/null
exit_val=$?
if [ $exit_val -ne 0 ] ; then
    echo "Error authenticating to $server.  Received error code $exit_val.  Exiting"
    exit 1
else
    echo "Successfully authenticated to $server"
fi

for system in $SYSTEM_LIST
do
    for location in $(ls $data_dir/$system)
    do
        for date in $(ls $data_dir/$system/$location)
        do
            if [ "$date" == "older" ] ; then
                echo "Skipping folder $data_dir/$system/$location/$date"
                continue
            fi
            datef=$(echo $date | tr '_' '-')
            event_date=$($DATE -d "$datef" +%s)
            if [ $BEGIN -gt $event_date -o $END -lt $event_date ] ; then
                echo Skipping $system/$location/$date
                continue
            fi
           
            for time in $(ls $data_dir/$system/$location/$date)
            do
                timef=$(echo $time | perl -ne 'while(m/(\d\d)(\d\d)(\d\d)(.\d)/g) { print "$1:$2:$3$4";}')

                # Filter out events that are not in our time range
                event_time=$($DATE -d "$datef $timef" +%s)  # Unix timestamp for comparison
                if [ "$BEGIN" -lt "$event_time" -a "$END" -gt "$event_time" ] ; then
                    event_time_utc=$(date -d @"$( expr 3600 \* 4 + $(date -d "$datef $timef" +%s) )" +"%F %T")\$(echo $timef | cut -c9-10)
                    echo -n "start POST "
                    echo $datef $timef

                    # Run the actual command - NOTE: grouped may need to be changed based on the system
                    $CURL -b $COOKIE_JAR -X POST \
                      -d datetime="$datef $timef" \
                      -d location="$location" \
                      -d system="$system" \
                      -d classification="" \
                      -d grouped="true" \
                      $event_url

                    echo
                    echo -n "end POST "
                else
                    echo Skipping $system/$location/$date/$time
                fi
            done
        done
    done
done
rm -f $COOKIE_JAR


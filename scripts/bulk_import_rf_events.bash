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
JQ='/bin/jq'

#data_dir=/usr/opsdata/waveforms/data/
data_dir=/data/waveforms/data/
curl_config="./curl.cfg"
SYSTEM_LIST='rf'

##################################
# Server configuration blocks.
##################################
# SERVER - what we post events to
# KEYlCOAK_SERVER - the keycloak server we are authenticating against (often a reverse proxy)
# CLIENT_ID - keycloak client that we are authenticating against

# PRODUCTION
#SERVER="accweb.acc.jlab.org"
#KEYCLOAK_SERVER="accweb.acc.jlab.org"
#CLIENT_ID="accweb-auth-util"

# TESTING
SERVER="accwebtest.acc.jlab.org"
KEYCLOAK_SERVER="accwebtest.acc.jlab.org"
CLIENT_ID="accwebtest-auth-util"

# DEVELOPMENT
#SERVER="sftadamc2.acc.jlab.org:8181"
#KEYCLOAK_SERVER="accwebtest.acc.jlab.org"
#CLIENT_ID="accwebtest-auth-util"
##################################

usage () {
    cat - <<EOF
Usage: $0 [-h] [-b <begin_time>] [-e <end_time>] [-H <server>[:port]] [-d]
-h                 Show this help message
-b <begin_time>    Earliest event time to import
-e <end_time>      Latest event time to import
-H <server[:port]> Upload server target

Defaults are -b 1970-01-01 -e 2100-01-01 -H waveformstest.acc.jlab.org
EOF
}


# Authenticate against a keycloak server and get the needed auth TOKEN for
# web access.
# client_id - The KeyCloak id of the service against which we are going to authenticate.
#             This client must allow public authentication, like accweb-auth-util does.
# keycloak_host - the hostname of the KeyCloak server
#
# Returns the authentation "bearer" token.
# Note: Set the following header to use it - "Authorization: bearer <TOKEN>"
do_keycloak_auth () {
    client_id="$1"
    keycloak_host="$2"
    realm="jlab"
    url="https://${keycloak_host}/auth/realms/${realm}/protocol/openid-connect/token"

    # curl_config should contain username and password as -d options
    result=$($CURL -s -d "grant_type=password" -d "client_id=${client_id}" -K ${curl_config} ${url} 2>&1)
    exit_val=$?

    # A problem making the request will give non-zero exit_val
    if [ "$exit_val" != "0" ] ; then
        echo $result
        return $exit_val
    fi

    echo $result >> /tmp/results.txt
    # A failed auth attempt will return exit_val == 0, but have a null access_token.  Instead you get
    # error and error_description fields.  Print the response and return 1 for caller to handle.
    token=$(echo $result | $JQ -r '.access_token')
    if [ "null" == "$token" ] ; then
        echo $result 
        return 1
    fi

    echo $token
    return $exit_val
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
GROUPED=true
while getopts ":b:e:H:h:" opt; do
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

event_url="https://${SERVER}/wfbrowser/ajax/event"

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
           
            # Get an auth token - these seem to expire pretty quickly
            # (Like every 5 minutes maybe?).  Just keep reauthing ...
            token=$(do_keycloak_auth $CLIENT_ID $KEYCLOAK_SERVER)
            exit_val=$?
            if [ "$exit_val" -ne 0 ] ; then
                echo "Error authenticating - $token"
                exit $exit_val
            fi

            for time in $(ls "$data_dir/$system/$location/$date")
            do
                echo "$data_dir/$system/$location/$date/$time"
                if [ ! -d "$data_dir/$system/$location/$date/$time" ] ; then
                    echo Skipping "$data_dir/$system/$location/$date/$time".  Not a directory
                    continue
                fi
                timef=$(echo $time | $PERL -ne 'while(m/(\d\d)(\d\d)(\d\d)(.\d)/g) { print "$1:$2:$3$4";}')
                result=$(rf_classifier analyze -o json "$data_dir/$system/$location/$date/$time")
                exit_val=$?
                error=$(echo $result | $JQ '.data[0].error')
                labels=""
                if [ $exit_val -ne 0 ] ; then
                    echo "rf_classifier error - $result"        
                elif [ "$error" != "null" ] ; then
                    echo "rf_classifier invalid data - $error"
                else
                    model=$(echo $result | $JQ '.data[0]["model"]')
                    cVal=$(echo $result | $JQ '.data[0]["cavity-label"]')
                    cConf=$(echo $result | $JQ '.data[0]["cavity-confidence"]')
                    fVal=$(echo $result | $JQ '.data[0]["fault-label"]')
                    fConf=$(echo $result | $JQ '.data[0]["fault-confidence"]')

                    # curl will collapse -d's with same parameter name into a single parameter.  Use & to avoid
                    labels="-d label={\"model-name\":$model,\"name\":\"cavity\",\"value\":$cVal,\"confidence\":$cConf}&label={\"model-name\":$model,\"name\":\"fault-type\",\"value\":$fVal,\"confidence\":$fConf}"
                fi

                # Filter out events that are not in our time range
                event_time=$($DATE -d "$datef $timef" +%s)  # Unix timestamp for comparison
                if [ "$BEGIN" -lt "$event_time" -a "$END" -gt "$event_time" ] ; then
                    
                    echo "POSTing $system/$location/$date/$time"
                    if [ -n "$labels" ] ; then
                        echo "==================================="
                        echo "$labels"
                        echo "==================================="
                    fi

                    # Run the actual command - NOTE: grouped may need to be changed based on the system
                    # labels should include their own -d
                    $CURL -X POST \
                      -H "Authorization: bearer $token" \
                      -d datetime="$datef $timef" \
                      -d location="$location" \
                      -d system="$system" \
                      -d classification="" \
                      -d grouped="$GROUPED" \
                      "$labels" \
                      $event_url

                    echo
                else
                    echo Skipping event $system/$location/$date/$time
                fi
            done
        done
    done
done


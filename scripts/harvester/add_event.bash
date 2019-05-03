#!/bin/bash

# Version history
# v1.0 2018-xx-xx Initial version to update test server
# v1.1 Updated to include classification and group event flags

# Command Definitions (same on RHEL6 and RHEL7)
CURL='/usr/csite/pubtools/bin/curl'
GREP='/bin/grep'
MAILX='/bin/mailx'
MKTEMP='/bin/mktemp'
DIRNAME='/usr/bin/dirname'

# The directory contain this script
SCRIPT_DIR=$($DIRNAME "$0")

# Which version of the script is this.  Needed to comply with certified rules
SCRIPT_VERSION='v1.1'

# Who to notifiy in case of error
#EMAIL_ADDRESS='accharvester@jlab.org'
EMAIL_ADDRESS='adamc@jlab.org'

# CURL parameters
COOKIE_JAR=`$MKTEMP --suffix=-waveforms`
curl_config="${SCRIPT_DIR}/../cfg/add_event1.0.cfg"

# Server to post to
#SERVER="waveforms.acc.jlab.org"
#SERVER="waveformstest.acc.jlab.org"
SERVER="sftadamc2.acc.jlab.org:8181"

usage () {
    cat - <<EOF
Usage: $0 [-h] <-s <system>> <-l <location>> <-c <classification>>
          <-t <date_time>> <-g <is_grouped>> <-f <filename>>
-h                   Show this help message
-s <system>          System name. Ex. rf
-l <location>        Location name.  Ex. 1L22
-c <classification>  Classification. Ex. periodic
-t <date_time>       Event timestamp. Ex. "2018-12-01 15:30:05.1"
-g <is_grouped>      Is event a group of capture files (true/false)
-f <file_name>       The name of the capture file to import

This script POSTs to the wfbrowser web service to request the addition
of an event specified by the above arguments.  The data should be on
the filesystem in the location dictated by the given parameters. A
server can have a different root directory path, and grouped and 
events will likely have different formats.

Giving no options will cause this script to produce a version number.
EOF
}

# Simple function for sending out a standard notification
alert () {
    message="$1"
    server="$2"
    system="$3"
    location="$4"
    classification="$5"
    timestamp="$6"
    grouped="$7"
    file="$8"

    mail_body="${message}\n\n"
    mail_body="${mail_body}Server: $server\n"
    mail_body="${mail_body}System: $system\n"
    mail_body="${mail_body}Location: $location\n"
    mail_body="${mail_body}Classification: $classification\n"
    mail_body="${mail_body}Timestamp: $timestamp\n"
    mail_body="${mail_body}Grouped: $grouped\n"
    mail_body="${mail_body}File: $file\n"

    # Print out the message for the harvester log
    echo $message" server=$server system=$system location=$location classification=$classification timestamp=$timestamp grouped=$grouped file=$file"

    # Email out the more verbose message to the concerned parties
    echo -e $mail_body | $MAILX -s "[Waveform Harvester Error] wfbrowser data import failed" $EMAIL_ADDRESS
}

# This function adds an event to the waveform browser server using an HTTP endpoint.
# The HTTP endpoint requires an authorized user in a role that has permissions to
# POST to the event HTTP endpoint (ADMIN, EVENTPOST roles as of Nov 2018).
add_event_to_server () {

    server=$1
    system=$2
    location=$3
    classification=$4
    timestamp=$5
    grouped=$6
    file=$7

    # URL pieces for making requests
    login_url="https://${server}/wfbrowser/login"
    event_url="https://${server}/wfbrowser/ajax/event"

    # POST to the login controller and tell curl to follow the redirect
    # for some reason, the login form only returns the glassfish SSO
    # session cookie, but the redirected page is on the server and sets up
    # application session cookie.  Alternatively, you could do the POST to
    # the login page and then a GET of a different page.
    #curl --trace-ascii - -v -c $COOKIE_JAR -L -K $curl_config "$login_url" 
    $CURL -k -s -c $COOKIE_JAR -K $curl_config "$login_url" -o /dev/null
    exit_val=$?

    if [ "$exit_val" -ne 0 ] ; then
        msg="Error: received non-zero status=$exit_val from curl login attempt"
        alert "$msg" "$server" "$system" "$location" "$classification" "$timestamp" "$grouped" "$file"

        rm -f $COOKIE_JAR
        return 1
    fi

    # Check that we got a valid pair of session cookies
    num_session_ids=`$GREP --count -P 'JSESSIONID' $COOKIE_JAR`
    if [ $num_session_ids -eq 0 ] ; then
        msg="Error: Did not receive the expected session cookies.  Got $num_session_ids, expected > 0"
        alert "$msg" "$server" "$system" "$location" "$classification" "$timestamp" "$grouped" "$file"
        
        rm -f $COOKIE_JAR
        return 1
    fi

    msg=`$CURL -k -s -b "$COOKIE_JAR" -X POST -d datetime="$timestamp" \
         -d location="$location" -d system="$system" -d classification="$classification" \
         -d grouped="$grouped" -d captureFile="$file" "$event_url"`
    exit_val=$?
    match=`echo -e "$msg" | $GREP --count "successfully added"`
    if [ $exit_val -ne 0 -o "$match" -eq 0 ] ; then
         mail_msg="Error:  Problem posting event to webservice.  Response: $msg"
         alert "$mail_msg" "$server" "$system" "$location" "$classification" "$timestamp" "$grouped" "$file"

         rm -f $COOKIE_JAR
         return 1
    fi
    
    rm -f $COOKIE_JAR

    return 0
}

##### PROCESS ARGUMENTS #####
if [ $# -eq 0 ] ; then
    echo "add_event.bash $SCRIPT_VERSION"
    exit 0
fi

# Xundef used as CLASSIFICATION can be ""
SYSTEM="Xundef"
LOCATION="Xundef"
CLASSIFICATION="Xundef"
TIMESTAMP="Xundef"
GROUPED="Xundef"
FILE="Xundef"

while getopts "s:l:c:t:g:f:" opt; do
    case $opt in
        h) usage
           exit 0
           ;;
        s) SYSTEM="$OPTARG"
           ;;
        l) LOCATION="$OPTARG"
           ;;
        c) CLASSIFICATION="$OPTARG"
           ;;
        t) TIMESTAMP="$OPTARG"
           ;;
        g) GROUPED="$OPTARG"
           ;;
        f) FILE="$OPTARG"
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

# Make sure we don't have any extra options left over
if [ $# -ne 0 ] ; then
    usage
    exit 1
fi

# Make sure everything was set that needs to be.  Xundef used as CLASSIFICATION can be ""
if [ "$SYSTEM" == "Xundef" ] ; then
    echo "-s <system> required"
    usage
    exit 1
fi
if [ "$LOCATION" == "Xundef" ] ; then
    echo "-l <location> required"
    usage
    exit 1
fi
if [ "$CLASSIFICATION" == "Xundef" ] ; then
    echo "-c <classification> required"
    usage
    exit 1
fi
if [ "$TIMESTAMP" == "Xundef" ] ; then
    echo "-t <date_time> required"
    usage
    exit 1
fi
if [ "$GROUPED" == "Xundef" ] ; then
    echo "-g <is_grouped> required"
    usage
    exit 1
fi
if [ "$FILE" == "Xundef" ] ; then
    echo "-f <file> required"
    usage
    exit 1
fi

###### MAIN ROUTINE #####
if [ ! -r $curl_config ] ; then
    msg="Error: $curl_config does not exist or is not readable.  Unable to add event to service."
    alert "$msg" "$SERVER" "$SYSTEM" "$LOCATION" "$CLASSIFICATION" "$TIMESTAMP" "$GROUPED" "$FILE"
    exit 1
fi

add_event_to_server "$SERVER" "$SYSTEM" "$LOCATION" "$CLASSIFICATION" "$TIMESTAMP" "$GROUPED" "$FILE"
exit $?

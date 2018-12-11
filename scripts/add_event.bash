#!/bin/bash

# Command Definitions (same on RHEL6 and RHEL7)
CURL='/usr/csite/pubtools/bin/curl'
GREP='/bin/grep'
MAILX='/bin/mailx'
MKTEMP='/bin/mktemp'
DIRNAME='/usr/bin/dirname'

# The directory contain this script
SCRIPT_DIR=$($DIRNAME "$0")

# Which version of the script is this.  Needed to comply with certified rules
SCRIPT_VERSION='v1.0'

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

# Simple function for sending out a standard notification
alert () {
    message="$1"
    server="$2"
    system="$3"
    location="$4"
    timestamp="$5"

    mail_body="${message}\n\n"
    mail_body="${mail_body}Server: $server\n"
    mail_body="${mail_body}System: $system\n"
    mail_body="${mail_body}Location: $location\n"
    mail_body="${mail_body}Timestamp: $timestamp\n"

    # Print out the message for the harvester log
    echo $message" server=$server system=$system location=$location timestamp=$timestamp"

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
    timestamp=$4

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
        alert "$msg" "$server" "$system" "$location" "$timestamp"

        rm -f $COOKIE_JAR
        return 1
    fi

    # Check that we got a valid pair of session cookies
    num_session_ids=`$GREP --count -P 'JSESSIONID' $COOKIE_JAR`
    if [ $num_session_ids -eq 0 ] ; then
        msg="Error: Did not receive the expected session cookies.  Got $num_session_ids, expected > 0"
        alert "$msg" "$server" "$system" "$location" "$timestamp"
        
        rm -f $COOKIE_JAR
        return 1
    fi

    # 
    msg=`$CURL -k -s -b "$COOKIE_JAR" -X POST -d datetime="$timestamp" \
         -d location="$location" -d system="$system" "$event_url"`
    exit_val=$?
    match=`echo -e "$msg" | $GREP --count "successfully added"`
    if [ $exit_val -ne 0 -o "$match" -eq 0 ] ; then
         mail_msg="Error:  Problem posting event to webservice.  Response: $msg"
         alert "$mail_msg" "$server" "$system" "$location" "$timestamp"

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

if [ $# -lt 3 ] ; then
    echo "Error: $0 requires 3 arguemnts, <system> <location> and <event_timestamp>"
    exit 1
fi

system="$1"
location="$2"
timestamp="$3"

###### MAIN ROUTINE #####
if [ ! -r $curl_config ] ; then
    msg="Error: $curl_config does not exist or is not readable.  Unable to add event to service."
    alert "$msg" "$SERVER" "$system" "$location" "$timestamp"
    exit 1
fi

add_event_to_server "$SERVER" "$system" "$location" "$timestamp"
exit $?

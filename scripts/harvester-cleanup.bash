#!/bin/bash

# This command will print out the files that match and return non-zero if no matches are found.
# The -exec command should be changed to rm -rf and the pipe to grep should be removed once we
# have been able to see what is being matched with the actual commands
/bin/find /usr/opsdata/waveforms/{data,view}/rf/*L* -mindepth 1 -maxdepth 1 -type d -mtime +365 -exec ls -ld {} \; | /bin/grep '.*'

# This message should only be printed if the find command was able to match some directories.
# This section can be removed when we are done with the "testing" phase.
if [ "$?" -eq 0 ] ; then
  echo 
  echo
  echo "This script is only printing out the files matched by the find command."
  echo "If this looks correct, please update the script so find executes rm -rf"
  echo "instead of ls -ld."
fi

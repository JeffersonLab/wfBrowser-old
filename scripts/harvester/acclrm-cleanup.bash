#!/bin/bash

# /bin/find throws an error if '.' is in the path (or you have no PATH) and you're running execdir
# We use full paths, so this doesn't matter either way
export PATH="/bin:/usr/bin"

# This is the data management script for the RF system.  For this system we do not want to
# automatically age out any data.  Since the waveform viewer is able to read gzipped files,
# we instead want to compress the data after 3 months.

# System data directory
#SYS_DIR=/usr/opsdata/waveforms/data/acclrm
SYS_DIR=/data/waveforms/data/acclrm
# Age at which we want to compress - anything 93 days or older
#AGE="+93"
AGE="+120"

# This data in this system is structured as ${SYS_DIR}/<location>/<classification>/<date>/<capture_files>
# The events are ungrouped so we need to gzip at the <capture_file> level for the viewer to
# be able to parse.  The system directory might have a couple of ancillary directories that are not
# locations, but all locations are CED style zone names (e.g., 1L23), so we can filter on that.
# Also make sure to not duplicate work if the directory has already been gzipped
# Note: -execdir says to run the command in the directory where the match was made.
# Note: subsequent -execdir commands are only run if the preceding -execdir command returns 0.
/bin/find ${SYS_DIR}/*L*/*/* \
  -mindepth 1 \
  -maxdepth 1 \
  -type f \
  -mtime ${AGE} \
  -print \
  -execdir /bin/bash -c 'if [ ! -e '{}.tar.gz' ] ; then /bin/tar -czf '{}.tar.gz' '{}'; else /bin/false ; fi' \; \
  -execdir /bin/rm -f {} \;


# NOTE: Any deletions must be coordinated with the waveform browser's database in order to keep the
# viewer tool in sync with the actual data.

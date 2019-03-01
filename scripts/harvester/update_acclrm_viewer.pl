#!/usr/csite/pubtools/bin/perl

use strict;
use warnings;
use File::Path qw(make_path);
use File::Basename;
use Data::Dumper;

# This tool takes one argument, the path to a capture file containing
# waveform data from an accelerometer.  It parses this path into
# the require components of a wfbrowser event, and calls a script that
# POSTs to the wfbrowser webservice end point for adding the event.

my $script_version = "v1.0";
my $script_dir = dirname(__FILE__);
my $Rscript = "/usr/csite/pubtools/bin/Rscript";
my $addEvent = $script_dir . "/add_event.bash";

if ( $#ARGV == -1 ) {
  print "update_acclrm_viewer.pl $script_version\n";
  print `$addEvent`;
  exit;
}

if ( $#ARGV != 0 ) {
  die "Error: Single argument required - path to waveform capture file\n";
}

my $file_path = $ARGV[0];

if ( ! -f "$file_path" ) {
  die "Error: capture file not found - $file_path\n";
}

# Make sure the basic view directory exists
my @file_path = split(/\//, $file_path);
my $grouped = "false";   # acclrm events are never grouped
my $file = pop @file_path;
my $date = pop @file_path;
my $classification = pop @file_path;
my $location = pop @file_path;
my $sys  = pop @file_path;
my $topdir = pop @file_path;

# Now run external command to update the waveform browser database
my $exit_val = 0;  # exit status of this script
my $e_val;  # For storing exit status individual external commands

my $eventTime;
if ( $file =~ /IAM\w+HRV.(\d+_\d+_\d+)_(\d{6}.\d).txt/ ) {
  my $eTime = $2;
  my $eDate = $1;
  if ( "$date" ne "$eDate" ) {
    die "Error: Capture file date ($eDate) and event date directory ($date) do not match";
  }
  $eTime =~ s/(\d\d)(\d\d)(\d\d)(.*)/$1:$2:$3$4/;
  $eDate =~ s/_/-/g;
  $eventTime = $eDate . " " . $eTime;
} else {
  die "Error: Capture file does not follow recognized format - $file";
}

my @cmd = ($addEvent, '-s', $sys, '-l', $location, '-c', $classification, '-t', $eventTime, '-g', $grouped, '-f', $file);
$e_val = system(@cmd);
if ( $e_val != 0 ) {
    print "add_event.bash script exited with errors: $!\n";
    $exit_val = 1;
}

exit $exit_val;

#!/usr/csite/pubtools/bin/perl

use strict;
use warnings;
use File::Path qw(make_path);
use File::Basename;
use Data::Dumper;

# This tool takes one argument, the directory path to a folder containing a set
# of waveform data from the trip of an RF zone.  This path is parsed to determine
# the system (rf), location, date, and time of the RF capture event.

my $script_version = "v1.2";
my $script_dir = dirname(__FILE__);
my $addEvent = $script_dir . "/add_event.bash";

if ( $#ARGV == -1 ) {
  print "wfGrapher.pl $script_version\n";
  print `$addEvent`;
  exit;
}

if ( $#ARGV != 0 ) {
  die "Error: Single argument required - path to waveform data directory\n";
}

my $data_path = $ARGV[0];

if ( ! -d "$data_path" ) {
  die "Error: directory not found - $data_path\n";
}

# Check that there are some TXT files in the data directory
opendir(my $d_fh, "$data_path") or die "Error opening directory $data_path: $!\n";
my @txt_files = grep(/\.txt$/, readdir($d_fh));
closedir($d_fh) or die "Error closing dirctory $data_path: $!";
if ( $#txt_files < 0 ) {
  die "Error: no TXT files found is directory $data_path\n";
}

# Make sure the basic view directory exists
my @dir_path = split(/\//, $data_path);
my $time = pop @dir_path;
my $date = pop @dir_path;
my $location = pop @dir_path;
my $sys  = pop @dir_path;
my $topdir = pop @dir_path;

# Update the waveform browser database
my $exit_val = 0;
my $e_val = 0;

my $eTime = $time;
my $eDate = $date;
$eTime =~ s/(\d\d)(\d\d)(\d\d)(.*)/$1:$2:$3$4/;
$eDate =~ s/_/-/g;
my $eventTime = $eDate . " " . $eTime;
my @cmd = ($addEvent, $sys, $location, $eventTime);
$e_val = system(@cmd);
if ( $e_val != 0 ) {
    print "add_event.bash script exited with errors: $!\n";
    $exit_val = 1;
}

exit $exit_val;

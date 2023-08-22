#!/usr/csite/pubtools/bin/perl

use strict;
use warnings;
use File::Path qw(make_path);
use File::Basename;
use File::Temp qw(tempfile tempdir);
use Cwd qw(abs_path);
use JSON qw(decode_json to_json);
use Data::Dumper;
use Try::Tiny;

# This tool takes one argument, the directory path to a folder containing a set
# of waveform data from the trip of an RF zone.  It takes this path and data,
# ensures that the near matching directory structure is created under the
# view folder, then calls an R script to handle the generation of interactive
# graphs that are saved to the filesystem as html files.

my $script_version = "v1.2";
my $script_dir = dirname(abs_path(__FILE__));
my $addEvent = $script_dir . "/add_event.bash";
my $rfClassifier = "/usr/csite/certified/bin/rf_classifier";

if ( $#ARGV == -1 ) {
  print "update_rf_viewer.pl $script_version\n";
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

# Method for getting the cavity and fault-type labels associated with the event
# Takes the path to the event directory as only argument
sub get_labels {
    my $event_dir = shift;

    # Try to label the fault event.  Perl doesn't have a nice way to capture STDERR and STDOUT
    # so we have to use temp files and redirect the output.
    my $tmpDir = tempdir(TEMPLATE => "/tmp/rf_classifier_tmp_XXXXXX", CLEANUP => 1);
    my ($out_fh, $tmpOut) = tempfile(TEMPLATE => "out_tmp_XXXXXX", DIR => $tmpDir);
    my ($err_fh, $tmpErr) = tempfile(TEMPLATE => "err_tmp_XXXXXX", DIR => $tmpDir);
    my $rfc_out = system("$rfClassifier analyze -o json $data_path > $tmpOut 2> $tmpErr");

    # We may get some error output, so process that and print it for harvester logging.
    if ( -f $tmpErr ) {
        while (<$err_fh>) {
            print;
        }

    }

    # Some things print warnings, etc. to STDOUT, so grab those and print them if they are
    # not valid JSON.  Otherwise, decode the JSON and use it as the labeling info.
    my $decoded;
    if ( -f $tmpOut) {
        while (my $line = <$out_fh>) {
            try {
                $decoded = decode_json($line);
            } catch {
                warn "Unexpected rf_classifier output: $line\n";
            };
        }
    }

    # Only process label output if we had a successful rf_classifier call
    my $cavity_label;
    my $fault_label;
    if ( $rfc_out == 0 ) {
        # Check if the analysis returned found the waveform to be invalid for analyzing
        if ( defined $decoded->{'data'}[0]{'error'} ) {
            # Print the error for harvester to log
            print "rf_classifier found problems with data: " . $decoded->{'data'}[0]{'error'} . "\n";
        } else {
            # The model returns a single structure with data for two wfbowser labels, i.e.,
            # a cavity label and a fault-type label
            my $model = $decoded->{'data'}[0]{'model'};
            my $f_value = $decoded->{'data'}[0]{'fault-label'};
            my $f_conf = $decoded->{'data'}[0]{'fault-confidence'};
            my $c_value = $decoded->{'data'}[0]{'cavity-label'};
            my $c_conf = $decoded->{'data'}[0]{'cavity-confidence'};
    
            # Get the label structures right and convert them to JSON
            $cavity_label = to_json({"name" => "cavity",
                            "value" => $c_value,
                            "confidence" => $c_conf,
                            "model-name" => $model});
            $fault_label = to_json({"name" => "fault-type",
                            "value" => $f_value,
                            "confidence" => $f_conf,
                            "model-name" => $model});
        }
    }

    # Return a list of the json formated strings
    my @labels = ();
    if ( defined $fault_label and defined $cavity_label) {
        push(@labels, $cavity_label);
        push(@labels, $fault_label);
        #open(my $err_fh, "<", "$tmpErr") or die "Error opening rf_classifier stderr: $!\n";
    }
    return @labels;
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
my $classification = ""; # rf events are never classified
my $grouped = "true"; # rf events are always grouped
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
my @cmd = ($addEvent, '-s', $sys, '-l', $location, '-c', $classification, '-t', $eventTime, '-g', $grouped, '-f', "");

# Analyze the event to get the appropriate labels
my @labels = get_labels($data_path);

# Check that both labels are defined.  If so, update the command
if (defined $labels[0] and defined $labels[1]) {
    push(@cmd, ("-L", "$labels[0]", "-L", "$labels[1]"));
}

$e_val = system(@cmd);
if ( $e_val != 0 ) {
    print "add_event.bash script exited with errors: $!\n";
    $exit_val = 1;
}

exit $exit_val;

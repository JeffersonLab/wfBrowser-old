#!/usr/csite/pubtools/python/3.6/bin/python3

import argparse
import sys
import pandas as pd
import numpy as np
import urllib
from datetime import datetime, timedelta
import tzlocal
import re

import requests
import json
from requests.adapters import HTTPAdapter
from requests.packages.urllib3.util.ssl_ import create_urllib3_context

import smtplib
import imghdr

from email.message import EmailMessage
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.mime.application import MIMEApplication
from email.mime.image import MIMEImage
import email.mime.application

from_addr = 'adamc@jlab.org'
#to_addrs = ['adamc@jlab.org', 'tennant@jlab.org', 'shabalin@jlab.org']
#to_addrs = ['adamc@jlab.org']
#to_addrs = ['lasithav@jlab.org']

email_css = """
<style>
p {
  margin: 1px;
}
p .more_space {
  margin-bottom: 10px;
}
h2 {
  padding-left: 10px;
  padding-bottom: 4px;
  background-color: #52489C;
  color: white;
}
table, th, td {
  border: 1px solid black;
  border-collapse: collapse;
}
th, td {
  padding-left: 15px;
  padding-right: 15px;
  text-align: left;
}
th {
  background-color: #EBEBEB;
}
</style>
"""

class SSLContextAdapter(HTTPAdapter):
    """An HTTPAdapter that loads the default system SSL trust store

    This is needed since the requests module ships with its own CA cert store that does not include the JLab PKI"""

    def init_poolmanager(self, *args, **kwargs):
        """Overrides the parent method to include call to load_default_certs()"""
        context = create_urllib3_context()
        kwargs['ssl_context'] = context
        context.load_default_certs()  # this loads the OS default trusted CA certs
        return super(SSLContextAdapter, self).init_poolmanager(*args, **kwargs)

def send_html_email(message, subject, fromaddr, toaddrs, imgs=None, smtp_server='localhost'):
    msg = MIMEMultipart('mixed')
    msg['Subject'] = subject
    msg['From'] = fromaddr
    msg['To'] = ",".join(toaddrs)

    part1 = MIMEText(message, 'html')
    msg.attach(part1)
    if imgs is not None:
        for filename in imgs.keys():
            #att = email.mime.application.MIMEApplication(imgs[filename], _subtype='png')
            att = email.mime.image.MIMEImage(imgs[filename])
            att.add_header('Content-Disposition', 'attachment', filename=filename)
            msg.attach(att)
    
    with smtplib.SMTP(smtp_server) as server:
        server.sendmail(msg['From'], toaddrs, msg.as_string())
    
def to_datetime(datetime_string):
    # Map of support datetime string formats to their parsing format counterparts
    pattern_map = {
      '^\d\d\d\d-\d\d-\d\d \d\d:\d\d:\d\d.\d{1,6}$': '%Y-%m-%d %H:%M:%S.%f',
      '^\d\d\d\d-\d\d-\d\d \d\d:\d\d:\d\d$': '%Y-%m-%d %H:%M:%S',
      '^\d\d\d\d-\d\d-\d\d \d\d:\d\d$': '%Y-%m-%d %H:%M',
      '^\d\d\d\d-\d\d-\d\d$': '%Y-%m-%d'
    }

    # Iterate across the dictionary.  Order should be maintained.  Make sure to
    # go from most specific to least.
    for pattern in pattern_map:
        if re.match(pattern, datetime_string):
            return datetime.strptime(datetime_string, pattern_map[pattern])

    # Raise an exception if we don't get find a supported match
    raise ValueError("Invalid datetime string format")

def get_events_from_web(data_server="accweb.acc.jlab.org", start=None, end=None):
    """Downloads a a list of event metadat from the waveforms web server."""
    fmt = "%Y-%m-%d %H:%M:%S"
    if start is None:
        start_string = "2018-01-01 00:00:00"
    else:
        start_string = start.strftime(fmt)
    if end is None:
        end_string = datetime.now().strftime(fmt)
    else:
        end_string = end.strftime(fmt)

    base = 'https://' + data_server + '/wfbrowser/ajax/event?'
    b = urllib.parse.quote_plus(start_string)
    e = urllib.parse.quote_plus(end_string)
    url = base + 'system=rf&out=json&includeData=false' + '&begin=' + b + '&end=' + e

    # Download the metadata about all of the events - supply the session/SSLContextAdapter to use system trust store
    # (required for Windows use)
    s = requests.Session()
    adapter = SSLContextAdapter()
    s.mount(url, adapter)
    r = s.get(url)

    # Test if we got a good status code.
    if not r.status_code == 200:
        raise RuntimeError("Received non-ok response - " + r.status_code)

    return json.loads(r.content)['events']

def get_wfb_report(start, end, locations, timeline_mode, heatmap_mode, server='accweb.acc.jlab.org'):
    """Download a PNG of the requested wfbrowser RF Fault Summary Report

    Args:
        start (datetime) - The start time for the report
        end (datetime) - The end time for the report
        locations (list(str)) - A list of locations (i.e., CED zone names) to include in report
        report_mode (str) - The desired report mode.  Current options are [all, linac, zone]
        server (str) - The FQDN of the server to query for both the report and PNG

    Returns (tuple) - A 2-tuple containing (<binary image data>, <report URL>)
    """

    # Prepare the URL parameters
    fmt = "%Y-%m-%d+%H:%M:%S"
    s = start.strftime(fmt)
    e = end.strftime(fmt)
    loc_string = ""
    for loc in locations:
        loc_string += f"&location={loc}"

    report_url = f"https://{server}/wfbrowser/reports/rf-label-summary?begin={s}&end={e}"
    report_url += f"&timeline={timeline_mode}&isLabeled=true&conf=0.0&confOp=>{loc_string}"
    report_url += f"&heatmap={heatmap_mode}"

    params = {'url': report_url, 'fullPage': 'true', 'waitForSelector': 'span.done'}
    url = f"https://{server}/puppet-show/screenshot"

    # Download the metadata about all of the events - supply the session/SSLContextAdapter 
    # to use system trust store
    # (required for Windows use)
    s = requests.Session()
    adapter = SSLContextAdapter()
    s.mount(url, adapter)

    r = s.get(url, params=params)

    return (r.content, report_url)
    

def retrieve_events(start, end):
    # Download the data as a nested dictionary structure
    events = get_events_from_web(start=start, end=end)

    # Initialize a dataframe to hold the events
    df = pd.DataFrame({
      'linac': [],
      'zone': [],
      'datetime': [],
      'fault-label': [],
      'fault-confidence': [],
      'cavity-label': [],
      'cavity-confidence': []
    })

    # Place the events in the dataframe
    for event in events:
        if event['labels'] is None or len(event['labels']) == 0:
            continue

        tz_fmt = '%Y-%m-%d %H:%M:%S.%f%z'
        dt_local = datetime.strptime(event['datetime_utc'] + "-0000", tz_fmt).astimezone(
                       tzlocal.get_localzone())
        for label in event['labels']:
            if label['name'] == 'cavity':
                c_label = label['value']
                c_conf = label['confidence']
            if label['name'] == 'fault-type':
                f_label = label['value']
                f_conf = label['confidence']

        df = df.append({
          'linac': event['location'][0:2],
          "zone": event['location'],
          'datetime': dt_local,
          'fault-label': f_label,
          'fault-confidence': f_conf,
          'cavity-label': c_label,
          'cavity-confidence': c_conf
        }, ignore_index=True)

    # Set all of the data types approriately
    df = df.astype({
      'linac': 'category',
      'zone': 'category',
      'datetime': "datetime64[ns]",
      'fault-label': 'category',
      'fault-confidence': 'float64',
      'cavity-label': 'category',
      'cavity-confidence': 'float64'
    })

    return df

def process_start_end(start, end, start_default, end_default):
    s = start
    e = end

    if s is None:
        s = start_default
    if e is None:
        e = end_default

    if s > e:
        raise ValueError(f"Start ({s}) must be earlier than end ({e})")

    return (s, e)

def format_html_message(preamble, threshold, start, end, report_links, fault_df):
    links = ""
    for key in report_links.keys():
        url = report_links[key]
        links += f"<p><a href='{url}'>{key} Report</a></p>\n"

    msg = f"""
<html>
<head>{email_css}</head>
<body>
{preamble}
<p class="more_space">
An alert is only generated if the fault count on class of faults has met or exceeded the specified threshold 
These alerts only consider labeled faults.  Faults are only labeled if they pass basic
validation checks.  These checks may vary over time, but the two goals are roughly the following:
<ul>
  <li>The trip is "real" and did not occur during recovery from a previous trip (e.g., not in SEL mode).</li>
  <li>The trip's data acquisition is suitable for the model to process it.</li>
</ul></p>
<p>Threshold:  {threshold} events</p>
<p>Start Time: {start}</p>
<p>End Time:   {end}</p>

<h2>Report Links</h2>
{links}

<h2>Fault Table</h2>
<p class='more_space'>The follow classes of faults were found to have
exceeded the specified threshold.</p>
{fault_df.to_html(index=False)}
</body></html>
"""

    return msg

def format_text_message(preamble, threshold, start, end, df):
    msg = f"""{preamble}
Threshold: {threshold}
Start: {start}
End: {end}

#### Fault Table ####
{df.to_string(index=False)}
"""

    return msg

def handle_fault_check(threshold, start, end, to_addrs):
    # Process the time range
    now = datetime.now()
    (s, e) = process_start_end(start, end, now - timedelta(days=28), now)

    # Get the fault data
    events = retrieve_events(s, e)

    # Do the check
    df = events.groupby(['zone', 'cavity-label', 'fault-label']).size().reset_index(name="count")
    df = df[df['count'] >= threshold]

    # Generate results if needed 
    if len(df) > 0:
        if to_addrs is not None:
            subject = "C100 Fault Alert - Cavity/Fault Pair"
            preamble = f"<h2>Recurring Cavity/Fault-type Pairs</h2>"
            imgs = {}
            report_links = {}
            zones = pd.unique(df['zone']).tolist()
            for zone in zones:
                (img, url) = get_wfb_report(s, e, [zone], 'single', 'zone')
                imgs[f"{zone}.png"] = img
                report_links[zone] = url

            # Get the standard HTML formatted message
            msg = format_html_message(preamble, threshold, start, end, report_links, df)
            send_html_email(msg, subject, from_addr, to_addrs, imgs)
        else:
            preamble = f"Found recurring Cavity/Fault-type events"
            msg = format_text_message(preamble, threshold, start, end, df)
            print(msg)

def handle_cavity_check(threshold, start, end, to_addrs):
    # Process the time range
    now = datetime.now()
    (s, e) = process_start_end(start, end, now - timedelta(days=7), now)

    # Get the fault data
    events = retrieve_events(s, e)

    # Do the check
    df = events.groupby(['zone', 'cavity-label']).size().reset_index(name="count")
    df = df[df['count'] >= threshold]

    if len(df) > 0:
        if to_addrs is not None:
            subject = "C100 Fault Alert - Cavities"
            preamble = f"<h2>Recurring Faulted Cavities</h2>\n"

            imgs = {}
            report_links = {}
            zones = pd.unique(df['zone']).tolist()
            for zone in zones:
                (img, url) = get_wfb_report(s, e, [zone], 'single', 'zone')
                imgs[f"{zone}.png"] = img
                report_links[zone] = url

            # Get the standard HTML formatted message
            msg = format_html_message(preamble, threshold, start, end, report_links, df)
            send_html_email(msg, subject, from_addr, to_addrs, imgs)
        else:
            preamble = f"Found recurring Cavity fault events"
            msg = format_text_message(preamble, threshold, start, end, df)
            print(msg)


def handle_zone_check(threshold, start, end, to_addrs):
    # Process the time range
    now = datetime.now()
    (s, e) = process_start_end(start, end, now - timedelta(days=1), now)

    # Get the fault data
    events = retrieve_events(s, e)

    # Do the check
    df = events.groupby(['zone']).size().reset_index(name="count")
    df = df[df['count'] >= threshold]

    if len(df) > 0:
        if to_addrs is not None:
            subject = "C100 Fault Alert - Zones"
            preamble = f"<h2>Recurring Faulted Zones</h2>\n"

            imgs = {}
            report_links = {}
            zones = pd.unique(df['zone']).tolist()
            for zone in zones:
                (img, url) = get_wfb_report(s, e, [zone], 'single', 'zone')
                imgs[f"{zone}.png"] = img
                report_links[zone] = url
    
            # Get the standard HTML formatted message
            msg = format_html_message(preamble, threshold, start, end, report_links, df)
            send_html_email(msg, subject, from_addr, to_addrs, imgs)
        else:
            preamble = f"Found recurring Zone fault events"
            msg = format_text_message(preamble, threshold, start, end, df)
            print(msg)


def handle_linac_check(threshold, start, end, to_addrs):
    # Process the time range
    now = datetime.now()
    (s, e) = process_start_end(start, end, now - timedelta(hours=1), now)

    # Get the fault data
    events = retrieve_events(s, e)

    # Do the check
    df = events.groupby(['linac']).size().reset_index(name="count")
    df = df[df['count'] >= threshold]

    if len(df) > 0:
        if to_addrs is not None:
            subject = "C100 Fault Alert - Linac"
            preamble = "<h2>Recurring Faults Within Linacs</h2>\n"

            imgs = {}
            report_links = {}
            linacs = pd.unique(df['linac']).tolist()
            for linac in linacs:
                if linac == '0L':
                    zones = ['0L04']
                elif linac == '1L':
                    zones = ['1L07', '1L22', '1L23', '1L24', '1L25', '1L26']
                elif linac == '2L':
                    zones = ['2L22', '2L23' '2L24', '2L25', '2L26']

                (img, url) = get_wfb_report(s, e, zones, 'separate', 'linac')
                imgs[f"{linac}.png"] = img
                report_links[linac] = url

            # Get the standard HTML formatted message
            msg = format_html_message(preamble, threshold, start, end, report_links, df)
            send_html_email(msg, subject, from_addr, to_addrs, imgs)
        else:
            preamble = f"Found recurring Linac fault events"
            msg = format_text_message(preamble, threshold, start, end, df)
            print(msg)

def handle_summary(start, end, to_addrs):
    # Process the time range
    now = datetime.now()
    (s, e) = process_start_end(start, end, now - timedelta(hours=1), now)

    # Get the fault data
    events = retrieve_events(s, e)

    # Split out the two tables we will report.  First faults by zone then by zone/labels.
    zone_g = events.groupby(['zone'])
    zone_df = zone_g['fault-confidence', 'cavity-confidence'].agg([np.mean, np.std])
    zone_df["count"] = zone_g.size()

    # This drops the multi index on both rows and columns
    zone_df = zone_df.reset_index()

    # Rename and reorder the columns.
    zone_df.columns = ("zone", "fault_conf_mean", "fault_conf_std", "cav_conf_mean", "cav_conf_std", "count")
    zone_df = zone_df[["zone", "count", "fault_conf_mean", "fault_conf_std", "cav_conf_mean", "cav_conf_std"]]

    # Now generate the finer grained report
    label_g = events.groupby(['zone', 'cavity-label', 'fault-label'])
    label_df = label_g['fault-confidence', 'cavity-confidence'].agg([np.mean, np.std])
    label_df['counts'] = label_g.size()

    # This drops the multi index on both rows and columns
    label_df = label_df .reset_index()

    # Rename and reorder the columns.
    label_df.columns = ("zone", 'cavity-label', 'fault-label', "fault_conf_mean", "fault_conf_std",
                        "cav_conf_mean", "cav_conf_std", "count")
    label_df = label_df[["zone", 'cavity-label', 'fault-label', "count", "fault_conf_mean", "fault_conf_std",
                         "cav_conf_mean", "cav_conf_std"]]

    if to_addrs is not None:
        # Email subject line.
        subject = "C100 Fault Summary Report"

        # Get the PNG and link for the web report
        zones = ['0L04', '1L07', '1L22', '1L23', '1L24', '1L25', '1L26', '2L22', '2L23',
                 '2L24', '2L25', '2L26']
        (img, url) = get_wfb_report(s, e, zones, 'separate', 'zone')
        imgs = {'summary.png': img}

        # This is the message body/ email report
        msg = f"""
<html>
<head>{email_css}</head>
<body>
  <h2>Labeled Fault Summary</h2>
  <p class="more_space">This report only consider labeled faults.  Faults are only labeled if they pass basic
  validation checks.  These checks may vary over time, but the two goals are roughly the following:
  <ul>
    <li>The trip is "real" and did not occur during recovery from a previous trip (e.g., not in SEL mode).</li>
    <li>The trip's data acquisition is suitable for the model to process it.</li>
  </ul></p>

  <p>Start: {start}</p>
  <p>End: {end}</p>

  <h2>Report Links</h2>
  <p><a href='{url}'>Summary Report</a></p>

  <h2>Faults By Zone</h2>
  {zone_df.round(2).to_html(index=False)}

  <h2>Faults By Cavity and Fault Type</h2>
  {label_df.round(2).to_html(index=False)}
</body>
"""
        
        # Send out the email
        send_html_email(msg, subject, from_addr, to_addrs, imgs)

    else:
        # No email to send.  Just print out an text summary
        print(f"""#### Labeled Fault Summary ####
Start: {start}
End:   {end}

# Faults By Zone #
{zone_df.round(2).to_string(index=False)}

# Faults By Cavity and Fault Type #
{label_df.round(2).to_string(index=False)}
""")
    

def main():
    # Root argument parser.
    parser = argparse.ArgumentParser(prog="rf_fault_checker",
                                        description="Check and alert on C100 fault trends")
    #parser.add_argument('-a', '--alert', action="store_true", required=False,
    #                       help="Send email to configured recipients in place of printing output")
    parser.add_argument('-a', '--alert', action="store", type=str, required=False,
                           help="Comma separated list of email recipients.  (No STDOUT)")
    subparsers = parser.add_subparsers(help="Check Modes", dest="mode")

    # Summary mode argument parser.  Just used a switch for different behavior.
    summary_parser = subparsers.add_parser('summary', help="Email a summary report of C100 RF Faults")
    summary_parser.add_argument('-s', '--start-time', action="store", required=False,
                           type=to_datetime, help="Start time for fault query")
    summary_parser.add_argument('-e', '--end-time', action="store", required=False,
                           type=to_datetime, help="End time for fault query")

    # "Trigger" mode parser.  Controls alert behavior
    trigger_parser = subparsers.add_parser('trigger', help="Send email alert if fault threshold is exceeded")
    trigger_parser.add_argument('-s', '--start-time', action="store", required=False,
                           type=to_datetime, help="Start time for fault query")
    trigger_parser.add_argument('-e', '--end-time', action="store", required=False,
                           type=to_datetime, help="End time for fault query")
    trigger_parser.add_argument('-f', '--fault-threshold', action="store", type=int,
                           help="Min number of faults for alerting", required=True)
    trigger_parser.add_argument('-c', '--check', action="store", required=True, type=str,
                           choices=['fault', 'cavity', 'zone', 'linac'], help="Specificity level")

    # Process command line arguements
    args = parser.parse_args()

    # If the alert option was given, get the specified recipients
    to_addrs = None
    if args.alert is not None:
        to_addrs = args.alert.split(",")
        pattern = '^[\w.]+@[\w.]+.[\w]+$'
        for address in to_addrs:
            if not re.match(pattern, address):
                raise ValueError(f"Invalid email address {address}")

    # There are two basic modes.  A summary report, and triggered notifications.
    if args.mode == 'summary':
        handle_summary(args.start_time, args.end_time, to_addrs)
    else:
        if args.check == 'fault':
            handle_fault_check(args.fault_threshold, args.start_time, args.end_time, to_addrs)
        elif args.check == 'cavity':
            handle_cavity_check(args.fault_threshold, args.start_time, args.end_time, to_addrs)
        elif args.check == 'zone':
            handle_zone_check(args.fault_threshold, args.start_time, args.end_time, to_addrs)
        elif args.check == 'linac':
            handle_linac_check(args.fault_threshold, args.start_time, args.end_time, to_addrs)

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)

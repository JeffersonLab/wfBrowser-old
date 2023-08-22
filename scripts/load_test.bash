#!/bin/bash

clients=300
tempdir='/usr/csmuser/adamc/apps/wfbrowser/temp/'
for i in `seq 1 $clients`
do
    echo $i
    rm "$tempdir/wf-$i"
    wget -q -O "$tempdir/wf-$i" --no-check-certificate 'https://sftadamc2.acc.jlab.org:8181/wfbrowser/graph?begin=2018-10-01+10:35:44&end=2018-10-03+10:35:44&eventId=17308&series=CFQE2&location=0L04&location=1L26&location=1L25&location=1L24&location=1L23&location=1L22' &
    sleep 0.25
done

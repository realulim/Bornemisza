#!/bin/bash

IFS=$'\n'
domain={{ pillar['ssldomain'] }}

records=`host -t srv _app._tcp.$domain.|cut -d" " -f8|xargs -L1 host|cut -d" " -f4`

lines=`grep ipapp /srv/pillar/haproxy.sls|cut -d" " -f2`

highstate=false
for line in $lines ; do
	if [[ ! "$records" =~ "$line" ]] ; then
		printf "[`date`] Gone: $line\n" >> /opt/logs/srvRecords.log
		sed -i "/$line/d" /srv/pillar/haproxy.sls
		highstate=true
	fi
done

if [ "$highstate" = true ]; then
	/usr/bin/bash -c '/opt/bootstrap-bornemisza.sh >> /opt/logs/highstate-srvRecords.log'
fi
#!/bin/bash

cd /opt
source ./config.sh app

# create state tree
mkdir -p $SaltLocal/files/haproxy
mkdir -p $SaltLocal/files/payara
for FILE in top.sls haproxy.sls files/haproxy/haproxy.cfg payara.sls files/payara/payara.service files/payara/hazelcast.xml ufw.sls
do
	curl -o $SaltLocal/$FILE -L $SaltRemote/$FILE
done

# static pillars
for FILE in top.sls
do
	curl -o $PillarLocal/$FILE -L $PillarRemote/$FILE
done

function generatepw {
	openssl rand -hex 12
}

# dynamic pillar: haproxy
if [[ ! -e $PillarLocal/haproxy.sls ]]; then
	curl -o $PillarLocal/haproxy.sls -L $PillarRemote/haproxy.sls
	sed -ie s/stats-password:/"stats-password: `generatepw`"/ $PillarLocal/haproxy.sls
	chmod 400 $PillarLocal/haproxy.sls
fi

# dynamic pillar: payara
if [[ ! -e $PillarLocal/payara.sls ]]; then
	curl -o $PillarLocal/payara.sls -L $PillarRemote/payara.sls
	sed -ie s/asadmin-password:/"asadmin-password: `generatepw`"/ $PillarLocal/payara.sls
	sed -ie s/asadmin-master-password:/"asadmin-master-password: `generatepw`"/ $PillarLocal/payara.sls
	chmod 400 $PillarLocal/payara.sls
fi

function getip {
	host $1 | cut -d' ' -f4
}

# determine cluster members hostnames and ips
if [ `grep hostname1 /srv/pillar/basics.sls | wc -l` -eq 0 ]; then
	for COUNTER in 1 2 3
	do
		HOSTNAME=app$COUNTER.fra.bornemisza.de
		printf "hostname$COUNTER: $HOSTNAME\n" >> $PillarLocal/basics.sls
		printf "ip$COUNTER: `getip $HOSTNAME`\n" >> $PillarLocal/basics.sls
	done
fi

# determine my position in the cluster
HOSTNAME=`uname -n`
IP=`host $HOSTNAME | cut -d' ' -f4`
if [ `grep privip /srv/pillar/basics.sls | wc -l` -eq 0 ]; then
	POS=`grep "$IP" $PillarLocal/basics.sls | grep -v "ip:" | cut -d':' -f1 | sed "s/ip//"`
	printf "privip: 10.99.0.$POS\n" >> $PillarLocal/basics.sls
fi

# create server
salt-call -l info state.highstate

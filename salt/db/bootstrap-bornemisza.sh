#!/bin/bash

cd /opt/scripts
source ./config.sh db
sh bootstrap-common.sh db

# create state tree
mkdir -p $SaltLocal/files/couchdb
mkdir -p $SaltLocal/files/haproxy
for FILE in	top.sls dns.sls files/haproxy/haproxy.cfg hosts.sls ufw.sls files/hosts \
		couchdb.sls files/couchdb/couchdb.service files/couchdb/netrc files/couchdb/vm.args files/couchdb/couchdb.logrotate.conf
do
	curl -o $SaltLocal/$FILE -L $SaltRemote/$FILE
done

# static pillars
for FILE in top.sls
do
	curl -o $PillarLocal/$FILE -L $PillarRemote/$FILE
done

# dynamic pillar: haproxy
if [[ ! -e $PillarLocal/haproxy.sls ]]; then
	curl -o $PillarLocal/haproxy.sls -L $PillarRemote/haproxy.sls
	sed -ie s/stats-password:/"stats-password: `generatepw`"/ $PillarLocal/haproxy.sls
fi

# dynamic pillar: couchdb
if [[ ! -e $PillarLocal/couchdb.sls ]]; then
	curl -o $PillarLocal/couchdb.sls -L $PillarRemote/couchdb.sls

	read -p 'CouchDB Admin Password [leave empty to generate random string]: ' COUCH_PW
	if [[ -z $COUCH_PW ]]; then
		COUCH_PW=`generatepw`
	fi
	sed -ie s/couchdb-admin-password:/"couchdb-admin-password: $COUCH_PW"/ $PillarLocal/couchdb.sls

	read -p 'Erlang Cookie [leave empty to generate random string]: ' COOKIE
	if [[ -z $COOKIE ]]; then
		COOKIE=`generatepw`
	fi
	sed -ie s/cookie:/"cookie: $COOKIE"/ $PillarLocal/couchdb.sls

	read -p 'IP Address of Node already in Cluster [leave empty if this is the first node]: ' CLUSTERIP
	if [[ -z $CLUSTERIP ]]; then
		CLUSTERIP=`getprivip db`
	fi
	sed -ie s/clusterip:/"clusterip: $CLUSTERIP"/ $PillarLocal/couchdb.sls

	CLUSTERSIZE=${#db_HostLocation[@]}
	sed -ie s/clustersize:/"clustersize: $CLUSTERSIZE"/ $PillarLocal/couchdb.sls
fi

# letsencrypt needs to know the ssl endpoint for creating its certificate
if [ `grep sslhost: /srv/pillar/basics.sls | wc -l` -eq 0 ]; then
	SSLHOST=`domainname -f`
	printf "sslhost: $SSLHOST\n" | tee -a $PillarLocal/basics.sls	
fi

# haproxy needs to know all external hostnames to select the correct load balancing strategy
COUNTER=1
for LOCATION in ${db_HostLocation[@]}
do
	if [ `grep hostname$COUNTER /srv/pillar/haproxy.sls | wc -l` -eq 0 ]; then
		HOSTNAME=$db_HostPrefix.$LOCATION.$db_Domain
		printf "hostname$COUNTER: $HOSTNAME\n" | tee -a $PillarLocal/haproxy.sls
	fi
	let "COUNTER++"
done

# haproxy needs to know the internal ips for load balancing between them
COUNTER=1
for LOCATION in ${db_HostLocation[@]}
do
	if [ `grep privip$COUNTER /srv/pillar/haproxy.sls | wc -l` -eq 0 ]; then
		INTERNALHOSTNAME=$db_HostPrefix.$LOCATION.internal.$db_Domain
		printf "privip$COUNTER: `getinternalip $INTERNALHOSTNAME`\n" | tee -a $PillarLocal/haproxy.sls
	fi
	let "COUNTER++"
done

# haproxy needs to know the source ips that are whitelisted for database access
for COUNTER in `seq -s' ' 1 $app_HostCount`
do
	if [ `grep ipapp$COUNTER /srv/pillar/haproxy.sls | wc -l` -eq 0 ]; then
		HOSTNAME=$app_HostPrefix$COUNTER.$app_Domain
		printf "ipapp$COUNTER: `getip $HOSTNAME`\n" | tee -a $PillarLocal/haproxy.sls
	fi
done

chmod -R 400 $PillarLocal

# create server
salt-call -l info state.highstate

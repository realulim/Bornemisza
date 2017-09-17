#!/bin/bash

cd /opt/scripts
source ./config.sh app
sh bootstrap-common.sh app

# create app state tree
svn export --force $SvnTrunk/salt/app/srv/salt /srv/salt
if [ ! -e $PillarLocal/top.sls ]; then
	svn export --force $SvnTrunk/salt/app/srv/pillar/top.sls /srv/pillar
fi

# dynamic pillar: haproxy
if [ ! -e $PillarLocal/haproxy.sls ]; then
	printf "stats-password: `generatepw`\n" > $PillarLocal/haproxy.sls
fi

# dynamic pillar: payara
if [ ! -e $PillarLocal/payara.sls ]; then
	printf "asadmin-password: `generatepw`\n" > $PillarLocal/payara.sls
	printf "asadmin-master-password: `generatepw`\n" >> $PillarLocal/payara.sls
fi

# if we have already setup payara, but not told it the couchdb admin password yet, then we ask for it here
if grep -s changeit /root/.payara ; then
	read -p 'CouchDB Admin Password [leave empty to specify it at a later time]: ' COUCH_PW
	if [ ! -z $COUCH_PW ]; then
		sed -i.bak "s/changeit/$COUCH_PW/g" /root/.payara
		printf " " >> /opt/payara/bin/domain-config.sh
	fi
fi

# letsencrypt needs to know the ssl endpoint for creating its certificate
if ! grep -q sslhost: /srv/pillar/basics.sls ; then
	FLOATIP=`getip $entrypoint`
	SSLHOST=`host $FLOATIP | cut -d' ' -f5 | sed -r 's/(.*)\..*/\1/'`
	printf "sslhost: $SSLHOST\n" | tee -a $PillarLocal/basics.sls
fi

# haproxy needs to know all appserver hostnames for load balancing between them
for COUNTER in `seq -s' ' 1 $app_HostCount`
do
	if ! grep -q hostname$COUNTER /srv/pillar/haproxy.sls ; then
		HOSTNAME=$app_HostPrefix$COUNTER.$app_Domain
		printf "hostname$COUNTER: $HOSTNAME\n" | tee -a $PillarLocal/haproxy.sls
	fi
done

# birdc needs to know the floating ip in order to manage failover routing
if ! grep -q floatip: /srv/pillar/basics.sls ; then
	FLOATIP=`getip $entrypoint`
	printf "floatip: $FLOATIP\n" | tee -a $PillarLocal/basics.sls
fi

# ask for autonomous system number
if ! grep -q ASN: /srv/pillar/basics.sls ; then
	read -p 'ASN: ' ASN
	printf "ASN: $ASN\n" >> $PillarLocal/basics.sls
fi

# ask for BGP password
if ! grep -q BGP: /srv/pillar/basics.sls ; then
	read -p 'BGP: ' BGP
	printf "BGP: $BGP\n" >> $PillarLocal/basics.sls
fi

chmod -R 400 $PillarLocal

# create server
salt-call -l info state.highstate

#!/bin/bash

cd /opt/scripts
source ./config.sh $1

# determine my hostname, domain and public ip
HOSTNAME=`domainname -f`
SSLDOMAIN=`printf $HOSTNAME | rev | awk -F. '{ print $1"."$2 }' | rev`
if [ `grep hostname: $PillarLocal/basics.sls | wc -l` -eq 0 ]; then
	VARNAME=$1_publicIpInterface
	IP=`ip addr show ${!VARNAME}|grep "inet "|cut -d"/" -f1|cut -d" " -f6`
	printf "hostname: $HOSTNAME\nip: $IP\n" | tee $PillarLocal/basics.sls
        printf "ssldomain: $SSLDOMAIN\n" | tee -a $PillarLocal/basics.sls
fi

# determine my private IP
if [ `grep privip: $PillarLocal/basics.sls | wc -l` -eq 0 ]; then
	printf "privip: `getprivip $1`\n" | tee -a $PillarLocal/basics.sls
fi

# ask for Cloudflare API key
if [ `grep CFKEY: $PillarLocal/basics.sls | wc -l` -eq 0 ]; then
	read -p 'Cloudflare API Key: ' CFKEY
	printf "CFKEY: $CFKEY\n" >> $PillarLocal/basics.sls
fi

# ask for Cloudflare email (username of Cloudflare account)
if [ `grep CFEMAIL: /srv/pillar/basics.sls | wc -l` -eq 0 ]; then
	read -p 'Cloudflare Email: ' CFEMAIL
	printf "CFEMAIL: $CFEMAIL\n" >> $PillarLocal/basics.sls
fi

set -x
# determine zone id of domain
if [ `grep CFZONEID: /srv/pillar/basics.sls | wc -l` -eq 0 ]; then
	CFZONEID=`cloudflareget "$CFAPI" $CFEMAIL $CFKEY | jq '.result|.[]|.id' | tr -d "\""`
	printf "CFZONEID: $CFZONEID\n" | tee -a $PillarLocal/basics.sls

	# write SRV records for domain
	for LOCATION in ${db_HostLocation[@]}
	do
		DBHOSTNAME=$db_HostPrefix.$LOCATION.$db_Domain
		SRVID=`cloudflareget "$CFAPI$CFZONEID/dns_records" $CFEMAIL $CFKEY | jq '.result|.[]|select(.type=="SRV")|select(.data.target=="$DBHOSTNAME")|.id'| tr -d "\""`

		if [ -n "$SRVID" ]; then
			# record exists, so let's update it
			cloudflareput "$CFAPI$CFZONEID/dns_records/$SRVID" $CFEMAIL $CFKEY "$SSLDOMAIN" "$DBHOSTNAME"
		else
			# record does not exist, so let's create it
			cloudflarepost "$CFAPI$CFZONEID/dns_records" $CFEMAIL $CFKEY "$SSLDOMAIN" "$DBHOSTNAME"
		fi
	done
fi

if [[ -e /opt/bootstrap.sh ]]; then
	mv /opt/bootstrap.sh /opt/scripts
fi

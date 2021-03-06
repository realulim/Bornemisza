#!/bin/bash

cd /opt/scripts || exit -1
source ./config.sh app

# collect all pillar secrets in order to exclude them from Salt's output
SECRET_KEYS=(CFEMAIL CFKEY BGP ASN stats-password asadmin-password asadmin-master-password couchdb-admin-password)
EXCL_STR=$(getsecrets "${SECRET_KEYS[@]}")

# decommission server
salt-call -l info state.apply decommission-common --force-color |& grep -E -v "$EXCL_STR"
salt-call -l info state.apply decommission-app --force-color |& grep -E -v "$EXCL_STR"

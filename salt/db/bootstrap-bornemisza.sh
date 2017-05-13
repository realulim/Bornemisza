#!/bin/bash

cd /opt
source ./config.sh db

# create state tree
for FILE in top.sls hosts.sls files/hosts
do
	curl -o $SaltLocal/$FILE -L $SaltRemote/$FILE
done

# static pillars
for FILE in top.sls
do
        curl -o $PillarLocal/$FILE -L $PillarRemote/$FILE
done

# download and run common script
curl -o ./common/bootstrap-bornemisza.de $SaltRemoteRoot/common/bootstrap-bornemisza.de
source ./common/bootstrap-bornemisza.de

# create server
salt-call -l info state.highstate

{% set COUCHDB_DIR='apache-couchdb-2.0.0' %}
{% set COUCHDB_TARGZ='apache-couchdb-2.0.0.tar.gz' %}
{% set COUCHDB_BINARY='/home/couchpotato/couchdb/bin/couchdb' %}
{% set COUCHDB_CONFIG='/home/couchpotato/couchdb/etc/local.ini' %}
{% set AUTH='--netrc-file /srv/pillar/netrc' %}

install_couchdb_pkgs:
  pkg.installed:
    - pkgs:
      - autoconf
      - autoconf-archive
      - automake
      - erlang-asn1
      - erlang-erl_interface
      - erlang-erts
      - erlang-eunit
      - erlang-jiffy
      - erlang-os_mon
      - erlang-reltool
      - erlang-snappy
      - erlang-xmerl
      - gcc-c++
      - help2man
      - jq
      - js-devel
      - libcurl-devel
      - libicu-devel
      - libtool
      - perl-Test-Harness

couchpotato:
  user.present:
    - fullname: CouchDB Administrator
    - shell: /bin/bash
    - home: /home/couchpotato

download-couchdb:
  cmd.run:
    - name: bash -c 'mkdir tmp && cd tmp && wget http://mirror.synyx.de/apache/couchdb/source/2.0.0/{{ COUCHDB_TARGZ }}'
    - cwd: /home/couchpotato
    - runas: couchpotato
    - unless: ls ./tmp/{{ COUCHDB_TARGZ }}

build-couchdb:
  cmd.run:
    - name: bash -c 'tar -xzf {{ COUCHDB_TARGZ }} && cd {{ COUCHDB_DIR }} && ./configure && make release'
    - cwd: /home/couchpotato/tmp
    - runas: couchpotato
    - unless: ls ./{{ COUCHDB_DIR }}/rel/couchdb

install-couchdb:
  cmd.run:
    - name: bash -c 'cp -R rel/couchdb ~/ && find ~/couchdb -type d -exec chmod 0770 {} \; && chmod 0644 ~/couchdb/etc/*'
    - cwd: /home/couchpotato/tmp/{{ COUCHDB_DIR }}
    - runas: couchpotato
    - unless: ls {{ COUCHDB_BINARY }}

install-systemctl-unitfile:
   file.managed:
    - name: /usr/lib/systemd/system/couchdb.service
    - source: salt://files/couchdb/couchdb.service

configure-admin-password:
  file.replace:
    - name: {{ COUCHDB_CONFIG }}
    - pattern: |
        ;admin = mysecretpassword
    - repl: |
        admin = {{ pillar['couchdb-admin-password'] }}
    - show_changes: False

configure-couchdb:
  file.replace:
    - name: {{ COUCHDB_CONFIG }}
    - pattern: |
        [chttpd]
        ;port = 5984
        ;bind_address = 127.0.0.1
    - repl: |
        [log]
        writer = file
        file = /home/couchpotato/couchdb.log
        level = info

        [chttpd]
        port = 5984
        bind_address = {{ pillar['privip'] }}

/home/couchpotato/couchdb/etc/vm.args:
  file.managed:
    - source: salt://files/couchdb/vm.args
    - template: jinja
    - show_changes: False

/srv/pillar/netrc:
  file.managed:
    - source: salt://files/couchdb/netrc
    - template: jinja
    - mode: 400
    - show_changes: False

run-couchdb:
  service.running:
    - name: couchdb
    - enable: True
    - init_delay: 5
    - listen:
      - file: /usr/lib/systemd/system/couchdb.service
      - file: {{ COUCHDB_CONFIG }}
      - file: /home/couchpotato/couchdb/etc/vm.args

join-couchdb-cluster:
  cmd.run:
     - name: curl -s {{ AUTH }} -X PUT "http://localhost:5986/_nodes/couchdb@{{ pillar['clusterip'] }}" -d {}
     - onlyif:
       - test '{{ pillar['clusterip'] }}' != '{{ pillar['privip'] }}'
       - curl -s {{ AUTH }} http://localhost:5984/_membership|grep -F '{"all_nodes":["couchdb@{{ pillar['privip'] }}"],"cluster_nodes":["couchdb@{{ pillar['privip'] }}"]}'
       - </dev/tcp/{{ pillar['clusterip'] }}/4369

{% for db in ['_users', '_replicator', '_global_changes'] %}
create-database-{{ db }}:
  cmd.run:
    - name: curl -s -X PUT {{ AUTH }} http://localhost:5984/{{ db }}
    - onlyif:
      - curl -s {{ AUTH }} http://localhost:5984/{{ db }} | grep "Database does not exist"
      - test `curl -s {{ AUTH }} http://localhost:5984/_membership|jq ".all_nodes[]"|wc -l` = '3'
      - test `curl -s {{ AUTH }} http://localhost:5984/_membership|jq ".cluster_nodes[]"|wc -l` = '3'
{% endfor %}

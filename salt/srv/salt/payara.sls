{% set PAYARA_DIR='/opt/payara' %}
{% set PAYARA_VERSION='4.1.1.171.1' %}
{% set PAYARA_ARTIFACT='payara-4.1.1.171.1.zip' %}
{% set PWD_FILE='/tmp/payara-pwd' %}
{% set ASADMIN='/opt/payara/bin/asadmin' %}

install-systemctl-unitfile:
   file.managed:
    - name: /usr/lib/systemd/system/payara.service
    - source: salt://files/payara/payara.service

download-payara:
  cmd.run:
    - name: curl -o /opt/{{ PAYARA_ARTIFACT }} -L https://search.maven.org/remotecontent?filepath=fish/payara/distributions/payara/{{ PAYARA_VERSION }}/{{ PAYARA_ARTIFACT }}
    - unless: ls /opt/payara-{{ PAYARA_VERSION }}

# If there is a new Payara zip file:
#  stop Payara
#  remove old installation
#  extract new zip file and rename extracted directory sensibly
payara-installed:
  service.dead:
    - name: payara
    - onchanges:
      - download-payara
  cmd.run:
    - name: rm -rf {{ PAYARA_DIR }}
    - onchanges:
      - download-payara
  archive.extracted:
    - name: /opt
    - source: /opt/{{ PAYARA_ARTIFACT }}
    - archive_format: zip
    - unless: ls {{ PAYARA_DIR }}
  file.rename:
    - source: /opt/payara41
    - name: /opt/payara-{{ PAYARA_VERSION }}
    - onlyif: ls /opt/payara41

create-symlink:
  file.symlink:
    - name: {{ PAYARA_DIR }}
    - target: payara-{{ PAYARA_VERSION }}

/opt/{{ PAYARA_ARTIFACT }}:
  file.absent

payara-running:
  service.running:
    - name: payara
    - require: 
       - payara-installed
    - watch:
      - file: /usr/lib/systemd/system/payara.service

make-asadmin-executable:
  cmd.run:
    - name: chmod 755 {{ ASADMIN }}
    - onchanges:
      - payara-installed

create-change-admin-password-file:
  cmd.run:
    - name: printf 'AS_ADMIN_PASSWORD=\nAS_ADMIN_NEWPASSWORD={{ pillar['asadmin-password'] }}\n' > {{ PWD_FILE }}
    - onchanges:
      - payara-installed

set-admin-password:
  cmd.run:
    - name: {{ ASADMIN }} --interactive=false --user admin --passwordfile={{ PWD_FILE }} change-admin-password
    - require:
      - make-asadmin-executable
      - payara-running
    - onchanges:
      - create-change-admin-password-file

create-admin-password-file:
  cmd.run:
    - name: printf 'AS_ADMIN_PASSWORD={{ pillar['asadmin-password'] }}' > {{ PWD_FILE }}
    - onchanges:
      - set-admin-password

# SecureAdmin must be activated for access to the admin console
enable-secure-admin:
  cmd.run:
    - name: {{ ASADMIN }} --interactive=false --user admin --passwordfile={{ PWD_FILE }} enable-secure-admin
    - onchanges:
      - create-admin-password-file

restart-payara-if-secure-admin-was-enabled:
  cmd.run:
    - name: systemctl restart payara && rm -f {{ PWD_FILE }}
    - onchanges:
      - enable-secure-admin

/opt/payara/glassfish/domains/domain1/docroot/index.html:
  file.line:
    - content: "<h2 style=\"color:blue\">{{ pillar['hostname'] }}</h2>"
    - mode: "insert"
    - before: "<img src=\"img/logo.png\" alt=\"Payara Logo\">"
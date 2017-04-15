install_basics_pkgs:
  pkg.installed:
    - pkgs:
      - java-1.8.0-openjdk.x86_64
      - nano
      - net-tools
      - telnet

install_basics_groups:
  pkg.group_installed:
    - name: "Development Tools"

/etc/profile.d/bash.sh:
  file.managed:
    - source: salt://files/basics/bash.sh

Europe/Berlin:
  timezone.system

sshd:
  service.running:
    - enable: True
    - watch:
      - file: /etc/ssh/sshd_config

/etc/ssh/sshd_config:
  file.replace:
    - pattern: "#Port 22"
    - repl: "Port 922"

rsyslog:
  service.running:
    - enable: True
    - watch:
      - file: /etc/rsyslog.conf

enable_rsyslog:
  file.append:
    - name: /etc/rsyslog.conf
    - text: $ModLoad imudp
    - text: $UDPServerRun 514
    - text: $UDPServerAddress 127.0.0.1

firewall_zone_public:
  firewalld.present:
    - name: public
    - block_icmp:
      - echo-reply
      - echo-request
    - ports:
      - 922/tcp
      - 25/tcp
      - 80/tcp
      - 4848/tcp
      - 9000/tcp

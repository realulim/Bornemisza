install_basics_pkgs:
  pkg.installed:
    - pkgs:
      - java-1.8.0-openjdk.x86_64
      - nano
      - net-tools

install_basics_groups:
  pkg.group_installed:
    - name: "Development Tools"

/etc/profile.d/bash.sh:
  file.managed:
    - source: salt://files/basics/bash.sh

# change ssh port to 922
/etc/ssh/sshd_config:
  file.replace:
    - pattern: "#Port 22"
    - repl: "Port 922"

sshd:
  service.running:
    - enable: True
    - reload: True
    - watch:
      - /etc/ssh/sshd_config

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
      - 8080/tcp

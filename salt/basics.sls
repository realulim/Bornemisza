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

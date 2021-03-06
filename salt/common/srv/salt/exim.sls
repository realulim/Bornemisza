install_exim_pkgs:
  pkg.installed:
    - pkgs:
      - exim
      - mailx

change-mta:
  cmd.run:
    - name: /usr/sbin/alternatives --set mta /usr/sbin/sendmail.exim
    - unless: ls -l /etc/alternatives/mta|grep -q exim

exim-running:
  service.running:
    - name: exim
    - enable: true

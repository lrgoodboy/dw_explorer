#!/bin/bash

cp target/scala-2.10/dw-explorer_2.10-0.1.0-SNAPSHOT.war jetty-distribution-8.1.15.v20140411/webapps/explorer.war

SUPERVISOR_HOME=/data2/data-profiling/supervisor
$SUPERVISOR_HOME/venv/bin/supervisorctl -c $SUPERVISOR_HOME/supervisord.conf restart dw_explorer

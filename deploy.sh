#!/bin/bash

scp target/scala-2.10/dw-explorer_2.10-0.1.0-SNAPSHOT.war root@10.20.8.38:/data2/dw_explorer/jetty-distribution-8.1.16.v20140903/webapps/explorer.war

ssh root@10.20.8.38 supervisorctl restart dw_explorer

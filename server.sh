#!/bin/bash

nohup java -server -Xmx1024M -XX:MaxPermSize=128M -XX:+UseConcMarkSweepGC -XX:+CMSClassUnloadingEnabled \
  -jar /data2/data-profiling/jetty-runner-8.1.11.v20130520.jar --port 9081 --path /explorer \
  /data2/data-profiling/dw_explorer/target/scala-2.10/dw-explorer_2.10-0.1.0-SNAPSHOT.war >>server.log 2>&1

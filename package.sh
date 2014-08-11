#!/bin/bash

git pull
VERSION=`git rev-parse HEAD`
sed -i -e s/version=.*/version=$VERSION/g src/main/resources/override/common.properties
./sbt package

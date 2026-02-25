#!/usr/bin/env bash
mvn clean verify -DskipUTs=true -DskipITs=false -Dmaven.javadoc.skip=true "$@"

#!/bin/bash

JAVA_OPTS="-XX:+UseZGC \
           -XX:+ZGenerational \
           -Xmx512m \
           -XX:+UseCompressedOops \
           -XX:+UseCompressedClassPointers \
           -XX:+HeapDumpOnOutOfMemoryError"

./gradlew bootRun --args="$JAVA_OPTS" 
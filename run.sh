#!/bin/bash

# 设置编码环境变量
export JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8"

JAVA_OPTS="-XX:+UseZGC \
           -XX:+ZGenerational \
           -Xmx2g \
           -XX:+UseCompressedOops \
           -XX:+UseCompressedClassPointers \
           -Dfile.encoding=UTF-8 \
           -Dconsole.encoding=UTF-8 \
           -Dspring.output.ansi.enabled=ALWAYS \
           -XX:+HeapDumpOnOutOfMemoryError"

./gradlew bootRun --args="$JAVA_OPTS" 
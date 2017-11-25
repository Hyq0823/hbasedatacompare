#!/bin/bash
for f in ./lib/*.jar; do
  CLASSPATH=${CLASSPATH}:$f;
done
CLASSPATH=./conf:$CLASSPATH
CLASSPATH=./hbasedatacompare-0.0.1-SNAPSHOT.jar:${CLASSPATH}
pidpid=`cat pid`
if kill -0 `cat pid` > /dev/null 2>&1; then
echo  running as process $pidpid . Stop it first.
exit 1
fi
echo ${CLASSPATH}

tablename=$1
export JVM_OPTS="-Xms8192m -Xmx8192m"
nohup /usr/local/jdk1.8.0_60/bin/java $JVM_OPTS -classpath :"$CLASSPATH"  core.hbasedatacompare ${tablename} >log.log  &
echo $! >pid


#!/bin/bash
# Wall Street Eye - Deploy to remote VM
# Usage: ./deploy.sh

set -e

# === Config ===
REMOTE_HOST="10.69.25.76"
REMOTE_USER="root"
REMOTE_PORT="22"
APP_NAME="wall-street-eye"
REMOTE_DIR="/opt/${APP_NAME}"
JAR_NAME="app.jar"
LOCAL_JAR="target/demo-0.0.1-SNAPSHOT.jar"
JAVA_HOME_REMOTE="/usr/lib/jvm/java-21"

echo "=== [1/4] Building project ==="
export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || echo "/Users/liziyang/Library/Java/JavaVirtualMachines/corretto-21.0.10/Contents/Home")
./mvnw package -DskipTests -q
if [ ! -f "$LOCAL_JAR" ]; then
    echo "ERROR: Build failed, jar not found at $LOCAL_JAR"
    exit 1
fi
echo "Build OK: $(ls -lh $LOCAL_JAR | awk '{print $5}')"

echo "=== [2/4] Uploading to ${REMOTE_HOST} ==="
ssh -p ${REMOTE_PORT} ${REMOTE_USER}@${REMOTE_HOST} "mkdir -p ${REMOTE_DIR}"
scp -P ${REMOTE_PORT} ${LOCAL_JAR} ${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}/${JAR_NAME}
echo "Upload OK"

echo "=== [3/4] Stopping old process ==="
ssh -p ${REMOTE_PORT} ${REMOTE_USER}@${REMOTE_HOST} << 'REMOTE_STOP'
PID=$(pgrep -f "wall-street-eye/app.jar" || true)
if [ -n "$PID" ]; then
    echo "Killing old process: $PID"
    kill $PID
    sleep 3
    # Force kill if still running
    kill -9 $PID 2>/dev/null || true
else
    echo "No old process found"
fi
REMOTE_STOP

echo "=== [4/4] Starting new process ==="
ssh -p ${REMOTE_PORT} ${REMOTE_USER}@${REMOTE_HOST} << REMOTE_START
cd ${REMOTE_DIR}

# Auto-detect Java 21
if [ -x "\$(command -v java)" ]; then
    JAVA_VER=\$(java -version 2>&1 | head -1)
    echo "Found Java: \$JAVA_VER"
else
    echo "Java not found, installing..."
    if [ -x "\$(command -v yum)" ]; then
        yum install -y java-21-amazon-corretto-devel 2>/dev/null || yum install -y java-21-openjdk 2>/dev/null
    elif [ -x "\$(command -v apt)" ]; then
        apt update -qq && apt install -y openjdk-21-jdk 2>/dev/null
    fi
fi

nohup java -jar ${REMOTE_DIR}/${JAR_NAME} \\
    --server.port=8080 \\
    --spring.datasource.url=jdbc:h2:file:${REMOTE_DIR}/data/wallstreet \\
    > ${REMOTE_DIR}/app.log 2>&1 &

echo "Started with PID: \$!"
sleep 3
if pgrep -f "${APP_NAME}/app.jar" > /dev/null; then
    echo "=== Deploy SUCCESS ==="
    echo "Access: http://${REMOTE_HOST}:8080/api/intelligences"
else
    echo "=== Deploy FAILED ==="
    tail -20 ${REMOTE_DIR}/app.log
    exit 1
fi
REMOTE_START

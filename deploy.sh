#!/bin/bash
# Wall Street Eye - Deploy to remote VM
# Usage: ./deploy.sh

# === Config ===
REMOTE_HOST="10.69.25.76"
REMOTE_USER="root"
REMOTE_PORT="22"
APP_NAME="wall-street-eye"
REMOTE_DIR="/opt/${APP_NAME}"
JAR_NAME="app.jar"
LOCAL_JAR="target/demo-0.0.1-SNAPSHOT.jar"

echo "=== [1/4] Building project ==="
export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || echo "/Users/liziyang/Library/Java/JavaVirtualMachines/corretto-21.0.10/Contents/Home")
./mvnw package -DskipTests
if [ ! -f "$LOCAL_JAR" ]; then
    echo "ERROR: Build failed"
    exit 1
fi
echo "Build OK: $(ls -lh $LOCAL_JAR | awk '{print $5}')"

echo "=== [2/4] Uploading ==="
ssh -p ${REMOTE_PORT} ${REMOTE_USER}@${REMOTE_HOST} "mkdir -p ${REMOTE_DIR}/data"
scp -P ${REMOTE_PORT} ${LOCAL_JAR} ${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}/${JAR_NAME}
echo "Upload OK"

echo "=== [3/4] Stopping old process ==="
ssh -p ${REMOTE_PORT} ${REMOTE_USER}@${REMOTE_HOST} "pkill -f '${APP_NAME}/app.jar' || true"
sleep 3
echo "Stop done"

echo "=== [4/4] Starting ==="
ssh -p ${REMOTE_PORT} ${REMOTE_USER}@${REMOTE_HOST} "JAVA_HOME=/usr/lib/jvm/java-21-openjdk-21.0.7.0.6-2.el8.x86_64 nohup /usr/lib/jvm/java-21-openjdk-21.0.7.0.6-2.el8.x86_64/bin/java -jar ${REMOTE_DIR}/${JAR_NAME} --server.port=8080 --spring.datasource.url=jdbc:h2:file:${REMOTE_DIR}/data/wallstreet > ${REMOTE_DIR}/app.log 2>&1 &"
echo "Waiting 5s..."
sleep 5

ssh -p ${REMOTE_PORT} ${REMOTE_USER}@${REMOTE_HOST} "pgrep -f '${APP_NAME}/app.jar' > /dev/null" && echo "=== Deploy SUCCESS ===" && echo "http://${REMOTE_HOST}:8080/index.html" || echo "=== Deploy FAILED ===" 

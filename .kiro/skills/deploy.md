---
inclusion: manual
---

# Deploy Wall Street Eye to VM

One-click deploy the project to the remote VM. Handles build, upload, stop old process, and start new one.

## Server Info

| Item | Value |
|------|-------|
| Host | 10.69.25.76 |
| Port | 22 |
| User | root |
| App Dir | /opt/wall-street-eye |
| App Port | 8080 |

## Prerequisites (First Time Only)

### 1. Local: JDK 21

Make sure JAVA_HOME points to JDK 21:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
# Add to ~/.zshrc for persistence
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
```

### 2. Local: SSH Passwordless Login

```bash
# Generate key if you don't have one
ssh-keygen -t rsa -b 4096 -f ~/.ssh/id_rsa -N ""

# Copy to server (enter password once)
ssh-copy-id root@10.69.25.76

# Verify (should print OK without asking password)
ssh root@10.69.25.76 "echo OK"
```

### 3. Remote: JDK 21

The deploy script auto-installs JDK on first run. Or manually:

```bash
# CentOS/RHEL
ssh root@10.69.25.76 "yum install -y java-21-amazon-corretto-devel"

# Ubuntu/Debian
ssh root@10.69.25.76 "apt update && apt install -y openjdk-21-jdk"
```

## Deploy

### Option A: Run deploy script

```bash
./deploy.sh
```

### Option B: Use Kiro hook

Click "Deploy to VM" in the Agent Hooks panel.

### Option C: Manual steps

```bash
# 1. Build
./mvnw package -DskipTests

# 2. Upload
scp target/demo-0.0.1-SNAPSHOT.jar root@10.69.25.76:/opt/wall-street-eye/app.jar

# 3. Stop old
ssh root@10.69.25.76 "pkill -f wall-street-eye/app.jar || true"

# 4. Start new
ssh root@10.69.25.76 "cd /opt/wall-street-eye && nohup java -jar app.jar --server.port=8080 --spring.datasource.url=jdbc:h2:file:/opt/wall-street-eye/data/wallstreet > app.log 2>&1 &"
```

## Operations

```bash
# Check if running
ssh root@10.69.25.76 "pgrep -fa wall-street-eye"

# View logs (live)
ssh root@10.69.25.76 "tail -f /opt/wall-street-eye/app.log"

# View last 100 lines
ssh root@10.69.25.76 "tail -100 /opt/wall-street-eye/app.log"

# Stop
ssh root@10.69.25.76 "pkill -f wall-street-eye/app.jar"

# Restart
ssh root@10.69.25.76 "pkill -f wall-street-eye/app.jar; sleep 2; cd /opt/wall-street-eye && nohup java -jar app.jar --server.port=8080 --spring.datasource.url=jdbc:h2:file:/opt/wall-street-eye/data/wallstreet > app.log 2>&1 &"
```

## Verify

```bash
# Health check
curl http://10.69.25.76:8080/api/stats

# Intelligence list
curl http://10.69.25.76:8080/api/intelligences

# Trigger news collection + clustering
curl -X POST http://10.69.25.76:8080/api/news/collect
```

## Deploy Script Reference

The deploy script is at project root: `deploy.sh`

#[[file:deploy.sh]]

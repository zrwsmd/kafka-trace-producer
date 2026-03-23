#!/bin/bash
# ============================================================
# Kafka 服务器一键安装脚本 (修复版 - 适配 Debian + Java 21)
# 在 AMD64 云服务器上运行
# ============================================================
set -e

KAFKA_VERSION="3.6.1"
SCALA_VERSION="2.13"
KAFKA_HOME="/opt/kafka"
KAFKA_DATA="/data/kafka"

echo "=== 安装 Kafka ${KAFKA_VERSION} ==="

# 1. 检查 Java 版本
echo ">>> 检查 Java..."
if command -v java &> /dev/null; then
    JAVA_VER=$(java -version 2>&1 | head -n 1)
    echo "  已安装: $JAVA_VER"
    
    # 提取主版本号
    JAVA_MAJOR=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F. '{print $1}')
    if [ "$JAVA_MAJOR" -ge 11 ]; then
        echo "  ✓ Java 版本满足要求 (>= 11)"
    else
        echo "  ✗ Java 版本过低，需要 >= 11"
        echo "  正在安装 OpenJDK 11..."
        sudo apt update
        sudo apt install -y default-jre-headless || sudo apt install -y openjdk-17-jre-headless
    fi
else
    echo "  未检测到 Java，正在安装..."
    sudo apt update
    sudo apt install -y default-jre-headless || sudo apt install -y openjdk-17-jre-headless
fi

# 确认安装成功
java -version

# 2. 安装 wget (如果没有)
if ! command -v wget &> /dev/null; then
    echo ">>> 安装 wget..."
    sudo apt install -y wget
fi

# 3. 下载 Kafka
echo ">>> 下载 Kafka ${KAFKA_VERSION}..."
cd /tmp

# 清理旧文件
rm -f kafka.tgz

# 尝试从 Apache 官方镜像下载
echo "  尝试从 Apache 官方下载..."
wget -q --timeout=30 "https://downloads.apache.org/kafka/${KAFKA_VERSION}/kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz" \
    -O kafka.tgz || {
    echo "  官方镜像失败，尝试归档镜像..."
    wget -q --timeout=30 "https://archive.apache.org/dist/kafka/${KAFKA_VERSION}/kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz" \
        -O kafka.tgz || {
        echo "  归档镜像失败，尝试清华镜像..."
        wget -q --timeout=30 "https://mirrors.tuna.tsinghua.edu.cn/apache/kafka/${KAFKA_VERSION}/kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz" \
            -O kafka.tgz
    }
}

if [ ! -f kafka.tgz ]; then
    echo "错误: Kafka 下载失败"
    echo "请手动下载: https://downloads.apache.org/kafka/${KAFKA_VERSION}/kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz"
    echo "然后放到 /tmp/kafka.tgz"
    exit 1
fi

echo "  ✓ 下载完成"

# 4. 解压安装
echo ">>> 安装到 ${KAFKA_HOME}..."
sudo rm -rf ${KAFKA_HOME}
sudo tar -xzf kafka.tgz -C /opt/
sudo mv /opt/kafka_${SCALA_VERSION}-${KAFKA_VERSION} ${KAFKA_HOME}
rm kafka.tgz

# 5. 创建数据目录
echo ">>> 创建数据目录..."
sudo mkdir -p ${KAFKA_DATA}/zookeeper
sudo mkdir -p ${KAFKA_DATA}/kafka-logs

# 6. 创建系统用户
echo ">>> 创建 kafka 用户..."
sudo useradd -r -s /sbin/nologin kafka 2>/dev/null || true
sudo chown -R kafka:kafka ${KAFKA_HOME}
sudo chown -R kafka:kafka ${KAFKA_DATA}

echo ""
echo "=== Kafka 安装完成 ==="
echo "Kafka 目录: ${KAFKA_HOME}"
echo "数据目录:   ${KAFKA_DATA}"
echo "Java 版本:  $(java -version 2>&1 | head -n 1)"
echo ""
echo "下一步: 运行 bash configure-kafka.sh 配置 Kafka"

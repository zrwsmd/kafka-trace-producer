#!/bin/bash
# ============================================================
# 将 Kafka 设为 systemd 服务 (开机自启)
# ============================================================
set -e

KAFKA_HOME="/opt/kafka"

# Zookeeper 服务
sudo tee /etc/systemd/system/zookeeper.service << EOF
[Unit]
Description=Apache Zookeeper
After=network.target

[Service]
Type=simple
User=kafka
ExecStart=${KAFKA_HOME}/bin/zookeeper-server-start.sh ${KAFKA_HOME}/config/zookeeper.properties
ExecStop=${KAFKA_HOME}/bin/zookeeper-server-stop.sh
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# Kafka 服务
sudo tee /etc/systemd/system/kafka.service << EOF
[Unit]
Description=Apache Kafka
After=zookeeper.service
Requires=zookeeper.service

[Service]
Type=simple
User=kafka
ExecStart=${KAFKA_HOME}/bin/kafka-server-start.sh ${KAFKA_HOME}/config/server.properties
ExecStop=${KAFKA_HOME}/bin/kafka-server-stop.sh
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable zookeeper kafka
sudo systemctl start zookeeper
sleep 3
sudo systemctl start kafka
sleep 3

echo "=== systemd 服务已配置 ==="
sudo systemctl status zookeeper --no-pager
sudo systemctl status kafka --no-pager
echo ""
echo "管理命令:"
echo "  sudo systemctl start|stop|restart|status kafka"
echo "  sudo journalctl -u kafka -f"

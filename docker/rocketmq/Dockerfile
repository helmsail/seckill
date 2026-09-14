# RocketMQ 本地镜像（namesrv/broker 共用）
#
# 预建 logs/store 目录且属主 rocketmq(3000)：历史上空命名卷挂载时 Docker 只能
# 创建 root 属主挂载点，导致进程（rocketmq 用户）日志不可写、broker 退出码 253
# 无限重启；现数据统一在容器内，预建目录保证属主正确、进程开箱即可写。
FROM apache/rocketmq:4.9.7
USER root
RUN mkdir -p /home/rocketmq/logs /home/rocketmq/store \
    && chown -R 3000:3000 /home/rocketmq/logs /home/rocketmq/store
USER rocketmq

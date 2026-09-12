# RocketMQ 本地镜像（namesrv/broker 共用）
#
# 官方镜像内不存在 logs/store 目录（运行期由进程自建），空命名卷挂载时
# Docker 只能创建 root 属主挂载点，而进程以 rocketmq(3000) 运行 → 日志不可写，
# broker 退出码 253 无限重启。此处预建目录且属主 3000:3000：
# 新建命名卷首次挂载时 Docker 会复制镜像目录（含属主）进卷，权限天然正确。
FROM apache/rocketmq:4.9.7
USER root
RUN mkdir -p /home/rocketmq/logs /home/rocketmq/store \
    && chown -R 3000:3000 /home/rocketmq/logs /home/rocketmq/store
USER rocketmq

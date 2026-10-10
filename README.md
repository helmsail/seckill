# 秒杀系统(Seckill)

基于 Spring Cloud Alibaba 分布式架构的高并发秒杀系统,业务上划分为**秒杀域与主域**,覆盖 C 端用户"抢购、浏览、支付"全流程与管理端选品运营。

系统按**一组件一机**形态部署于 16 台 4C8G 集群,压测实测(内网、4000 QPS 恒定到达、95% 支付跟进、持续 2 分钟):秒杀受理成功率 **99.98%**、HTTP 失败率 **0%**,提交延迟 **P90 877ms / P99 1.34s**,全机峰值负载 **≤72%**。

---

## 一、项目简介

### 背景与目标

秒杀场景的本质矛盾:瞬时洪峰流量远超系统常态容量。本项目围绕该矛盾做了四件事:

1. **流量治理**:入口限流、多重校验、无效请求快速失败,保证洪峰有序进入;
2. **异步削峰**:受理与下单解耦,消息队列缓冲洪峰,下游按自身能力匀速消费;
3. **交易安全**:防刷、防重、幂等收据,保证不重复扣减、不重复建单;
4. **最终一致**:延迟消息关单回补、定时任务重发兜底、全链路对账。

### 业务形态

- **秒杀域与主域分离**:秒杀域独立承载抢购流量与交易处理,主域提供商品、订单、支付等稳定支撑;
- **双端覆盖**:C 端(抢购、浏览、支付、订单)与管理端(活动、SKU、选品、库存)。

## 二、功能特性

**C 端**

- 用户登录与鉴权
- 活动与商品浏览(活动详情、SKU 库存查询)
- 秒杀抢购与结果轮询
- 在线支付(预支付、支付回调)
- 订单查询与状态跟踪

**管理端**

- 活动管理(创建、生效时段、状态维护)
- SKU 管理(上下架)
- 选品与库存配置

**系统能力**

- 秒杀异步受理与订单异步建单
- 超时未支付自动关单
- 两级缓存与定时预热刷新
- 全链路幂等与对账
- 支付渠道可扩展(默认 Mock,可选支付宝沙箱)
- 全链路追踪与定时补偿任务

## 三、技术栈

| 层面 | 技术 |
|---|---|
| 语言/框架 | Java 21、Spring Boot 3.5、Spring Cloud 2025、Spring Cloud Alibaba 2025 |
| 服务通信 | Dubbo 3.3(注册中心 Nacos) |
| 网关/防护 | Spring Cloud Gateway、Sentinel(限流面板)、JWT(jjwt) |
| 消息 | RocketMQ 2.3.4(订单/关单延迟/订单同步三主题) |
| 存储 | MySQL 8(ShardingSphere 5.5 分片)、Redis 7(Redisson)、MyBatis-Plus 3.5 |
| 缓存 | Caffeine(本地)+ Redis(分布式) |
| 调度 | XXL-Job 2.5 |
| 支付 | Mock 渠道(默认)、支付宝沙箱(可选启用,alipay-easysdk) |
| 部署/压测 | Docker Compose(单机一键 + 16 台集群)、k6 |

## 四、系统架构

### 4.1 模块职责

| 模块 | 职责 |
|---|---|
| `seckill-common` | 公共底座:链路追踪(traceId 贯穿)、统一结果与异常、JWT、Redis/MyBatis/Dubbo 增强等 |
| `seckill-gateway` | 网关:JWT 鉴权、Sentinel 限流(service/read/admin 三档)、路由转发 |
| `seckill-service` | C 端业务服务:秒杀受理、浏览查询(两级缓存)、订单与支付入口 |
| `seckill-processor` | 秒杀域消费:RocketMQ 消费、二次校验、分片建单、幂等收据 |
| `seckill-base` | 基础域(api + server):活动、SKU、库存 |
| `support` | 支撑域(api + server):用户、订单、支付 |
| `seckill-job` | 定时任务(XXL-Job):缓存预热、关单/同步重发、库存与信息清理 |
| `seckill-admin` | 运营端接口:活动、SKU、选品管理 |
| `seckill-web` | 前端:C 端与管理端静态页、nginx 反向代理(同源免跨域) |

### 4.2 运行时拓扑

```
浏览器 ──> nginx(web) ──/api──> gateway(JWT 鉴权 + Sentinel 限流)
                                    │
                ┌───────────────────┼───────────────────┐
                ▼                   ▼                   ▼
          service(受理/浏览)     admin(运营端)      support(用户/订单/支付)
                │ 受理成功发 MQ                        ▲
                ▼                                     │ 支付成功订单同步
          seckill-order-topic ──> processor ──────────┘
                                   │ 二次校验/分片建单
                                   ├─> seckill-close-order-topic(延迟关单)
                                   └─> MySQL(分片存储)
          base(活动/SKU/库存) <──> Redis(缓存/令牌/凭据)
          job(XXL 定时: 预热/重发/清理)
          Nacos(注册中心) · Sentinel(限流) · XXL(调度)
```

## 五、快速启动

### 5.1 方式一:本地一键(单机全组件)

**前置要求**

| 项 | 要求 |
|---|---|
| JDK | 21 |
| Maven | 3.9+ |
| Docker | 含 compose v2 |
| 内存 | 建议宿主机 ≥ 8G(全组件同机) |

**步骤**

```bash
# 1. 构建各服务可执行 jar(父工程聚合构建)
mvn package -DskipTests

# 2. 准备配置(唯一配置源);Windows 用 copy .env.example .env
cp .env.example .env
#    必填四项:
#      HOST_IP=<本机内网 IPv4(XXL 执行器回调与雪花机器号依据)>
#      MYSQL_PASSWORD=<自定义>
#      JWT_SECRET=<任意足够长随机串>
#      NACOS_AUTH_TOKEN=<openssl rand -base64 48 生成(须 ≥32 字节)>
#    其余项保持默认即可

# 3. 启动(首次自动初始化 MySQL:建库建表 + 种子数据,见 deploy/initdb/)
docker compose up -d --build
```

**访问入口**

| 入口 | 地址 | 说明 |
|---|---|---|
| C 端页面 | http://localhost:18087 | 抢购/浏览/支付 |
| 运营端页面 | http://localhost:18087/admin.html | 活动/选品/库存管理 |
| XXL-Job 调度台 | http://localhost:8080/xxl-job-admin | 定时任务管理 |
| Nacos 控制台 | http://localhost:8848/nacos | 服务注册与配置 |
| Sentinel 面板 | http://localhost:8858 | 限流规则与监控 |
| 网关直连 | http://localhost:18080 | 接口调试用 |

**验证**

1. 打开 C 端页面,用 `deploy/initdb/seckill-init.sql` 中的种子账号登录;
2. 在运营端确认活动已配置(有 SKU 与库存);
3. C 端参与秒杀,页面将轮询出单结果;如需支付,点击支付走 Mock 渠道闭环。

**停止与清理**

```bash
docker compose stop      # 停止(数据保留)
docker compose restart   # 重启(数据保留)
docker compose down      # 删除全部容器(下次 up 重新初始化,数据重置)
```

### 5.2 方式二:分布式集群(16 台生产形态)

**机器规格**(详见 `deploy/部署架构.md`)

| 角色 | 台数 | 规格 | 组件 |
|---|---|---|---|
| web | 1 | 4C8G | nginx |
| gateway | 3 | 4C8G | seckill-gateway |
| service | 3 | 4C8G | seckill-service |
| base | 4 | 4C8G | seckill-base |
| processor | 1 | 4C8G | seckill-processor |
| support | 1 | 4C8G | seckill-support |
| redis | 1 | 4C8G | Redis |
| mixmq | 1 | 4C8G | RocketMQ + Nacos + Sentinel + XXL-Job + job + admin |
| mysql | 1 | 4C8G | MySQL |
| k6 | 1 | 16C32G | 压测机(非生产) |

**前置要求**

- 各机安装 Ubuntu,root SSH 可达;
- 本地机器:Python 3 + `pip install paramiko`;
- 应用镜像已构建并推送至镜像仓库(各模块附带 Dockerfile)。

**配置**

```bash
cp deploy/.env.example deploy/.env
# 填写:各节点 IP(NODE_*)、凭据、镜像仓库等
# deploy/ 是集群部署的唯一事实源,由 deploy_push.py 渲染并分发到各机
```

**部署流程(按序执行)**

| 步 | 命令 | 说明 |
|---|---|---|
| ① | `python scripts/check_conn.py` | 验证全部节点(16 角色 + 压测机)SSH 连通 |
| ② | `python scripts/check_docker.py` | 检测各机 Docker 及版本(可选,排查用) |
| ③ | `python scripts/install_docker.py` | 未装的机器安装 Docker 并配置日志轮转(幂等) |
| ④ | `python scripts/deploy_push.py` | 渲染 .env 并分发 compose/配置到目标角色 |
| ⑤ | `python scripts/pull.py` | 各机从镜像仓库拉取最新镜像(不启动) |
| ⑥ | `python scripts/up.py` | 重建启动(全部 16 角色;支持指定单角色) |
| ⑦ | `python scripts/check_up.py` | 检查各机容器编排状态 |
| ⑧ | `python scripts/smoke_test.py` | 冒烟验证:登录 → 库存检查 → 提交 → 出单 |
| ⑨ | `python scripts/bench.py 4000 2m` | k6 压测:4000 QPS × 2 分钟,含全机监控与报告 |

**常用运维**

```bash
python scripts/restart.py           # 轻量重启(保留数据,日常重启用)
python scripts/up.py svc1           # 仅重建单个角色(如 svc1)
python scripts/check_up.py          # 随时查看 16 台容器状态
python scripts/check_docker.py      # 查看各机 Docker 状态
```

> ⚠️ **语义说明**:`up.py` 是「重建即重置」——每次执行 = 全新环境(MySQL 重新 initdb、中间件清空),压测前复位常用;仅想保数据重启请用 `restart.py`。

## 六、配置说明

**唯一配置源**:本地 = 根目录 `.env`,集群 = `deploy/.env`;均由对应 `.env.example` 拷贝后填写。compose 自动读取、应用经 `env_file` 注入,不再内置默认值。以下按模板注释逐区说明:

```bash
# ============ 【A】本机标识与运行(HOST_IP 必填) ============
HOST_IP=                          # 本机内网 IPv4;XXL 执行器回调地址与雪花机器号的派生依据
JAVA_TOOL_OPTIONS=-Xms128m -Xmx384m   # 应用统一 JVM 堆(本地 8G 环境建议值)

# ============ 【B】凭据(建议全部自定义) ============
MYSQL_USERNAME=root
MYSQL_PASSWORD=                   # 必填:数据库密码
NACOS_USERNAME=nacos
NACOS_PASSWORD=nacos
NACOS_AUTH_TOKEN=                 # 必填:base64 且解码后 ≥32 字节(openssl rand -base64 48)
NACOS_AUTH_IDENTITY_KEY=seckill-auth-identity-key
NACOS_AUTH_IDENTITY_VALUE=seckill-auth-identity-value
JWT_SECRET=                       # 必填:gateway/base/admin/support 四模块必须一致
JWT_EXPIRATION=86400000           # Token 有效期(毫秒)

# ============ 【C】地址(本地填容器服务名;集群由推送脚本渲染为节点 IP) ============
MYSQL_HOST=mysql
REDIS_HOST=redis
NACOS_ADDR=nacos:8848
ROCKETMQ_ADDR=rocketmq-namesrv:9876
SENTINEL_DASHBOARD_ADDR=sentinel-dashboard:8858
XXL_ADMIN_ADDR=http://xxl-job-admin:8080/xxl-job-admin
MOCK_PAY_CALLBACK_URL=http://localhost:18087/api/c/pay/callback/mock   # 模拟支付回调(浏览器可达地址)
MOCK_PAY_STATUS_URL=http://localhost:18087/api/c/order/status

# ============ 【D】端口(宿主映射与容器监听一致) ============
GATEWAY_PORT=18080                # 网关
BASE_SERVER_PORT=18081            # 基础服务(另有 Dubbo 协议端口 28081,仅容器网络内)
ADMIN_PORT=18082                  # 运营端接口
JOB_PORT=18083                    # 定时任务(另有 XXL 执行器回调端口 19999)
SERVICE_PORT=18084                # C 端业务服务
PROCESSOR_PORT=18085              # 秒杀处理器
SUPPORT_PORT=18086                # 支撑服务(另有 Dubbo 协议端口 28080)
WEB_PORT=18087                    # nginx 入口(C 端页面与 /api 反向代理)
MYSQL_PORT=3306
REDIS_PORT=6379
XXL_ADMIN_PORT=8080               # XXL-Job 调度台
SENTINEL_PORT=8858                # Sentinel 面板

# ============ 【E】JVM(中间件各自堆;应用统一堆见【A】) ============
NACOS_JVM_XMS=256m
NACOS_JVM_XMX=512m
NAMESRV_JAVA_OPT_EXT=-Xms256m -Xmx256m
BROKER_JAVA_OPT_EXT=-Xms512m -Xmx512m
SENTINEL_JAVA_OPTS=-Xmx384m

# ============ 【F】网关限流(单实例阈值 = 该档总目标) ============
GATEWAY_QPS_SERVICE=6000          # 写类(秒杀提交等)
GATEWAY_QPS_READ=12000            # 读类(浏览/查询)
GATEWAY_QPS_ADMIN=500             # 管理端

# ============ 【G】选配:支付宝沙箱(需整段启用;单独留空会被视为已配置导致误装配) ============
# ALIPAY_APP_ID=
# ALIPAY_PRIVATE_KEY=
# ALIPAY_PUBLIC_KEY=
# ALIPAY_NOTIFY_URL=
# ALIPAY_GATEWAY_URL=
```

集群模式另有压测参数:`SECKILL_RATE`(到达率)、`PRE_VUS` / `MAX_VUS`(虚拟用户),定义在 `deploy/.env`,由 `bench.py` 自动同步到压测机。

## 七、目录结构

```
seckill/
├── docker-compose.yml          # 本地一键编排(单机全组件,与集群同源)
├── .env.example                # 本地配置模板(唯一配置源)
├── seckill-common/             # 公共底座(追踪/结果/异常/JWT/Redis/MyBatis/Dubbo)
├── seckill-gateway/            # 网关(JWT + Sentinel 限流 + 路由)
├── seckill-service/            # C 端业务(受理/浏览/支付入口)
├── seckill-processor/          # 秒杀域消费(二次校验/建单/幂等)
├── seckill-base/               # 基础域 api+server(活动/SKU/库存)
├── support/                    # 支撑域 api+server(用户/订单/支付)
├── seckill-job/                # XXL 定时任务(预热/重发/清理)
├── seckill-admin/              # 运营端接口
├── seckill-web/                # 前端 nginx(C 端 + 管理端静态页)
├── docker/                     # 基础镜像构建(mysql/rocketmq)
├── deploy/                     # 集群编排(compose/.env/initdb/k6/架构文档)
│   ├── initdb/                 #   数据库初始化脚本(建库建表/种子/xxl-job)
│   ├── k6/                     #   压测脚本
│   └── 部署架构.md              #   16 台角色与规格
└── scripts/                    # 运维脚本(①~⑨ 生命周期 + 压测)
    ├── common.py               #   公共库(SSH/配置渲染/并发执行)
    ├── check_conn.py           #   ① 连通性
    ├── check_docker.py         #   ② Docker 检测
    ├── install_docker.py       #   ③ Docker 安装
    ├── deploy_push.py          #   ④ 配置分发
    ├── pull.py                 #   ⑤ 拉取镜像
    ├── up.py                   #   ⑥ 重建启动
    ├── check_up.py             #   ⑦ 状态检查
    ├── smoke_test.py           #   ⑧ 冒烟验证
    ├── bench.py                #   ⑨ 压测与监控
    └── restart.py              #   轻量重启(保数据)
```


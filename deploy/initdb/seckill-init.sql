-- ============================================================
-- 秒杀系统数据库初始化脚本（单文件集中维护）
--
-- 执行方式：
--   1. docker compose 首次启动自动执行（mysql 容器 initdb 目录挂载本目录）
--   2. 手工执行：mysql -uroot -p < seckill-init.sql（脚本内自建库并切换）
--
-- 内容顺序：建库 → 秒杀域表（sk_*）→ 主域表（t_*）→ 基础数据
-- ============================================================

-- 客户端字符集：mysql 客户端默认可能为 latin1，中文 UTF-8 字节会被误判超长（Data too long）
SET NAMES utf8mb4;

-- ---------- 建库 ----------
-- 业务库（docker compose 场景由 MYSQL_DATABASE 自动创建，此处兼容手工执行）
CREATE DATABASE IF NOT EXISTS seckill DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- 调度库（xxl-job 表结构见同目录 xxl-job-base.sql、调度规则见 xxl-job-init.sql，均自动导入，无需手工操作；默认登录账号 admin/123456）
CREATE DATABASE IF NOT EXISTS xxl_job DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE seckill;

-- ============================ 秒杀域表（seckill-base） ============================

CREATE TABLE IF NOT EXISTS sk_activity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_no VARCHAR(32) NOT NULL COMMENT '活动编号',
    activity_name VARCHAR(50) NOT NULL COMMENT '活动名称',
    start_date DATE NOT NULL COMMENT '开始日期',
    end_date DATE NOT NULL COMMENT '结束日期',
    start_time TIME NOT NULL DEFAULT '00:00:00' COMMENT '当天开始时间（禁止跨天）',
    end_time TIME NOT NULL DEFAULT '23:59:59' COMMENT '当天结束时间',
    week_bitmap TINYINT NOT NULL DEFAULT 127 COMMENT '周位图：bit0=周一…bit6=周日（127=每天；21=周一/三/五）',
    purchase_limit TINYINT NOT NULL DEFAULT 0 COMMENT '每人限购数量，0=不限购',
    activity_status TINYINT NOT NULL DEFAULT 0 COMMENT '活动状态：0=待开始，1=进行中，2=已暂停，3=已关闭',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
    UNIQUE KEY uk_activity_no (activity_no),
    KEY idx_activity_status (activity_status),
    KEY idx_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动表';

CREATE TABLE IF NOT EXISTS sk_product_sku (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_no VARCHAR(32) NOT NULL COMMENT '活动编号',
    spu_no VARCHAR(32) NOT NULL COMMENT 'SPU编号（快照）',
    spu_name VARCHAR(100) NOT NULL COMMENT 'SPU名称快照',
    sku_no VARCHAR(32) NOT NULL COMMENT 'SKU编号（主域）',
    sku_name VARCHAR(100) NOT NULL COMMENT 'SKU名称快照',
    discount_type TINYINT NOT NULL DEFAULT 0 COMMENT '折扣类型：0=固定秒杀价，1=折扣，2=固定扣减',
    discount_parameter DECIMAL(10,2) DEFAULT NULL COMMENT '折扣参数',
    original_price DECIMAL(10,2) NOT NULL COMMENT '原价',
    seckill_price DECIMAL(10,2) NOT NULL COMMENT '秒杀价（自动计算）',
    activity_stock INT NOT NULL DEFAULT 0 COMMENT '秒杀库存（主域划拨）',
    purchase_limit INT NOT NULL DEFAULT 0 COMMENT 'SKU级别限购，0=不限购',
    shelf_status TINYINT NOT NULL DEFAULT 1 COMMENT '上架状态：0=下架，1=上架',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_activity_sku_no (activity_no, sku_no),
    KEY idx_activity_no (activity_no),
    KEY idx_sku_no (sku_no),
    KEY idx_activity_shelf (activity_no, shelf_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动商品SKU表（物理删除）';

-- 秒杀订单分片表（sk_order_0 ~ sk_order_3）
CREATE TABLE IF NOT EXISTS sk_order_0 (
    id BIGINT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL COMMENT '订单编号',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    activity_no VARCHAR(32) NOT NULL COMMENT '活动编号（关单回补/对账溯源）',
    sku_no VARCHAR(32) NOT NULL COMMENT 'SKU编号（关单回补/对账溯源）',
    quantity INT NOT NULL DEFAULT 1 COMMENT '购买数量',
    total_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '原价',
    pay_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '实付金额',
    order_status TINYINT NOT NULL DEFAULT 0 COMMENT '订单状态：0=待支付，1=已支付，2=已关闭',
    paid_time DATETIME DEFAULT NULL COMMENT '支付时间',
    trade_no VARCHAR(64) DEFAULT NULL COMMENT '第三方支付流水号',
    trace_id VARCHAR(64) DEFAULT NULL COMMENT '幂等键（同一次秒杀请求全局唯一，网关生成；NULL 不参与唯一约束）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_user_trace (user_id, trace_id),
    KEY idx_user_id (user_id),
    KEY idx_trade_no (trade_no),
    KEY idx_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单表0';

CREATE TABLE IF NOT EXISTS sk_order_1 LIKE sk_order_0;
CREATE TABLE IF NOT EXISTS sk_order_2 LIKE sk_order_0;
CREATE TABLE IF NOT EXISTS sk_order_3 LIKE sk_order_0;

-- ============================ 主域表（support） ============================

CREATE TABLE IF NOT EXISTS t_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    spu_no VARCHAR(32) NOT NULL COMMENT '商品编号',
    product_name VARCHAR(100) NOT NULL COMMENT '商品名称',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
    UNIQUE KEY uk_spu_no (spu_no),
    KEY idx_product_name (product_name),
    KEY idx_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

CREATE TABLE IF NOT EXISTS t_sku (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    spu_no VARCHAR(32) NOT NULL COMMENT '关联商品编号',
    sku_no VARCHAR(32) NOT NULL COMMENT 'SKU编号',
    sku_name VARCHAR(100) NOT NULL COMMENT 'SKU名称',
    price DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '销售价',
    stock INT NOT NULL DEFAULT 0 COMMENT '总库存',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
    UNIQUE KEY uk_sku_no (sku_no),
    KEY idx_spu_no (spu_no),
    KEY idx_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SKU表';

CREATE TABLE IF NOT EXISTS t_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码',
    role TINYINT NOT NULL DEFAULT 0 COMMENT '角色：0=C端用户，1=管理员',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
    UNIQUE KEY uk_username (username),
    KEY idx_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS t_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL COMMENT '订单编号',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    order_source VARCHAR(32) NOT NULL COMMENT '订单来源（主域/秒杀域）',
    total_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '原价',
    pay_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '实付金额',
    paid_time DATETIME DEFAULT NULL COMMENT '支付时间',
    trade_no VARCHAR(64) DEFAULT NULL COMMENT '第三方支付流水号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_id (user_id),
    KEY idx_trade_no (trade_no),
    KEY idx_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ============================ 压测种子数据（唯一数据集） ============================
--
-- 本文件数据部分仅包含压测（k6）与演示所需，随容器冷启动自动重建，保证每轮压测环境完全一致：
--   1. 主域商品：12 个中文商品（白酒/粮油/水果/生鲜/运动/服饰/家电/零食/图书等），各 2 个 SKU；
--   2. 压测活动：LT-LOADTEST-001，开始时间 = 初始化时刻 + 5 分钟（相对时间，跨天自动拆分），
--      结束 2030-12-31 全天（周位图 127）、不限购、挂 6 个不同品类 SKU 各 100 万库存；
--      为什么必须留未来时间：库存键只在"待开始且距开始 <=30 分钟"的预热窗口内由 activityCacheJob
--      初始化——开始时刻设成过去会跳过预热窗口，激活后库存键永远缺失、秒杀全部失败；
--      完整链路：冷启动初始化 -> 应用启动（3~5 分钟）-> cacheJob 预热（写快照/在售/库存）
--      -> 到点 activityStatusJob 自动激活 -> 开始压测（约在冷启动后 5 分钟；激活后长期有效，
--      之后随压随测，无需再次等待）；
--   3. 用户：运营账号 3 个（管理端演示）+ 压测用户池 lt0001 ~ lt5000（密码 123456，C 端）——
--      用户级令牌桶 1 QPS/用户，5000 个账号支撑 5000 VU 并发上限；
--   4. 验收口径（逐 SKU 独立核对）：成功订单数 + Redis 剩余库存 = 划拨库存 100 万
--      （不超卖即成功数 <= 100 万；正常压测不会售罄）。

-- ---------- 主域商品（12 个，中文多品类） ----------
INSERT INTO t_product (spu_no, product_name) VALUES
('1752035129379131392', '贵州茅台 飞天 53度'),
('1752035133573435393', '五常大米 稻花香2号'),
('1752035137767739394', '云南褚橙 冰糖橙'),
('1752035141962043395', '内蒙古锡盟羔羊排'),
('1752035146156347396', '安踏 氮科技跑步鞋'),
('1752035150350651397', '优衣库 精梳棉短袖T恤'),
('1752035154544955398', '小熊 智能电饭煲'),
('1752035158739259399', '戴森 吹风机 HD15'),
('1752035162933563400', '小米 空气净化器 5'),
('1752035167127867401', '三只松鼠 每日坚果大礼包'),
('1752035171322171402', '《三体》全集 典藏版'),
('1752035175516475403', '海尔 滚筒洗衣机 10kg');

INSERT INTO t_sku (spu_no, sku_no, sku_name, price, stock) VALUES
('1752035129379131392', '2752035979942170624', '53度 500ml 单瓶装', 2699.00, 1000),
('1752035129379131392', '2752035984136474625', '53度 500ml 双瓶礼盒', 5199.00, 1000),
('1752035133573435393', '2752035988330778626', '稻花香2号 5kg 装', 89.00, 1000),
('1752035133573435393', '2752035992525082627', '稻花香2号 10kg 装', 159.00, 1000),
('1752035137767739394', '2752035996719386628', '冰糖橙 5kg 装', 108.00, 1000),
('1752035137767739394', '2752036000913690629', '冰糖橙 10kg 装', 198.00, 1000),
('1752035141962043395', '2752036005107994630', '羔羊排 1kg 装', 128.00, 1000),
('1752035141962043395', '2752036009302298631', '羔羊排 2kg 装', 238.00, 1000),
('1752035146156347396', '2752036013496602632', '跑步鞋 42码 黑色', 499.00, 1000),
('1752035146156347396', '2752036017690906633', '跑步鞋 43码 白色', 499.00, 1000),
('1752035150350651397', '2752036021885210634', '短袖T恤 S码 白色', 99.00, 1000),
('1752035150350651397', '2752036026079514635', '短袖T恤 M码 黑色', 99.00, 1000),
('1752035154544955398', '2752036030273818636', '电饭煲 3L 米白色', 329.00, 1000),
('1752035154544955398', '2752036034468122637', '电饭煲 4L 灰色', 399.00, 1000),
('1752035158739259399', '2752036038662426638', '吹风机 HD15 紫红色', 2999.00, 1000),
('1752035158739259399', '2752036042856730639', '吹风机 HD15 星耀金', 2999.00, 1000),
('1752035162933563400', '2752036047051034640', '空气净化器 5 标准版', 1499.00, 1000),
('1752035162933563400', '2752036051245338641', '空气净化器 5 Pro版', 1999.00, 1000),
('1752035167127867401', '2752036055439642642', '每日坚果 8袋装', 139.00, 1000),
('1752035167127867401', '2752036059633946643', '每日坚果 16袋装', 259.00, 1000),
('1752035171322171402', '2752036063828250644', '《三体》全集 精装版', 168.00, 1000),
('1752035171322171402', '2752036068022554645', '《三体》全集 典藏礼盒', 328.00, 1000),
('1752035175516475403', '2752036072216858646', '洗衣机 10kg 标准版', 2299.00, 1000),
('1752035175516475403', '2752036076411162647', '洗衣机 10kg 洗烘一体', 3299.00, 1000);

-- ---------- 压测活动 + 活动商品划拨（6 个品类 SKU × 100 万库存） ----------
INSERT INTO sk_activity
    (activity_no, activity_name, start_date, end_date, start_time, end_time, week_bitmap, purchase_limit, activity_status)
VALUES
    ('LT-LOADTEST-001', '压测活动（冷启动 5 分钟后开始）',
     DATE(DATE_ADD(NOW(), INTERVAL 5 MINUTE)), '2030-12-31',
     TIME(DATE_ADD(NOW(), INTERVAL 5 MINUTE)), '23:59:59', 127, 0, 0);

INSERT INTO sk_product_sku
    (activity_no, spu_no, spu_name, sku_no, sku_name, discount_type, discount_parameter,
     original_price, seckill_price, activity_stock, purchase_limit, shelf_status)
VALUES
    ('LT-LOADTEST-001', '1752035129379131392', '贵州茅台 飞天 53度', '2752035979942170624', '53度 500ml 单瓶装',
     0, NULL, 2699.00, 2399.00, 1000000, 0, 1),
    ('LT-LOADTEST-001', '1752035133573435393', '五常大米 稻花香2号', '2752035988330778626', '稻花香2号 5kg 装',
     0, NULL, 89.00, 69.00, 1000000, 0, 1),
    ('LT-LOADTEST-001', '1752035137767739394', '云南褚橙 冰糖橙', '2752035996719386628', '冰糖橙 5kg 装',
     0, NULL, 108.00, 88.00, 1000000, 0, 1),
    ('LT-LOADTEST-001', '1752035146156347396', '安踏 氮科技跑步鞋', '2752036013496602632', '跑步鞋 42码 黑色',
     0, NULL, 499.00, 399.00, 1000000, 0, 1),
    ('LT-LOADTEST-001', '1752035162933563400', '小米 空气净化器 5', '2752036047051034640', '空气净化器 5 标准版',
     0, NULL, 1499.00, 1199.00, 1000000, 0, 1),
    ('LT-LOADTEST-001', '1752035167127867401', '三只松鼠 每日坚果大礼包', '2752036055439642642', '每日坚果 8袋装',
     0, NULL, 139.00, 99.00, 1000000, 0, 1);

-- ---------- 用户：运营 3 个（管理端演示）+ 压测用户池 20000 个（循环生成） ----------
INSERT INTO t_user (username, password, role) VALUES
('admin', 'admin123', 1),
('operator01', '123456', 1),
('operator02', '123456', 1);

-- 压测用户池（循环生成）：lt00001 ~ lt20000，密码 123456，C 端用户
INSERT INTO t_user (username, password, role)
SELECT CONCAT('lt', LPAD(n, 5, '0')), '123456', 0
FROM (
  SELECT a.n + b.n * 10 + c.n * 100 + d.n * 1000 + e.n * 10000 + 1 AS n
  FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
  CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
  CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c
  CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d
  CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) e
) seq
WHERE n <= 20000;


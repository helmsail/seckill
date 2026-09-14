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
    role TINYINT NOT NULL DEFAULT 0 COMMENT '角色：0=C端用户，1=运营人员',
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

-- ============================ 基础数据 ============================

INSERT INTO t_product (spu_no, product_name) VALUES
('1752035129379131392', 'iPhone 15'),
('1752035133573435393', 'iPhone 15 Pro'),
('1752035137767739394', 'iPhone 15 Pro Max'),
('1752035141962043395', 'iPhone 14'),
('1752035146156347396', 'iPhone 14 Pro'),
('1752035150350651397', 'MacBook Air M3'),
('1752035154544955398', 'MacBook Pro 14 M3'),
('1752035158739259399', 'MacBook Pro 16 M3'),
('1752035162933563400', 'iPad Air M2'),
('1752035167127867401', 'iPad Pro M4'),
('1752035171322171402', 'iPad mini 6'),
('1752035175516475403', 'Apple Watch Series 9');

INSERT INTO t_sku (spu_no, sku_no, sku_name, price, stock) VALUES
('1752035129379131392', '2752035979942170624', 'SKU 1-1', 100.00, 100),
('1752035129379131392', '2752035984136474625', 'SKU 1-2', 100.00, 100),
('1752035133573435393', '2752035988330778626', 'SKU 2-1', 100.00, 100),
('1752035133573435393', '2752035992525082627', 'SKU 2-2', 100.00, 100),
('1752035137767739394', '2752035996719386628', 'SKU 3-1', 100.00, 100),
('1752035137767739394', '2752036000913690629', 'SKU 3-2', 100.00, 100),
('1752035141962043395', '2752036005107994630', 'SKU 4-1', 100.00, 100),
('1752035141962043395', '2752036009302298631', 'SKU 4-2', 100.00, 100),
('1752035146156347396', '2752036013496602632', 'SKU 5-1', 100.00, 100),
('1752035146156347396', '2752036017690906633', 'SKU 5-2', 100.00, 100),
('1752035150350651397', '2752036021885210634', 'SKU 6-1', 100.00, 100),
('1752035150350651397', '2752036026079514635', 'SKU 6-2', 100.00, 100),
('1752035154544955398', '2752036030273818636', 'SKU 7-1', 100.00, 100),
('1752035154544955398', '2752036034468122637', 'SKU 7-2', 100.00, 100),
('1752035158739259399', '2752036038662426638', 'SKU 8-1', 100.00, 100),
('1752035158739259399', '2752036042856730639', 'SKU 8-2', 100.00, 100),
('1752035162933563400', '2752036047051034640', 'SKU 9-1', 100.00, 100),
('1752035162933563400', '2752036051245338641', 'SKU 9-2', 100.00, 100),
('1752035167127867401', '2752036055439642642', 'SKU 10-1', 100.00, 100),
('1752035167127867401', '2752036059633946643', 'SKU 10-2', 100.00, 100),
('1752035171322171402', '2752036063828250644', 'SKU 11-1', 100.00, 100),
('1752035171322171402', '2752036068022554645', 'SKU 11-2', 100.00, 100),
('1752035175516475403', '2752036072216858646', 'SKU 12-1', 100.00, 100),
('1752035175516475403', '2752036076411162647', 'SKU 12-2', 100.00, 100);

INSERT INTO t_user (username, password, role) VALUES
('admin', 'admin123', 1),
('operator01', '123456', 1),
('operator02', '123456', 1),
('zhangsan', '123456', 0),
('lisi', '123456', 0),
('wangwu', '123456', 0);

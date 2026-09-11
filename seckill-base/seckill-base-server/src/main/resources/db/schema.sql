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
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
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
    total_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '原价',
    pay_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '实付金额',
    order_status TINYINT NOT NULL DEFAULT 0 COMMENT '订单状态：0=待支付，1=已支付，2=已关闭',
    paid_time DATETIME DEFAULT NULL COMMENT '支付时间',
    trade_no VARCHAR(64) DEFAULT NULL COMMENT '第三方支付流水号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_id (user_id),
    KEY idx_trade_no (trade_no),
    KEY idx_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单表0';

CREATE TABLE IF NOT EXISTS sk_order_1 LIKE sk_order_0;
CREATE TABLE IF NOT EXISTS sk_order_2 LIKE sk_order_0;
CREATE TABLE IF NOT EXISTS sk_order_3 LIKE sk_order_0;

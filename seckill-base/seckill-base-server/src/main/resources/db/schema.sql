CREATE TABLE IF NOT EXISTS sk_activity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_no VARCHAR(32) NOT NULL COMMENT '活动编号',
    activity_name VARCHAR(50) NOT NULL COMMENT '活动名称',
    start_time DATETIME NOT NULL COMMENT '开始时间',
    end_time DATETIME NOT NULL COMMENT '结束时间',
    effective_type TINYINT NOT NULL DEFAULT 0 COMMENT '生效类型：0=不限，1=周期性',
    effective_days VARCHAR(20) DEFAULT NULL COMMENT '生效日期（如 1,3,5）',
    effective_start TIME DEFAULT NULL COMMENT '每日生效开始时间',
    effective_end TIME DEFAULT NULL COMMENT '每日生效结束时间',
    purchase_limit TINYINT NOT NULL DEFAULT 0 COMMENT '每人限购数量，0=不限购',
    activity_status TINYINT NOT NULL DEFAULT 0 COMMENT '活动状态：0=待开始，1=进行中，2=已暂停，3=已结束',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
    UNIQUE KEY uk_activity_no (activity_no),
    KEY idx_activity_status (activity_status),
    KEY idx_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动表';

CREATE TABLE IF NOT EXISTS sk_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_no VARCHAR(32) NOT NULL COMMENT '活动编号',
    spu_no VARCHAR(32) NOT NULL COMMENT 'SPU编号（溯源）',
    spu_name VARCHAR(100) NOT NULL COMMENT '商品名称快照',
    discount_type TINYINT NOT NULL DEFAULT 0 COMMENT '折扣类型：0=固定秒杀价，1=折扣，2=固定扣减',
    discount_parameter DECIMAL(10,2) DEFAULT NULL COMMENT '折扣参数',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序顺序',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
    KEY idx_activity_no (activity_no),
    KEY idx_spu_no (spu_no),
    KEY idx_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀商品表';

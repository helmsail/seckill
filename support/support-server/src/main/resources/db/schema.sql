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

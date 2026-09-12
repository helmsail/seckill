package com.helmsail.seckill.support.server.sku;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SKU 库存变更流水（幂等闸：request_id 唯一约束，与库存更新同事务）
 *
 * 不继承 BaseEntity：insert-only 表，无需更新/逻辑删除字段；
 * create_time 由数据库默认填充，插入时不显式写入。
 */
@Data
@TableName("t_stock_log")
public class StockLog {

    private Long id;

    /** 幂等键（调用方生成，全局唯一） */
    private String requestId;

    private String skuNo;

    /** 变更类型：1=扣减，2=归还 */
    private Integer changeType;

    private Integer quantity;

    private LocalDateTime createTime;
}

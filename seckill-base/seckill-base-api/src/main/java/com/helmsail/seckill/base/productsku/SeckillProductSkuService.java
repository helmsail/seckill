package com.helmsail.seckill.base.productsku;

import java.util.List;

/**
 * 活动商品SKU Dubbo 服务接口
 *
 * sk_product_sku 为活动与 SKU 的聚合配置表（物理删除）。
 * 状态规则收敛在服务端：添加/删除仅待开始；上架/下架非终态可用。
 */
public interface SeckillProductSkuService {

    /**
     * 批量添加（仅待开始状态可用）
     *
     * 主域库存划拨由调用方先行完成；本服务失败时调用方负责补偿归还。
     */
    void batchAdd(AddProductSkuRequest request);

    /**
     * 批量删除（仅待开始状态可用；物理删除）
     *
     * @return 需归还主域的库存清单，由调用方编排归还
     */
    List<StockRestoreItem> batchRemove(RemoveProductSkuRequest request);

    /**
     * 批量上架/下架（活动非终态可用，仅切换状态位，不触碰库存）
     */
    void batchShelf(ShelfProductSkuRequest request);

    /**
     * 查询活动下全部商品SKU
     */
    List<SeckillProductSkuDTO> listByActivityNo(String activityNo);

    /**
     * 按活动编号 + SKU 编号查询单条
     */
    SeckillProductSkuDTO getByActivityNoAndSkuNo(String activityNo, String skuNo);
}

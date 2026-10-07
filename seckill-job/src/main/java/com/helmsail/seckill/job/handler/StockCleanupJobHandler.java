package com.helmsail.seckill.job.handler;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 活动库存清理任务（壳子，待实现）
 *
 * 为何必须手动清理：库存键无补充机制（仅“待开始”分支初始化），且缺失=扣减全拒（stock==nil → -1），
 * 故“无 TTL + 绝不淘汰 + 终态手动清理”是唯一安全形态。
 *
 * 实现时的设计要点（已论证，待落地）：
 *   - 候选：已关闭且结束超安全期（≥24h，覆盖关闭瞬间在途请求与关单延迟窗口）的活动；
 *   - 前置：该活动无未终局订单（防迟到回补对已删键 incrby 复活孤儿键、致归档值残缺）；
 *   - 顺序：先落库（归档/对账）成功，再删库存键（删失败下轮重做，值不变幂等）；
 *   - 失败必须结构化告警（需人工核对）；
 *   - 归档口径待定：直接读结余写回 DB / 与累计卖出对账后回写。
 *
 * 快照/上下架/限购上限由 TTL 自然回收；活动信息 field 见 ActivityInfoCleanupJobHandler。
 */
@Slf4j
@Service
public class StockCleanupJobHandler {

    @XxlJob("stockCleanupJob")
    public void execute() {
        // TODO 待实现：扫已关闭活动 → 前置校验 → 读结余落库 → 删库存键（失败告警）
        log.info("活动库存清理任务启动（壳子，待实现）");
    }
}

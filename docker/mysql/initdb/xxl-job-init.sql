-- ============================================================
-- xxl-job 业务调度规则（★ 日常唯一需要维护的 xxl-job 文件）
--
-- 内容：执行器组 seckill-job + 8 个任务。每行一条规则：
--       job_desc（说明）/ handler（必须与代码 @XxlJob("...") 逐字一致）/ cron。
-- 修改：调整周期 = 改对应行的 cron；新增任务 = 追加一行 SELECT。
--       handler 改错的任务在 admin 里会报 "job handler not found"。
-- 生效：仅 docker MySQL 首次初始化（数据卷为空）时自动执行；
--       存量环境请在 admin UI 调整并同步维护本文件（本文件插入幂等，
--       也可在库上重跑：mysql -uroot -p xxl_job < xxl-job-init.sql）。
-- 顺序：表结构在 xxl-job-base.sql（initdb 按文件名字母序先执行它，本文件依赖其建表）。
-- 策略：路由 FIRST / 阻塞 SERIAL_EXECUTION（单机演示无需变更）。
-- ============================================================
SET NAMES utf8mb4;
USE `xxl_job`;

-- 执行器组：与 seckill-job 的 xxl.job.executor.appname 一致（0=自动注册）
INSERT INTO `xxl_job_group`(`app_name`, `title`, `address_type`, `address_list`, `update_time`)
SELECT 'seckill-job', '秒杀任务执行器', 0, NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `xxl_job_group` WHERE `app_name` = 'seckill-job');

-- 任务清单（幂等：按 executor_handler 判重，重复执行不产生重复任务；演示统一每分钟，生产按负载错峰）
INSERT INTO `xxl_job_info`(`job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`, `executor_handler`,
    `executor_param`, `executor_block_strategy`, `executor_timeout`, `executor_fail_retry_count`, `glue_type`,
    `glue_source`, `glue_remark`, `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`)
SELECT g.`id`, t.`job_desc`, NOW(), NOW(), 'seckill', NULL,
    'CRON', t.`cron`, 'DO_NOTHING', 'FIRST', t.`handler`,
    NULL, 'SERIAL_EXECUTION', 0, 0, 'BEAN',
    NULL, NULL, NOW(), NULL, 1, 0, 0
FROM `xxl_job_group` g
JOIN (
    SELECT '活动状态流转：待开始到点激活' AS `job_desc`, 'activityStatusJob' AS `handler`, '0 * * * * ?' AS `cron`
    UNION ALL SELECT '活动预热：开始前30分钟写入缓存与库存', 'activityWarmUpJob', '0 * * * * ?'
    UNION ALL SELECT '活动在售名单同步', 'activityShelfJob', '0 * * * * ?'
    UNION ALL SELECT '活动状态同步与终态清理', 'activityRefreshJob', '0 * * * * ?'
    UNION ALL SELECT '活动到期关闭', 'activityCloseJob', '0 * * * * ?'
    UNION ALL SELECT '订单超时关单', 'orderTimeoutJob', '0 * * * * ?'
    UNION ALL SELECT '订单同步对账', 'orderSyncReconcileJob', '0 * * * * ?'
    UNION ALL SELECT '库存补偿重试', 'compensationJob', '0 * * * * ?'
) t ON 1 = 1
WHERE g.`app_name` = 'seckill-job'
  AND NOT EXISTS (SELECT 1 FROM `xxl_job_info` i WHERE i.`executor_handler` = t.`handler`);

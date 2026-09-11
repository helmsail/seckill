package com.helmsail.seckill.common.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * 自动填充处理器
 *
 * 插入时填充 createTime 和 updateTime。
 * 更新时填充 updateTime。
 */
public class AutoFillMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 严格填充只在字段为 null 时生效；先查后改场景 updateTime 已带旧值会被跳过，故无条件覆盖
        this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
    }
}

package com.helmsail.seckill.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果封装
 *
 * 将 MyBatis-Plus IPage 转换为统一的分页格式。
 */
@Getter
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 数据列表 */
    private final List<T> records;

    /** 总记录数 */
    private final long total;

    /** 当前页码 */
    private final long pageNum;

    /** 每页条数 */
    private final long pageSize;

    /**
     * 从 MyBatis-Plus IPage 转换
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        if (page == null) {
            return empty(10);
        }
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 空分页结果
     */
    public static <T> PageResult<T> empty(long pageSize) {
        return new PageResult<>(Collections.emptyList(), 0, 1, pageSize);
    }
}

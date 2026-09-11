package com.helmsail.seckill.common.dubbo;

import com.helmsail.seckill.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;

/**
 * Provider 日志过滤器
 *
 * 自动记录 Dubbo 请求和响应日志。
 */
@Slf4j
@Activate(group = CommonConstants.PROVIDER)
public class DubboProviderLogFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String service = invoker.getInterface().getSimpleName();
        String method = invocation.getMethodName();

        log.debug("[Dubbo Provider] {}.{} 开始", service, method);

        long start = System.currentTimeMillis();
        Result result = invoker.invoke(invocation);
        long elapsed = System.currentTimeMillis() - start;

        if (result.hasException()) {
            Throwable ex = result.getException();
            if (ex instanceof BizException) {
                log.warn("[Dubbo Provider] {}.{} 业务异常: {}, 耗时: {}ms", service, method, ex.getMessage(), elapsed);
            } else {
                log.error("[Dubbo Provider] {}.{} 异常, 耗时: {}ms", service, method, elapsed, ex);
            }
        } else {
            log.debug("[Dubbo Provider] {}.{} 成功, 耗时: {}ms", service, method, elapsed);
        }

        return result;
    }
}

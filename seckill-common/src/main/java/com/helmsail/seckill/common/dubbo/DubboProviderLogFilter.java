package com.helmsail.seckill.common.dubbo;

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
        Object[] args = invocation.getArguments();

        log.info("[Dubbo Provider] {}.{} 开始", service, method);

        long start = System.currentTimeMillis();
        Result result = invoker.invoke(invocation);
        long elapsed = System.currentTimeMillis() - start;

        if (result.hasException()) {
            log.error("[Dubbo Provider] {}.{} 异常, 耗时: {}ms", service, method, elapsed, result.getException());
        } else {
            log.info("[Dubbo Provider] {}.{} 成功, 耗时: {}ms", service, method, elapsed);
        }

        return result;
    }
}

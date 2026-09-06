package com.helmsail.seckill.common.dubbo;

import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.result.ResultEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;

/**
 * Consumer 异常过滤器
 *
 * 自动解析 Provider 返回的异常，转换为 BizException。
 */
@Slf4j
@Activate(group = CommonConstants.CONSUMER)
public class DubboConsumerExceptionFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        Result result = invoker.invoke(invocation);

        if (result.hasException()) {
            Throwable ex = result.getException();

            // 已经是 BizException，直接抛出
            if (ex instanceof BizException) {
                log.warn("[Dubbo Consumer] {}.{} 业务异常: {}",
                        invoker.getInterface().getSimpleName(),
                        invocation.getMethodName(), ex.getMessage());
                throw (BizException) ex;
            }

            // 其他异常包装为 BizException
            log.error("[Dubbo Consumer] {}.{} 调用异常",
                    invoker.getInterface().getSimpleName(),
                    invocation.getMethodName(), ex);
            throw new BizException(ResultEnum.DUBBO_CALL_ERROR);
        }

        return result;
    }
}

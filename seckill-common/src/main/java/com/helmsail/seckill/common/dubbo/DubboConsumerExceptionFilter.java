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
 * 注意：Dubbo 的 ExceptionFilter 会将“接口未声明 throws”的自定义异常包装为 RuntimeException，
 * 因此需沿 cause 链解包，恢复原始 BizException 以保留业务错误码。
 */
@Slf4j
@Activate(group = CommonConstants.CONSUMER)
public class DubboConsumerExceptionFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        Result result = invoker.invoke(invocation);

        if (result.hasException()) {
            Throwable ex = result.getException();

            // 解包 Dubbo 包装后的业务异常，恢复原始 BizException（保留业务错误码）
            BizException bizException = findBizException(ex);
            if (bizException != null) {
                log.warn("[Dubbo Consumer] {}.{} 业务异常: {}",
                        invoker.getInterface().getSimpleName(),
                        invocation.getMethodName(), bizException.getMessage());
                throw bizException;
            }

            // 其他异常包装为 BizException
            log.error("[Dubbo Consumer] {}.{} 调用异常",
                    invoker.getInterface().getSimpleName(),
                    invocation.getMethodName(), ex);
            throw new BizException(ResultEnum.DUBBO_CALL_ERROR);
        }

        return result;
    }

    /**
     * 沿 cause 链查找 BizException（异常被 Dubbo 运行时包装时类型会丢失）
     */
    private static BizException findBizException(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof BizException bizException) {
                return bizException;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return null;
    }
}

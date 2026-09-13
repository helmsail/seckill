package com.helmsail.seckill.common.dubbo;

import com.helmsail.seckill.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;

/**
 * Consumer 异常过滤器
 *
 * 统一 Dubbo 调用的异常语义，只做一件事：还原业务异常。
 *
 * 1) 业务异常（BizException）：接口已声明 throws，理论上原样返回，但跨进程序列化
 *    可能降级为 RuntimeException，因此沿 cause 链解包还原，保留业务错误码；
 * 2) 技术异常（超时/网络/序列化失败等）：原样上抛（RpcException），
 *    不伪造成 BizException——技术故障应归为系统错误（SYSTEM_ERROR），
 *    与“业务规则拒绝”区分开，调用方可据此做重试/告警等差异化处理。
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

            // 技术异常原样上抛，由上层按系统错误处理（不转译为业务异常）
            log.error("[Dubbo Consumer] {}.{} 调用异常",
                    invoker.getInterface().getSimpleName(),
                    invocation.getMethodName(), ex);
            if (ex instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (ex instanceof Error error) {
                throw error;
            }
            // 受检异常（接口声明 throws 的非 RuntimeException）：包装上抛，保留 cause
            throw new RpcException("Dubbo 调用返回受检异常: " + ex.getMessage(), ex);
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

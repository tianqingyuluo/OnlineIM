package icu.tianqingyuluo.onlineim.exception;

/**
 * 控制器里兜底的 catch (Exception) 会把业务异常吞成 500。
 * 在兜底分支开头调用 {@link #rethrowIfTyped(Exception)}，把带语义的业务异常继续抛给
 * {@link GlobalExceptionHandler}，由它统一映射为 403 / 400 等状态码。
 */
public final class TypedExceptions {

    private TypedExceptions() {
    }

    public static void rethrowIfTyped(Exception e) {
        if (e instanceof ForbiddenException forbidden) {
            throw forbidden;
        }
        if (e instanceof IllegalArgumentException illegalArgument) {
            throw illegalArgument;
        }
    }
}

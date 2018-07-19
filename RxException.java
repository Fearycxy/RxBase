package com.base.utils.rx;

public class RxException extends RuntimeException {
    public RxException(Throwable e) {
        super(e.getMessage(), e.getCause());
        setStackTrace(e.getStackTrace());
    }
}

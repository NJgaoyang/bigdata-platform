package com.company.platform.common;

public record Result<T>(boolean success, T data, String message) {
    public static <T> Result<T> ok(T data) {
        return new Result<>(true, data, "OK");
    }

    public static <T> Result<T> ok(T data, String message) {
        return new Result<>(true, data, message);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(false, null, message);
    }
}

package com.billing.util;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GeneralResponse<T> {
    private boolean success;
    private String message;
    private T data;

    public GeneralResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static <T> GeneralResponse<T> success(String message, T data) {
        return new GeneralResponse<>(true, message, data);
    }

    public static <T> GeneralResponse<T> error(String message) {
        return new GeneralResponse<>(false, message, null);
    }
}

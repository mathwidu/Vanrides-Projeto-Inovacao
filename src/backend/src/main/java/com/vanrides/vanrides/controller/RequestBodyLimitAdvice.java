package com.vanrides.vanrides.controller;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.IOException;
import java.lang.reflect.Type;

@ControllerAdvice
public final class RequestBodyLimitAdvice extends RequestBodyAdviceAdapter {

    private static final int DRAIN_BUFFER_BYTES = 8192;

    @Override
    public boolean supports(
            MethodParameter methodParameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType
    ) {
        return true;
    }

    @Override
    public Object afterBodyRead(
            Object body,
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType
    ) {
        byte[] drainBuffer = new byte[DRAIN_BUFFER_BYTES];
        try {
            int read;
            while ((read = inputMessage.getBody().read(drainBuffer)) >= 0) {
                if (read == 0 && inputMessage.getBody().read() < 0) {
                    break;
                }
                // Drena apenas o stream já limitado, antes de invocar o controller.
            }
        } catch (IOException exception) {
            throw new HttpMessageNotReadableException(
                    "Falha ao concluir a leitura limitada do corpo.",
                    exception,
                    inputMessage
            );
        }
        return body;
    }
}

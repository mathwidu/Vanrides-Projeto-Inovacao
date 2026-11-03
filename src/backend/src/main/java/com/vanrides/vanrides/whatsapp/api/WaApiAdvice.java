package com.vanrides.vanrides.whatsapp.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Keep legacy exception contracts intact and never expose SQL, tokens or native error text. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {WaController.class, WaTransportController.class})
public class WaApiAdvice {
    @ExceptionHandler(WaProblem.class)
    public void problem(WaProblem problem, HttpServletRequest request, HttpServletResponse response) throws IOException {
        WaHttp.writeError(request, response, problem);
    }
    @ExceptionHandler(Exception.class)
    public void unexpected(Exception ignored, HttpServletRequest request, HttpServletResponse response) throws IOException {
        WaHttp.writeError(request, response, new WaProblem(WaProblem.Code.DEPENDENCY_UNAVAILABLE));
    }
}

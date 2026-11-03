package com.vanrides.vanrides.config;

import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnreadRequestBodyValveRaceTest {

    @Test
    void shouldCloseUnreadBodyWhenAsyncCompletionWinsListenerRegistration() throws Exception {
        Request request = mock(Request.class);
        Response response = mock(Response.class);
        org.apache.coyote.Request coyoteRequest = new org.apache.coyote.Request();
        Valve next = mock(Valve.class);
        AtomicInteger metric = new AtomicInteger();
        UnreadRequestBodyValve valve = new UnreadRequestBodyValve(metric::incrementAndGet);
        valve.setNext(next);

        when(request.isAsyncStarted()).thenReturn(true);
        when(request.isFinished()).thenReturn(false);
        when(request.getCoyoteRequest()).thenReturn(coyoteRequest);
        when(response.isCommitted()).thenReturn(false);
        doThrow(new IllegalStateException("async cycle completed"))
                .when(request).getAsyncContext();

        valve.invoke(request, response);

        verify(next).invoke(request, response);
        verify(response).setHeader("Connection", "close");
        verify(response).flushBuffer();
        verify(request).removeAttribute(anyString());
        assertThat(metric.get()).isOne();
    }
}

package com.vanrides.vanrides.config;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.coyote.ActionCode;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UnreadRequestBodyValve extends ValveBase {

    private static final String CLOSER_ATTRIBUTE =
            UnreadRequestBodyValve.class.getName() + ".closer";

    private final Runnable closeMetric;

    public UnreadRequestBodyValve(Runnable closeMetric) {
        super(true);
        this.closeMetric = Objects.requireNonNull(closeMetric, "closeMetric");
    }

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        UnreadBodyCloser closer = closerFor(request, response);
        boolean dispatchReturned = false;
        try {
            getNext().invoke(request, response);
            dispatchReturned = true;
        } finally {
            boolean responseCanBeCommitted = dispatchReturned
                    && !response.isErrorReportRequired();
            closer.afterDispatch(responseCanBeCommitted);
        }
    }

    private UnreadBodyCloser closerFor(Request request, Response response) {
        Object existing = request.getAttribute(CLOSER_ATTRIBUTE);
        if (existing instanceof UnreadBodyCloser closer) {
            return closer;
        }
        UnreadBodyCloser closer = new UnreadBodyCloser(request, response);
        request.setAttribute(CLOSER_ATTRIBUTE, closer);
        return closer;
    }

    private final class UnreadBodyCloser implements AsyncListener {

        private final Request request;
        private final Response response;
        private final AtomicBoolean swallowDisabled = new AtomicBoolean();
        private final AtomicBoolean asyncObserved = new AtomicBoolean();
        private final AtomicBoolean initialListenerRegistered = new AtomicBoolean();

        private UnreadBodyCloser(Request request, Response response) {
            this.request = request;
            this.response = response;
        }

        private void afterDispatch(boolean responseCanBeCommitted) throws IOException {
            if (request.isAsyncStarted()) {
                asyncObserved.set(true);
                try {
                    if (initialListenerRegistered.compareAndSet(false, true)) {
                        request.getAsyncContext().addListener(this);
                    }
                    return;
                } catch (IllegalStateException ignored) {
                    initialListenerRegistered.set(false);
                    // O ciclo concluiu entre isAsyncStarted() e o registro.
                    // Sem listener futuro, finalize a proteção neste dispatch.
                    try {
                        closeUnreadConnectionIfBodyUnread(response, true);
                    } finally {
                        request.removeAttribute(CLOSER_ATTRIBUTE);
                    }
                    return;
                }
            }
            if (asyncObserved.get()) {
                // Redispatch assíncrono: onComplete é o único ponto seguro para
                // fechar o socket sem substituir o status final por 500.
                return;
            }
            closeUnreadConnectionIfBodyUnread(response, responseCanBeCommitted);
        }

        private void closeUnreadConnectionIfBodyUnread(
                ServletResponse servletResponse,
                boolean commitBeforeClose
        ) throws IOException {
            if (!claimUnreadBody()) {
                return;
            }
            prepareConnectionClose(servletResponse);
            try {
                if (commitBeforeClose && !servletResponse.isCommitted()) {
                    servletResponse.flushBuffer();
                }
            } finally {
                request.getCoyoteRequest().action(ActionCode.DISABLE_SWALLOW_INPUT, null);
                closeMetric.run();
            }
        }

        private boolean claimUnreadBody() {
            if (request.isFinished() || !swallowDisabled.compareAndSet(false, true)) {
                return false;
            }
            if (request.isFinished()) {
                swallowDisabled.set(false);
                return false;
            }
            return true;
        }

        private void prepareConnectionClose(ServletResponse servletResponse) {
            if (!servletResponse.isCommitted()
                    && servletResponse instanceof HttpServletResponse httpResponse) {
                httpResponse.setHeader("Connection", "close");
            }
        }

        @Override
        public void onComplete(AsyncEvent event) throws IOException {
            try {
                closeUnreadConnectionIfBodyUnread(event.getSuppliedResponse(), true);
            } finally {
                request.removeAttribute(CLOSER_ATTRIBUTE);
            }
        }

        @Override
        public void onTimeout(AsyncEvent event) {
            // O container ainda pode despachar uma resposta de timeout/recovery.
            // A conexão é tratada somente no onComplete desse ciclo.
        }

        @Override
        public void onError(AsyncEvent event) {
            // O erro pode ser recuperado por um dispatch; onComplete encerra o ciclo.
        }

        @Override
        public void onStartAsync(AsyncEvent event) {
            event.getAsyncContext().addListener(this);
        }
    }
}

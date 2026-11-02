package com.vanrides.vanrides.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationResendQueueTest {

    @Mock
    private EmailVerificationService emailVerificationService;

    @Test
    void shouldCompactInvalidInputsBeforeSubmittingThroughTheSameBoundary() {
        DirectExecutorService executor = new DirectExecutorService();
        EmailVerificationResendQueue queue = new EmailVerificationResendQueue(
                emailVerificationService,
                executor
        );

        queue.enqueue(null);
        queue.enqueue("");
        queue.enqueue("invalid");
        queue.enqueue("user@vanrides.dev");

        verify(emailVerificationService, times(3)).resendForEmail(null);
        verify(emailVerificationService).resendForEmail("user@vanrides.dev");
    }

    @Test
    void shouldNeverRetainAnOversizedRawEmailInQueuedWork() {
        DirectExecutorService executor = new DirectExecutorService();
        EmailVerificationResendQueue queue = new EmailVerificationResendQueue(
                emailVerificationService,
                executor
        );

        queue.enqueue("x".repeat(EmailVerificationResendQueue.MAX_RAW_EMAIL_LENGTH + 1));

        verify(emailVerificationService).resendForEmail(null);
    }

    @Test
    void shouldCoalesceEquivalentPendingEmailWork() {
        HoldingExecutorService executor = new HoldingExecutorService();
        EmailVerificationResendQueue queue = new EmailVerificationResendQueue(
                emailVerificationService,
                executor
        );

        queue.enqueue("  USER@VANRIDES.DEV  ");
        queue.enqueue("user@vanrides.dev");

        assertThat(executor.pendingTasks).hasSize(1);
        executor.pendingTasks.remove(0).run();
        verify(emailVerificationService).resendForEmail("user@vanrides.dev");
    }

    @Test
    void shouldKeepWorkerFailureOpaqueToTheCaller() {
        DirectExecutorService executor = new DirectExecutorService();
        EmailVerificationResendQueue queue = new EmailVerificationResendQueue(
                emailVerificationService,
                executor
        );
        doThrow(new IllegalStateException("sensitive.user@vanrides.dev"))
                .when(emailVerificationService)
                .resendForEmail("sensitive.user@vanrides.dev");
        ListAppender<ILoggingEvent> logs = attachLogCapture();

        try {
            assertThatNoException().isThrownBy(
                    () -> queue.enqueue("sensitive.user@vanrides.dev")
            );
            assertThat(logs.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .hasSize(1)
                    .noneMatch(message -> message.contains("sensitive.user@vanrides.dev"));
        } finally {
            detachLogCapture(logs);
        }
    }

    @Test
    void shouldKeepQueueRejectionOpaqueToTheCaller() {
        ExecutorService executor = mock(ExecutorService.class);
        EmailVerificationResendQueue queue = new EmailVerificationResendQueue(
                emailVerificationService,
                executor
        );
        doThrow(new RejectedExecutionException("sensitive.user@vanrides.dev"))
                .when(executor)
                .execute(any(Runnable.class));
        ListAppender<ILoggingEvent> logs = attachLogCapture();

        try {
            assertThatNoException().isThrownBy(
                    () -> {
                        queue.enqueue("sensitive.user@vanrides.dev");
                        queue.enqueue("sensitive.user@vanrides.dev");
                        queue.enqueue("sensitive.user@vanrides.dev");
                    }
            );
            verifyNoInteractions(emailVerificationService);
            assertThat(logs.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .hasSize(1)
                    .noneMatch(message -> message.contains("sensitive.user@vanrides.dev"));
        } finally {
            detachLogCapture(logs);
        }
    }

    @Test
    void shouldUseOneWorkerAndABoundedQueue() {
        ExecutorService executor = EmailVerificationResendQueue.createExecutor();
        try {
            assertThat(executor).isInstanceOf(ThreadPoolExecutor.class);
            ThreadPoolExecutor threadPool = (ThreadPoolExecutor) executor;
            assertThat(threadPool.getCorePoolSize()).isEqualTo(1);
            assertThat(threadPool.getMaximumPoolSize()).isEqualTo(1);
            assertThat(threadPool.getQueue().remainingCapacity())
                    .isEqualTo(EmailVerificationResendQueue.QUEUE_CAPACITY);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldShutdownGracefullyAndForcePendingWorkAfterTimeout() throws Exception {
        ExecutorService executor = mock(ExecutorService.class);
        EmailVerificationResendQueue queue = new EmailVerificationResendQueue(
                emailVerificationService,
                executor
        );
        when(executor.awaitTermination(
                EmailVerificationResendQueue.SHUTDOWN_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        )).thenReturn(false);

        queue.close();

        InOrder shutdownOrder = inOrder(executor);
        shutdownOrder.verify(executor).shutdown();
        shutdownOrder.verify(executor).awaitTermination(
                EmailVerificationResendQueue.SHUTDOWN_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        );
        shutdownOrder.verify(executor).shutdownNow();
    }

    private ListAppender<ILoggingEvent> attachLogCapture() {
        Logger logger = (Logger) LoggerFactory.getLogger(EmailVerificationResendQueue.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detachLogCapture(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(EmailVerificationResendQueue.class);
        logger.detachAppender(appender);
        appender.stop();
    }

    private static class DirectExecutorService extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            if (shutdown) {
                throw new RejectedExecutionException("executor shut down");
            }
            command.run();
        }
    }

    private static class HoldingExecutorService extends AbstractExecutorService {
        private final List<Runnable> pendingTasks = new ArrayList<>();
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> remaining = List.copyOf(pendingTasks);
            pendingTasks.clear();
            return remaining;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown && pendingTasks.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }

        @Override
        public void execute(Runnable command) {
            if (shutdown) {
                throw new RejectedExecutionException("executor shut down");
            }
            pendingTasks.add(command);
        }
    }
}

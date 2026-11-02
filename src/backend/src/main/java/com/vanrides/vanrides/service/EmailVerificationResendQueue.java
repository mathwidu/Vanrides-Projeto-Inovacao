package com.vanrides.vanrides.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class EmailVerificationResendQueue implements AutoCloseable {

    static final int QUEUE_CAPACITY = 128;
    static final int MAX_RAW_EMAIL_LENGTH = 512;
    static final long SHUTDOWN_TIMEOUT_SECONDS = 5L;
    static final long WARNING_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1);

    private static final String INVALID_EMAIL_WORK_KEY = "<invalid>";

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationResendQueue.class);
    private static final AtomicInteger WORKER_SEQUENCE = new AtomicInteger();

    private final EmailVerificationService emailVerificationService;
    private final EmailAddressNormalizer emailAddressNormalizer;
    private final ExecutorService executor;
    private final Set<String> pendingWorkKeys = ConcurrentHashMap.newKeySet();
    private final AtomicLong nextRejectionWarningAt = new AtomicLong();
    private final AtomicLong nextWorkerWarningAt = new AtomicLong();

    @Autowired
    public EmailVerificationResendQueue(
            EmailVerificationService emailVerificationService,
            EmailAddressNormalizer emailAddressNormalizer) {
        this(emailVerificationService, emailAddressNormalizer, createExecutor());
    }

    EmailVerificationResendQueue(
            EmailVerificationService emailVerificationService,
            ExecutorService executor) {
        this(emailVerificationService, new EmailAddressNormalizer(), executor);
    }

    EmailVerificationResendQueue(
            EmailVerificationService emailVerificationService,
            EmailAddressNormalizer emailAddressNormalizer,
            ExecutorService executor) {
        this.emailVerificationService = Objects.requireNonNull(emailVerificationService);
        this.emailAddressNormalizer = Objects.requireNonNull(emailAddressNormalizer);
        this.executor = Objects.requireNonNull(executor);
    }

    public void enqueue(String rawEmail) {
        String queuedEmail = compactForQueue(rawEmail);
        String workKey = queuedEmail != null ? queuedEmail : INVALID_EMAIL_WORK_KEY;
        if (!pendingWorkKeys.add(workKey)) {
            return;
        }

        try {
            executor.execute(() -> {
                try {
                    process(queuedEmail);
                } finally {
                    pendingWorkKeys.remove(workKey);
                }
            });
        } catch (RejectedExecutionException ignored) {
            pendingWorkKeys.remove(workKey);
            warnThrottled(
                    nextRejectionWarningAt,
                    "Fila local de reenvio indisponível; solicitações estão sendo descartadas de forma opaca."
            );
        }
    }

    private String compactForQueue(String rawEmail) {
        if (rawEmail == null || rawEmail.length() > MAX_RAW_EMAIL_LENGTH) {
            return null;
        }
        return emailAddressNormalizer.tryNormalize(rawEmail).orElse(null);
    }

    private void process(String email) {
        try {
            emailVerificationService.resendForEmail(email);
        } catch (RuntimeException failure) {
            warnThrottled(
                    nextWorkerWarningAt,
                    "Falha assíncrona no reenvio de verificação; solicitações estão sendo encerradas de forma opaca."
            );
        }
    }

    private void warnThrottled(AtomicLong nextWarningAt, String message) {
        long now = System.nanoTime();
        long currentGate = nextWarningAt.get();
        if (now < currentGate) {
            return;
        }
        if (nextWarningAt.compareAndSet(currentGate, now + WARNING_INTERVAL_NANOS)) {
            log.warn(message);
        }
    }

    static ExecutorService createExecutor() {
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(
                    task,
                    "email-verification-resend-" + WORKER_SEQUENCE.incrementAndGet()
            );
            thread.setDaemon(true);
            return thread;
        };

        return new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @PreDestroy
    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

package com.vanrides.vanrides.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanrides.vanrides.controller.ApiErrorResponseWriter;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class UnreadRequestBodyValveIntegrationTest {

    @TempDir
    Path tempDir;

    private final AtomicInteger handlerCalls = new AtomicInteger();
    private final AtomicInteger asyncHandlerCalls = new AtomicInteger();
    private final AtomicInteger asyncConsumedStarterCalls = new AtomicInteger();
    private final AtomicInteger consumedHandlerCalls = new AtomicInteger();
    private final AtomicInteger closedUnreadConnections = new AtomicInteger();
    private final InetAddress loopback = InetAddress.getLoopbackAddress();

    private Tomcat tomcat;
    private int port;

    @BeforeEach
    void startTomcat() throws Exception {
        Path baseDir = Files.createDirectories(tempDir.resolve("tomcat"));
        Path docBase = Files.createDirectories(tempDir.resolve("webapp"));

        tomcat = new Tomcat();
        tomcat.setSilent(true);
        tomcat.setBaseDir(baseDir.toString());
        tomcat.setAddDefaultWebXmlToWebapp(false);
        tomcat.setPort(0);

        Connector connector = tomcat.getConnector();
        AbstractHttp11Protocol<?> protocol =
                (AbstractHttp11Protocol<?>) connector.getProtocolHandler();
        protocol.setAddress(loopback);
        protocol.setMaxSwallowSize(2 * 1024 * 1024);
        protocol.setConnectionTimeout(30_000);

        Context context = tomcat.addContext("", docBase.toString());
        context.getPipeline().addValve(new UnreadRequestBodyValve(
                closedUnreadConnections::incrementAndGet
        ));

        Wrapper servlet = Tomcat.addServlet(context, "reject-without-reading", new HttpServlet() {
            @Override
            protected void doPost(
                    HttpServletRequest request,
                    HttpServletResponse response
            ) throws java.io.IOException {
                handlerCalls.incrementAndGet();
                ApiErrorResponseWriter.write(
                        new ObjectMapper(),
                        response,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "TEST_REJECT",
                        "Rejected without reading the request body."
                );
            }
        });
        servlet.setLoadOnStartup(1);
        context.addServletMappingDecoded("/reject", "reject-without-reading");

        Wrapper asyncFirst = Tomcat.addServlet(context, "async-first", new HttpServlet() {
            @Override
            protected void doPost(
                    HttpServletRequest request,
                    HttpServletResponse response
            ) {
                asyncHandlerCalls.incrementAndGet();
                request.startAsync().dispatch("/async-second");
            }
        });
        asyncFirst.setAsyncSupported(true);
        asyncFirst.setLoadOnStartup(1);
        context.addServletMappingDecoded("/async-first", "async-first");

        Wrapper asyncSecond = Tomcat.addServlet(context, "async-second", new HttpServlet() {
            @Override
            protected void doPost(
                    HttpServletRequest request,
                    HttpServletResponse response
            ) {
                asyncHandlerCalls.incrementAndGet();
                response.setStatus(HttpServletResponse.SC_ACCEPTED);
                response.setContentLength(0);
            }
        });
        asyncSecond.setAsyncSupported(true);
        asyncSecond.setLoadOnStartup(1);
        context.addServletMappingDecoded("/async-second", "async-second");

        Wrapper asyncConsumeFirst = Tomcat.addServlet(
                context,
                "async-consume-first",
                new HttpServlet() {
                    @Override
                    protected void doPost(
                            HttpServletRequest request,
                            HttpServletResponse response
                    ) {
                        asyncConsumedStarterCalls.incrementAndGet();
                        request.startAsync().dispatch("/consume");
                    }
                }
        );
        asyncConsumeFirst.setAsyncSupported(true);
        asyncConsumeFirst.setLoadOnStartup(1);
        context.addServletMappingDecoded("/async-consume-first", "async-consume-first");

        Wrapper consume = Tomcat.addServlet(context, "consume", new HttpServlet() {
            @Override
            protected void doPost(
                    HttpServletRequest request,
                    HttpServletResponse response
            ) throws java.io.IOException {
                consumedHandlerCalls.incrementAndGet();
                byte[] body = request.getInputStream().readAllBytes();
                response.setStatus(body.length == 3
                        ? HttpServletResponse.SC_OK
                        : HttpServletResponse.SC_BAD_REQUEST);
                response.setContentLength(0);
            }
        });
        consume.setAsyncSupported(true);
        consume.setLoadOnStartup(1);
        context.addServletMappingDecoded("/consume", "consume");

        tomcat.start();
        port = connector.getLocalPort();
        assertThat(port).isPositive();
    }

    @AfterEach
    void stopTomcat() throws Exception {
        if (tomcat == null) {
            return;
        }
        try {
            tomcat.stop();
        } finally {
            tomcat.destroy();
        }
    }

    @Test
    void shouldReturnAndCloseConnectionWithoutSwallowingUnreadChunkedBody() {
        assertIncompleteRequestClosed(
                "Transfer-Encoding: chunked\r\n",
                "5\r\nx"
        );

        assertThat(handlerCalls.get()).isOne();
        assertThat(closedUnreadConnections.get()).isOne();
    }

    @Test
    void shouldReturnAndCloseConnectionWithoutSwallowingUnreadFixedLengthBody() {
        assertIncompleteRequestClosed(
                "Content-Length: 5\r\n",
                "x"
        );

        assertThat(handlerCalls.get()).isOne();
        assertThat(closedUnreadConnections.get()).isOne();
    }

    @Test
    void shouldPreserveFinalStatusAndCloseOnceAfterAsyncRedispatch() {
        String rawResponse = sendIncompleteRequest(
                "/async-first",
                "Content-Length: 5\r\n",
                "x"
        );

        assertThat(rawResponse).startsWith("HTTP/1.1 202");
        assertThat(rawResponse).containsIgnoringCase("\r\nContent-Length: 0\r\n");
        assertThat(rawResponse).containsIgnoringCase("\r\nConnection: close\r\n");
        assertThat(asyncHandlerCalls.get()).isEqualTo(2);
        assertThat(closedUnreadConnections.get()).isOne();
    }

    @Test
    void shouldPreserveKeepAliveWhenApplicationConsumesTheCompleteBody() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(loopback, port), 1_000);
                socket.setSoTimeout(2_000);

                writeCompleteRequest(socket, "keep-alive");
                String firstResponse = readHeaders(socket);
                assertThat(firstResponse).startsWith("HTTP/1.1 200");
                assertThat(firstResponse).doesNotContainIgnoringCase("Connection: close");

                writeCompleteRequest(socket, "close");
                String secondResponse = new String(
                        socket.getInputStream().readAllBytes(),
                        StandardCharsets.ISO_8859_1
                );
                assertThat(secondResponse).startsWith("HTTP/1.1 200");
            }
        });

        assertThat(consumedHandlerCalls.get()).isEqualTo(2);
        assertThat(closedUnreadConnections.get()).isZero();
    }

    @Test
    void shouldPreserveKeepAliveWhenAsyncApplicationConsumesTheCompleteBody() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(loopback, port), 1_000);
                socket.setSoTimeout(2_000);

                writeCompleteRequest(socket, "/async-consume-first", "keep-alive");
                String firstResponse = readHeaders(socket);
                assertThat(firstResponse).startsWith("HTTP/1.1 200");
                assertThat(firstResponse).doesNotContainIgnoringCase("Connection: close");

                writeCompleteRequest(socket, "/consume", "close");
                String secondResponse = new String(
                        socket.getInputStream().readAllBytes(),
                        StandardCharsets.ISO_8859_1
                );
                assertThat(secondResponse).startsWith("HTTP/1.1 200");
            }
        });

        assertThat(asyncConsumedStarterCalls.get()).isOne();
        assertThat(consumedHandlerCalls.get()).isEqualTo(2);
        assertThat(closedUnreadConnections.get()).isZero();
    }

    private void assertIncompleteRequestClosed(String bodyHeader, String incompleteBody) {
        String rawResponse = sendIncompleteRequest("/reject", bodyHeader, incompleteBody);

        assertThat(rawResponse).startsWith("HTTP/1.1 401");
        assertThat(rawResponse).containsIgnoringCase("\r\nContent-Type: application/json");
        assertThat(rawResponse).containsIgnoringCase("\r\nConnection: close\r\n");
        assertThat(rawResponse).contains("\"code\":\"TEST_REJECT\"");
    }

    private String sendIncompleteRequest(
            String path,
            String bodyHeader,
            String incompleteBody
    ) {
        AtomicReference<String> response = new AtomicReference<>();
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(loopback, port), 1_000);
                socket.setSoTimeout(2_000);

                String incompleteRequest =
                        "POST " + path + " HTTP/1.1\r\n"
                                + "Host: localhost\r\n"
                                + bodyHeader
                                + "Connection: keep-alive\r\n"
                                + "\r\n"
                                + incompleteBody;
                socket.getOutputStream().write(
                        incompleteRequest.getBytes(StandardCharsets.ISO_8859_1)
                );
                socket.getOutputStream().flush();

                response.set(new String(
                        socket.getInputStream().readAllBytes(),
                        StandardCharsets.ISO_8859_1
                ));
            }
        });
        return response.get();
    }

    private void writeCompleteRequest(Socket socket, String connection) throws java.io.IOException {
        writeCompleteRequest(socket, "/consume", connection);
    }

    private void writeCompleteRequest(
            Socket socket,
            String path,
            String connection
    ) throws java.io.IOException {
        String request = "POST " + path + " HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Content-Length: 3\r\n"
                + "Connection: " + connection + "\r\n"
                + "\r\n"
                + "abc";
        socket.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
        socket.getOutputStream().flush();
    }

    private String readHeaders(Socket socket) throws java.io.IOException {
        ByteArrayOutputStream headers = new ByteArrayOutputStream();
        int matched = 0;
        byte[] delimiter = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        while (matched < delimiter.length) {
            int value = socket.getInputStream().read();
            if (value < 0) {
                throw new java.io.EOFException("conexão fechou antes dos headers completos");
            }
            headers.write(value);
            matched = value == delimiter[matched] ? matched + 1 : value == delimiter[0] ? 1 : 0;
        }
        return headers.toString(StandardCharsets.ISO_8859_1);
    }
}

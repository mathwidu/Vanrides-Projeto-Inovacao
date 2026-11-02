package com.vanrides.vanrides.filtros;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestCorrelationFilterTest {

    @Test
    void shouldGenerateRequestIdAndExposeResponseHeader() throws Exception {
        RequestCorrelationFilter filter = new RequestCorrelationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/motoristas");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        String requestId = response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
        assertNotNull(requestId);
        assertEquals(requestId, request.getAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE));
        assertNull(MDC.get(RequestCorrelationFilter.MDC_REQUEST_ID_KEY));
    }

    @Test
    void shouldPreserveSafeIncomingRequestId() throws Exception {
        RequestCorrelationFilter filter = new RequestCorrelationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/motoristas");
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "portal-req-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertEquals("portal-req-123", response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER));
        assertEquals("portal-req-123", request.getAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE));
        assertNull(MDC.get(RequestCorrelationFilter.MDC_REQUEST_ID_KEY));
    }
}

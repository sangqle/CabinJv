package com.cabin.express.middleware;

import com.cabin.express.exception.BadRequestException;
import com.cabin.express.exception.CabinException;
import com.cabin.express.http.Request;
import com.cabin.express.http.Response;
import com.cabin.express.interfaces.Handler;
import com.cabin.express.interfaces.Middleware;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.rmi.ServerError;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MiddlewareChainTest {

    // Using mocked Request and Response instead of dummy subclasses
    private Request mockRequest;
    private Response mockResponse;

    @BeforeEach
    void setUp() {
        // Create mocked objects for tests
        mockRequest = mock(Request.class);
        mockResponse = mock(Response.class);
    }

    @Test
    void testMiddlewareOrderAndHandlerCalled() throws IOException {
        // Given
        List<String> callOrder = new ArrayList<>();

        Middleware m1 = (req, res, chain) -> {
            callOrder.add("m1");
            chain.next(req, res);
        };

        Middleware m2 = (req, res, chain) -> {
            callOrder.add("m2");
            chain.next(req, res);
        };

        Handler handler = (req, res) -> callOrder.add("handler");

        // When
        MiddlewareChain chain = new MiddlewareChain(List.of(m1, m2), handler);
        chain.next(mockRequest, mockResponse);

        // Then
        assertEquals(List.of("m1", "m2", "handler"), callOrder,
                "Middleware should be called in order followed by the handler");
    }

    @Test
    void testNoMiddlewareRouteHandlerOnly() throws IOException {
        // Given
        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        Handler handler = (req, res) -> handlerCalled.set(true);

        // When
        MiddlewareChain chain = new MiddlewareChain(List.of(), handler);
        chain.next(mockRequest, mockResponse);

        // Then
        assertTrue(handlerCalled.get(), "Handler should be called when no middleware is present");
    }

    @Test
    void testNoMiddlewareNoRouteHandler() throws IOException {
        // Given
        MiddlewareChain chain = new MiddlewareChain(List.of(), null);

        // When/Then - Should not throw
        chain.next(mockRequest, mockResponse);
        // No assertion needed - we're just verifying no exception occurs
    }

    @Test
    void testMiddlewareShortCircuit() throws IOException {
        // Given
        AtomicBoolean m2Called = new AtomicBoolean(false);
        AtomicBoolean handlerCalled = new AtomicBoolean(false);

        Middleware m1 = (req, res, chain) -> {
            // Intentionally not calling chain.next() to short-circuit
        };

        Middleware m2 = (req, res, chain) -> {
            m2Called.set(true);
            chain.next(req, res);
        };

        Handler handler = (req, res) -> handlerCalled.set(true);

        // When
        MiddlewareChain chain = new MiddlewareChain(List.of(m1, m2), handler);
        chain.next(mockRequest, mockResponse);

        // Then
        assertFalse(m2Called.get(), "M2 should not be called when M1 short-circuits");
        assertFalse(handlerCalled.get(), "Handler should not be called when middleware short-circuits");
    }

    @Test
    void testMiddlewareCanModifyRequestAndResponse() throws IOException {
        // Given
        Middleware middleware = (req, res, chain) -> {
            // Add information to the request
            when(req.getAttribute("testAttribute")).thenReturn("testValue");

            // Modify the response
            res.setHeader("X-Test-Header", "TestValue");

            // Continue the chain
            chain.next(req, res);
        };

        Handler handler = (req, res) -> {
            // Handler uses the data added by middleware
            String attrValue = req.getAttribute("testAttribute");
            res.setHeader("X-Handler-Header", "Handler-" + attrValue);
        };

        // Set up mock behavior
        doNothing().when(mockResponse).setHeader(anyString(), anyString());

        // When
        MiddlewareChain chain = new MiddlewareChain(List.of(middleware), handler);
        chain.next(mockRequest, mockResponse);

        // Then
        verify(mockResponse).setHeader("X-Test-Header", "TestValue");
        verify(mockResponse).setHeader("X-Handler-Header", "Handler-testValue");
    }

    @Test
    void testExceptionHandlingInMiddleware() {
        // Given
        IOException ioException = new IOException("Test IO Exception");

        Middleware throwingMiddleware = (req, res, chain) -> {
            throw ioException;
        };

        Handler handler = (req, res) -> fail("Handler should not be called after exception");

        // When/Then
        MiddlewareChain chain = new MiddlewareChain(List.of(throwingMiddleware), handler);
        Exception thrown = assertThrows(IOException.class, () -> chain.next(mockRequest, mockResponse));
        assertEquals(ioException, thrown, "Original exception should be propagated");
    }

    @Test
    void testRuntimeExceptionHandlingInMiddleware() {
        // Given
        RuntimeException runtimeException = new RuntimeException("Test Runtime Exception");

        Middleware throwingMiddleware = (req, res, chain) -> {
            throw runtimeException;
        };

        Handler handler = (req, res) -> fail("Handler should not be called after exception");

        // When/Then
        MiddlewareChain chain = new MiddlewareChain(List.of(throwingMiddleware), handler);
        Exception thrown = assertThrows(RuntimeException.class, () -> chain.next(mockRequest, mockResponse));
        assertEquals(runtimeException, thrown, "Original runtime exception should be propagated");
    }

    @Test
    void testCabinExceptionHandlingInMiddleware() {
        // Given
        CabinException cabinException = new BadRequestException("Test Cabin Exception");

        Middleware throwingMiddleware = (req, res, chain) -> {
            throw cabinException;
        };

        Handler handler = (req, res) -> fail("Handler should not be called after exception");

        // When/Then
        MiddlewareChain chain = new MiddlewareChain(List.of(throwingMiddleware), handler);
        Exception thrown = assertThrows(BadRequestException.class, () -> chain.next(mockRequest, mockResponse));
        assertEquals(cabinException, thrown, "Original CabinException should be propagated");
    }

    @Test
    void testNestedMiddlewareChains() throws IOException {
        // Given
        AtomicInteger counter = new AtomicInteger(0);
        List<String> executionOrder = new ArrayList<>();

        // First middleware chain
        Middleware m1 = (req, res, chain) -> {
            executionOrder.add("m1-before");
            counter.incrementAndGet();  // Increment 1
            chain.next(req, res);
            executionOrder.add("m1-after");
            // Note: No increment here - m1 is counted once
        };

        // Second middleware that creates a sub-chain
        Middleware m2 = (req, res, chain) -> {
            executionOrder.add("m2-before");
            counter.incrementAndGet();  // Increment 2

            // Create nested middleware
            Middleware nestedM1 = (nestedReq, nestedRes, nestedChain) -> {
                executionOrder.add("nested-m1");
                counter.incrementAndGet();  // Increment 3
                nestedChain.next(nestedReq, nestedRes);
            };

            Middleware nestedM2 = (nestedReq, nestedRes, nestedChain) -> {
                executionOrder.add("nested-m2");
                counter.incrementAndGet();  // Increment 4
                nestedChain.next(nestedReq, nestedRes);
            };

            // Create and execute nested chain
            Handler nestedFinalHandler = (nestedReq, nestedRes) -> {
                executionOrder.add("nested-handler");
                counter.incrementAndGet();  // Increment 5
                // Continue with original chain after nested chain completes
                chain.next(nestedReq, nestedRes);
            };

            MiddlewareChain nestedChain = new MiddlewareChain(
                    List.of(nestedM1, nestedM2), nestedFinalHandler);
            nestedChain.next(req, res);

            executionOrder.add("m2-after");
            // Note: No increment here - m2 is counted once
        };

        Middleware m3 = (req, res, chain) -> {
            executionOrder.add("m3");
            counter.incrementAndGet();  // Increment 6
            chain.next(req, res);
        };

        Handler finalHandler = (req, res) -> {
            executionOrder.add("final-handler");
            counter.incrementAndGet();  // Increment 7
        };

        // When
        MiddlewareChain chain = new MiddlewareChain(List.of(m1, m2, m3), finalHandler);
        chain.next(mockRequest, mockResponse);

        // Then
        assertEquals(7, counter.get(), "All middleware and handlers should be executed once");
        assertEquals(List.of(
                "m1-before",
                "m2-before",
                "nested-m1",
                "nested-m2",
                "nested-handler",
                "m3",
                "final-handler",
                "m2-after",
                "m1-after"
        ), executionOrder, "Middleware should execute in correct order with proper nesting");
    }
}
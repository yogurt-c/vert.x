/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.http;

import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketClientOptions;
import io.vertx.test.http.HttpTestBase;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class Http1xClientEvictionTest extends HttpTestBase {

    @Test
    public void testWebSocketClientHandshakeEviction() throws Exception {
        // Create WebSocket client with very short idle timeout to trigger eviction
        WebSocketClient wsClient = vertx.createWebSocketClient(
            new WebSocketClientOptions()
                .setIdleTimeout(100)
                .setIdleTimeoutUnit(TimeUnit.MILLISECONDS)
        );

        // Server: delay the handshake response to trigger idle timeout during upgrade
        server.requestHandler(req -> {
            if (req.headers().contains("Upgrade", "websocket", true)) {
                // Delay longer than client idle timeout before completing handshake
                // This simulates a slow server response during WebSocket upgrade
                vertx.setTimer(300, id -> {
                    req.toWebSocket().onComplete(ar -> {
                        if (ar.succeeded()) {
                            ar.result().close();
                        }
                    });
                });
            }
        }).listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST).onComplete(onSuccess(s -> {

            // Attempt WebSocket connection
            wsClient.connect(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/").onComplete(res -> {
                // After fix: evictionHandler is called before upgrade starts,
                // removing connection from pool, so idle timeout won't evict it
                if (res.succeeded()) {
                    res.result().close();
                    testComplete();
                } else {
                    // Before fix: connection would be evicted during delayed handshake
                    fail("WebSocket handshake should succeed after fix: " + res.cause().getMessage());
                }
            });
        }));

        await();
    }
}

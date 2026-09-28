/*
 * Copyright (C) 2018-2026 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.junit.jupiter.api.Test;

class ClosedConnectionRetryStrategyTest {

  private static final String URL = "http://localhost/";

  @Test
  void retriesIdempotentRequestOnceAfterClosedConnection() {
    SimpleHttpRequest get = SimpleRequestBuilder.get(URL).build();
    ConnectionClosedException closed = new ConnectionClosedException();

    assertTrue(ClosedConnectionRetryStrategy.INSTANCE.retryRequest(get, closed, 1, null));
    assertFalse(ClosedConnectionRetryStrategy.INSTANCE.retryRequest(get, closed, 2, null));
  }

  @Test
  void doesNotRetryNonIdempotentRequest() {
    SimpleHttpRequest post = SimpleRequestBuilder.post(URL).build();

    assertFalse(ClosedConnectionRetryStrategy.INSTANCE.retryRequest(
        post, new ConnectionClosedException(), 1, null));
  }

  @Test
  void keepsDefaultNonRetriableExceptions() {
    SimpleHttpRequest get = SimpleRequestBuilder.get(URL).build();

    assertFalse(ClosedConnectionRetryStrategy.INSTANCE.retryRequest(
        get, new UnknownHostException(), 1, null));
  }

  @Test
  void sharedClientRecoversFromConnectionClosedUnderRequest() throws Exception {
    try (ClosingServer server = new ClosingServer();
        CloseableHttpAsyncClient client = ConnectionManager.createHttpClient("test")) {
      SimpleHttpResponse response = client.execute(server.request(), null)
          .get(10, TimeUnit.SECONDS);

      assertEquals(200, response.getCode());
      assertEquals("ok", response.getBodyText());
      assertEquals(2, server.accepted());
    }
  }

  @Test
  void defaultClientFailsWhenConnectionClosesUnderRequest() throws Exception {
    try (ClosingServer server = new ClosingServer();
        CloseableHttpAsyncClient client = HttpAsyncClients.createDefault()) {
      client.start();

      ExecutionException failure = assertThrows(ExecutionException.class,
          () -> client.execute(server.request(), null).get(10, TimeUnit.SECONDS));

      assertInstanceOf(ConnectionClosedException.class, failure.getCause());
      assertEquals(1, server.accepted());
    }
  }

  /**
   * A server that closes the first connection as soon as its request has been read, the way a
   * pooled connection the remote end has just closed fails a request, and answers every later
   * connection with {@code 200 ok}.
   */
  private static final class ClosingServer implements AutoCloseable {

    private static final byte[] OK = ("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n"
        + "Connection: close\r\n\r\nok").getBytes(StandardCharsets.US_ASCII);

    private final ServerSocket socket;
    private final AtomicInteger accepted = new AtomicInteger();

    ClosingServer() throws IOException {
      this.socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
      Thread thread = new Thread(this::serve, "closing-server");
      thread.setDaemon(true);
      thread.start();
    }

    SimpleHttpRequest request() {
      return SimpleRequestBuilder.get("http://127.0.0.1:" + socket.getLocalPort() + "/").build();
    }

    int accepted() {
      return accepted.get();
    }

    private void serve() {
      while (!socket.isClosed()) {
        try (Socket connection = socket.accept()) {
          readRequestHead(connection.getInputStream());
          if (accepted.incrementAndGet() > 1) {
            connection.getOutputStream().write(OK);
            connection.getOutputStream().flush();
          }
        } catch (SocketException closed) {
          return;
        } catch (IOException ignored) {
          // A client that went away mid-request is the next accept's concern.
        }
      }
    }

    private static void readRequestHead(InputStream in) throws IOException {
      int matched = 0;
      byte[] end = {'\r', '\n', '\r', '\n'};
      while (matched < end.length) {
        int read = in.read();
        if (read < 0) {
          return;
        }
        matched = read == end[matched] ? matched + 1 : (read == end[0] ? 1 : 0);
      }
    }

    @Override
    public void close() throws IOException {
      socket.close();
    }
  }
}

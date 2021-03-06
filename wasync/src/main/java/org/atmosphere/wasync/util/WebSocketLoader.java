/*
 * Copyright 2012 Jeanfrancois Arcand
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.wasync.util;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import org.atmosphere.wasync.Client;
import org.atmosphere.wasync.ClientFactory;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.RequestBuilder;
import org.atmosphere.wasync.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A utility class that can be used to load a WebSocket enabled Server
 *
 * @author jeanfrancois Arcand
 */
public class WebSocketLoader {

    private final static Logger logger = LoggerFactory.getLogger(WebSocketLoader.class);

    public static void main(String[] s) throws InterruptedException, IOException {

        if (s.length == 0) {
            s = new String[]{"1", "10000", "http://10.0.1.4:8080/default/test"};
        }

        final int clientNum = Integer.valueOf(s[0]);
        final int messageNum = Integer.valueOf(s[1]);
        String url = s[2];

        System.out.println("Connecting to: " + s[2]);
        System.out.println("Number of Client: " + clientNum);
        System.out.println("Number of Message: " + messageNum);

        AsyncHttpClientConfig.Builder b = new AsyncHttpClientConfig.Builder();
        b.setFollowRedirects(true).setIdleConnectionTimeoutInMs(-1).setRequestTimeoutInMs(-1).setConnectionTimeoutInMs(600000);
        final AsyncHttpClient c = new AsyncHttpClient(b.build());

        final CountDownLatch l = new CountDownLatch(clientNum);

        final CountDownLatch messages = new CountDownLatch(messageNum * clientNum);

        Client client = ClientFactory.getDefault().newClient();
        RequestBuilder request = client.newRequestBuilder();
        request.method(Request.METHOD.GET).uri(url);
        request.transport(Request.TRANSPORT.WEBSOCKET);

        long clientCount = l.getCount();
        final AtomicLong total = new AtomicLong(0);
        final AtomicLong completed = new AtomicLong(0);
        final AtomicInteger cc = new AtomicInteger();

        Socket[] sockets = new Socket[clientNum];
        for (int i = 0; i < clientCount; i++) {
            final AtomicLong start = new AtomicLong(0);
            sockets[i] = client.create(client.newOptionsBuilder().runtime(c).reconnect(false).build())
                    .on(new Function<Integer>() {


                        @Override
                        public void on(Integer statusCode) {
                            start.set(System.currentTimeMillis());
                            System.out.println("Connected clients: " + cc.getAndIncrement());
                            l.countDown();
                        }
                    }).on(new Function<String>() {

                        int mCount = 0;

                        @Override
                        public void on(String s) {
                            if (s.startsWith("message")) {
                                String[] m = s.split("\n\r");
                                mCount += m.length;
                                messages.countDown();
                                if (mCount == messageNum) {
                                    System.out.println("All messages received " + mCount + " Completed: " + completed.incrementAndGet());
                                    total.addAndGet(System.currentTimeMillis() - start.get());
                                }
                            }
                        }
                    }).on(new Function<Throwable>() {
                        @Override
                        public void on(Throwable t) {
                            t.printStackTrace();
                        }
                    });

        }

        for (int i = 0; i < clientCount; i++) {
            sockets[i].open(request.build());
        }

        l.await(60, TimeUnit.SECONDS);

        System.out.println("OK, all Connected: " + clientNum);

        Socket socket = client.create(client.newOptionsBuilder().runtime(c).build());
        socket.open(request.build());
        for (int i = 0; i < messageNum; i++) {
            socket.fire("message" + i);
        }
        messages.await();
        socket.close();
        for (int i = 0; i < clientCount; i++) {
            sockets[i].close();
        }
        c.close();
        System.out.println("Total: " + (total.get() / clientCount));

    }

}

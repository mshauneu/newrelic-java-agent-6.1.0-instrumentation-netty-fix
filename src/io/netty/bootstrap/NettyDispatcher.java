/*
 *
 *  * Copyright 2020 New Relic Corporation. All rights reserved.
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package io.netty.bootstrap;

import com.agent.instrumentation.netty40.RequestWrapper;
import com.newrelic.agent.bridge.AgentBridge;
import com.newrelic.agent.bridge.Transaction;
import com.newrelic.agent.bridge.TransactionNamePriority;
import com.newrelic.api.agent.Trace;
import com.newrelic.api.agent.TracedMethod;

import io.netty.channel.ChannelHandlerContext_Instrumentation;
import io.netty.handler.codec.http.HttpRequest;

import java.util.logging.Level;

/**
 * This isn't a netty class. This is an agent class which will start a transaction on i/o read.
 *
 * Since this class creates a tracer, its class+method name will show in the TT, hence the class name.
 */
public class NettyDispatcher {

    private static volatile NettyDispatcher instance = null;

    public static NettyDispatcher get() {
        if (null == instance) {
            synchronized (NettyDispatcher.class) {
                if (null == instance) {
                    instance = new NettyDispatcher();
                }
            }
        }
        return instance;
    }

    private NettyDispatcher() {
        AgentBridge.instrumentation.retransformUninstrumentedClass(NettyDispatcher.class);
    }

    @Trace(dispatcher = true)
    public static void channelRead(ChannelHandlerContext_Instrumentation ctx, Object msg) {
        HttpRequest httpRequest = (HttpRequest) msg;
        ctx.pipeline().token = AgentBridge.getAgent().getTransaction().getToken();

        TracedMethod tracer = AgentBridge.getAgent().getTransaction().getTracedMethod();
        if (tracer == null) {
            AgentBridge.getAgent().getLogger().log(Level.FINEST, "Unable to dispatch netty tx. No tracer."); // it happens.
        } else {
            tracer.setMetricName("NettyUpstreamDispatcher");
            AgentBridge.getAgent().getTransaction().setTransactionName(
                    TransactionNamePriority.SERVLET_NAME, true, "NettyDispatcher", getTransactionName(httpRequest));
        }
        Transaction tx = AgentBridge.getAgent().getTransaction(false);
        if (tx != null) {
            tx.setWebRequest(new RequestWrapper(httpRequest));
        }
    }

    private static String getTransactionName(HttpRequest httpRequest) {
        String uri = httpRequest.getUri();
        StringBuilder builder = new StringBuilder(uri.length() + 16);

        for (int i = 0; i < uri.length(); i++) {
            char c = uri.charAt(i);
            if (c == '?') {
               break;
            }
            if (c == '/' && builder.length() > 0 && builder.charAt(builder.length() - 1) == '/') {
                continue;
            }
            builder.append(c);
        }

        if (builder.charAt(builder.length() - 1) == '/') {
            builder.replace(builder.length() - 1, builder.length(), " ");
        } else {
            builder.append(' ');
        }
        builder.append('(').append(httpRequest.getMethod()).append(')');
        return builder.toString();
    }

}

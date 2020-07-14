package de.zalando.ep.zalenium.proxy.novnc;


import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSockifyServer {

    private static Server server;
    private final static List<Handler> webSocketHandlerList = new ArrayList<>();
    private final static Object _synchronizationObject = new Object();
    private static final Logger LOG = LoggerFactory.getLogger(WebSockifyServer.class.getName());

    private final static void setupSSL(Server server, int port) {

        Resource keyStoreResource = Resource.newResource(WebSockifyServer.class.getResource("/keystore"));
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStoreResource(keyStoreResource);
        sslContextFactory.setKeyStorePassword("sh4@ts!97");
        sslContextFactory.setKeyManagerPassword("sh4@ts!97");
        SslConnectionFactory sslConnectionFactory = new SslConnectionFactory(sslContextFactory,
                HttpVersion.HTTP_1_1.asString());
        HttpConnectionFactory httpConnectionFactory = new HttpConnectionFactory(new HttpConfiguration());
        ServerConnector sslConnector = new ServerConnector(server, sslConnectionFactory, httpConnectionFactory);
        sslConnector.setHost("localhost");
        sslConnector.setPort(port);
        server.addConnector(sslConnector);
    }

    public final static void setup(final int port, final boolean enableSSL) {
        synchronized (_synchronizationObject) {
            if (server == null || server.isStopped() || server.isStopping()) {
                new Thread(() -> {
                    try {

                        LOG.info("Initializing WsServer on Port " + port);

                        if (enableSSL) {
                            server = new Server();
                            setupSSL(server, port);
                            LOG.debug("SSL enabled on Port " + port);
                        } else {
                            server = new Server(port);                            
                        }

                        // handler configuration
                        //HandlerCollection handlerCollection = new HandlerCollection();
                        //handlerCollection.setHandlers(webSocketHandlerList.toArray(new Handler[0]));
                        //server.setHandler(handlerCollection);

                        //addHandler(new WebSockifyHandler(), "/");
                        server.setHandler(new WebSockifyHandler());
                        server.start();
                        LOG.info("WsServer started.");

                        server.join();
                    } catch (Exception e) {
                        e.printStackTrace(System.out);
                    }
                }).start();
            }
            // pause needed
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace(System.out);
            }
        }
    }

    public final static void addHandler(WebSocketHandler handler, String path) {
        ContextHandler wsContextHandler = new ContextHandler();
        wsContextHandler.setHandler(handler);
        wsContextHandler.setContextPath(path); // this context path doesn't work ftm
        webSocketHandlerList.add(handler);
    }

    public final static boolean isRunning() {
        return server != null && server.isRunning();
    }

    public final static String describe() {
        if (!isRunning())
            return WebSockifyServer.class.getName() + " is NOT Running!";
        return WebSockifyServer.class.getName() +" Status -- Uri: " + server.getURI().toString() + ", Status: " + server.getState() + ", Version: " + Server.getVersion();
    }

}


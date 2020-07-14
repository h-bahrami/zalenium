package de.zalando.ep.zalenium.proxy.novnc;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.websocket.api.CloseStatus;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketFrame;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.jetty.websocket.api.StatusCode;

@WebSocket
public class WebSockifyHandler extends WebSocketHandler {

    Socket vncSocket;
    private final Logger LOG = LoggerFactory.getLogger(WebSockifyHandler.class.getName());

    @Override
    public void configure(WebSocketServletFactory webSocketServletFactory) {
        webSocketServletFactory.register(WebSockifyHandler.class);
    }

    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        LOG.info("Received handle " + target);

        try {
            if (this.getWebSocketFactory().isUpgradeRequest(request, response)) {
                LOG.debug("Adding header Sec-WebSocket-Protocol");

                // response.addHeader("Sec-WebSocket-Protocol", "binary");

                if (this.getWebSocketFactory().acceptWebSocket(request, response)) {

                    LOG.debug("websocket accepted");
                    baseRequest.setHandled(true);

                    return;
                }
                LOG.debug("websocket not accepted");
                if (response.isCommitted()) {
                    LOG.debug("response commited.");
                    return;
                }
            }
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        } finally {
            // super.handle(target, baseRequest, request, response);
        }
    }

    @OnWebSocketConnect
    public void onConnect(final Session session) throws IOException, InterruptedException {
        LOG.info("Connect: " + session.getRemoteAddress().getAddress());
        LOG.debug(session.getUpgradeRequest().getRequestURI().toString());

        String[] temp = session.getUpgradeRequest().getRequestURI().getRawPath().split("/");
        String ip = temp[temp.length - 2];
        int port = Integer.parseInt(temp[temp.length - 1]) + 10000;
        LOG.info("Connecting to " + ip + ":" + port);
        vncSocket = new Socket(ip, port);
        Thread readThread = new Thread(new Runnable() {
            public void run() {
                try {
                    byte[] b = new byte[1500];
                    int readBytes;
                    while (true) {
                        readBytes = vncSocket.getInputStream().read(b);
                        // LOG.debug("read bytes " + readBytes);
                        // if(readBytes > 0 && readBytes < 20) LOG.debug(new String(b, 0, readBytes));
                        if (readBytes == -1) {
                            break;
                        }
                        if (readBytes > 0) {
                            session.getRemote().sendBytes(ByteBuffer.wrap(b, 0, readBytes));
                        }
                    }
                    session.close(new CloseStatus(StatusCode.NORMAL,
                            "Test has been finished and no more VNC available."));
                    session.disconnect();
                    LOG.info("Streaming finished and session closed.");
                } catch (IOException e) {
                    LOG.error(e.getMessage(), e);
                }
            }
        });
        readThread.start();

    }

    @OnWebSocketFrame
    public void onFrame(Frame f) throws IOException {
        // LOG.info("Frame: " + f.getPayloadLength());
        byte[] data = new byte[f.getPayloadLength()];
        f.getPayload().get(data);
        // LOG.info(new String(data));
        vncSocket.getOutputStream().write(data);
    }

    @OnWebSocketError
    public void onError(Throwable cause) {
        // System.out.println(cause.getMessage());
        // LOG.error(cause.getMessage(), cause);
        LOG.info(cause.getMessage());
    }

}
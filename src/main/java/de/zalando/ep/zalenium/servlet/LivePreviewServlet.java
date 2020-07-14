package de.zalando.ep.zalenium.servlet;

/*
    This class renders an HTML with a similar appearance to the Grid Console, it just adds an iFrame that
    allows users to see what is happening inside the container while they run their tests.
    The code here is based on the ConsoleServlet class from the Selenium Grid
 */

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openqa.grid.internal.GridRegistry;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.utils.HtmlRenderer;
import org.openqa.grid.web.servlet.RegistryBasedServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteStreams;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import de.zalando.ep.zalenium.dashboard.TestInformation;
import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;
import de.zalando.ep.zalenium.servlet.renderer.LiveNodeHtmlRenderer;
import de.zalando.ep.zalenium.servlet.renderer.TemplateRenderer;
import de.zalando.ep.zalenium.util.Environment;

public class LivePreviewServlet extends RegistryBasedServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(LivePreviewServlet.class.getName());
    private static final Environment env = new Environment();
    private static final String contextPath = env.getContextPath();

    @SuppressWarnings("unused")
    public LivePreviewServlet() {
        this(null);
    }

    public LivePreviewServlet(GridRegistry registry) {
        super(registry);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        try {
            String command = request.getParameter("get");
            LOGGER.info("command: " + command);
            if (command != null && command.equalsIgnoreCase("sessions")) {
                processSessionsList(request, response);
            } else if (command != null && command.equalsIgnoreCase("vncurl")) {
                String sessionId = request.getParameter("id");
                processGetSessionVncIpPort(request, response, sessionId);
            } else {
                // process default
                process(request, response);
            }            
        } catch (Exception e) {
            LOGGER.error(e.toString(), e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        try {
            process(request, response);          
        } catch (Exception e) {
            LOGGER.error(e.toString(), e);
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected void process(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String refresh = "1200";
        String testBuild = "";
        boolean filterActiveSessions = false;
        try {
            refresh = Optional.ofNullable(request.getParameter("refresh")).orElse(refresh);
            testBuild = Optional.ofNullable(request.getParameter("build")).orElse(testBuild);
            String only_active_sessions = Optional.ofNullable(request.getParameter("only_active_sessions"))
                    .orElse(Boolean.FALSE.toString());
            filterActiveSessions = Boolean.parseBoolean(only_active_sessions);
        } catch (Exception e) {
            LOGGER.debug(e.toString(), e);
        }

        List<String> nodes = new ArrayList<>();
        for (RemoteProxy proxy : getRegistry().getAllProxies()) {
            if (proxy instanceof DockerSeleniumRemoteProxy) {
                DockerSeleniumRemoteProxy dockerSeleniumRemoteProxy = (DockerSeleniumRemoteProxy) proxy;
                HtmlRenderer renderer = new LiveNodeHtmlRenderer(dockerSeleniumRemoteProxy);
                // Render the nodes that are part of an specified test build
                if ((testBuild.isEmpty() || testBuild.equalsIgnoreCase(dockerSeleniumRemoteProxy.getTestBuild()))
                        && (!filterActiveSessions || proxy.isBusy())) {
                    nodes.add(renderer.renderSummary());
                }
            }
        }

        int size = nodes.size();
        int rightColumnSize = size / 2;
        int leftColumnSize = size - rightColumnSize;

        StringBuilder leftColumnNodes = new StringBuilder();
        for (int i = 0; i < leftColumnSize; i++) {
            leftColumnNodes.append(nodes.get(i));
        }

        StringBuilder rightColumnNodes = new StringBuilder();
        for (int i = leftColumnSize; i < nodes.size(); i++) {
            rightColumnNodes.append(nodes.get(i));
        }

        Map<String, String> livePreviewValues = new HashMap<>();
        livePreviewValues.put("{{refreshInterval}}", refresh);
        livePreviewValues.put("{{leftColumnNodes}}", leftColumnNodes.toString());
        livePreviewValues.put("{{rightColumnNodes}}", rightColumnNodes.toString());
        livePreviewValues.put("{{contextPath}}", contextPath);
        String templateFile = "html_templates/live_preview_servlet.html";
        TemplateRenderer templateRenderer = new TemplateRenderer(templateFile);
        String renderTemplate = templateRenderer.renderTemplate(livePreviewValues);

        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(200);

        writeOut(response, renderTemplate);
    }

    @SuppressWarnings("WeakerAccess")
    protected void processGetSessionVncIpPort(HttpServletRequest request, HttpServletResponse response,
            String sessionId) throws IOException {

        String refresh = "1200";
        String testBuild = "";
        try {
            refresh = Optional.ofNullable(request.getParameter("refresh")).orElse(refresh);
            testBuild = Optional.ofNullable(request.getParameter("build")).orElse(testBuild);
        } catch (Exception e) {
            LOGGER.debug(e.toString(), e);
        }

        String json = "";
        for (RemoteProxy proxy : getRegistry().getAllProxies()) {
            if (proxy instanceof DockerSeleniumRemoteProxy) {
                DockerSeleniumRemoteProxy dockerSeleniumRemoteProxy = (DockerSeleniumRemoteProxy) proxy;
                TestInformation testInfo = dockerSeleniumRemoteProxy.getTestInformation();
                if (testInfo != null && (testInfo.getTestFileNameTemplate().equals(sessionId)
                        || testInfo.getWebdriverRemoteSessionId().equals(sessionId))) {

                    if (testBuild.isEmpty() || testBuild.equalsIgnoreCase(dockerSeleniumRemoteProxy.getTestBuild())) {
                        JsonObject jobj = new JsonObject();
                        jobj.addProperty("ip", dockerSeleniumRemoteProxy.getRegistration().getIpAddress());
                        jobj.addProperty("port", dockerSeleniumRemoteProxy.getRegistration().getNoVncPort());
                        json = jobj.toString();
                    }
                    break;
                }
            }
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(200);

        if (json.length() == 0) {
            LOGGER.debug("No Session found with ID: " + sessionId);
            JsonObject jobj = new JsonObject();
            jobj.addProperty("message", "Session is Closed or Not Available!");
            json = jobj.toString();
        }

        writeOut(response, json);
    }

    @SuppressWarnings("WeakerAccess")
    protected void processSessionsList(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String refresh = "1200";
        String testBuild = "";
        try {
            refresh = Optional.ofNullable(request.getParameter("refresh")).orElse(refresh);
            testBuild = Optional.ofNullable(request.getParameter("build")).orElse(testBuild);
        } catch (Exception e) {
            LOGGER.debug(e.toString(), e);
        }

        JsonArray json = new JsonArray();
        for (RemoteProxy proxy : getRegistry().getAllProxies()) {
            if (proxy instanceof DockerSeleniumRemoteProxy) {
                DockerSeleniumRemoteProxy dockerSeleniumRemoteProxy = (DockerSeleniumRemoteProxy) proxy;

                if (testBuild.isEmpty() || testBuild.equalsIgnoreCase(dockerSeleniumRemoteProxy.getTestBuild())) {
                    JsonObject obj = new JsonObject();

                    TestInformation testInfo = dockerSeleniumRemoteProxy.getTestInformation();
                    obj.addProperty("testName", dockerSeleniumRemoteProxy.getTestName());
                    if (testInfo != null) {
                        obj.addProperty("sessionId", testInfo.getSeleniumSessionId());
                        obj.addProperty("testStatus", testInfo.getTestStatus().toString());
                        obj.addProperty("web.driver.remote.sessionid", testInfo.getWebdriverRemoteSessionId());
                        obj.addProperty("testFileNameTemplate", testInfo.getTestFileNameTemplate());
                    }
                    json.add(obj);
                }
            }
        }
        response.setContentType("application/json");
        writeOut(response, json.toString());
    }

    private void writeOut(HttpServletResponse response, String content)
            throws IOException, UnsupportedClassVersionError {
        try (InputStream in = new ByteArrayInputStream(content.getBytes("UTF-8"))) {
            ByteStreams.copy(in, response.getOutputStream());
        } finally {
            response.getOutputStream().close();
        }
    }

}

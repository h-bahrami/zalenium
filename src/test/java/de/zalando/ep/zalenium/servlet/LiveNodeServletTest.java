package de.zalando.ep.zalenium.servlet;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.function.Supplier;

import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import de.zalando.ep.zalenium.container.DockerContainerClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.selenium.remote.server.jmx.JMXHelper;

import de.zalando.ep.zalenium.container.ContainerFactory;
import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;
import de.zalando.ep.zalenium.util.DockerContainerMock;
import de.zalando.ep.zalenium.util.SimpleRegistry;
import de.zalando.ep.zalenium.util.TestUtils;

@SuppressWarnings("Duplicates")
public class LiveNodeServletTest {

    private GridRegistry registry;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private Supplier<DockerContainerClient> originalContainerClient;

    @Before
    public void setUp() throws IOException {
        try {
            ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=Hub");
            ManagementFactory.getPlatformMBeanServer().getObjectInstance(objectName);
            new JMXHelper().unregister(objectName);
        } catch (MalformedObjectNameException | InstanceNotFoundException e) {
            // Might be that the object does not exist, it is ok. Nothing to do, this is
            // just a cleanup task.
        }
        registry = new SimpleRegistry();

        this.originalContainerClient = ContainerFactory.getDockerContainerClient();
        ContainerFactory.setDockerContainerClient(DockerContainerMock::getRegisterOnlyDockerContainerClient);

        DockerSeleniumRemoteProxy p1 = TestUtils.getNewBasicRemoteProxy("app1", "http://machine1:4444/", registry);
        DockerSeleniumRemoteProxy p2 = TestUtils.getNewBasicRemoteProxy("app1", "http://machine2:4444/", registry);

        registry.add(p1);
        registry.add(p2);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);

        when(request.getParameter("refresh")).thenReturn("1");
        when(request.getServerName()).thenReturn("localhost");
        when(response.getOutputStream()).thenReturn(TestUtils.getMockedServletOutputStream());
    }

    @Test
    public void addedNodesAreRenderedInServlet() throws IOException {

        LivePreviewServlet livePreviewServletServlet = new LivePreviewServlet(registry);

        livePreviewServletServlet.doPost(request, response);

        String responseContent = response.getOutputStream().toString();
        assertThat(responseContent, containsString("Zalenium Live Preview"));
        assertThat(responseContent, containsString("http://machine1:4444"));
        assertThat(responseContent, containsString("http://machine2:4444"));
        assertThat(responseContent, containsString(
                "/vnc/host/machine1/port/40000/?nginx=&path=proxy/machine1:40000/websockify&view_only=true'"));
        assertThat(responseContent, containsString(
                "/vnc/host/machine1/port/40000/?nginx=&path=proxy/machine1:40000/websockify&view_only=false'"));
        assertThat(responseContent, containsString(
                "/vnc/host/machine2/port/40000/?nginx=&path=proxy/machine2:40000/websockify&view_only=true'"));
        assertThat(responseContent, containsString(
                "/vnc/host/machine2/port/40000/?nginx=&path=proxy/machine2:40000/websockify&view_only=false'"));
    }

    @Test
    public void postAndGetReturnSameContent() throws IOException {

        LivePreviewServlet livePreviewServletServlet = new LivePreviewServlet(registry);

        livePreviewServletServlet.doPost(request, response);
        String postResponseContent = response.getOutputStream().toString();

        livePreviewServletServlet.doGet(request, response);
        String getResponseContent = response.getOutputStream().toString();
        assertThat(getResponseContent, containsString(postResponseContent));
    }

    @Test
    public void noRefreshInHtmlWhenParameterIsInvalid() throws IOException {
        when(request.getParameter("refresh")).thenReturn("XYZ");

        LivePreviewServlet livePreviewServletServlet = new LivePreviewServlet(registry);

        livePreviewServletServlet.doPost(request, response);
        String postResponseContent = response.getOutputStream().toString();
        assertThat(postResponseContent, containsString("<meta http-equiv='refresh' content='XYZ' />"));
    }

    @Test
    public void getSessionsReturnsAnArrayOfJsonObjects() throws IOException {
        when(request.getParameter("get")).thenReturn("sessions");

        LivePreviewServlet livePreviewServlet = new LivePreviewServlet(registry);
        livePreviewServlet.doGet(request, response);
        String json = response.getOutputStream().toString();
        Gson gson = new Gson();
        Object[] array = gson.fromJson(json, Object[].class);
        assertNotNull(array);
        assertNotEquals(array.length, 0);
    }

    
    @Test
    public void getVncIpPortJson() throws IOException{
        when(request.getParameter("get")).thenReturn("vncurl");
        when(request.getParameter("id")).thenReturn("f016f84d-34c9-47a2-8a4c-039ff636b7b4");

        LivePreviewServlet livePreviewServlet = new LivePreviewServlet(registry);
        livePreviewServlet.doGet(request, response);
        String json = response.getOutputStream().toString();
        Gson gson = new Gson();        
        
        TestType obj = gson.fromJson(json, TestType.class);
        assertNotNull(obj);
        assertEquals(obj.message, "Session is Closed or Not Available!");        
    }

    @After
    public void tearDown() throws MalformedObjectNameException {
        ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://localhost:40000\"");
        new JMXHelper().unregister(objectName);
        objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://localhost:40001\"");
        new JMXHelper().unregister(objectName);
        ContainerFactory.setDockerContainerClient(originalContainerClient);
    }


    class TestType {private String message; public String getMessage() {return message;}}

}

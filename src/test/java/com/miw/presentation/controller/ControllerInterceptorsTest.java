package com.miw.presentation.controller;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.Logger;

import com.miw.presentation.commands.Command;
import com.miw.presentation.commands.LoginCommand;
import com.miw.presentation.commands.ShowBooksCommand;
import com.miw.presentation.commands.ShowSpecialOfferCommand;
import com.miw.presentation.interceptors.AuthorizationInterceptor;
import com.miw.presentation.interceptors.Interceptor;
import com.miw.presentation.interceptors.InterceptorChain;
import com.miw.presentation.di.HttpSessionAware;
import com.miw.presentation.di.ServletRequestAware;
import com.miw.presentation.interceptors.LoggingInterceptor;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import junit.framework.TestCase;

public class ControllerInterceptorsTest extends TestCase {
    private final ControllerServlet servlet = new ControllerServlet();
    private final Map<String, Properties> configurationSnapshot = new HashMap<>();

    @Override
    protected void setUp() throws Exception {
        for (String name : List.of("data", "commands", "interceptors")) {
            configurationSnapshot.put(name, (Properties) configurationProperties(name).clone());
        }
    }

    @Override
    protected void tearDown() throws Exception {
        for (Map.Entry<String, Properties> entry : configurationSnapshot.entrySet()) {
            Properties properties = configurationProperties(entry.getKey());
            properties.clear();
            properties.putAll(entry.getValue());
        }
    }

    private Properties configurationProperties(String name) throws Exception {
        Field field = ControllerConfigurationManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Properties) field.get(ControllerConfigurationManager.getInstance());
    }

    private boolean executeCommand(HttpServletRequest request, HttpServletResponse response,
            Command command, List<Interceptor> interceptors) throws Exception {
        return new InterceptorChain(interceptors).execute(request, response, command);
    }


    public void testActionMappings() {
        ControllerConfigurationManager configuration = ControllerConfigurationManager.getInstance();
        assertTrue(configuration.getCommand("LoginAction") instanceof LoginCommand);
        assertEquals("index.html", configuration.getForward("LoginAction"));
        assertEquals(1, configuration.getInterceptorsForCommand("LoginAction").size());
        for (String action : List.of("ShowBooksAction", "ShowSpecialOfferAction")) {
            List<Interceptor> chain = configuration.getInterceptorsForCommand(action);
            assertEquals(2, chain.size());
            assertTrue(chain.get(0) instanceof LoggingInterceptor);
            assertTrue(chain.get(1) instanceof AuthorizationInterceptor);
        }
    }

    public void testConfigurationIsSingleton() {
        assertSame(ControllerConfigurationManager.getInstance(), ControllerConfigurationManager.getInstance());
        assertEquals(0, ControllerConfigurationManager.class.getConstructors().length);
    }

    public void testNewInterceptorBlocksThroughConfigurationWithoutRenderingAResponse() throws Exception {
        configureProbe("logging,probe");
        Exchange exchange = new Exchange();
        exchange.parameters.put("action", new String[] { "ProbeAction" });
        exchange.sessionAttributes.put("user", "user2");
        servlet.doGet(exchange.request, exchange.response);
        assertEquals(403, exchange.status);
        assertEquals(List.of("/error.jsp"), exchange.forwards);
        assertEquals("Operación no permitida.", exchange.attributes.get(Interceptor.ERROR_MESSAGE_ATTRIBUTE));
        assertEquals("user2", exchange.attributes.get("probeUser"));
        assertEquals(Boolean.TRUE, exchange.attributes.get("probeLoggerInjected"));
        assertNull(exchange.attributes.get("commandExecuted"));
        assertNull(exchange.attributes.get("startTime_ProbeCommand"));
    }

    public void testBlockedViewCanBeOverriddenPerAction() throws Exception {
        configureProbe("probe");
        configurationProperties("data").setProperty("ProbeAction.blocked", "/custom-denied.jsp");
        Exchange exchange = new Exchange();
        exchange.parameters.put("action", new String[] { "ProbeAction" });
        servlet.doGet(exchange.request, exchange.response);
        assertEquals(403, exchange.status);
        assertEquals(List.of("/custom-denied.jsp"), exchange.forwards);
    }

    public void testExplicitEmptyChainExecutesExistingCommand() throws Exception {
        configureProbe("");
        Exchange exchange = new Exchange();
        exchange.parameters.put("action", new String[] { "ProbeAction" });
        servlet.doGet(exchange.request, exchange.response);
        assertEquals(200, exchange.status);
        assertEquals(Boolean.TRUE, exchange.attributes.get("commandExecuted"));
        assertEquals(List.of("/probe.jsp"), exchange.forwards);
    }

    public void testChangingConfiguredOrderChangesWhichInterceptorsStart() throws Exception {
        configureProbe("probe,logging");
        List<String> messages = new ArrayList<>();
        servlet.logger = proxy(Logger.class, (p, m, a) -> {
            messages.add(m.getName());
            return null;
        });
        Exchange exchange = new Exchange();
        exchange.parameters.put("action", new String[] { "ProbeAction" });
        servlet.doGet(exchange.request, exchange.response);
        assertEquals(403, exchange.status);
        assertTrue(messages.isEmpty());
    }

    private void configureProbe(String aliases) throws Exception {
        configurationProperties("data").setProperty("ProbeAction.command", ProbeCommand.class.getName());
        configurationProperties("data").setProperty("ProbeAction.success", "/probe.jsp");
        configurationProperties("commands").setProperty("command.ProbeAction.interceptors", aliases);
        configurationProperties("interceptors").setProperty("interceptor.probe.class", ProbeInterceptor.class.getName());
    }

    public static class ProbeCommand implements Command, ServletRequestAware {
        private HttpServletRequest request;

        public void setServletRequest(HttpServletRequest request) {
            this.request = request;
        }

        public void execute() {
            request.setAttribute("commandExecuted", Boolean.TRUE);
        }
    }

    // Solo se registra en la configuración de prueba; el framework no conoce esta clase.
    public static class ProbeInterceptor implements Interceptor, HttpSessionAware {
        private HttpSession session;
        private Logger log;

        public void setHttpSession(HttpSession session) {
            this.session = session;
        }

        public void setLog(Logger log) {
            this.log = log;
        }

        public boolean preExecute(HttpServletRequest request, HttpServletResponse response, Command command) {
            request.setAttribute("probeUser", session.getAttribute("user"));
            request.setAttribute("probeLoggerInjected", log != null);
            return false;
        }

        public void postExecute(HttpServletRequest request, HttpServletResponse response, Command command, Exception ex) {
        }
    }

    public void testLoginThroughServlet() throws Exception {
        Exchange exchange = new Exchange();
        exchange.parameters.put("action", new String[] { "LoginAction" });
        exchange.parameters.put("username", new String[] { "user2" });
        servlet.doPost(exchange.request, exchange.response);
        assertEquals("user2", exchange.sessionAttributes.get("user"));
        assertEquals(List.of("index.html"), exchange.forwards);
        assertNull(exchange.attributes.get("startTime_LoginCommand"));
    }

    public void testRolePermissions() throws Exception {
        for (String user : List.of("user1", "user2", "user3")) {
            assertAuthorization(user, new ShowBooksCommand(), true);
            assertAuthorization(user, new ShowSpecialOfferCommand(), !"user2".equals(user));
        }
        assertAuthorization(null, new ShowBooksCommand(), false);
        assertAuthorization(null, new ShowSpecialOfferCommand(), false);
        assertAuthorization("unknown", new ShowBooksCommand(), false);
    }

    private void assertAuthorization(String user, Command command, boolean expected) throws Exception {
        Exchange exchange = new Exchange();
        exchange.sessionAttributes.put("user", user);
        AuthorizationInterceptor authorization = new AuthorizationInterceptor();
        authorization.setHttpSession(exchange.session);
        assertEquals(expected, authorization.preExecute(exchange.request, exchange.response, command));
        // La autorización no escribe la respuesta ni selecciona la vista.
        assertEquals(200, exchange.status);
        assertTrue(exchange.forwards.isEmpty());
        assertEquals(!expected, exchange.attributes.containsKey(Interceptor.ERROR_MESSAGE_ATTRIBUTE));
    }

    public void testDeniedRequestDoesNotForwardToSuccessAndClosesLogging() throws Exception {
        Exchange exchange = new Exchange();
        exchange.parameters.put("action", new String[] { "ShowBooksAction" });
        servlet.doGet(exchange.request, exchange.response);
        assertEquals(403, exchange.status);
        assertEquals(List.of("/error.jsp"), exchange.forwards);
        assertEquals(Boolean.TRUE, exchange.attributes.get("commandBlocked"));
        assertNull(exchange.attributes.get("startTime_ShowBooksCommand"));
    }

    public void testChainOrderOnSuccess() throws Exception {
        Exchange exchange = new Exchange();
        List<String> events = new ArrayList<>();
        assertTrue(executeCommand(exchange.request, exchange.response, () -> events.add("command"),
                List.of(recording("a", events, true, null), recording("b", events, true, null))));
        assertEquals(List.of("pre-a", "pre-b", "command", "post-b", "post-a"), events);
    }

    public void testBlockedChainOnlyClosesStartedInterceptors() throws Exception {
        Exchange exchange = new Exchange();
        List<String> events = new ArrayList<>();
        assertFalse(executeCommand(exchange.request, exchange.response, () -> fail("Command executed"),
                List.of(recording("a", events, true, null), recording("b", events, false, null),
                        recording("c", events, true, null))));
        assertEquals(List.of("pre-a", "pre-b", "post-b", "post-a"), events);
    }

    public void testPreExecuteFailureIsPropagatedAndClosesChain() throws Exception {
        Exchange exchange = new Exchange();
        List<String> events = new ArrayList<>();
        Exception failure = new Exception("preExecute failed");
        try {
            executeCommand(exchange.request, exchange.response, () -> fail("Command executed"),
                    List.of(recording("a", events, true, null), recording("b", events, true, failure)));
            fail("Failure swallowed");
        } catch (Exception e) {
            assertSame(failure, e);
        }
        assertEquals(List.of("pre-a", "pre-b", "post-b", "post-a"), events);
    }

    public void testCommandFailureReachesPostExecuteAndCaller() throws Exception {
        Exchange exchange = new Exchange();
        RuntimeException failure = new RuntimeException("command failed");
        List<Exception> received = new ArrayList<>();
        Interceptor interceptor = new Interceptor() {
            public boolean preExecute(HttpServletRequest req, HttpServletResponse resp, Command command) {
                return true;
            }
            public void postExecute(HttpServletRequest req, HttpServletResponse resp, Command command, Exception ex) {
                received.add(ex);
            }
        };
        try {
            executeCommand(exchange.request, exchange.response, () -> { throw failure; }, List.of(interceptor));
            fail("Failure swallowed");
        } catch (Exception e) {
            assertSame(failure, e);
        }
        assertEquals(List.of(failure), received);
    }

    public void testInvalidInterceptorConfigurationFailsClosed() throws Exception {
        ControllerConfigurationManager configuration = ControllerConfigurationManager.getInstance();
        Field field = ControllerConfigurationManager.class.getDeclaredField("interceptors");
        field.setAccessible(true);
        ((Properties) field.get(configuration)).remove("interceptor.authorization.class");
        try {
            configuration.getInterceptorsForCommand("ShowBooksAction");
            fail("Incomplete chain returned");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("authorization"));
        }
        try {
            configuration.getInterceptorsForCommand("MissingAction");
            fail("Missing configuration accepted");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("MissingAction"));
        }
    }

    public void testMissingAndUnknownAction() throws Exception {
        Exchange exchange = new Exchange();
        servlet.doGet(exchange.request, exchange.response);
        assertEquals(400, exchange.status);
        exchange.parameters.put("action", new String[] { "UnknownAction" });
        servlet.doGet(exchange.request, exchange.response);
        assertEquals(404, exchange.status);
        assertTrue(exchange.forwards.isEmpty());
    }

    public void testLoggingReportsBlockedInsteadOfSuccess() throws Exception {
        Exchange exchange = new Exchange();
        List<String> messages = new ArrayList<>();
        LoggingInterceptor logging = new LoggingInterceptor();
        logging.setLog(proxy(Logger.class, (p, m, a) -> {
            if (!"info".equals(m.getName())) {
                throw new UnsupportedOperationException(m.getName());
            }
            messages.add((String) a[0]);
            return null;
        }));
        assertFalse(executeCommand(exchange.request, exchange.response, new LoginCommand(),
                List.of(logging, recording("deny", new ArrayList<>(), false, null))));
        assertEquals(2, messages.size());
        assertTrue(messages.get(0).contains("INICIO"));
        assertTrue(messages.get(1).contains("BLOQUEADO"));
    }

    public void testPostExecuteFailurePreservesCommandFailureAndClosesOtherInterceptors() throws Exception {
        Exchange exchange = new Exchange();
        RuntimeException commandFailure = new RuntimeException("command failed");
        Exception postFailure = new Exception("postExecute failed");
        List<String> events = new ArrayList<>();
        Interceptor failingPost = new Interceptor() {
            public boolean preExecute(HttpServletRequest req, HttpServletResponse resp, Command command) {
                return true;
            }
            public void postExecute(HttpServletRequest req, HttpServletResponse resp, Command command, Exception ex)
                    throws Exception {
                throw postFailure;
            }
        };
        try {
            executeCommand(exchange.request, exchange.response, () -> { throw commandFailure; },
                    List.of(recording("a", events, true, null), failingPost));
            fail("Failure swallowed");
        } catch (Exception e) {
            assertSame(commandFailure, e);
            assertEquals(1, e.getSuppressed().length);
            assertSame(postFailure, e.getSuppressed()[0]);
        }
        assertEquals(List.of("pre-a", "post-a"), events);
    }

    private Interceptor recording(String name, List<String> events, boolean allowed, Exception failure) {
        return new Interceptor() {
            public boolean preExecute(HttpServletRequest req, HttpServletResponse resp, Command command) throws Exception {
                events.add("pre-" + name);
                if (failure != null) {
                    throw failure;
                }
                return allowed;
            }
            public void postExecute(HttpServletRequest req, HttpServletResponse resp, Command command, Exception ex) {
                events.add("post-" + name);
            }
        };
    }

    // Dobles HTTP sin servidor ni base de datos: solo los métodos utilizados por el controlador.
    private static class Exchange {
        final Map<String, Object> attributes = new HashMap<>();
        final Map<String, Object> sessionAttributes = new HashMap<>();
        final Map<String, String[]> parameters = new HashMap<>();
        final List<String> forwards = new ArrayList<>();
        int status = 200;
        final HttpSession session = proxy(HttpSession.class, (p, m, a) -> {
            switch (m.getName()) {
                case "getAttribute": return sessionAttributes.get(a[0]);
                case "setAttribute": sessionAttributes.put((String) a[0], a[1]); return null;
                default: throw new UnsupportedOperationException(m.getName());
            }
        });
        final HttpServletResponse response = proxy(HttpServletResponse.class, (p, m, a) -> {
            switch (m.getName()) {
                case "setStatus": case "sendError": status = (Integer) a[0]; return null;
                case "isCommitted": return false;
                default: throw new UnsupportedOperationException(m.getName());
            }
        });
        final HttpServletRequest request = proxy(HttpServletRequest.class, (p, m, a) -> {
            switch (m.getName()) {
                case "getAttribute": return attributes.get(a[0]);
                case "setAttribute": attributes.put((String) a[0], a[1]); return null;
                case "removeAttribute": attributes.remove(a[0]); return null;
                case "getSession": return session;
                case "getServletContext": return null;
                case "getParameterMap": return parameters;
                case "getParameter":
                    String[] values = parameters.get(a[0]);
                    return values == null ? null : values[0];
                case "getRequestDispatcher":
                    String path = (String) a[0];
                    return proxy(RequestDispatcher.class, (dispatcher, method, args) -> {
                        if (!"forward".equals(method.getName())) {
                            throw new UnsupportedOperationException(method.getName());
                        }
                        forwards.add(path);
                        return null;
                    });
                default: throw new UnsupportedOperationException(m.getName());
            }
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler));
    }
}

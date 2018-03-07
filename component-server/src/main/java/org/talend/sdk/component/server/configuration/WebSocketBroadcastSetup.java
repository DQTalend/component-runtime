/**
 * Copyright (C) 2006-2018 Talend Inc. - www.talend.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.talend.sdk.component.server.configuration;

import static java.util.Collections.emptyEnumeration;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.enumeration;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Locale.ENGLISH;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.ReadListener;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.HttpHeaders;
import javax.xml.namespace.QName;

import org.apache.cxf.Bus;
import org.apache.cxf.BusException;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.continuations.Continuation;
import org.apache.cxf.continuations.ContinuationCallback;
import org.apache.cxf.continuations.ContinuationProvider;
import org.apache.cxf.endpoint.ServerRegistry;
import org.apache.cxf.jaxrs.JAXRSServiceFactoryBean;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.AbstractDestination;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.DestinationFactoryManager;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.apache.cxf.transport.http.ContinuationProviderFactory;
import org.apache.cxf.transport.http.DestinationRegistry;
import org.apache.cxf.transport.http.HTTPSession;
import org.apache.cxf.transport.http.HTTPTransportFactory;
import org.apache.cxf.transport.servlet.ServletController;
import org.apache.cxf.transport.servlet.ServletDestination;
import org.apache.cxf.transport.servlet.servicelist.ServiceListGeneratorServlet;
import org.apache.cxf.transports.http.configuration.HTTPServerPolicy;
import org.apache.cxf.ws.addressing.EndpointReferenceType;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.talend.sdk.component.server.front.security.ConnectionSecurityProvider;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

// ensure any JAX-RS command can use websockets
@Slf4j
@Dependent
@WebListener
public class WebSocketBroadcastSetup implements ServletContextListener {

    private static final String EOM = "^@";

    @Inject
    private Bus bus;

    @Inject
    private Instance<Application> applications;

    @Override
    public void contextInitialized(final ServletContextEvent sce) {
        final ServerContainer container =
                ServerContainer.class.cast(sce.getServletContext().getAttribute(ServerContainer.class.getName()));

        final JAXRSServiceFactoryBean factory = JAXRSServiceFactoryBean.class
                .cast(bus.getExtension(ServerRegistry.class).getServers().iterator().next().getEndpoint().get(
                        JAXRSServiceFactoryBean.class.getName()));

        final String appBase = StreamSupport
                .stream(Spliterators.spliteratorUnknownSize(applications.iterator(), Spliterator.IMMUTABLE), false)
                .filter(a -> a.getClass().isAnnotationPresent(ApplicationPath.class))
                .map(a -> a.getClass().getAnnotation(ApplicationPath.class))
                .map(ApplicationPath::value)
                .findFirst()
                .map(s -> !s.startsWith("/") ? "/" + s : s)
                .orElse("/api/v1");
        final String version = appBase.replaceFirst("/api", "");

        final DestinationRegistry registry;
        try {
            final HTTPTransportFactory transportFactory = HTTPTransportFactory.class
                    .cast(bus.getExtension(DestinationFactoryManager.class).getDestinationFactory(
                            "http://cxf.apache.org/transports/http" + "/configuration"));
            registry = transportFactory.getRegistry();
        } catch (final BusException e) {
            throw new IllegalStateException(e);
        }

        final ServletContext servletContext = sce.getServletContext();

        final WebSocketRegistry webSocketRegistry = new WebSocketRegistry(registry);
        final ServletController controller = new ServletController(webSocketRegistry, new ServletConfig() {

            @Override
            public String getServletName() {
                return "Talend Component Kit Websocket Transport";
            }

            @Override
            public ServletContext getServletContext() {
                return servletContext;
            }

            @Override
            public String getInitParameter(final String s) {
                return null;
            }

            @Override
            public Enumeration<String> getInitParameterNames() {
                return emptyEnumeration();
            }
        }, new ServiceListGeneratorServlet(registry, bus));
        webSocketRegistry.controller = controller;

        Stream
                .concat(factory
                        .getClassResourceInfo()
                        .stream()
                        .flatMap(cri -> cri.getMethodDispatcher().getOperationResourceInfos().stream())
                        .map(ori -> {
                            final String uri = ori.getClassResourceInfo().getURITemplate().getValue()
                                    + ori.getURITemplate().getValue();
                            return ServerEndpointConfig.Builder
                                    .create(Endpoint.class,
                                            "/websocket" + version + "/"
                                                    + String.valueOf(ori.getHttpMethod()).toLowerCase(ENGLISH) + uri)
                                    .configurator(new ServerEndpointConfig.Configurator() {

                                        @Override
                                        public <T> T getEndpointInstance(final Class<T> clazz)
                                                throws InstantiationException {
                                            final Map<String, List<String>> headers = new HashMap<>();
                                            if (!ori.getProduceTypes().isEmpty()) {
                                                headers.put(HttpHeaders.CONTENT_TYPE, singletonList(
                                                        ori.getProduceTypes().iterator().next().toString()));
                                            }
                                            if (!ori.getConsumeTypes().isEmpty()) {
                                                headers.put(HttpHeaders.ACCEPT, singletonList(
                                                        ori.getConsumeTypes().iterator().next().toString()));
                                            }
                                            return (T) new JAXRSEndpoint(appBase, controller, servletContext,
                                                    ori.getHttpMethod(), uri, headers);
                                        }
                                    })
                                    .build();
                        }),
                        Stream.of(ServerEndpointConfig.Builder
                                .create(Endpoint.class, "/websocket" + version + "/bus")
                                .configurator(new ServerEndpointConfig.Configurator() {

                                    @Override
                                    public <T> T getEndpointInstance(final Class<T> clazz)
                                            throws InstantiationException {

                                        return (T) new JAXRSEndpoint(appBase, controller, servletContext, "GET", "/",
                                                emptyMap());
                                    }
                                })
                                .build()))
                .sorted(Comparator.comparing(ServerEndpointConfig::getPath))
                .peek(e -> log.info("Deploying WebSocket(path={})", e.getPath()))
                .forEach(config -> {
                    try {
                        container.addEndpoint(config);
                    } catch (final DeploymentException e) {
                        throw new IllegalStateException(e);
                    }
                });
    }

    @Data
    private static class JAXRSEndpoint extends Endpoint {

        private final String appBase;

        private final ServletController controller;

        private final ServletContext context;

        private final String defaultMethod;

        private final String defaultUri;

        private final Map<String, List<String>> baseHeaders;

        @Override
        public void onOpen(final Session session, final EndpointConfig endpointConfig) {
            log.debug("Opened session {}", session.getId());
            session.addMessageHandler(InputStream.class, message -> {
                final Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                headers.putAll(baseHeaders);

                final StringBuilder buffer = new StringBuilder(128);
                try { // read headers from the message
                    if (!"SEND".equalsIgnoreCase(readLine(buffer, message))) {
                        throw new IllegalArgumentException("not a message");
                    }

                    String line;
                    int del;
                    while ((line = readLine(buffer, message)) != null) {
                        final boolean done = line.endsWith(EOM);
                        if (done) {
                            line = line.substring(0, line.length() - EOM.length());
                        }
                        if (!line.isEmpty()) {
                            del = line.indexOf(':');
                            if (del < 0) {
                                headers.put(line.trim(), emptyList());
                            } else {
                                headers.put(line.substring(0, del).trim(),
                                        singletonList(line.substring(del + 1).trim()));
                            }
                        }
                        if (done) {
                            break;
                        }
                    }
                } catch (final IOException ioe) {
                    throw new IllegalStateException(ioe);
                }

                final List<String> uris = headers.get("destination");
                final String uri;
                if (uris == null || uris.isEmpty()) {
                    uri = defaultUri;
                } else {
                    uri = uris.iterator().next();
                }

                final List<String> methods = headers.get("destinationMethod");
                final String method;
                if (methods == null || methods.isEmpty()) {
                    method = defaultMethod;
                } else {
                    method = methods.iterator().next();
                }

                final String queryString;
                final String path;
                final int query = uri.indexOf('?');
                if (query > 0) {
                    queryString = uri.substring(query + 1);
                    path = uri.substring(0, query);
                } else {
                    queryString = null;
                    path = uri;
                }

                try {
                    final WebSocketRequest request =
                            new WebSocketRequest(method.toUpperCase(ENGLISH), headers, path, appBase + path, appBase,
                                    queryString, 8080, context, new WebSocketInputStream(message), session);
                    controller.invoke(request, new WebSocketResponse(session));
                } catch (final ServletException e) {
                    throw new IllegalArgumentException(e);
                }
            });
        }

        @Override
        public void onClose(final Session session, final CloseReason closeReason) {
            log.debug("Closed session {}", session.getId());
        }

        @Override
        public void onError(final Session session, final Throwable throwable) {
            log.warn("Error for session {}", session.getId(), throwable);
        }

        private static String readLine(final StringBuilder buffer, final InputStream in) throws IOException {
            int c;
            while ((c = in.read()) != -1) {
                if (c == '\n') {
                    break;
                } else if (c != '\r') {
                    buffer.append((char) c);
                }
            }

            if (buffer.length() == 0) {
                return null;
            }
            final String string = buffer.toString();
            buffer.setLength(0);
            return string;
        }
    }

    private static class WebSocketRequest implements HttpServletRequest {

        private static final Cookie[] NO_COOKIE = new Cookie[0];

        private static final SimpleDateFormat DATE_FORMATS[] =
                { new SimpleDateFormat(FastHttpDateFormat.RFC1123_DATE, Locale.US),
                        new SimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
                        new SimpleDateFormat("EEE MMMM d HH:mm:ss yyyy", Locale.US) };

        private final Map<String, Object> attributes = new HashMap<>();

        private final String method;

        private final Map<String, List<String>> headers;

        private final String requestUri;

        private final String pathInfo;

        private final String servletPath;

        private final String query;

        private final int port;

        private final ServletContext servletContext;

        private final ServletInputStream inputStream;

        private final Session session;

        private String encoding;

        private long length;

        private String type;

        private Map<String, String[]> parameters = new HashMap<>();

        private Locale locale = Locale.getDefault();

        private BufferedReader reader;

        private WebSocketRequest(final String method, final Map<String, List<String>> headers, final String requestUri,
                final String pathInfo, final String servletPath, final String query, final int port,
                final ServletContext servletContext, final ServletInputStream inputStream, final Session session) {
            this.method = method;
            this.headers = headers;
            this.requestUri = requestUri;
            this.pathInfo = pathInfo;
            this.servletPath = servletPath;
            this.query = query;
            this.port = port;
            this.servletContext = servletContext;
            this.inputStream = inputStream;
            this.session = session;
        }

        @Override
        public String getAuthType() {
            return null;
        }

        @Override
        public Cookie[] getCookies() {
            return NO_COOKIE;
        }

        @Override
        public long getDateHeader(final String name) {
            final String value = getHeader(name);
            if (value == null) {
                return -1L;
            }

            final SimpleDateFormat[] formats = new SimpleDateFormat[DATE_FORMATS.length];
            for (int i = 0; i < formats.length; i++) {
                formats[i] = SimpleDateFormat.class.cast(DATE_FORMATS[i].clone());
            }

            final long result = FastHttpDateFormat.parseDate(value, formats);
            if (result != -1L) {
                return result;
            }
            throw new IllegalArgumentException(value);
        }

        @Override
        public String getHeader(final String s) {
            final List<String> strings = headers.get(s);
            return strings == null || strings.isEmpty() ? null : strings.iterator().next();
        }

        @Override
        public Enumeration<String> getHeaders(final String s) {
            final List<String> strings = headers.get(s);
            return strings == null || strings.isEmpty() ? null : enumeration(strings);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            return enumeration(headers.keySet());
        }

        @Override
        public int getIntHeader(final String s) {
            final String value = getHeader(s);
            if (value == null) {
                return -1;
            }

            return Integer.parseInt(value);
        }

        @Override
        public String getMethod() {
            return method;
        }

        @Override
        public String getPathInfo() {
            return pathInfo;
        }

        @Override
        public String getPathTranslated() {
            return pathInfo;
        }

        @Override
        public String getContextPath() {
            return servletContext.getContextPath();
        }

        @Override
        public String getQueryString() {
            return query;
        }

        @Override
        public String getRemoteUser() {
            final Principal principal = getUserPrincipal();
            return principal == null ? null : principal.getName();
        }

        @Override
        public boolean isUserInRole(final String s) {
            return false; // if needed do it with the original request
        }

        @Override
        public Principal getUserPrincipal() {
            return session.getUserPrincipal();
        }

        @Override
        public String getRequestedSessionId() {
            return null;
        }

        @Override
        public String getRequestURI() {
            return requestUri;
        }

        @Override
        public StringBuffer getRequestURL() {
            return new StringBuffer(requestUri);
        }

        @Override
        public String getServletPath() {
            return servletPath;
        }

        @Override
        public HttpSession getSession(final boolean b) {
            return null;
        }

        @Override
        public HttpSession getSession() {
            return null;
        }

        @Override
        public String changeSessionId() {
            return null;
        }

        @Override
        public boolean isRequestedSessionIdValid() {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromCookie() {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromURL() {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromUrl() {
            return false;
        }

        @Override
        public boolean authenticate(final HttpServletResponse httpServletResponse)
                throws IOException, ServletException {
            return false;
        }

        @Override
        public void login(final String s, final String s1) throws ServletException {
            // no-op
        }

        @Override
        public void logout() throws ServletException {
            // no-op
        }

        @Override
        public Collection<Part> getParts() throws IOException, ServletException {
            return emptySet();
        }

        @Override
        public Part getPart(final String s) throws IOException, ServletException {
            return null;
        }

        @Override
        public <T extends HttpUpgradeHandler> T upgrade(final Class<T> aClass) throws IOException, ServletException {
            return null;
        }

        @Override
        public Object getAttribute(final String s) {
            if (ConnectionSecurityProvider.SKIP.equalsIgnoreCase(s)) {
                return Boolean.TRUE;
            }
            return attributes.get(s);
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return enumeration(attributes.keySet());
        }

        @Override
        public String getCharacterEncoding() {
            return encoding;
        }

        @Override
        public void setCharacterEncoding(final String s) throws UnsupportedEncodingException {
            encoding = s;
        }

        @Override
        public int getContentLength() {
            return (int) length;
        }

        @Override
        public long getContentLengthLong() {
            return length;
        }

        @Override
        public String getContentType() {
            return type;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return inputStream;
        }

        @Override
        public String getParameter(final String s) {
            final String[] strings = parameters.get(s);
            return strings == null || strings.length == 0 ? null : strings[0];
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return enumeration(parameters.keySet());
        }

        @Override
        public String[] getParameterValues(final String s) {
            return parameters.get(s);
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return parameters;
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public String getScheme() {
            return "http";
        }

        @Override
        public String getServerName() {
            return servletContext.getVirtualServerName();
        }

        @Override
        public int getServerPort() {
            return port;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return reader == null ? (reader = new BufferedReader(new InputStreamReader(getInputStream()))) : reader;
        }

        @Override
        public String getRemoteAddr() {
            return null;
        }

        @Override
        public String getRemoteHost() {
            return null;
        }

        @Override
        public void setAttribute(final String s, final Object o) {
            attributes.put(s, o);
        }

        @Override
        public void removeAttribute(final String s) {
            attributes.remove(s);
        }

        @Override
        public Locale getLocale() {
            return locale;
        }

        @Override
        public Enumeration<Locale> getLocales() {
            return locale == null ? emptyEnumeration() : enumeration(singleton(locale));
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public String getRealPath(final String s) {
            return null;
        }

        @Override
        public int getRemotePort() {
            return 0;
        }

        @Override
        public String getLocalName() {
            return null;
        }

        @Override
        public String getLocalAddr() {
            return null;
        }

        @Override
        public int getLocalPort() {
            return 0;
        }

        @Override
        public ServletContext getServletContext() {
            return servletContext;
        }

        @Override
        public RequestDispatcher getRequestDispatcher(final String s) {
            return servletContext.getRequestDispatcher(s);
        }

        @Override
        public AsyncContext startAsync() throws IllegalStateException {
            throw new UnsupportedOperationException();
        }

        @Override
        public AsyncContext startAsync(final ServletRequest servletRequest, final ServletResponse servletResponse)
                throws IllegalStateException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isAsyncStarted() {
            return false;
        }

        @Override
        public boolean isAsyncSupported() {
            return false;
        }

        @Override
        public AsyncContext getAsyncContext() {
            throw new UnsupportedOperationException();
        }

        @Override
        public DispatcherType getDispatcherType() {
            throw new UnsupportedOperationException();
        }
    }

    private static class WebSocketInputStream extends ServletInputStream {

        private final InputStream delegate;

        private boolean finished;

        private int previous = Integer.MAX_VALUE;

        private WebSocketInputStream(final InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isFinished() {
            return finished;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(final ReadListener readListener) {
            // no-op
        }

        @Override
        public int read() throws IOException {
            if (finished) {
                return -1;
            }
            if (previous != Integer.MAX_VALUE) {
                previous = Integer.MAX_VALUE;
                return previous;
            }
            final int read = delegate.read();
            if (read == '^') {
                previous = delegate.read();
                if (previous == '@') {
                    finished = true;
                    return -1;
                }
            }
            if (read < 0) {
                finished = true;
            }
            return read;
        }
    }

    private static class WebSocketResponse implements HttpServletResponse {

        private final Session session;

        private String responseString = "OK";

        private int code = HttpServletResponse.SC_OK;

        private final Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        private transient PrintWriter writer;

        private transient ServletByteArrayOutputStream sosi;

        private boolean commited = false;

        private String encoding = "UTF-8";

        private Locale locale = Locale.getDefault();

        private WebSocketResponse(final Session session) {
            this.session = session;
        }

        /**
         * sets a header to be sent back to the browser
         *
         * @param name
         * the name of the header
         * @param value
         * the value of the header
         */
        public void setHeader(final String name, final String value) {
            headers.put(name, new ArrayList<>(singletonList(value)));
        }

        @Override
        public void setIntHeader(final String s, final int i) {
            setHeader(s, Integer.toString(i));
        }

        @Override
        public void setStatus(final int i) {
            setCode(i);
        }

        @Override
        public void setStatus(final int i, final String s) {
            setCode(i);
        }

        @Override
        public void addCookie(final Cookie cookie) {
            setHeader(cookie.getName(), cookie.getValue());
        }

        @Override
        public void addDateHeader(final String s, final long l) {
            setHeader(s, Long.toString(l));
        }

        @Override
        public void addHeader(final String s, final String s1) {
            Collection<String> list = headers.get(s);
            if (list == null) {
                setHeader(s, s1);
            } else {
                list.add(s1);
            }
        }

        @Override
        public void addIntHeader(final String s, final int i) {
            setIntHeader(s, i);
        }

        @Override
        public boolean containsHeader(final String s) {
            return headers.containsKey(s);
        }

        @Override
        public String encodeURL(final String s) {
            return toEncoded(s);
        }

        @Override
        public String encodeRedirectURL(final String s) {
            return toEncoded(s);
        }

        @Override
        public String encodeUrl(final String s) {
            return toEncoded(s);
        }

        @Override
        public String encodeRedirectUrl(final String s) {
            return encodeRedirectURL(s);
        }

        public String getHeader(final String name) {
            final Collection<String> strings = headers.get(name);
            return strings == null ? null : strings.iterator().next();
        }

        @Override
        public Collection<String> getHeaderNames() {
            return headers.keySet();
        }

        @Override
        public Collection<String> getHeaders(final String s) {
            return headers.get(s);
        }

        @Override
        public int getStatus() {
            return getCode();
        }

        @Override
        public void sendError(final int i) throws IOException {
            setCode(i);
        }

        @Override
        public void sendError(final int i, final String s) throws IOException {
            setCode(i);
        }

        @Override
        public void sendRedirect(final String path) throws IOException {
            if (commited) {
                throw new IllegalStateException("response already committed");
            }
            resetBuffer();

            try {
                setStatus(SC_FOUND);

                setHeader("Location", toEncoded(path));
            } catch (final IllegalArgumentException e) {
                setStatus(SC_NOT_FOUND);
            }
        }

        @Override
        public void setDateHeader(final String s, final long l) {
            addDateHeader(s, l);
        }

        @Override
        public ServletOutputStream getOutputStream() {
            return sosi == null ? (sosi = createOutputStream()) : sosi;
        }

        @Override
        public PrintWriter getWriter() {
            return writer == null ? (writer = new PrintWriter(getOutputStream())) : writer;
        }

        @Override
        public boolean isCommitted() {
            return commited;
        }

        @Override
        public void reset() {
            createOutputStream();
        }

        private ServletByteArrayOutputStream createOutputStream() {
            return sosi = new ServletByteArrayOutputStream(session, () -> {
                final StringBuilder top = new StringBuilder("MESSAGE\r\n");
                top.append("status: ").append(getStatus()).append("\r\n");
                headers.forEach(
                        (k, v) -> top.append(k).append(": ").append(v.stream().collect(Collectors.joining(","))).append(
                                "\r\n"));
                top.append("\r\n");// empty line, means the next bytes are the payload
                return top.toString();
            });
        }

        public void flushBuffer() throws IOException {
            if (writer != null) {
                writer.flush();
            }
        }

        @Override
        public int getBufferSize() {
            return sosi.outputStream.size();
        }

        @Override
        public String getCharacterEncoding() {
            return encoding;
        }

        public void setCode(final int code) {
            this.code = code;
            commited = true;
        }

        public int getCode() {
            return code;
        }

        public void setContentType(final String type) {
            setHeader("Content-Type", type);
        }

        @Override
        public void setLocale(final Locale loc) {
            locale = loc;
        }

        public String getContentType() {
            return getHeader("Content-Type");
        }

        @Override
        public Locale getLocale() {
            return locale;
        }

        @Override
        public void resetBuffer() {
            sosi.outputStream.reset();
        }

        @Override
        public void setBufferSize(final int i) {
            // no-op
        }

        @Override
        public void setCharacterEncoding(final String s) {
            encoding = s;
        }

        @Override
        public void setContentLength(final int i) {
            // no-op
        }

        @Override
        public void setContentLengthLong(final long length) {
            // no-op
        }

        private String toEncoded(final String url) {
            return url;
        }
    }

    private static class ServletByteArrayOutputStream extends ServletOutputStream {

        private static final byte[] EOM_BYTES = EOM.getBytes(StandardCharsets.UTF_8);

        private static final int BUFFER_SIZE = 1024 * 8;

        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        private final Session session;

        private final Supplier<String> preWrite;

        private boolean closed;

        private boolean headerWritten;

        private ServletByteArrayOutputStream(final Session session, final Supplier<String> preWrite) {
            this.session = session;
            this.preWrite = preWrite;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(final WriteListener listener) {
            // no-op
        }

        @Override
        public void write(final int b) throws IOException {
            outputStream.write(b);
        }

        @Override
        public void write(final byte[] b, final int off, final int len) {
            outputStream.write(b, off, len);
        }

        public void writeTo(final OutputStream out) throws IOException {
            outputStream.writeTo(out);
        }

        public void reset() {
            outputStream.reset();
        }

        @Override
        public void flush() throws IOException {
            if (!session.isOpen()) {
                return;
            }
            if (outputStream.size() >= BUFFER_SIZE) {
                doFlush();
            }
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }

            outputStream.write(EOM_BYTES);
            doFlush();
            closed = true;
        }

        private void doFlush() throws IOException {
            final RemoteEndpoint.Basic basicRemote = session.getBasicRemote();

            final byte[] array = outputStream.toByteArray();
            final boolean written = array.length > 0 || !headerWritten;

            if (!headerWritten) {
                final String headers = preWrite.get();
                basicRemote.sendBinary(ByteBuffer.wrap(headers.getBytes(StandardCharsets.UTF_8)));
                headerWritten = true;
            }

            if (array.length > 0) {
                outputStream.reset();
                basicRemote.sendBinary(ByteBuffer.wrap(array));
            }

            if (written && basicRemote.getBatchingAllowed()) {
                basicRemote.flushBatch();
            }
        }
    }

    private static class WebSocketRegistry implements DestinationRegistry {

        private final DestinationRegistry delegate;

        private ServletController controller;

        private WebSocketRegistry(final DestinationRegistry registry) {
            this.delegate = registry;
        }

        @Override
        public void addDestination(final AbstractHTTPDestination destination) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeDestination(final String path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AbstractHTTPDestination getDestinationForPath(final String path) {
            return wrap(delegate.getDestinationForPath(path));
        }

        @Override
        public AbstractHTTPDestination getDestinationForPath(final String path, final boolean tryDecoding) {
            return wrap(delegate.getDestinationForPath(path, tryDecoding));
        }

        @Override
        public AbstractHTTPDestination checkRestfulRequest(final String address) {
            return wrap(delegate.checkRestfulRequest(address));
        }

        @Override
        public Collection<AbstractHTTPDestination> getDestinations() {
            return delegate.getDestinations();
        }

        @Override
        public AbstractDestination[] getSortedDestinations() {
            return delegate.getSortedDestinations();
        }

        @Override
        public Set<String> getDestinationsPaths() {
            return delegate.getDestinationsPaths();
        }

        private AbstractHTTPDestination wrap(final AbstractHTTPDestination destination) {
            try {
                return destination == null ? null : new WebSocketDestination(destination, this);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static class WebSocketDestination extends AbstractHTTPDestination {

        static final Logger LOG = LogUtils.getL7dLogger(ServletDestination.class);

        private final AbstractHTTPDestination delegate;

        private WebSocketDestination(final AbstractHTTPDestination delegate, final WebSocketRegistry registry)
                throws IOException {
            super(delegate.getBus(), registry, new EndpointInfo(), delegate.getPath(), false);
            this.delegate = delegate;
            this.cproviderFactory = new WebSocketContinuationFactory(registry);
        }

        @Override
        public EndpointReferenceType getAddress() {
            return delegate.getAddress();
        }

        @Override
        public Conduit getBackChannel(final Message inMessage) throws IOException {
            return delegate.getBackChannel(inMessage);
        }

        @Override
        public EndpointInfo getEndpointInfo() {
            return delegate.getEndpointInfo();
        }

        @Override
        public void shutdown() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setMessageObserver(final MessageObserver observer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessageObserver getMessageObserver() {
            return delegate.getMessageObserver();
        }

        @Override
        protected Logger getLogger() {
            return LOG;
        }

        @Override
        public Bus getBus() {
            return delegate.getBus();
        }

        @Override
        public void invoke(final ServletConfig config, final ServletContext context, final HttpServletRequest req,
                final HttpServletResponse resp) throws IOException {
            // eager create the message to ensure we set our continuation for @Suspended
            Message inMessage = retrieveFromContinuation(req);
            if (inMessage == null) {
                inMessage = new MessageImpl();

                final ExchangeImpl exchange = new ExchangeImpl();
                exchange.setInMessage(inMessage);
                setupMessage(inMessage, config, context, req, resp);

                exchange.setSession(new HTTPSession(req));
                MessageImpl.class.cast(inMessage).setDestination(this);
            }

            delegate.invoke(config, context, req, resp);
        }

        @Override
        public void finalizeConfig() {
            delegate.finalizeConfig();
        }

        @Override
        public String getBeanName() {
            return delegate.getBeanName();
        }

        @Override
        public EndpointReferenceType getAddressWithId(final String id) {
            return delegate.getAddressWithId(id);
        }

        @Override
        public String getId(final Map<String, Object> context) {
            return delegate.getId(context);
        }

        @Override
        public String getContextMatchStrategy() {
            return delegate.getContextMatchStrategy();
        }

        @Override
        public boolean isFixedParameterOrder() {
            return delegate.isFixedParameterOrder();
        }

        @Override
        public boolean isMultiplexWithAddress() {
            return delegate.isMultiplexWithAddress();
        }

        @Override
        public HTTPServerPolicy getServer() {
            return delegate.getServer();
        }

        @Override
        public void assertMessage(final Message message) {
            delegate.assertMessage(message);
        }

        @Override
        public boolean canAssert(final QName type) {
            return delegate.canAssert(type);
        }

        @Override
        public String getPath() {
            return delegate.getPath();
        }
    }

    private static class WebSocketContinuationFactory implements ContinuationProviderFactory {

        private static final String KEY = WebSocketContinuationFactory.class.getName();

        private final WebSocketRegistry registry;

        private WebSocketContinuationFactory(final WebSocketRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ContinuationProvider createContinuationProvider(final Message inMessage, final HttpServletRequest req,
                final HttpServletResponse resp) {
            return new WebSocketContinuation(inMessage, req, resp, registry);
        }

        @Override
        public Message retrieveFromContinuation(final HttpServletRequest req) {
            return Message.class.cast(req.getAttribute(KEY));
        }
    }

    private static class WebSocketContinuation implements ContinuationProvider, Continuation {

        private final Message message;

        private final HttpServletRequest request;

        private final HttpServletResponse response;

        private final WebSocketRegistry registry;

        private final ContinuationCallback callback;

        private Object object;

        private boolean resumed;

        private boolean pending;

        private boolean isNew;

        private WebSocketContinuation(final Message message, final HttpServletRequest request,
                final HttpServletResponse response, final WebSocketRegistry registry) {
            this.message = message;
            this.request = request;
            this.response = response;
            this.registry = registry;
            this.request.setAttribute(AbstractHTTPDestination.CXF_CONTINUATION_MESSAGE,
                    message.getExchange().getInMessage());
            this.callback = message.getExchange().get(ContinuationCallback.class);
        }

        @Override
        public Continuation getContinuation() {
            return this;
        }

        @Override
        public void complete() {
            message.getExchange().getInMessage().remove(AbstractHTTPDestination.CXF_CONTINUATION_MESSAGE);
            if (callback != null) {
                final Exception ex = message.getExchange().get(Exception.class);
                if (ex == null) {
                    callback.onComplete();
                } else {
                    callback.onError(ex);
                }
            }
            try {
                response.getWriter().close();
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public boolean suspend(final long timeout) {
            isNew = false;
            resumed = false;
            pending = true;
            message.getExchange().getInMessage().getInterceptorChain().suspend();
            return true;
        }

        @Override
        public void resume() {
            resumed = true;
            try {
                registry.controller.invoke(request, response);
            } catch (final ServletException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void reset() {
            pending = false;
            resumed = false;
            isNew = false;
            object = null;
        }

        @Override
        public boolean isNew() {
            return isNew;
        }

        @Override
        public boolean isPending() {
            return pending;
        }

        @Override
        public boolean isResumed() {
            return resumed;
        }

        @Override
        public Object getObject() {
            return object;
        }

        @Override
        public void setObject(final Object o) {
            object = o;
        }

        @Override
        public boolean isReadyForWrite() {
            return true;
        }
    }
}

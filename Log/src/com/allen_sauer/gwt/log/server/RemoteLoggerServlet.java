/*
 * Copyright 2009 Fred Sauer Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in
 * writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package com.allen_sauer.gwt.log.server;

import com.google.gwt.logging.server.StackTraceDeobfuscator;
import com.google.gwt.user.server.rpc.RemoteServiceServlet;

import com.allen_sauer.gwt.log.client.Log;
import com.allen_sauer.gwt.log.client.RemoteLoggerService;
import com.allen_sauer.gwt.log.shared.LogRecord;
import com.allen_sauer.gwt.log.shared.WrappedClientThrowable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Default remote logger servlet, which can be configured as a {@code web.xml} servlet.
 */
@SuppressWarnings("serial")
public class RemoteLoggerServlet extends RemoteServiceServlet implements RemoteLoggerService {

  /**
   * HTTP header for cross-domain XHR.
   */
  private static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";

  /**
   * HTTP header for cross-domain XHR.
   */
  private static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";

  /**
   * HTTP header and {@code init-param} parameter name to specify allowed cross domain origins.
   */
  private static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";

  /**
   * Location of symbolMaps directory as generated by the <code>-extra</code> GWT compile parameter.
   */
  private static final String PARAMETER_SYMBOL_MAPS = "symbolMaps";

  /**
   * Non-RFC standard header. See http://en.wikipedia.org/wiki/X-Forwarded-For
   */
  private static final String X_FORWARDED_FOR = "X-Forwarded-For";

  /**
   * The {@code init-param} parameter value of
   * {@value RemoteLoggerServlet#ACCESS_CONTROL_ALLOW_ORIGIN}.
   */
  private String accessControlAllowOriginHeader;

  private List<StackTraceDeobfuscator> deobfuscatorList;

  private final HashSet<String> permutationStrongNamesChecked = new HashSet<String>();

  @Override
  public final void init(ServletConfig config) throws ServletException {
    super.init(config);

    String cwd = "<unknown>";
    try {
      cwd = new File(".").getCanonicalPath();
    } catch (IOException ignore) {
    }

    deobfuscatorList = new ArrayList<StackTraceDeobfuscator>();
    for (@SuppressWarnings("unchecked")
    Enumeration<String> e = config.getInitParameterNames(); e.hasMoreElements();) {
      String name = e.nextElement();
      if (name.startsWith(PARAMETER_SYMBOL_MAPS)) {
        String path = config.getInitParameter(name);
        File symbolMapsDirectory = new File(path);
        if (symbolMapsDirectory.isDirectory()) {
          deobfuscatorList.add(new StackTraceDeobfuscator(path));
        } else {
          Log.warn("Servlet configuration parameter '"
              + name
              + "' specifies directory '"
              + path
              + "' which does not exist or is not relative to your server's current working directory '"
              + cwd + "'");
        }
      }
    }
    if (deobfuscatorList.isEmpty()) {
      Log.warn("In order to enable stack trace deobfuscation, please specify the '"
          + PARAMETER_SYMBOL_MAPS + "' <init-param> for the " + RemoteLoggerServlet.class.getName()
          + " servlet in your web.xml");
    }

    accessControlAllowOriginHeader = config.getInitParameter(ACCESS_CONTROL_ALLOW_ORIGIN);
  }

  @Override
  public final ArrayList<LogRecord> log(ArrayList<LogRecord> logRecords) {
    for (LogRecord record : logRecords) {
      try {
        HttpServletRequest request = getThreadLocalRequest();
        record.set("remoteAddr", request.getRemoteAddr());
        String xForwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (xForwardedFor != null) {
          record.set(X_FORWARDED_FOR, xForwardedFor);
        }
        deobfuscate(record);
        Log.log(record);
      } catch (RuntimeException e) {
        System.err.println("Failed to log message due to " + e.toString());
        e.printStackTrace();
      }
    }
    return shouldReturnDeobfuscatedStackTraceToClient() ? logRecords : null;
  }

  /**
   * If the {@value #ACCESS_CONTROL_ALLOW_ORIGIN} servlet {@code init-param} is set, handle
   * preflight {@code OPTIONS} requests which are sent by the browser before sending a cross-domain
   * XHR.
   * 
   * @param request the current HTTP request
   * @param response the current HTTP response
   * @throws ServletException see super implementation
   * @throws IOException see super implementation
   */
  @Override
  protected void doOptions(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    if (!maybeSetAccessControlAllowHeaders(request, response)) {
      super.doOptions(request, response);
      return;
    }
  }

  /**
   * Method which returns the {@value #ACCESS_CONTROL_ALLOW_ORIGIN}, or {@code null} if no
   * cross-domain access control headers should be set. This classes uses the
   * {@link #ACCESS_CONTROL_ALLOW_ORIGIN} {@code init-param} configuration parameter. Subclasses may
   * override this method implementation.
   * 
   * @param request the current HTTP request
   * @return the {@value #ACCESS_CONTROL_ALLOW_ORIGIN} which should be set in response to the
   *         current {@link #getThreadLocalResponse()}
   */
  protected String getAccessControlAllowOriginHeader(HttpServletRequest request) {
    return accessControlAllowOriginHeader;
  }

  /**
   * Ensures that the the RPC response contains the necessary access control headers for
   * cross-domain access.
   * 
   * @param serializedResponse the serialized RPC response
   */
  @Override
  protected void onAfterResponseSerialized(String serializedResponse) {
    super.onAfterResponseSerialized(serializedResponse);
    maybeSetAccessControlAllowHeaders(getThreadLocalRequest(), getThreadLocalResponse());
  }

  /**
   * Override this method to prevent clients from receiving deobfuscated JavaScript stack traces.
   * For example, you may choose to only allow (logged in) developers to access resymbolized stack
   * traces.
   * 
   * @see #getThreadLocalRequest()
   * @return true if the deobfuscated stack traces should be returned to the client
   */
  protected boolean shouldReturnDeobfuscatedStackTraceToClient() {
    return true;
  }

  private void deobfuscate(LogRecord record) {
    WrappedClientThrowable wrappedClientThrowable = record.getModifiableWrappedClientThrowable();

    deobfuscate(wrappedClientThrowable);
  }

  private void deobfuscate(WrappedClientThrowable wrappedClientThrowable) {
    if (wrappedClientThrowable == null) {
      // no throwable to deobfuscate
      return;
    }

    // recursive
    deobfuscate(wrappedClientThrowable.getCause());

    String permutationStrongName = getPermutationStrongName();
    if ("HostedMode".equals(permutationStrongName)) {
      // For Development Mode
      return;
    }

    StackTraceElement[] originalStackTrace = wrappedClientThrowable.getClientStackTrace();
    StackTraceElement[] deobfuscatedStackTrace = originalStackTrace;
    for (StackTraceDeobfuscator deobf : deobfuscatorList) {
      deobfuscatedStackTrace = deobf.deobfuscateStackTrace(deobfuscatedStackTrace,
          permutationStrongName);
    }

    // Verify each permutation once that a symbolMap is available
    if (permutationStrongNamesChecked.add(permutationStrongName)) {
      if (equal(originalStackTrace, deobfuscatedStackTrace)) {
        Log.warn("Failed to deobfuscate stack trace for permutation " + permutationStrongName
            + ". Verify that the corresponding symbolMap is available.");
      }
    }

    wrappedClientThrowable.setClientStackTrace(deobfuscatedStackTrace);
  }

  private boolean equal(StackTraceElement[] st1, StackTraceElement[] st2) {
    for (int i = 0; i < st2.length; i++) {
      if (!st1[i].equals(st2[i])) {
        return false;
      }
    }
    return true;
  }

  /**
   * Sets the {@value #ACCESS_CONTROL_ALLOW_HEADERS}, {@value #ACCESS_CONTROL_ALLOW_METHODS}, and
   * {@value #ACCESS_CONTROL_ALLOW_ORIGIN} HTTP headers, if the {@link #ACCESS_CONTROL_ALLOW_ORIGIN}
   * when cross-domain is enabled via the {@code init-param} parameter in {@code web.xml}. Returns
   * {@code true} of the headers were set, otherwise {@code false}.
   * 
   * @param request the current HTTP request
   * @param response the current HTTP servlet response to which the headers can be added
   * @return true if access control headers were set
   */
  private boolean maybeSetAccessControlAllowHeaders(HttpServletRequest request,
      HttpServletResponse response) {
    String origin = getAccessControlAllowOriginHeader(request);
    if (origin == null) {
      return false;
    }

    response.setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
    response.setHeader(ACCESS_CONTROL_ALLOW_METHODS, "POST");
    response.setHeader(ACCESS_CONTROL_ALLOW_HEADERS,
        "X-GWT-Module-Base, X-GWT-Permutation, Content-Type");
    return true;
  }
}

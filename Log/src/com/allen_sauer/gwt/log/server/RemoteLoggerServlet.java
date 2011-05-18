/*
 * Copyright 2009 Fred Sauer
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.allen_sauer.gwt.log.server;

import com.google.gwt.logging.server.StackTraceDeobfuscator;
import com.google.gwt.user.server.rpc.RemoteServiceServlet;

import com.allen_sauer.gwt.log.client.Log;
import com.allen_sauer.gwt.log.client.RemoteLoggerService;
import com.allen_sauer.gwt.log.shared.LogRecord;
import com.allen_sauer.gwt.log.shared.WrappedClientThrowable;

import java.util.ArrayList;
import java.util.HashSet;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

// CHECKSTYLE_JAVADOC_OFF

@SuppressWarnings("serial")
public class RemoteLoggerServlet extends RemoteServiceServlet implements RemoteLoggerService {

  /**
   * Location of symbolMaps directory as generated by the <code>-extra</code> GWT compile parameter.
   */
  private static final String PARAMETER_SYMBOL_MAPS = "symbolMaps";

  /**
   * Non-RFC standard header. See http://en.wikipedia.org/wiki/X-Forwarded-For
   */
  private static final String X_FORWARDED_FOR = "X-Forwarded-For";

  private StackTraceDeobfuscator deobfuscator;

  private final HashSet<String> permutationStrongNamesChecked = new HashSet<String>();

  @Override
  public final void init(ServletConfig config) throws ServletException {
    super.init(config);
    String symbolMaps = config.getInitParameter(PARAMETER_SYMBOL_MAPS);
    if (symbolMaps == null) {
      Log.warn("In order to enable stack trace deobfuscation, please specify the '"
          + PARAMETER_SYMBOL_MAPS + "' <init-param> for the " + RemoteLoggerServlet.class.getName()
          + " servlet in your web.xml");
      return;
    }
    deobfuscator = new StackTraceDeobfuscator(symbolMaps);
  }

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
    StackTraceElement[] originalStackTrace = wrappedClientThrowable.getClientStackTrace();
    StackTraceElement[] deobfuscatedStackTrace = deobfuscator.deobfuscateStackTrace(
        originalStackTrace, permutationStrongName);

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
}
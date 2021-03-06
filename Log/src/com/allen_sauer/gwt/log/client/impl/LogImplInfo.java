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
package com.allen_sauer.gwt.log.client.impl;

import com.allen_sauer.gwt.log.client.Log;

/**
 * Implementation of {@link LogImplInterface} which attempts to compile out all code with a log level lower
 * than {@link Log#LOG_LEVEL_INFO}.
 */
public final class LogImplInfo extends LogImplBase {
  // CHECKSTYLE_JAVADOC_OFF

  @Override
  public int getLowestLogLevel() {
    return Log.LOG_LEVEL_INFO;
  }

  @Override
  public boolean isDebugEnabled() {
    return false;
  }

  @Override
  public boolean isTraceEnabled() {
    return false;
  }
}

/*
 * Copyright 2008 Fred Sauer
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.allen_sauer.gwt.log.server;

import com.allen_sauer.gwt.log.client.ServerSideLog;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class ServerLogImplJavaUtil extends ServerLogImpl {
  private final Logger logger = Logger.getLogger("gwt-log");

  public final void clear() {
    // do nothing
  }

  public final void debug(String message, Throwable e) {
    logger.log(Level.FINE, message, e);
  }

  public final void error(String message, Throwable e) {
    logger.log(Level.SEVERE, message, e);
  }

  public final void fatal(String message, Throwable e) {
    logger.log(Level.SEVERE, message, e);
  }

  public final int getCurrentLogLevel() {
    return logger.getLevel().intValue();
  }

  public final void info(String message, Throwable e) {
    logger.log(Level.INFO, message, e);
  }

  public final boolean isDebugEnabled() {
    return logger.getLevel().intValue() >= Level.FINE.intValue();
  }

  public final boolean isErrorEnabled() {
    return logger.getLevel().intValue() >= Level.SEVERE.intValue();
  }

  public final boolean isFatalEnabled() {
    return logger.getLevel().intValue() >= Level.SEVERE.intValue();
  }

  public final boolean isInfoEnabled() {
    return logger.getLevel().intValue() >= Level.INFO.intValue();
  }

  public final boolean isLoggingEnabled() {
    return logger.getLevel().intValue() >= Level.OFF.intValue();
  }

  public final boolean isWarnEnabled() {
    return logger.getLevel().intValue() >= Level.WARNING.intValue();
  }

  public int mapGWTLogLevelToImplLevel(int gwtLogLevel) {
    switch (gwtLogLevel) {
      case ServerSideLog.LOG_LEVEL_DEBUG:
        return Level.FINE.intValue();
      case ServerSideLog.LOG_LEVEL_INFO:
        return Level.INFO.intValue();
      case ServerSideLog.LOG_LEVEL_WARN:
        return Level.WARNING.intValue();
      case ServerSideLog.LOG_LEVEL_ERROR:
        return Level.SEVERE.intValue();
      case ServerSideLog.LOG_LEVEL_FATAL:
        return Level.SEVERE.intValue();
      case ServerSideLog.LOG_LEVEL_OFF:
        return Level.OFF.intValue();
      default:
        throw new IllegalArgumentException();
    }
  }

  public int mapImplLevelToGWTLogLevel(int implLogLevel) {
    if (implLogLevel == Level.FINE.intValue()) {
      return ServerSideLog.LOG_LEVEL_DEBUG;
    } else if (implLogLevel == Level.INFO.intValue()) {
      return ServerSideLog.LOG_LEVEL_INFO;
    } else if (implLogLevel == Level.WARNING.intValue()) {
      return ServerSideLog.LOG_LEVEL_WARN;
      //    } else if (implLogLevel == Level.ERROR.intValue()) {
      //      return Log.LOG_LEVEL_ERROR;
    } else if (implLogLevel == Level.SEVERE.intValue()) {
      return ServerSideLog.LOG_LEVEL_FATAL;
    } else if (implLogLevel == Level.OFF.intValue()) {
      return ServerSideLog.LOG_LEVEL_OFF;
    } else {
      throw new IllegalArgumentException();
    }
  }

  public final void setCurrentLogLevel(int level) {
    logger.setLevel(Level.parse("" + level));
  }

  public final void warn(String message, Throwable e) {
    logger.log(Level.WARNING, message, e);
  }
}
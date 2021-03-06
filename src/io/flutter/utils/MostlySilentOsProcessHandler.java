/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.UnixProcessManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.io.BaseOutputReader;
import io.flutter.FlutterInitializer;
import org.jetbrains.annotations.NotNull;

/**
 * An {@link OSProcessHandler} that uses {@code BaseOutputReader.Options.forMostlySilentProcess}
 * in order to reduce cpu usage of the process it runs.
 *
 * <p>
 * This works by defaulting to a non-blocking process polling mode instead of a blocking mode.
 * The default can be overriden by setting the following registry flags:
 * <ul>
 *   <li> "output.reader.blocking.mode.for.mostly.silent.processes" = false
 *   <li> "output.reader.blocking.mode" = true
 * </ul>
 *
 * <p>
 * Note that long-running processes that don't use these options may log a warning in message
 * in the IntelliJ log.  See {@link BaseOSProcessHandler}'s {@code SimpleOutputReader.beforeSleeping}
 * for more information.
 */
public class MostlySilentOsProcessHandler extends OSProcessHandler {
  /*
  This determines whether a soft kill (SIGINT) is sent to the process on destroy, instead of the default SIGKILL. SIGKILL can't be routed
  to remote processes spawned from the original one.
   */
  private boolean softKill;
  private GeneralCommandLine commandLine;

  public MostlySilentOsProcessHandler(@NotNull GeneralCommandLine commandLine)
    throws ExecutionException {
    this(commandLine, false);
  }

  public MostlySilentOsProcessHandler(@NotNull GeneralCommandLine commandLine, boolean softKill)
    throws ExecutionException {
    super(commandLine);
    this.softKill = softKill;
    this.commandLine = commandLine;
  }

  @NotNull
  @Override
  protected BaseOutputReader.Options readerOptions() {
    return BaseOutputReader.Options.forMostlySilentProcess();
  }

  @Override
  protected void doDestroyProcess() {
    final Process process = getProcess();
    if (softKill && SystemInfo.isUnix && shouldDestroyProcessRecursively() && processCanBeKilledByOS(process)) {
      final boolean result = UnixProcessManager.sendSigIntToProcessTree(process);
      if (!result) {
        FlutterInitializer.getAnalytics().sendEvent("process", "process kill failed");
        super.doDestroyProcess();
      }
    }
    else {
      super.doDestroyProcess();
    }
  }
}

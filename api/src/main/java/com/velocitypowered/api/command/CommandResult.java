/*
 * Copyright (C) 2018-2026 Velocity Contributors
 *
 * The Velocity API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.velocitypowered.api.command;

/**
 * The result of a command invocation attempt.
 */
public enum CommandResult {

  /**
   * The proxy successfully executed the command.
   */
  EXECUTED,

  /**
   * The command was forwarded to the backend server.
   * The command may be successfully executed or not
   */
  FORWARDED,

  /**
   * The provided command input contained syntax errors.
   */
  SYNTAX_ERROR,

  /**
   * An unexpected exception occurred while executing the command in the proxy.
   */
  EXCEPTION
}

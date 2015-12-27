/*******************************************************************************
 * Copyright (c) 2015 Ericsson and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.cdt.dsf.gdb.service;

/**
  * @since 5.0
 */
public interface IGDBBackendWithConsole {
	
	/**
	 * 
	 */
	boolean shouldLaunchGdbCli();
	
	/**
	 * Returns the command with arguments that should
	 * be used to start GDB in the console.
	 */
	String[] getGdbLaunchCommand();
	
	/**
	 * Sets the GDB process that was started by the console
	 * so that the Backend service can make use of it.
	 */
	void setGdbProcess(Process proc);
}

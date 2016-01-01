/*******************************************************************************
 * Copyright (c) 2015 Ericsson and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.cdt.dsf.gdb.service;

import java.io.InputStream;
import java.io.OutputStream;

/**
  * @since 5.0
 */
public interface IGDBBackendWithConsole {
	
	/**
	 * 
	 */
	boolean shouldLaunchGdbCli();
	
	Process getProcess();
	
    public InputStream getCLIInputStream();

    public OutputStream getCLIOutputStream();
    
    public InputStream getCLIErrorStream();

}

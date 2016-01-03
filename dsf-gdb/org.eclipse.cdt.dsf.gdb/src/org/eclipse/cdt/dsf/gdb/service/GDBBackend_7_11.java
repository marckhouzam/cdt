/*******************************************************************************
 * Copyright (c) 2015 Ericsson and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.cdt.dsf.gdb.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedOutputStream;
import java.util.Hashtable;

import org.eclipse.cdt.dsf.concurrent.DataRequestMonitor;
import org.eclipse.cdt.dsf.concurrent.ImmediateRequestMonitor;
import org.eclipse.cdt.dsf.concurrent.RequestMonitor;
import org.eclipse.cdt.dsf.mi.service.command.LargePipedInputStream;
import org.eclipse.cdt.dsf.service.DsfSession;
import org.eclipse.cdt.utils.CommandLineUtil;
import org.eclipse.cdt.utils.pty.PTY;
import org.eclipse.debug.core.ILaunchConfiguration;

/**
 * Implementation of {@link IGDBBackend} using GDB 7.11. This version provides
 * full GDB console support.  It achieves this by launching GDB in CLI mode
 * in a special console widget and then connecting to it by telling GDB to
 * open a new MI console.  The rest of the DSF-GDB support then stays the same.
 * 
 * If we are unable to create a PTY, then we revert to the previous behavior of
 * the base class.
 * 
 * @since 5.0
 */
public class GDBBackend_7_11 extends GDBBackend implements IGDBBackendWithConsole {

	private PTY fPty;
	private InputStream fErrorStream;

	public GDBBackend_7_11(DsfSession session, ILaunchConfiguration lc) {
		super(session, lc);
	}

    @Override
    public void initialize(final RequestMonitor rm) {
    	createPty(new ImmediateRequestMonitor(rm) {
            @Override
            protected void handleSuccess() {
            	GDBBackend_7_11.super.initialize(new ImmediateRequestMonitor(rm) {
            		@Override
            		protected void handleSuccess() {
            	        register(new String[]{ IGDBBackendWithConsole.class.getName() }, 
            	                 new Hashtable<String,String>());
            	    	rm.done();
            		}
            	});
            }
    	});
    }

	protected void createPty(RequestMonitor rm) {
		try {
			fPty = new PTY();
			fPty.validateSlaveName();

			PipedOutputStream errorStreamPiped = new PipedOutputStream();
			try {
				// Using a LargePipedInputStream see https://bugs.eclipse.org/bugs/show_bug.cgi?id=223154
				fErrorStream = new LargePipedInputStream(errorStreamPiped);
			} catch (IOException e) {
			}
		} catch (IOException e) {
			fPty = null;
		}
		rm.done();
	}
	
	@Override
	public OutputStream getMIOutputStream() {
		if (fPty == null) {
			return super.getMIOutputStream();
		}
		return fPty.getOutputStream();
	};

	@Override
	public InputStream getMIInputStream() {
		if (fPty == null) {
			return super.getMIInputStream();
		}
		return fPty.getInputStream();
	};

	@Override
	public InputStream getMIErrorStream() {
		if (fPty == null) {
			return super.getMIErrorStream();
		}
		return fErrorStream;
	};

	@Override
	public void shouldLaunchGdbCli(DataRequestMonitor<Boolean> rm) {
		rm.done(true);
	}
	
	@Override
	protected String[] getGDBCommandLineArray() {
		String cmd =  getGDBPath().toOSString()
				// Don't read the gdbinit file here. It is read explicitly in
				// the FinalLaunchSequence to make it easier to customize.
				+ " --nx" //$NON-NLS-1$
				// Start with -q option to avoid extra output which may trigger pagination
				// We must do this because the version is output before we can turn off pagination.
				// This is important because if pagination is triggered at this time, we won't
				// be able to send the command to start the MI channel.
				+ " -q" //$NON-NLS-1$
				// Now turn off pagination then print the version for the user to get the familiar
				// startup printout, then turn pagination on again for the rest of the session
				+ " -ex" //$NON-NLS-1$
				+ " set\\ pagination\\ off" //$NON-NLS-1$ 
				+ " -ex" //$NON-NLS-1$
				+ " show\\ version"  //$NON-NLS-1$ 
				+ " -ex" //$NON-NLS-1$
				+ " set\\ pagination\\ on"  //$NON-NLS-1$ 
				// Finally, trigger the new console towards our PTY.
				+ " -ex" //$NON-NLS-1$
				+ " new-console\\ " + fPty.getSlaveName(); //$NON-NLS-1$ 

		// Parse to properly handle spaces and such things (bug 458499)
		return CommandLineUtil.argumentsToArray(cmd);
	}
}

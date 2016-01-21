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
import org.eclipse.cdt.dsf.concurrent.RequestMonitor;
import org.eclipse.cdt.dsf.concurrent.Sequence;
import org.eclipse.cdt.dsf.concurrent.Sequence.Step;
import org.eclipse.cdt.dsf.gdb.service.command.GDBControl.InitializationShutdownStep;
import org.eclipse.cdt.dsf.mi.service.command.LargePipedInputStream;
import org.eclipse.cdt.dsf.service.DsfSession;
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
	protected Sequence getStartupSequence(final RequestMonitor requestMonitor) {
		Sequence originalSequence = super.getStartupSequence(requestMonitor);
		Step[] originalSteps = originalSequence.getSteps();

		// Add the creation of the pty at the beginning of the startup sequence
        final Sequence.Step[] newSteps = new Sequence.Step[originalSteps.length+1];
        System.arraycopy(originalSteps, 0, newSteps, 1, originalSteps.length);
        newSteps[0] = new CreatePty(InitializationShutdownStep.Direction.INITIALIZING);

        return new Sequence(getExecutor(), requestMonitor) {
            @Override public Step[] getSteps() { return newSteps; }
        };
    }

    @Override
	protected Sequence getShutdownSequence(RequestMonitor requestMonitor) {
		Sequence originalSequence = super.getShutdownSequence(requestMonitor);
		Step[] originalSteps = originalSequence.getSteps();

		// Add the shutdown of the pty at the end of the shutdown sequence
        final Sequence.Step[] newSteps = new Sequence.Step[originalSteps.length+1];
        System.arraycopy(originalSteps, 0, newSteps, 0, originalSteps.length);
        newSteps[newSteps.length-1] = new CreatePty(InitializationShutdownStep.Direction.SHUTTING_DOWN);

        return new Sequence(getExecutor(), requestMonitor) {
            @Override public Step[] getSteps() { return newSteps; }
        };
    }

	private class CreatePty extends InitializationShutdownStep {
		CreatePty(Direction direction) { super(direction); }
		@Override
		public void initialize(RequestMonitor requestMonitor) {
			doCreatePtyStep(requestMonitor);
		}

		@Override
		protected void shutdown(RequestMonitor requestMonitor) {
			undoCreatePtyStep(requestMonitor);
		}
	}

	protected void doCreatePtyStep(RequestMonitor rm) {
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

	protected void undoCreatePtyStep(RequestMonitor rm) {
		fPty = null;
		rm.done();
	}

	@Override
	protected void doRegisterStep(RequestMonitor requestMonitor) {
		register(new String[]{ IGDBBackendWithConsole.class.getName() },
				 new Hashtable<String,String>());
		super.doRegisterStep(requestMonitor);
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

	protected String[] getGDBCommandLineArray2() {
		// Start from the original command line method which
		// could have been overridden, and add what we need
		// to convert it to a command that will launch in CLI and MI mode
		String[] originalCommandLine = getGDBCommandLineArray();

		String[] extraArguments = new String[] {
				// Start with -q option to avoid extra output which may trigger pagination
				// This is important because if pagination is triggered on the version
				// printout, we won't be able to send the command to start the MI channel.
				"-q", //$NON-NLS-1$
				// Force a CLI console as the original command
				// probably specified "-i mi"
				"-i console", //$NON-NLS-1$
				// Now trigger the new console towards our PTY.
				"-ex new-console " + fPty.getSlaveName(), //$NON-NLS-1$
				// Now print the version so the user gets that familiar output
				"-ex show version"  //$NON-NLS-1$
		};

		int oriLength = originalCommandLine.length;
		int extraLength = extraArguments.length;
		String[] newCommandLine = new String[oriLength+extraLength];
		System.arraycopy(originalCommandLine, 0, newCommandLine, 0, oriLength);
		System.arraycopy(extraArguments, 0, newCommandLine, oriLength, extraLength);

		return newCommandLine;
	}
}

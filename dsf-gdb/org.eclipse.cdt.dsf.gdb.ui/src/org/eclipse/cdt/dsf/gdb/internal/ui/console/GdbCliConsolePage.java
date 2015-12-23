/*******************************************************************************
 * Copyright (c) 2015 Ericsson and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.cdt.dsf.gdb.internal.ui.console;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.cdt.dsf.gdb.launching.LaunchUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.tm.internal.terminal.control.ITerminalListener;
import org.eclipse.tm.internal.terminal.control.ITerminalViewControl;
import org.eclipse.tm.internal.terminal.control.TerminalViewControlFactory;
import org.eclipse.tm.internal.terminal.provisional.api.ITerminalConnector;
import org.eclipse.tm.internal.terminal.provisional.api.ITerminalControl;
import org.eclipse.tm.internal.terminal.provisional.api.TerminalState;
import org.eclipse.tm.terminal.connector.process.ProcessConnector;
import org.eclipse.tm.terminal.view.core.interfaces.constants.ITerminalsConnectorConstants;
import org.eclipse.tm.terminal.view.ui.interfaces.ILauncherDelegate;
import org.eclipse.tm.terminal.view.ui.launcher.LauncherDelegateManager;
import org.eclipse.ui.part.IPageSite;
import org.eclipse.ui.part.Page;

public class GdbCliConsolePage extends Page {

	private final GdbCliConsole fGdbConsole;
	private final String fEncoding;
	private Composite fMainComposite;
	private ITerminalViewControl fViewControl;

	public GdbCliConsolePage(GdbCliConsole gdbConsole, String encoding) {
		fGdbConsole = gdbConsole;
		fEncoding = encoding;
	}

	public GdbCliConsole getConsole() {
		return fGdbConsole;
	}

	@Override
	public void init(IPageSite pageSite) {
		super.init(pageSite);
	}

	@Override
	public void dispose() {
		super.dispose();
		fViewControl.disposeTerminal();
	}
	
	@Override
	public void createControl(Composite parent) {
		fMainComposite = new Composite(parent, SWT.NONE);
		fMainComposite.setLayoutData(new GridData(GridData.FILL_BOTH));
		fMainComposite.setLayout(new FillLayout());

		fViewControl = TerminalViewControlFactory.makeControl(
				new ITerminalListener() {
					@Override public void setState(TerminalState state) {}
					@Override public void setTerminalTitle(final String title) {}
		        },
				fMainComposite,
				new ITerminalConnector[] {}, 
				true);
		
		try {
			fViewControl.setEncoding(fEncoding);
		} catch (UnsupportedEncodingException e) {
		}
		
		// Now wait for the Backend service to be ready before
		// actually starting the GDB process
	}

	@Override
	public Control getControl() {
		return fMainComposite;
	}

	@Override
	public void setFocus() {
		fViewControl.setFocus();
	}
	
	public void disconnectTerminal() {
		if (fViewControl.getState() != TerminalState.CLOSED) {
			fViewControl.disconnectTerminal();
		}
	}

	/**
	 * Returns the process that was started within this console page.
	 * Returns null if no process can be found.
	 */
	public Process getProcess() {
		ProcessConnector proc = fViewControl.getTerminalConnector().getAdapter(ProcessConnector.class);
		if (proc != null) {
			return proc.getProcess();
		}
		return null;
	}
	
	public void startProcess(String processCommand, String arguments) {
		ILauncherDelegate delegate = 
				LauncherDelegateManager.getInstance().getLauncherDelegate("org.eclipse.tm.terminal.connector.local.launcher.local", false); //$NON-NLS-1$
		if (delegate != null) {
			// Create the terminal connector
			Map<String, Object> properties = new HashMap<String, Object>();
			properties.put(ITerminalsConnectorConstants.PROP_TITLE, "My Local Terminal");
			properties.put(ITerminalsConnectorConstants.PROP_ENCODING, fEncoding);

			// It would be better to call the backend service to get this information
			properties.put(ITerminalsConnectorConstants.PROP_PROCESS_WORKING_DIR, "/tmp"); //$NON-NLS-1$
			properties.put(ITerminalsConnectorConstants.PROP_PROCESS_PATH, processCommand);
			properties.put(ITerminalsConnectorConstants.PROP_PROCESS_ARGS, arguments);
			properties.put(ITerminalsConnectorConstants.PROP_DATA_NO_RECONNECT, Boolean.FALSE);
			try {
				String[] env = LaunchUtils.getLaunchEnvironment(fGdbConsole.getLaunch().getLaunchConfiguration());
				properties.put(ITerminalsConnectorConstants.PROP_PROCESS_ENVIRONMENT, env);
			} catch (CoreException e) {
			}
			ITerminalConnector connector = delegate.createTerminalConnector(properties);
			fViewControl.setConnector(connector);
			if (fViewControl instanceof ITerminalControl) {
				((ITerminalControl)fViewControl).setConnectOnEnterIfClosed(false);
			}

			// Must use syncExec because the logic within must complete before the rest
			// of the class methods (specifically getProcess()) is called
			fMainComposite.getDisplay().syncExec(new Runnable() {
				@Override
				public void run() {
					if (fViewControl != null && !fViewControl.isDisposed()) {
						fViewControl.clearTerminal();
						fViewControl.connectTerminal();
					}
				}
			}); 
		}
	}

//	public TerminalState getTerminalState() {
//		return tViewCtrl.getState();
//	}
//
//	public void connectTerminal() {
//		if (!tViewCtrl.isConnected()) {
//			connectTerminalJob.schedule();
//		}
//	}
//
//
//	public void setScrollLock(boolean enabled) {
//		tViewCtrl.setScrollLock(enabled);
//	}
//
//	public boolean getScrollLock() {
//		return tViewCtrl.isScrollLock();
//	}
}

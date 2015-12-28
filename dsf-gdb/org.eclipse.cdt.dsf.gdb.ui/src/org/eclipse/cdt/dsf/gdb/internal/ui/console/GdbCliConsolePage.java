/*******************************************************************************
 * Copyright (c) 2015 Ericsson and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.cdt.dsf.gdb.internal.ui.console;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

import org.eclipse.cdt.core.parser.util.StringUtil;
import org.eclipse.cdt.dsf.concurrent.ConfinedToDsfExecutor;
import org.eclipse.cdt.dsf.concurrent.DsfRunnable;
import org.eclipse.cdt.dsf.gdb.internal.ui.GdbUIPlugin;
import org.eclipse.cdt.dsf.gdb.launching.GdbLaunch;
import org.eclipse.cdt.dsf.gdb.launching.LaunchUtils;
import org.eclipse.cdt.dsf.gdb.service.IGDBBackendWithConsole;
import org.eclipse.cdt.dsf.mi.service.IMIBackend;
import org.eclipse.cdt.dsf.service.DsfServicesTracker;
import org.eclipse.cdt.dsf.service.DsfSession;
import org.eclipse.cdt.utils.pty.PTY;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunch;
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
	private DsfSession fSession;
	private Composite fMainComposite;
	
	/** The control for the terminal widget embedded in the console */
	private ITerminalViewControl fTerminalControl;

	public GdbCliConsolePage(GdbCliConsole gdbConsole) {
		fGdbConsole = gdbConsole;
		ILaunch launch = gdbConsole.getLaunch();
		if (launch instanceof GdbLaunch) {
			fSession = ((GdbLaunch)launch).getSession();
		} else {
			assert false;
		}
	}

	@Override
	public void init(IPageSite pageSite) {
		super.init(pageSite);
	}

	@Override
	public void dispose() {
		super.dispose();
		fTerminalControl.disposeTerminal();
	}
	
	@Override
	public void createControl(Composite parent) {
		fMainComposite = new Composite(parent, SWT.NONE);
		fMainComposite.setLayoutData(new GridData(GridData.FILL_BOTH));
		fMainComposite.setLayout(new FillLayout());

		fTerminalControl = TerminalViewControlFactory.makeControl(
				new ITerminalListener() {
					@Override public void setState(TerminalState state) {}
					@Override public void setTerminalTitle(final String title) {}
		        },
				fMainComposite,
				new ITerminalConnector[] {}, 
				false);
		
		try {
			fTerminalControl.setEncoding("UTF-8"); //$NON-NLS-1$
		} catch (UnsupportedEncodingException e) {
		}
		
		startGdbProcess();
	}

	@Override
	public Control getControl() {
		return fMainComposite;
	}

	@Override
	public void setFocus() {
		fTerminalControl.setFocus();
	}
	
	public void disconnectTerminal() {
		if (fTerminalControl.getState() != TerminalState.CLOSED) {
			fTerminalControl.disconnectTerminal();
		}
	}

	/**
	 * Returns the process that was started within this console page.
	 * Returns null if no process can be found.
	 */
	private Process getProcess() {
		ProcessConnector proc = fTerminalControl.getTerminalConnector().getAdapter(ProcessConnector.class);
		if (proc != null) {
			return proc.getProcess();
		}
		return null;
	}
	
	
	private void startProcess(Map<String, Object> properties) {
		ILauncherDelegate delegate = 
				LauncherDelegateManager.getInstance().getLauncherDelegate("org.eclipse.tm.terminal.connector.local.launcher.local", false); //$NON-NLS-1$
		if (delegate != null) {
			ITerminalConnector connector = delegate.createTerminalConnector(properties);
			fTerminalControl.setConnector(connector);
			if (fTerminalControl instanceof ITerminalControl) {
				((ITerminalControl)fTerminalControl).setConnectOnEnterIfClosed(false);
			}

			// Must use syncExec because the logic within must complete before the rest
			// of the class methods (specifically getProcess()) is called
			fMainComposite.getDisplay().syncExec(new Runnable() {
				@Override
				public void run() {
					if (fTerminalControl != null && !fTerminalControl.isDisposed()) {
						fTerminalControl.clearTerminal();
						fTerminalControl.connectTerminal();
					}
				}
			});
		}
	}
	
	private Map<String, Object> createNewSettings(String processCommand, String arguments) {
		// Create the terminal connector
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put(ITerminalsConnectorConstants.PROP_TITLE, "My Local Terminal");
		properties.put(ITerminalsConnectorConstants.PROP_ENCODING, fTerminalControl.getEncoding());

		// It would be better to call the backend service to get this information
		properties.put(ITerminalsConnectorConstants.PROP_PROCESS_WORKING_DIR, "/tmp"); //$NON-NLS-1$
		properties.put(ITerminalsConnectorConstants.PROP_PROCESS_PATH, processCommand);
		properties.put(ITerminalsConnectorConstants.PROP_PROCESS_ARGS, arguments);
		try {
			String[] env = LaunchUtils.getLaunchEnvironment(fGdbConsole.getLaunch().getLaunchConfiguration());
			properties.put(ITerminalsConnectorConstants.PROP_PROCESS_ENVIRONMENT, env);
		} catch (CoreException e) {
		}
		return properties;
	}

	private Map<String, Object> createSettingsForCopy(Process proc) {
		// Create the terminal connector
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put(ITerminalsConnectorConstants.PROP_TITLE, "My Local Terminal");
		properties.put(ITerminalsConnectorConstants.PROP_ENCODING, fTerminalControl.getEncoding());

		properties.put(ITerminalsConnectorConstants.PROP_PROCESS_OBJ, proc);
		try {
			properties.put(ITerminalsConnectorConstants.PROP_PTY_OBJ, new PTY());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return properties;
	}

    @ConfinedToDsfExecutor("fsession.getExecutor()")
    private void startGdbProcess() {
		if (fSession == null) {
			return;
		}

		try {
			fSession.getExecutor().submit(new DsfRunnable() {
	        	@Override
	        	public void run() {
	            	DsfServicesTracker tracker = new DsfServicesTracker(GdbUIPlugin.getBundleContext(), fSession.getId());
	            	IMIBackend miBackend = tracker.getService(IMIBackend.class);
	            	tracker.dispose();

	            	if (miBackend instanceof IGDBBackendWithConsole) {
	            		IGDBBackendWithConsole backend = (IGDBBackendWithConsole)miBackend;
	            		
	            		if (backend.getGdbProcess() == null) {
	            			// Start the GDB process as specified by the backend service
	            			String[] command = backend.getGdbLaunchCommand();
	            			String gdbCommand = command[0];
	            			String[] arguments = Arrays.copyOfRange(command, 1, command.length);
	            			startProcess(createNewSettings(gdbCommand, StringUtil.join(arguments, " ")));  //$NON-NLS-1$

	            			// Let the backend service know about the process that was started
	            			backend.setGdbProcess(getProcess());
	            		} else {
	            			startProcess(createSettingsForCopy(backend.getGdbProcess()));
	            		}
	            	}
	        	}
	        });
		} catch (RejectedExecutionException e) {
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

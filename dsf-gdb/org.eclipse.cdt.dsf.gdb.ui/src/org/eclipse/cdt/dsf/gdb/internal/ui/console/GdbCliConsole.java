/*******************************************************************************
 * Copyright (c) 2015 Ericsson and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.cdt.dsf.gdb.internal.ui.console;

import java.util.Arrays;
import java.util.concurrent.RejectedExecutionException;

import javax.swing.plaf.metal.MetalBorders.TableHeaderBorder;

import org.eclipse.cdt.core.parser.util.StringUtil;
import org.eclipse.cdt.dsf.concurrent.ConfinedToDsfExecutor;
import org.eclipse.cdt.dsf.concurrent.DsfRunnable;
import org.eclipse.cdt.dsf.gdb.internal.ui.GdbUIPlugin;
import org.eclipse.cdt.dsf.gdb.launching.GdbLaunch;
import org.eclipse.cdt.dsf.gdb.service.IGDBBackend;
import org.eclipse.cdt.dsf.gdb.service.IGDBBackendWithConsole;
import org.eclipse.cdt.dsf.mi.service.IMIBackend;
import org.eclipse.cdt.dsf.mi.service.IMIBackend.BackendStateChangedEvent;
import org.eclipse.cdt.dsf.service.DsfServiceEventHandler;
import org.eclipse.cdt.dsf.service.DsfServicesTracker;
import org.eclipse.cdt.dsf.service.DsfSession;
import org.eclipse.cdt.ui.dialogs.FastIndexerBlock;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.swt.internal.theme.Theme;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.AbstractConsole;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleView;
import org.eclipse.ui.part.IPageBookViewPage;

/**
 * A GDB CLI console.
 * This console actually runs a GDB process in CLI mode to achieve a 
 * full-featured CLI interface.  This is only supported with GDB >= 7.11.
 * 
 * This class will use the {@link IGdbBackendWithConsole} service to know
 * if it can be used or not. If it cannot be used (GDB < 7.11), it will 
 * dispose and remove itself.
 */
public class GdbCliConsole extends AbstractConsole {
	private final ILaunch fLaunch;
	private final DsfSession fSession;
	private String fLabel = ""; //$NON-NLS-1$
	private GdbCliConsolePage fPage;
	private boolean fShouldStartProcess;
	
	public GdbCliConsole(ILaunch launch, String label) {
		super("", null); //$NON-NLS-1$
		fLaunch = launch;
        fLabel = label;
        fSession = ((GdbLaunch)launch).getSession();

        resetName();        
	}
	
    @Override
	protected void init() {
        super.init();
        fSession.getExecutor().submit(new DsfRunnable() {
        	@Override
        	public void run() {
        		fSession.addServiceEventListener(GdbCliConsole.this, null);
        	}
        });
    }
    
	@Override
	protected void dispose() {
		stop();
		super.dispose();
	}

	protected void stop() {
        try {
        	fSession.getExecutor().submit(new DsfRunnable() {
                @Override
        		public void run() {
        			fSession.removeServiceEventListener(GdbCliConsole.this);
        		}
        	});
		} catch (RejectedExecutionException e) {
			// Session already disposed
		}
        
		if (fPage != null) {
			fPage.disconnectTerminal();
			fPage = null;
		}
	}

	public ILaunch getLaunch() { return fLaunch; }
    
    protected String computeName() {
        String label = fLabel;

        ILaunchConfiguration config = fLaunch.getLaunchConfiguration();
        if (config != null && !DebugUITools.isPrivate(config)) {
        	String type = null;
        	try {
        		type = config.getType().getName();
        	} catch (CoreException e) {
        	}
        	StringBuffer buffer = new StringBuffer();
        	buffer.append(config.getName());
        	if (type != null) {
        		buffer.append(" ["); //$NON-NLS-1$
        		buffer.append(type);
        		buffer.append("] "); //$NON-NLS-1$
        	}
        	buffer.append(label);
        	label = buffer.toString();
        }

        if (fLaunch.isTerminated()) {
        	return ConsoleMessages.ConsoleMessages_trace_console_terminated + label; 
        }
        
        return label;
    }
    
    public void resetName() {
    	final String newName = computeName();
    	String name = getName();
    	if (!name.equals(newName)) {
    		Runnable r = new Runnable() {
                @Override
    			public void run() {
    				setName(newName);
    			}
    		};
    		PlatformUI.getWorkbench().getDisplay().asyncExec(r);
    	}
    }

    @Override
	public synchronized IPageBookViewPage createPage(IConsoleView view) {
		view.setFocus();
		fPage = new GdbCliConsolePage(this, "UTF-8"); //$NON-NLS-1$
		if (fShouldStartProcess) {
			fShouldStartProcess = false;
		   try {
		    	fSession.getExecutor().submit(new DsfRunnable() {
		            @Override
		    	    public void run() {
		            	this is too fast, the page content is not initialized yet
		            	// to reproduce launch once then close console and launch again
		            	startGdbProcess();
		        	}
		        });
			} catch (RejectedExecutionException e) {
			}
		}
		return fPage;
    }
    
    @DsfServiceEventHandler
    public void eventDispatched(BackendStateChangedEvent event) {
        if (event.getState() == IMIBackend.State.STARTED &&
        		event.getSessionId().equals(fSession.getId())) 
        {
        	// Now that the backend service is started, we can start the GDB process
        	if (canStartGdbProcess()) {
        		startGdbProcess();
        	}
        }
    }

    @ConfinedToDsfExecutor("fsession.getExecutor()")
    private void startGdbProcess() {
    	DsfServicesTracker tracker = new DsfServicesTracker(GdbUIPlugin.getBundleContext(), fSession.getId());
    	IGDBBackendWithConsole backend = (IGDBBackendWithConsole)tracker.getService(IGDBBackend.class);
    	tracker.dispose();

		// Start the GDB process as specified by the backend service
		String[] command = backend.getGdbLaunchCommand();
		String gdbCommand = command[0];
		String[] arguments = Arrays.copyOfRange(command, 1, command.length);
		synchronized (this) {
			fPage.startProcess(gdbCommand, StringUtil.join(arguments, " "));  //$NON-NLS-1$

			// Let the backend service know about the process that was started
			backend.setGdbProcess(fPage.getProcess());
		}    	
    }

    
    @ConfinedToDsfExecutor("fsession.getExecutor()")
   	private boolean canStartGdbProcess() {
    	DsfServicesTracker tracker = new DsfServicesTracker(GdbUIPlugin.getBundleContext(), fSession.getId());
    	IGDBBackend backendTmp = tracker.getService(IGDBBackend.class);
    	tracker.dispose();

    	if (backendTmp instanceof IGDBBackendWithConsole) {
    		synchronized (this) {
    			if (fPage == null) {
    				// Page is not ready, wait for it
    				fShouldStartProcess = true;
    				return false;
    			}
    		}
    		return true;

    	} else {
    		// GDB is too old: we cannot use this console.  Let's dispose of it to clean up.
    		ConsolePlugin.getDefault().getConsoleManager().removeConsoles(new IConsole[]{GdbCliConsole.this});
    		return false;
    	}
    }
}

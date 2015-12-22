/*******************************************************************************
 * Copyright (c) 2015 Ericsson and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.cdt.dsf.gdb.internal.ui.console;

import java.util.concurrent.RejectedExecutionException;

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
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.AbstractConsole;
import org.eclipse.ui.console.IConsoleView;
import org.eclipse.ui.part.IPageBookViewPage;

/**
 */
public class GdbConsole extends AbstractConsole {
	private ILaunch fLaunch;
	private DsfSession fSession;
	private String fLabel = ""; //$NON-NLS-1$
	private GdbConsolePage fPage;
	
	public GdbConsole(ILaunch launch, String label) {
		super("", null); //$NON-NLS-1$
		fLaunch = launch;
        fSession = ((GdbLaunch)launch).getSession();
        fLabel = label;
        
        resetName();
	}
	
    @Override
	protected void init() {
        super.init();
        fSession.getExecutor().submit(new DsfRunnable() {
            @Override
        	public void run() {
        		fSession.addServiceEventListener(GdbConsole.this, null);
        	}
        });
    }
    
	@Override
	protected void dispose() {
		if (fPage != null) {
			fPage.disconnectTerminal();
		}
        try {
        	fSession.getExecutor().submit(new DsfRunnable() {
                @Override
        		public void run() {
        			fSession.removeServiceEventListener(GdbConsole.this);
        		}
        	});
		} catch (RejectedExecutionException e) {
			// Session already disposed
		}
		super.dispose();
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
	public IPageBookViewPage createPage(IConsoleView view) {
		view.setFocus();
		fPage = new GdbConsolePage(this, "UTF-8"); //$NON-NLS-1$
		return fPage;
    }
    
    @DsfServiceEventHandler
    public void eventDispatched(BackendStateChangedEvent event) {
        if (event.getState() == IMIBackend.State.STARTED &&
        		event.getSessionId().equals(fSession.getId())) 
        {
        	// Now that the backend service is started, we can set the process
        	setProcessInService();        	
        }
    }
    
    private void setProcessInService() {
    	try {
    		fSession.getExecutor().submit(new DsfRunnable() {
                @Override
    			public void run() {
    				DsfServicesTracker tracker = new DsfServicesTracker(GdbUIPlugin.getBundleContext(), fSession.getId());
    				IGDBBackend backend = tracker.getService(IGDBBackend.class);
    				tracker.dispose();
    				if (backend instanceof IGDBBackendWithConsole) {
    					// Special method that need not be called on the executor
    					((IGDBBackendWithConsole)backend).setGdbProcess(fPage.getProcess());
    				}
    			}
    		});
	    } catch (RejectedExecutionException e) {
	    }
	}
}

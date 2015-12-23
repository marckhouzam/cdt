/*******************************************************************************
 * Copyright (c) 2009, 2015 Ericsson and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Ericsson - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.dsf.gdb.internal.ui.console;

import org.eclipse.cdt.dsf.gdb.IGdbDebugPreferenceConstants;
import org.eclipse.cdt.dsf.gdb.internal.ui.GdbUIPlugin;
import org.eclipse.cdt.dsf.gdb.launching.GdbLaunch;
import org.eclipse.cdt.dsf.gdb.launching.ITracedLaunch;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchesListener2;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;

/**
 * A console manager for GDB sessions which adds and removes: 

 * 1- gdb traces consoles
 *    These consoles can be enabled or disabled using a preference.
 *    They support a configurable size through the use of water marks 
 *    They apply to {@link ITracedLaunch}
 * 2- gdb cli consoles
 *    These consoles cannot be enabled/disabled by the user.
 *    However, they are only supported by GDB >= 7.11;
 *    to handled this, the console itself will use the DSF Backend
 *    service to establish if it should be used or not.
 *    These consoles apply to {@link GdbLaunch} running GDB >= 7.11
 *    
 */
public class GdbConsoleManager implements ILaunchesListener2, IPropertyChangeListener {

	/**
	 * The number of characters that should be deleted once the GDB traces console
	 * reaches its configurable maximum.
	 */
	private static final int NUMBER_OF_CHARS_TO_DELETE = 100000;

	/**
	 * The minimum number of characters that should be kept when truncating
	 * the console output. 
	 */
	private static final int MIN_NUMBER_OF_CHARS_TO_KEEP = 5000;

	/**
	 * Member to keep track of the preference.
	 * We keep it up-to-date by registering as an IPropertyChangeListener
	 */
	private boolean fTracingEnabled = false;
	
	/**
	 * The maximum number of characters that are allowed per console
	 */
	private int fTracingMaxNumCharacters = 500000;
	
	/**
	 * The number of characters that will be kept in the console once we
	 * go over fMaxNumCharacters and that we must remove some characters
	 */
	private int fTracingMinNumCharacters = fTracingMaxNumCharacters - NUMBER_OF_CHARS_TO_DELETE;
	
	/**
	 * Start the tracing console.  We don't do this in a constructor, because
	 * we need to use <code>this</code>.
	 */
	public void startup() {
		IPreferenceStore store = GdbUIPlugin.getDefault().getPreferenceStore();
		
		store.addPropertyChangeListener(this);
		fTracingEnabled = store.getBoolean(IGdbDebugPreferenceConstants.PREF_TRACES_ENABLE);
		int maxChars = store.getInt(IGdbDebugPreferenceConstants.PREF_MAX_GDB_TRACES);
		setTracingConsoleWaterMarks(maxChars);
		
		// Listen to launch events for both types of consoles
		DebugPlugin.getDefault().getLaunchManager().addLaunchListener(this);

		if (fTracingEnabled) {
			toggleTracing(true);
		}
	}

	public void shutdown() {
		DebugPlugin.getDefault().getLaunchManager().removeLaunchListener(this);
		GdbUIPlugin.getDefault().getPreferenceStore().removePropertyChangeListener(this);
		removeAllTracingConsoles();
		removeAllCliConsoles();
	}

	protected void toggleTracing(boolean enabled) {
		if (enabled) {
			addAllTracingConsoles();
		} else {
			removeAllTracingConsoles();
		}
	}
	
	protected void addAllTracingConsoles() {
		ILaunch[] launches = DebugPlugin.getDefault().getLaunchManager().getLaunches();
		for (ILaunch launch : launches) {
			addTracingConsole(launch);
		}
	}

	protected void removeAllTracingConsoles() {
		ILaunch[] launches = DebugPlugin.getDefault().getLaunchManager().getLaunches();
		for (ILaunch launch : launches) {
			removeTracingConsole(launch);
		}
	}

	protected void removeAllCliConsoles() {
		ILaunch[] launches = DebugPlugin.getDefault().getLaunchManager().getLaunches();
		for (ILaunch launch : launches) {
			removeCliConsole(launch);
		}
	}

    @Override
	public void launchesAdded(ILaunch[] launches) {
		for (ILaunch launch : launches) {
			if (fTracingEnabled) {
				addTracingConsole(launch);
			}
			addCliConsole(launch);
		}
	}

    @Override
	public void launchesChanged(ILaunch[] launches) {
	}

    @Override
	public void launchesRemoved(ILaunch[] launches) {
		for (ILaunch launch : launches) {
			if (fTracingEnabled) {
				removeTracingConsole(launch);
			}
			removeCliConsole(launch);
		}
	}
	
    @Override
	public void launchesTerminated(ILaunch[] launches) {
		for (ILaunch launch : launches) {
			if (fTracingEnabled) {
				// Since we already had a console, don't get rid of it
				// just yet.  Simply rename it to show it is terminated.
				renameTracingConsole(launch);
			}

			stopCliConsole(launch);
			renameCliConsole(launch);
		}
	}
	
    @Override
	public void propertyChange(PropertyChangeEvent event) {
		if (event.getProperty().equals(IGdbDebugPreferenceConstants.PREF_TRACES_ENABLE)) {
			fTracingEnabled = (Boolean)event.getNewValue();
			toggleTracing(fTracingEnabled);
		} else if (event.getProperty().equals(IGdbDebugPreferenceConstants.PREF_MAX_GDB_TRACES)) {
			int maxChars = (Integer)event.getNewValue();
			updateAllTracingConsoleWaterMarks(maxChars);
		}
	}

	protected void addTracingConsole(ILaunch launch) {		
		// Tracing consoles are only added to ITracedLaunches
		if (launch instanceof ITracedLaunch) {
			// Make sure we didn't already add this console
			if (getTracingConsole(launch) == null) {
				if (!launch.isTerminated()) {
					// Create an new tracing console.
					TracingConsole console = new TracingConsole(launch, ConsoleMessages.ConsoleMessages_trace_console_name);
					console.setWaterMarks(fTracingMinNumCharacters, fTracingMaxNumCharacters);
					ConsolePlugin.getDefault().getConsoleManager().addConsoles(new IConsole[]{console});
				} // else we don't create a new console for a terminated launch
			}
		}
	}

	protected void addCliConsole(ILaunch launch) {
		// Cli consoles are only added for GdbLaunches and if supported by the backend service
		// We know this by looking for a backend service of type IGdbBackedWithConsole
		if (launch instanceof GdbLaunch) {
			// Create an new Cli console .
			GdbCliConsole console = new GdbCliConsole(launch, ConsoleMessages.ConsoleMessages_gdb_console_name);
			//	console.setWaterMarks(fMinNumCharacters, fMaxNumCharacters);

			// Register this console right away to allow it to initialize properly.
			// The console may later find out it is not required and remove itself
			ConsolePlugin.getDefault().getConsoleManager().addConsoles(new IConsole[]{console});
		}
	}

	protected void removeTracingConsole(ILaunch launch) {
		if (launch instanceof ITracedLaunch) {
			TracingConsole console = getTracingConsole(launch);
			if (console != null) {
				ConsolePlugin.getDefault().getConsoleManager().removeConsoles(new IConsole[]{console});
			}
		}
	}

	protected void removeCliConsole(ILaunch launch) {
		if (launch instanceof GdbLaunch) {
			GdbCliConsole console = getCliConsole(launch);
			if (console != null) {
				ConsolePlugin.getDefault().getConsoleManager().removeConsoles(new IConsole[]{console});
			}
		}
	}

	protected void renameTracingConsole(ILaunch launch) {
		if (launch instanceof ITracedLaunch) {
			TracingConsole console = getTracingConsole(launch);
			if (console != null) {
				console.resetName();
			}		
		}
	}

	protected void renameCliConsole(ILaunch launch) {
		if (launch instanceof GdbLaunch) {
			GdbCliConsole console = getCliConsole(launch);
			if (console != null) {
				console.resetName();
			}		
		}
	}
	
	/**
	 * Stop the CliConsole to prevent it from automatically
	 * restarting the GDB process after it has terminated.
	 */
	protected void stopCliConsole(ILaunch launch) {
		if (launch instanceof GdbLaunch) {
			GdbCliConsole console = getCliConsole(launch);
			if (console != null) {
				console.stop();
			}		
		}
	}

	private TracingConsole getTracingConsole(ILaunch launch) {
		ConsolePlugin plugin = ConsolePlugin.getDefault();
		if (plugin != null) {
			// I've seen the plugin be null when running headless JUnit tests
			IConsoleManager manager = plugin.getConsoleManager(); 
			IConsole[] consoles = manager.getConsoles();
			for (IConsole console : consoles) {
				if (console instanceof TracingConsole) {
					TracingConsole tracingConsole = (TracingConsole)console;
					if (tracingConsole.getLaunch().equals(launch)) {
						return tracingConsole;
					}
				}
			}
		}
		return null;
	}
	
	private GdbCliConsole getCliConsole(ILaunch launch) {
		ConsolePlugin plugin = ConsolePlugin.getDefault();
		if (plugin != null) {
			// This plugin can be null when running headless JUnit tests
			IConsoleManager manager = plugin.getConsoleManager(); 
			IConsole[] consoles = manager.getConsoles();
			for (IConsole console : consoles) {
				if (console instanceof GdbCliConsole) {
					GdbCliConsole gdbConsole = (GdbCliConsole)console;
					if (gdbConsole.getLaunch().equals(launch)) {
						return gdbConsole;
					}
				}
			}
		}
		return null;
	}

	protected void setTracingConsoleWaterMarks(int maxChars) {
		if (maxChars < (MIN_NUMBER_OF_CHARS_TO_KEEP * 2)) {
			maxChars = MIN_NUMBER_OF_CHARS_TO_KEEP * 2;
		}
		
		fTracingMaxNumCharacters = maxChars;
		// If the max number of chars is anything below the number of chars we are going to delete
		// (plus our minimum buffer), we only keep the minimum.
		// If the max number of chars is bigger than the number of chars we are going to delete (plus
		// the minimum buffer), we truncate a fixed amount chars.
		fTracingMinNumCharacters = maxChars < (NUMBER_OF_CHARS_TO_DELETE + MIN_NUMBER_OF_CHARS_TO_KEEP) 
								? MIN_NUMBER_OF_CHARS_TO_KEEP : maxChars - NUMBER_OF_CHARS_TO_DELETE;
	}

	protected void updateAllTracingConsoleWaterMarks(int maxChars) {
		setTracingConsoleWaterMarks(maxChars);
		ILaunch[] launches = DebugPlugin.getDefault().getLaunchManager().getLaunches();
		for (ILaunch launch : launches) {
			updateTracingConsoleWaterMarks(launch);
		}
	}
	
	protected void updateTracingConsoleWaterMarks(ILaunch launch) {
		if (launch instanceof ITracedLaunch) {
			TracingConsole console = getTracingConsole(launch);
			if (console != null) {
				console.setWaterMarks(fTracingMinNumCharacters, fTracingMaxNumCharacters);
			}		
		}
	}
}

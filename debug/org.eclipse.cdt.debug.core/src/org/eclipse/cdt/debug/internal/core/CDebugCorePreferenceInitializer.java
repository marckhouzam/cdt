/*******************************************************************************
 * Copyright (c) 2004, 2012 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * QNX Software Systems - Initial API and implementation
 * Ken Ryall (Nokia) - 207675
 * Mathias Kunter - Using adequate default charsets (bug 370462)
*******************************************************************************/
package org.eclipse.cdt.debug.internal.core; 

import java.nio.charset.Charset;

import org.eclipse.cdt.debug.core.CDebugCorePlugin;
import org.eclipse.cdt.debug.core.ICDebugConstants;
import org.eclipse.cdt.debug.core.cdi.ICDIFormat;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
 
/**
 * Default preference value initializer for <code>CDebugCorePlugin</code>.
 */
public class CDebugCorePreferenceInitializer extends AbstractPreferenceInitializer {

	/** 
	 * Constructor for CDebugCorePreferenceInitializer. 
	 */
	public CDebugCorePreferenceInitializer() {
		super();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer#initializeDefaultPreferences()
	 */
	@Override
	public void initializeDefaultPreferences() {
		CDebugCorePlugin.getDefault().getPluginPreferences().setDefault( ICDebugConstants.PREF_MAX_NUMBER_OF_INSTRUCTIONS, ICDebugConstants.DEF_NUMBER_OF_INSTRUCTIONS );
		CDebugCorePlugin.getDefault().getPluginPreferences().setDefault( ICDebugConstants.PREF_DEFAULT_VARIABLE_FORMAT, ICDIFormat.NATURAL );
		CDebugCorePlugin.getDefault().getPluginPreferences().setDefault( ICDebugConstants.PREF_DEFAULT_EXPRESSION_FORMAT, ICDIFormat.NATURAL );
		CDebugCorePlugin.getDefault().getPluginPreferences().setDefault( ICDebugConstants.PREF_DEFAULT_REGISTER_FORMAT, ICDIFormat.NATURAL );
		CDebugCorePlugin.getDefault().getPluginPreferences().setDefault( ICDebugConstants.PREF_DEBUG_CHARSET, Charset.defaultCharset().name() );
		if (Platform.getOS().equals(Platform.OS_WIN32))
			CDebugCorePlugin.getDefault().getPluginPreferences().setDefault( ICDebugConstants.PREF_DEBUG_WIDE_CHARSET, "UTF-16"); //$NON-NLS-1$
		else
			CDebugCorePlugin.getDefault().getPluginPreferences().setDefault( ICDebugConstants.PREF_DEBUG_WIDE_CHARSET, "UTF-32"); //$NON-NLS-1$
		CDebugCorePlugin.getDefault().getPluginPreferences().setDefault( ICDebugConstants.PREF_INSTRUCTION_STEP_MODE_ON, false );
	}
}

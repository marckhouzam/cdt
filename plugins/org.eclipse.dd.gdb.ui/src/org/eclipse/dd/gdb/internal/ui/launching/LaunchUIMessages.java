/*******************************************************************************
 * Copyright (c) 2004, 2006 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * QNX Software Systems - Initial API and implementation
 *******************************************************************************/
package org.eclipse.dd.gdb.internal.ui.launching;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class LaunchUIMessages {

	private static final String BUNDLE_NAME = "org.eclipse.dd.gdb.internal.ui.launching.LaunchUIMessages";//$NON-NLS-1$

	private static ResourceBundle RESOURCE_BUNDLE = null;

	static {
        try {
        	RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);
        }
        catch (MissingResourceException x) {
        }
	}

	private LaunchUIMessages() {}

	public static String getFormattedString(String key, String arg) {
		return MessageFormat.format(getString(key), (Object[])new String[]{arg});
	}

	public static String getFormattedString(String key, String[] args) {
		return MessageFormat.format(getString(key), (Object[])args);
	}

	public static String getString(String key) {
		if (RESOURCE_BUNDLE == null) return '!' + key + '!';
		return RESOURCE_BUNDLE.getString(key);
	}
}

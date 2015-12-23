/*******************************************************************************
 * Copyright (c) 2015 Ericsson and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.cdt.dsf.gdb.launching;

import java.util.Map;

import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IStreamsProxy;
import org.eclipse.debug.internal.core.NullStreamsProxy;

/**
 * @since 5.0
 */
public class GDBProcessNoStreams extends GDBProcess {

	public GDBProcessNoStreams(ILaunch launch, Process process, String name,
			Map<String, String> attributes) {
		super(launch, process, name, attributes);
	}

	@Override
	public IStreamsProxy getStreamsProxy() {
        return null;
	}

	@Override
	protected IStreamsProxy createStreamsProxy() {
        return new NullStreamsProxy(getSystemProcess());
	}

}

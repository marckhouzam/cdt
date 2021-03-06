/*******************************************************************************
 * Copyright (c) 2009, 2011 Red Hat Inc..
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Incorporated - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.internal.autotools.ui.actions;

import java.util.List;

import org.eclipse.cdt.internal.autotools.core.AutotoolsPropertyConstants;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.swt.widgets.Shell;

/**
 * @author Jeff Johnston
 *
 */
public class LibtoolizeHandler extends AbstractAutotoolsHandler {

	@Override
	public Object execute(ExecutionEvent event) {
		return execute1(event);
	}

	private static final String DEFAULT_OPTION = ""; //$NON-NLS-1$
	private static final String DEFAULT_COMMAND = "libtoolize"; //$NON-NLS-1$

	@Override
	public void run(Shell activeShell) {

		IContainer container = getSelectedContainer();
		if (container == null) {
			return;
		}

		IPath execDir = getExecDir(container);
		String cwd = InvokeMessages.getString("CWD") + getCWD(container); //$NON-NLS-1$

		InputDialog optionDialog = new SingleInputDialog(activeShell, cwd,
				InvokeMessages.getString("InvokeLibtoolizeAction.windowTitle.options"), //$NON-NLS-1$
				InvokeMessages.getString("InvokeLibtoolizeAction.message.options.otherOptions"), //$NON-NLS-1$
				DEFAULT_OPTION, null);
		optionDialog.open();

		// chop args into string array
		String rawArgList = optionDialog.getValue();

		List<String> optionsList = simpleParseOptions(rawArgList);

		String libtoolizeCommand = null;
		IProject project = getSelectedContainer().getProject();
		try {
			libtoolizeCommand = project.getPersistentProperty(AutotoolsPropertyConstants.LIBTOOLIZE_TOOL);
		} catch (CoreException e) {
			// do nothing
		}

		// If unset, use default system path
		if (libtoolizeCommand == null) {
			libtoolizeCommand = DEFAULT_COMMAND;
		}

		executeConsoleCommand(DEFAULT_COMMAND, libtoolizeCommand, optionsList, execDir);
	}

}

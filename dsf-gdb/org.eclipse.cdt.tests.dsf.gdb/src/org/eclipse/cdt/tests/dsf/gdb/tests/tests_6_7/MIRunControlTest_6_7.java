/*******************************************************************************
 * Copyright (c) 2009, 2010 Ericsson and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Ericsson	AB		  - Initial implementation of Test cases
 *******************************************************************************/
package org.eclipse.cdt.tests.dsf.gdb.tests.tests_6_7;


import org.eclipse.cdt.tests.dsf.gdb.framework.BackgroundRunner;
import org.eclipse.cdt.tests.dsf.gdb.tests.ITestConstants;
import org.eclipse.cdt.tests.dsf.gdb.tests.tests_6_6.MIRunControlTest_6_6;
import org.junit.runner.RunWith;

@RunWith(BackgroundRunner.class)
public class MIRunControlTest_6_7 extends MIRunControlTest_6_6 {
    @Override
	protected void setGdbVersion() {
		setGdbProgramNamesLaunchAttributes(ITestConstants.SUFFIX_GDB_6_7);		
	}
}

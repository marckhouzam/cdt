/*******************************************************************************
 * Copyright (c) 2000, 2006 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     QNX Software Systems - Initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.internal.autotools.ui.editors.automake;

import java.util.ArrayList;
import java.util.Arrays;

import org.eclipse.cdt.make.core.makefile.IDirective;
import org.eclipse.cdt.make.core.makefile.IParent;

/**
 * IParent
 */

public abstract class Parent extends Directive implements IParent {

	ArrayList<IDirective> children = new ArrayList<>();

	public Parent(Directive parent) {
		super(parent);
	}

	public IDirective[] getDirectives(boolean expand) {
		return getDirectives();
	}

	@Override
	public IDirective[] getDirectives() {
		children.trimToSize();
		return children.toArray(new IDirective[0]);
	}

	public void addDirective(Directive directive) {
		children.add(directive);
		// reparent
		directive.setParent(this);
	}

	public void addDirectives(Directive[] directives) {
		children.addAll(Arrays.asList(directives));
		// reparent
		for (int i = 0; i < directives.length; i++) {
			directives[i].setParent(this);
		}
	}

	public void clearDirectives() {
		children.clear();
	}

	public Directive[] getStatements() {
		children.trimToSize();
		return children.toArray(new Directive[0]);		
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		IDirective[] directives = getDirectives();
		for (int i = 0; i < directives.length; i++) {
			sb.append(directives[i]);
		}
		return sb.toString();
	}

}

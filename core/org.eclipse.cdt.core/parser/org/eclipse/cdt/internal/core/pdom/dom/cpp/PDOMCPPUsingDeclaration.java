/*******************************************************************************
 * Copyright (c) 2008 Google, Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * 	   Sergey Prigogin (Google) - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.internal.core.pdom.dom.cpp;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPUsingDeclaration;
import org.eclipse.cdt.core.parser.util.ArrayUtil;
import org.eclipse.cdt.internal.core.index.IIndexCPPBindingConstants;
import org.eclipse.cdt.internal.core.pdom.PDOM;
import org.eclipse.cdt.internal.core.pdom.dom.PDOMBinding;
import org.eclipse.cdt.internal.core.pdom.dom.PDOMLinkage;
import org.eclipse.cdt.internal.core.pdom.dom.PDOMNode;
import org.eclipse.core.runtime.CoreException;

/**
 * Represents "using" declaration in PDOM. A single "using" declaration resolving to multiple
 * objects, e.g. functions with the same name but different signatures, is represented by multiple
 * chained PDOMCPPUsingDeclaration records.
 * 
 * @see ICPPUsingDeclaration
 */
class PDOMCPPUsingDeclaration extends PDOMCPPBinding implements	ICPPUsingDeclaration {
	
	private static final int TARGET_BINDING = PDOMCPPBinding.RECORD_SIZE + 0;
	// Using declarations for functions may have multiple delegates. We model such case
	// by creating a chain of PDOMCPPUsingDeclaration objects linked by NEXT_DELEGATE field.
	
	private static final int NEXT_DELEGATE = PDOMCPPBinding.RECORD_SIZE + 4;
	
	@SuppressWarnings("hiding")
	protected static final int RECORD_SIZE = PDOMCPPBinding.RECORD_SIZE + 8;
	
	private IBinding[] delegates;
	
	@SuppressWarnings("hiding")
	public PDOMCPPUsingDeclaration(PDOM pdom, PDOMNode parent, ICPPUsingDeclaration using)
			throws CoreException {
		super(pdom, parent, using.getNameCharArray());
		IBinding[] delegates= using.getDelegates();
		int nextRecord = 0;
		for (int i = delegates.length; --i >= 0;) {
			PDOMCPPUsingDeclaration simpleUsing = i > 0 ?
					new PDOMCPPUsingDeclaration(pdom, parent, getNameCharArray()) : this;
			simpleUsing.setTargetBinding(parent.getLinkageImpl(), delegates[i]);
			pdom.getDB().putInt(record + NEXT_DELEGATE, nextRecord); 
			nextRecord = simpleUsing.getRecord();
		}
	}

	public PDOMCPPUsingDeclaration(PDOM pdom, int record) {
		super(pdom, record);
	}

	private PDOMCPPUsingDeclaration(PDOM pdom, PDOMNode parent, char[] name) throws CoreException {
		super(pdom, parent, name);
	}

	private void setTargetBinding(PDOMLinkage linkage, IBinding delegate) throws CoreException {
		PDOMBinding target = getLinkageImpl().adaptBinding(delegate);
		pdom.getDB().putInt(record + TARGET_BINDING, target != null ? target.getRecord() : 0);
	}

	@Override
	protected int getRecordSize() {
		return RECORD_SIZE;
	}

	@Override
	public int getNodeType() {
		return IIndexCPPBindingConstants.CPP_USING_DECLARATION;
	}

	public IBinding[] getDelegates() {
		if (delegates == null) {
			delegates = new IBinding[1];
			int i = 0;
			PDOMCPPUsingDeclaration alias = this;
			try {
				do {
					IBinding delegate = alias.getBinding();
					if (delegate != null) {
						delegates= (IBinding[]) ArrayUtil.append(IBinding.class, delegates, i++, delegate);
					}
				} while ((alias = alias.getNext()) != null);
			} catch (CoreException e) {
				CCorePlugin.log(e);
			}
			delegates = (IBinding[]) ArrayUtil.trim(IBinding.class, delegates);
		}
		return delegates;
	}

	private PDOMCPPUsingDeclaration getNext() throws CoreException {
		int nextRecord = pdom.getDB().getInt(record + NEXT_DELEGATE);
		return nextRecord != 0 ? new PDOMCPPUsingDeclaration(pdom, nextRecord) : null;
	}

	private IBinding getBinding() {
		try {
			return (IBinding) getLinkageImpl().getNode(
					getPDOM().getDB().getInt(record + TARGET_BINDING));
		} catch (CoreException e) {
			CCorePlugin.log(e);
		}
		return null;
	}
}

/*******************************************************************************
 * Copyright (c) 2006, 2007 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * QNX - Initial API and implementation
 * Markus Schorn (Wind River Systems)
 *******************************************************************************/
package org.eclipse.cdt.internal.core.pdom.dom.cpp;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.dom.ILinkage;
import org.eclipse.cdt.core.dom.ast.DOMException;
import org.eclipse.cdt.core.dom.ast.IScope;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.ITypedef;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPBinding;
import org.eclipse.cdt.internal.core.Util;
import org.eclipse.cdt.internal.core.dom.parser.ITypeContainer;
import org.eclipse.cdt.internal.core.index.IIndexType;
import org.eclipse.cdt.internal.core.pdom.PDOM;
import org.eclipse.cdt.internal.core.pdom.dom.PDOMBinding;
import org.eclipse.cdt.internal.core.pdom.dom.PDOMNode;
import org.eclipse.core.runtime.CoreException;

/**
 * @author Doug Schaefer
 */
class PDOMCPPTypedef extends PDOMCPPBinding implements ITypedef, ITypeContainer, IIndexType {

	private static final int TYPE = PDOMBinding.RECORD_SIZE + 0;
	
	protected static final int RECORD_SIZE = PDOMBinding.RECORD_SIZE + 4;
	
	public PDOMCPPTypedef(PDOM pdom, PDOMNode parent, ITypedef typedef)
			throws CoreException {
		super(pdom, parent, typedef.getNameCharArray());
		try {
			IType type = typedef.getType();
			PDOMNode typeNode = parent.getLinkageImpl().addType(this, type);
			if (typeNode != null)
				pdom.getDB().putInt(record + TYPE, typeNode.getRecord());
		} catch (DOMException e) {
			throw new CoreException(Util.createStatus(e));
		}
	}

	public PDOMCPPTypedef(PDOM pdom, int record) {
		super(pdom, record);
	}

	protected int getRecordSize() {
		return RECORD_SIZE;
	}
	
	public int getNodeType() {
		return PDOMCPPLinkage.CPPTYPEDEF;
	}

	public IType getType() {
		try {
			PDOMNode node = getLinkageImpl().getNode(pdom.getDB().getInt(record + TYPE));
			return node instanceof IType ? (IType)node : null;
		} catch (CoreException e) {
			CCorePlugin.log(e);
			return null;
		}
	}

	public boolean isSameType(IType type) {
		try {
			IType myrtype = getType();
			if (myrtype == null)
				return false;
			
			if (type instanceof ITypedef) {
				type= ((ITypedef)type).getType();
			}
			return myrtype.isSameType(type);
		} catch (DOMException e) {
		}
		return false;
	}

	public void setType(IType type) { fail(); }

	public Object clone() {
		return new PDOMCPPTypedefClone(this);
	}
	
	private static class PDOMCPPTypedefClone implements ITypedef, ITypeContainer, IIndexType, ICPPBinding {
		protected final ITypedef delegate;
		private IType type = null;
		
		public PDOMCPPTypedefClone(ITypedef typedef) {
			this.delegate = typedef;
		}
		public IType getType() throws DOMException {
			if (type == null) {
				return delegate.getType();
			}
			return type;
		}
		public ILinkage getLinkage() throws CoreException {
			return delegate.getLinkage();
		}
		public String getName() {
			return delegate.getName();
		}
		public char[] getNameCharArray() {
			return delegate.getNameCharArray();
		}
		public IScope getScope() throws DOMException {
			return delegate.getScope();
		}
		public Object getAdapter(Class adapter) {
			return delegate.getAdapter(adapter);
		}
		public boolean isSameType(IType type) {
			try {
				IType myrtype = getType();
				if (myrtype == null)
					return false;
				
				if (type instanceof ITypedef) {
					type= ((ITypedef)type).getType();
				}
				return myrtype.isSameType(type);
			} catch (DOMException e) {
			}
			return false;
		}
		public void setType(IType type) {
			this.type = type;
		}
		public String[] getQualifiedName() throws DOMException {
			return ((ICPPBinding)delegate).getQualifiedName();
		}
		public char[][] getQualifiedNameCharArray() throws DOMException {
			return ((ICPPBinding)delegate).getQualifiedNameCharArray();
		}
		public boolean isGloballyQualified() throws DOMException {
			return ((ICPPBinding)delegate).isGloballyQualified();
		}
		public Object clone() {
			return new PDOMCPPTypedefClone(this);
		}
	}
}

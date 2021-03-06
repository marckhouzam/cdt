/*******************************************************************************
 * Copyright (c) 2015 Nathan Ridge.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.cdt.core.dom.ast;

/**
 * An attribute-specifier of the form [[ attribute-list ]] or __attribute__(( attribute-list )).

 * @noextend This interface is not intended to be extended by clients.
 * @noimplement This interface is not intended to be implemented by clients.
 * @since 5.12
 */
public interface IASTAttributeList extends IASTAttributeSpecifier {
	/**
	 * Returns the attributes in the list.
	 */
	@Override
	public abstract IASTAttribute[] getAttributes();

	/**
	 * Adds an attribute to the list.
	 */
	@Override
	public abstract void addAttribute(IASTAttribute attribute);
}

/*******************************************************************************
 * Copyright (c) 2005, 2012 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Doug Schaefer (QNX) - Initial API and implementation
 *     IBM Corporation
 *     Markus Schorn (Wind River Systems)
 *     Sergey Prigogin (Google)
 *******************************************************************************/
package org.eclipse.cdt.internal.core.pdom.dom.cpp;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.dom.ast.DOMException;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.IScope;
import org.eclipse.cdt.core.dom.ast.ISemanticProblem;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPFunction;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPFunctionType;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPMethod;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPParameter;
import org.eclipse.cdt.internal.core.dom.parser.ProblemFunctionType;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPFunction;
import org.eclipse.cdt.internal.core.dom.parser.cpp.ICPPComputableFunction;
import org.eclipse.cdt.internal.core.dom.parser.cpp.ICPPEvaluation;
import org.eclipse.cdt.internal.core.index.IIndexCPPBindingConstants;
import org.eclipse.cdt.internal.core.index.IndexCPPSignatureUtil;
import org.eclipse.cdt.internal.core.pdom.db.Database;
import org.eclipse.cdt.internal.core.pdom.dom.IPDOMOverloader;
import org.eclipse.cdt.internal.core.pdom.dom.PDOMBinding;
import org.eclipse.cdt.internal.core.pdom.dom.PDOMLinkage;
import org.eclipse.cdt.internal.core.pdom.dom.PDOMNode;
import org.eclipse.cdt.internal.core.pdom.dom.c.PDOMCAnnotation;
import org.eclipse.core.runtime.CoreException;

/**
 * Binding for c++ functions in the index.
 */
class PDOMCPPFunction extends PDOMCPPBinding implements ICPPFunction, IPDOMOverloader, ICPPComputableFunction {
	private static final short ANNOT_PARAMETER_PACK = 8;
	private static final short ANNOT_IS_DELETED = 9;
	private static final short ANNOT_IS_CONSTEXPR = 10;

	/**
	 * Offset of total number of function parameters (relative to the beginning of the record).
	 */
	private static final int NUM_PARAMS = PDOMCPPBinding.RECORD_SIZE;

	/**
	 * Offset of pointer to the first parameter of this function (relative to the beginning of the record).
	 */
	private static final int FIRST_PARAM = NUM_PARAMS + 4;

	/**
	 * Offset of pointer to the function type record of this function (relative to the beginning of the
	 * record).
	 */
	protected static final int FUNCTION_TYPE = FIRST_PARAM + Database.PTR_SIZE;

	/**
	 * Offset of hash of parameter information to allow fast comparison
	 */
	private static final int SIGNATURE_HASH = FUNCTION_TYPE + Database.TYPE_SIZE;

	/**
	 * Offset of start of exception specifications
	 */
	protected static final int EXCEPTION_SPEC = SIGNATURE_HASH + 4; // int

	/**
	 * Offset of annotation information (relative to the beginning of the record).
	 */
	private static final int ANNOTATION = EXCEPTION_SPEC + Database.PTR_SIZE; // short

	/** Offset of the number of the required arguments. */
	private static final int REQUIRED_ARG_COUNT = ANNOTATION + 2; // short

	/** Offset of the return expression for constexpr functions. */
	private static final int RETURN_EXPRESSION = REQUIRED_ARG_COUNT + 2; // Database.EVALUATION_SIZE
	/**
	 * The size in bytes of a PDOMCPPFunction record in the database.
	 */
	@SuppressWarnings("hiding")
	protected static final int RECORD_SIZE = RETURN_EXPRESSION + Database.EVALUATION_SIZE;

	private short fAnnotation = -1;
	private int fRequiredArgCount = -1;
	private ICPPFunctionType fType; // No need for volatile, all fields of ICPPFunctionTypes are final.

	public PDOMCPPFunction(PDOMCPPLinkage linkage, PDOMNode parent, ICPPFunction function,
			boolean setTypes, IASTNode point) throws CoreException, DOMException {
		super(linkage, parent, function.getNameCharArray());
		Database db = getDB();
		Integer sigHash = IndexCPPSignatureUtil.getSignatureHash(function);
		getDB().putInt(record + SIGNATURE_HASH, sigHash != null ? sigHash.intValue() : 0);
		db.putShort(record + ANNOTATION, getAnnotation(function));
		db.putShort(record + REQUIRED_ARG_COUNT, (short) function.getRequiredArgumentCount());
		if (setTypes) {
			linkage.new ConfigureFunction(function, this, point);
		}
	}

	private short getAnnotation(ICPPFunction function) {
		int annot = PDOMCPPAnnotation.encodeAnnotation(function) & 0xff;
		if (function.hasParameterPack()) {
			annot |= (1 << ANNOT_PARAMETER_PACK);
		}
		if (function.isDeleted()) {
			annot |= (1 << ANNOT_IS_DELETED);
		}
		if (function.isConstexpr()) {
			annot |= (1 << ANNOT_IS_CONSTEXPR);
		}
		return (short) annot;
	}

	public void initData(ICPPFunctionType ftype, ICPPParameter[] params, IType[] exceptionSpec,
			ICPPEvaluation returnExpression) {
		try {
			setType(ftype);
			setParameters(params);
			storeExceptionSpec(exceptionSpec);
			getLinkage().storeEvaluation(record + RETURN_EXPRESSION, returnExpression);
		} catch (CoreException e) {
			CCorePlugin.log(e);
		}
	}

	@Override
	public void update(final PDOMLinkage linkage, IBinding newBinding, IASTNode point) throws CoreException {
		if (!(newBinding instanceof ICPPFunction))
			return;

		ICPPFunction func = (ICPPFunction) newBinding;
		ICPPFunctionType newType;
		ICPPParameter[] newParams;
		short newAnnotation;
		int newBindingRequiredArgCount;
		newType = func.getType();
		newParams = func.getParameters();
		newAnnotation = getAnnotation(func);
		newBindingRequiredArgCount = func.getRequiredArgumentCount();

		fType = null;
		linkage.storeType(record + FUNCTION_TYPE, newType);

		PDOMCPPParameter oldParams = getFirstParameter(null);
		int requiredCount;
		if (oldParams != null && hasDeclaration()) {
			int parCount = 0;
			requiredCount = 0;
			for (ICPPParameter newPar : newParams) {
				parCount++;
				if (parCount <= newBindingRequiredArgCount && !oldParams.hasDefaultValue())
					requiredCount = parCount;
				oldParams.update(newPar);
				long next = oldParams.getNextPtr();
				if (next == 0)
					break;
				oldParams = new PDOMCPPParameter(linkage, next, null);
			}
			if (parCount < newBindingRequiredArgCount) {
				requiredCount = newBindingRequiredArgCount;
			}
		} else {
			requiredCount = newBindingRequiredArgCount;
			setParameters(newParams);
			if (oldParams != null) {
				oldParams.delete(linkage);
			}
		}
		final Database db = getDB();
		db.putShort(record + ANNOTATION, newAnnotation);
		fAnnotation = newAnnotation;
		db.putShort(record + REQUIRED_ARG_COUNT, (short) requiredCount);
		fRequiredArgCount = requiredCount;

		long oldRec = db.getRecPtr(record + EXCEPTION_SPEC);
		storeExceptionSpec(extractExceptionSpec(func));
		if (oldRec != 0) {
			PDOMCPPTypeList.clearTypes(this, oldRec);
		}
		linkage.storeEvaluation(record + RETURN_EXPRESSION, CPPFunction.getReturnExpression(func, point));
	}

	private void storeExceptionSpec(IType[] exceptionSpec) throws CoreException {
		long typelist = PDOMCPPTypeList.putTypes(this, exceptionSpec);
		getDB().putRecPtr(record + EXCEPTION_SPEC, typelist);
	}

	IType[] extractExceptionSpec(ICPPFunction binding) {
		IType[] exceptionSpec;
		if (binding instanceof ICPPMethod && ((ICPPMethod) binding).isImplicit()) {
			// Don't store the exception specification, compute it on demand.
			exceptionSpec = null;
		} else {
			exceptionSpec = binding.getExceptionSpecification();
		}
		return exceptionSpec;
	}

	private void setParameters(ICPPParameter[] params) throws CoreException {
		final PDOMLinkage linkage = getLinkage();
		final Database db = getDB();
		db.putInt(record + NUM_PARAMS, params.length);
		db.putRecPtr(record + FIRST_PARAM, 0);
		PDOMCPPParameter next = null;
		for (int i = params.length; --i >= 0;) {
			next = new PDOMCPPParameter(linkage, this, params[i], next);
		}
		db.putRecPtr(record + FIRST_PARAM, next == null ? 0 : next.getRecord());
	}

	private void setType(ICPPFunctionType ft) throws CoreException {
		fType = null;
		getLinkage().storeType(record + FUNCTION_TYPE, ft);
	}

	@Override
	public int getSignatureHash() throws CoreException {
		return getDB().getInt(record + SIGNATURE_HASH);
	}

	public static int getSignatureHash(PDOMLinkage linkage, long record) throws CoreException {
		return linkage.getDB().getInt(record + SIGNATURE_HASH);
	}

	public PDOMCPPFunction(PDOMLinkage linkage, long bindingRecord) {
		super(linkage, bindingRecord);
	}

	@Override
	protected int getRecordSize() {
		return RECORD_SIZE;
	}

	@Override
	public int getNodeType() {
		return IIndexCPPBindingConstants.CPPFUNCTION;
	}

	private PDOMCPPParameter getFirstParameter(IType type) throws CoreException {
		long rec = getDB().getRecPtr(record + FIRST_PARAM);
		return rec != 0 ? new PDOMCPPParameter(getLinkage(), rec, type) : null;
	}

	@Override
	public boolean isInline() {
		return getBit(getAnnotation(), PDOMCAnnotation.INLINE_OFFSET);
	}

	@Override
	public int getRequiredArgumentCount() {
		if (fRequiredArgCount == -1) {
			try {
				fRequiredArgCount = getDB().getShort(record + REQUIRED_ARG_COUNT);
			} catch (CoreException e) {
				fRequiredArgCount = 0;
			}
		}
		return fRequiredArgCount;
	}

	final protected short getAnnotation() {
		if (fAnnotation == -1) {
			try {
				fAnnotation = getDB().getShort(record + ANNOTATION);
			} catch (CoreException e) {
				fAnnotation = 0;
			}
		}
		return fAnnotation;
	}

	@Override
	public boolean isExternC() {
		return getBit(getAnnotation(), PDOMCPPAnnotation.EXTERN_C_OFFSET);
	}

	@Override
	public boolean isMutable() {
		return false;
	}

	@Override
	public IScope getFunctionScope() {
		return null;
	}

	@Override
	public ICPPParameter[] getParameters() {
		try {
			PDOMLinkage linkage = getLinkage();
			Database db = getDB();
			ICPPFunctionType ft = getType();
			IType[] ptypes = ft == null ? IType.EMPTY_TYPE_ARRAY : ft.getParameterTypes();

			int n = db.getInt(record + NUM_PARAMS);
			ICPPParameter[] result = new ICPPParameter[n];

			long next = db.getRecPtr(record + FIRST_PARAM);
			for (int i = 0; i < n && next != 0; i++) {
				IType type = i < ptypes.length ? ptypes[i] : null;
				final PDOMCPPParameter par = new PDOMCPPParameter(linkage, next, type);
				next = par.getNextPtr();
				result[i] = par;
			}
			return result;
		} catch (CoreException e) {
			CCorePlugin.log(e);
			return ICPPParameter.EMPTY_CPPPARAMETER_ARRAY;
		}
	}

	@Override
	public final ICPPFunctionType getType() {
		if (fType == null) {
			try {
				fType = (ICPPFunctionType) getLinkage().loadType(record + FUNCTION_TYPE);
			} catch (CoreException e) {
				CCorePlugin.log(e);
				fType = new ProblemFunctionType(ISemanticProblem.TYPE_NOT_PERSISTED);
			}
		}
		return fType;
	}

	@Override
	public boolean isAuto() {
		// ISO/IEC 14882:2003 7.1.1.2
		return false;
	}

	@Override
	public boolean isConstexpr() {
		return getBit(getAnnotation(), ANNOT_IS_CONSTEXPR);
	}

	@Override
	public boolean isDeleted() {
		return getBit(getAnnotation(), ANNOT_IS_DELETED);
	}

	@Override
	public boolean isExtern() {
		return getBit(getAnnotation(), PDOMCAnnotation.EXTERN_OFFSET);
	}

	@Override
	public boolean isRegister() {
		// ISO/IEC 14882:2003 7.1.1.2
		return false;
	}

	@Override
	public boolean isStatic() {
		return getBit(getAnnotation(), PDOMCAnnotation.STATIC_OFFSET);
	}

	@Override
	public boolean takesVarArgs() {
		return getBit(getAnnotation(), PDOMCAnnotation.VARARGS_OFFSET);
	}

	@Override
	public boolean isNoReturn() {
		return getBit(getAnnotation(), PDOMCAnnotation.NO_RETURN);
	}

	@Override
	public boolean hasParameterPack() {
		return getBit(getAnnotation(), ANNOT_PARAMETER_PACK);
	}

	@Override
	public Object clone() {
		throw new UnsupportedOperationException();
	}

	@Override
	public int pdomCompareTo(PDOMBinding other) {
		int cmp = super.pdomCompareTo(other);
		return cmp == 0 ? compareSignatures(this, other) : cmp;
	}

	protected static int compareSignatures(IPDOMOverloader a, Object b) {
		if (b instanceof IPDOMOverloader) {
			IPDOMOverloader bb = (IPDOMOverloader) b;
			try {
				int mySM = a.getSignatureHash();
				int otherSM = bb.getSignatureHash();
				return mySM == otherSM ? 0 : mySM < otherSM ? -1 : 1;
			} catch (CoreException e) {
				CCorePlugin.log(e);
			}
		} else {
			assert false;
		}
		return 0;
	}

	@Override
	public IType[] getExceptionSpecification() {
		try {
			final long rec = getPDOM().getDB().getRecPtr(record + EXCEPTION_SPEC);
			return PDOMCPPTypeList.getTypes(this, rec);
		} catch (CoreException e) {
			CCorePlugin.log(e);
			return null;
		}
	}

	@Override
	public ICPPEvaluation getReturnExpression(IASTNode point) {
		if (!isConstexpr())
			return null;

		try {
			return (ICPPEvaluation) getLinkage().loadEvaluation(record + RETURN_EXPRESSION);
		} catch (CoreException e) {
			CCorePlugin.log(e);
			return null;
		}
	}
}

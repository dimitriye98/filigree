/*
 * This file contains code by Cadix Development, as per the license notice below.
 * All modifications by this project to the code are clearly marked by comments
 * where possible, and otherwise may be investigated through git blame.
 * Such modifications are available under the terms of the MIT license as well as
 * the EPL-2.0
 *
 * Copyright (c) 2018 Cadix Development (https://www.cadixdev.org)
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package com.dimitriye.filigree.remapper;

import java.util.*;
import java.util.function.BiFunction;
import org.cadixdev.bombe.analysis.InheritanceProvider;
import org.cadixdev.bombe.type.signature.FieldSignature;
import org.cadixdev.bombe.type.signature.MethodSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.InnerClassMapping;
import org.cadixdev.lorenz.model.MemberMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.cadixdev.mercury.RewriteContext;
import org.cadixdev.mercury.analysis.MercuryInheritanceProvider;
import org.cadixdev.mercury.util.GracefulCheck;
import org.eclipse.jdt.core.dom.*;

import static org.cadixdev.mercury.util.BombeBindings.convertSignature;

/**
 * Remaps only methods and fields.
 */
class SimpleRemapperVisitor extends ASTVisitor {
	final RewriteContext context;
	final MappingSet mappings;
	private final InheritanceProvider inheritanceProvider;

	SimpleRemapperVisitor(RewriteContext context, MappingSet mappings, boolean javadoc) {
		super(javadoc);
		this.context = context;
		this.mappings = mappings;
		this.inheritanceProvider = MercuryInheritanceProvider.get(context.getMercury());
	}

	final void updateIdentifier(SimpleName node, String newName) {
		if (!node.getIdentifier().equals(newName)) {
			this.context.createASTRewrite().set(node, SimpleName.IDENTIFIER_PROPERTY, newName, null);
		}
	}

	/**
	 * Gathers all superclasses of a type into a collection
	 *
	 * @author Dimitriye Danilovic
	 * @param binding the type whose ancestors to collect
	 * @param col output collection
	 * @param ast the AST object
	 */
	private void ascendHierarchy(ITypeBinding binding, Collection<ITypeBinding> col, AST ast) {
		if (Objects.equals(binding, ast.resolveWellKnownType("java.lang.Object"))) {
			return;
		}

		if (!binding.isInterface()) {
			col.add(binding.getSuperclass());
			ascendHierarchy(binding.getSuperclass(), col, ast);
		}

		for (ITypeBinding it: binding.getInterfaces()) {
			col.add(it);
			ascendHierarchy(it, col, ast);
		}
	}

	private void remapMethod(SimpleName node, IMethodBinding binding) {
		ITypeBinding declaringClass = binding.getDeclaringClass();
		if (GracefulCheck.checkGracefully(this.context, declaringClass)) {
			return;
		}
		final ClassMapping<?, ?> classMapping = this.mappings.getOrCreateClassMapping(declaringClass.getBinaryName());

		if (binding.isConstructor()) {
			updateIdentifier(node, classMapping.getSimpleDeobfuscatedName());
		} else {
			classMapping.complete(this.inheritanceProvider, declaringClass);

			MethodSignature bindingSignature = convertSignature(binding);
			MethodMapping mapping = findMemberMapping(bindingSignature, classMapping, ClassMapping::getMethodMapping);

			if (mapping == null) {
				mapping = classMapping.getMethodMapping(bindingSignature).orElse(null);
			}

			/* *********************
			 * Begin Filigree Code *
			 ********************* */

			if (mapping == null) {
				List<ITypeBinding> parents = new ArrayList<>();
				ascendHierarchy(declaringClass, parents, context.createASTRewrite().getAST());

				for (ITypeBinding parent: parents) {
					final ClassMapping<?, ?> parentMapping = this.mappings.getClassMapping(parent.getBinaryName()).orElse(null);
					if (parentMapping == null) { continue; }
					IMethodBinding[] methods = parent.getDeclaredMethods();
					for (int i = 0; i < methods.length; ++i) {
						IMethodBinding method = methods[i];
						if (binding.overrides(method)) {
							IMethodBinding canonical = parent.getErasure().getDeclaredMethods()[i];

							mapping = findMemberMapping(
								convertSignature(canonical.getMethodDeclaration()),
								parentMapping,
								ClassMapping::getMethodMapping
							);
						}
					}
				}
			}

			/* *********************
			 *  End Filigree Code  *
			 ********************* */

			if (mapping == null) {
				return;
			}

			updateIdentifier(node, mapping.getDeobfuscatedName());
		}
	}

	private void remapField(SimpleName node, IVariableBinding binding) {
		if (!binding.isField()) {
			return;
		}

		ITypeBinding declaringClass = binding.getDeclaringClass();
		if (declaringClass == null) {
			return;
		}

		ClassMapping<?, ?> classMapping = this.mappings.getClassMapping(declaringClass.getBinaryName()).orElse(null);
		if (classMapping == null) {
			return;
		}

		FieldSignature bindingSignature = convertSignature(binding);
		FieldMapping mapping = findMemberMapping(bindingSignature, classMapping, ClassMapping::computeFieldMapping);
		if (mapping == null) {
			return;
		}

		updateIdentifier(node, mapping.getDeobfuscatedName());
	}

	private <T extends MemberMapping<?, ?>, M> T findMemberMapping(
		M matcher,
		ClassMapping<?, ?> classMapping,
		BiFunction<ClassMapping<?, ?>, M, Optional<? extends T>> getMapping
	) {
		T mapping = getMapping.apply(classMapping, matcher).orElse(null);
		if (mapping != null) {
			return mapping;
		}

		return findMemberMappingAnonClass(matcher, classMapping, getMapping);
	}

	private <T extends MemberMapping<?, ?>, M> T findMemberMappingAnonClass(
		M matcher,
		ClassMapping<?, ?> classMapping,
		BiFunction<ClassMapping<?, ?>, M, Optional<? extends T>> getMapping
	) {
		// If neither name is different then this method won't do anything
		if (Objects.equals(classMapping.getObfuscatedName(), classMapping.getDeobfuscatedName())) {
			return null;
		}
		// Anonymous classes must be inner classes
		if (!(classMapping instanceof InnerClassMapping)) {
			return null;
		}
		// Verify this is inner class is anonymous
		if (!classMapping.getObfuscatedName().chars().allMatch(Character::isDigit)) {
			return null;
		}
		ClassMapping<?, ?> parentMapping = ((InnerClassMapping) classMapping).getParent();
		if (parentMapping == null) {
			return null;
		}

		// Find a sibling anonymous class whose obfuscated name is our deobfuscated name
		ClassMapping<?, ?> otherClassMapping = parentMapping
			.getInnerClassMapping(classMapping.getDeobfuscatedName()).orElse(null);
		if (otherClassMapping != null) {
			T mapping = getMapping.apply(otherClassMapping, matcher).orElse(null);
			if (mapping != null) {
				return mapping;
			}
		}

		// Find a sibling anonymous class whose deobfuscated name is our obfuscated name
		// We have to do something a little less direct for this case
		for (InnerClassMapping innerClassMapping : parentMapping.getInnerClassMappings()) {
			if (Objects.equals(classMapping.getObfuscatedName(), innerClassMapping.getDeobfuscatedName())) {
				otherClassMapping = innerClassMapping;
				break;
			}
		}
		if (otherClassMapping == null) {
			return null;
		}
		return getMapping.apply(otherClassMapping, matcher).orElse(null);
	}

	protected void visit(SimpleName node, IBinding binding) {
		switch (binding.getKind()) {
			case IBinding.METHOD:
				remapMethod(node, ((IMethodBinding) binding).getMethodDeclaration());
				break;
			case IBinding.VARIABLE:
				remapField(node, ((IVariableBinding) binding).getVariableDeclaration());
				break;
		}
	}

	@Override
	public final boolean visit(SimpleName node) {
		IBinding binding = node.resolveBinding();
		if (binding != null) {
			visit(node, binding);
		}
		return false;
	}

}

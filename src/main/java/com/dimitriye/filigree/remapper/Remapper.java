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

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.mercury.RewriteContext;
import org.cadixdev.mercury.SourceRewriter;

import java.util.Objects;

public final class Remapper implements SourceRewriter {

	public static SourceRewriter create(MappingSet mappings) {
		return new Remapper(mappings, false, true);
	}

	public static SourceRewriter create(MappingSet mappings, boolean javadoc) {
		return new Remapper(mappings, false, javadoc);
	}

	public static SourceRewriter createSimple(MappingSet mappings) {
		return new Remapper(mappings, true, true);
	}

	public static SourceRewriter createSimple(MappingSet mappings, boolean javadoc) {
		return new Remapper(mappings, true, javadoc);
	}

	private final MappingSet mappings;
	private final boolean simple;
	private final boolean javadoc;

	private Remapper(MappingSet mappings, boolean simple, boolean javadoc) {
		this.mappings = Objects.requireNonNull(mappings, "mappings");
		this.simple = simple;
		this.javadoc = javadoc;
	}

	@Override
	public int getFlags() {
		return FLAG_RESOLVE_BINDINGS;
	}

	@Override
	public void rewrite(RewriteContext context) {
		context.getCompilationUnit().accept(this.simple ?
			new SimpleRemapperVisitor(context, this.mappings, this.javadoc) :
			new RemapperVisitor(context, this.mappings, this.javadoc));
	}

}

/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.dalvik.test.callGraph;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import junit.framework.Assert;

import org.junit.Test;

import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.core.tests.util.TestConstants;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Pair;

public class JVMLDalvikComparison extends DalvikCallGraphTestBase {

	private static Pair<CallGraph,PointerAnalysis<InstanceKey>> makeJavaBuilder(String scopeFile, String mainClass) throws IOException, ClassHierarchyException, IllegalArgumentException, CancelException {
		AnalysisScope scope = CallGraphTestUtil.makeJ2SEAnalysisScope(scopeFile, CallGraphTestUtil.REGRESSION_EXCLUSIONS);
		ClassHierarchy cha = ClassHierarchy.make(scope);
		Iterable<Entrypoint> entrypoints = com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(scope, cha, mainClass);
		AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);
		SSAPropagationCallGraphBuilder builder = Util.makeZeroCFABuilder(options, new AnalysisCache(), cha, scope);
		CallGraph CG = builder.makeCallGraph(options);
		return Pair.make(CG, builder.getPointerAnalysis());
	}
	
	private static Set<Pair<CGNode,CGNode>> edgeDiff(CallGraph from, CallGraph to) {
		Set<Pair<CGNode,CGNode>> result = HashSetFactory.make();
		for(CGNode f : from) {
			if (! f.getMethod().isSynthetic()) {
			outer: for(CGNode t : from) {
				if (!t.getMethod().isSynthetic() && from.hasEdge(f, t)) {
					Set<CGNode> fts = to.getNodes(f.getMethod().getReference());
					Set<CGNode> tts = to.getNodes(t.getMethod().getReference());
					for(CGNode x : fts) {
						for(CGNode y : tts) {
							if (to.hasEdge(x, y)) {
								continue outer;
							}
						}
					}
					result.add(Pair.make(f, t));
				}
			}
			}
		}
		return result;
	}
	
	private static void test(boolean useAndroidLib, String mainClass, String javaScopeFile) throws ClassHierarchyException, IllegalArgumentException, IOException, CancelException, InterruptedException {
		Pair<CallGraph, PointerAnalysis<InstanceKey>> java = makeJavaBuilder(javaScopeFile, mainClass);

		AnalysisScope javaScope = java.fst.getClassHierarchy().getScope();
		String javaJarPath = getJavaJar(javaScope);
		File androidDex = convertJarToDex(new File(javaJarPath));
		Pair<CallGraph,PointerAnalysis<InstanceKey>> android = makeDalvikCallGraph(useAndroidLib, mainClass, androidDex.getAbsolutePath());
	
		Set<MethodReference> androidMethods = applicationMethods(android.fst);
		Set<MethodReference> javaMethods = applicationMethods(java.fst);
		
		Set<MethodReference> androidExtra = HashSetFactory.make(androidMethods);
		androidExtra.removeAll(javaMethods);
		System.err.println(androidExtra);
		
		Set<MethodReference> javaExtra = HashSetFactory.make(javaMethods);
		javaExtra.removeAll(androidMethods);
		System.err.println(javaExtra);
		
		System.err.println(edgeDiff(android.fst, java.fst));
		
		System.err.println(edgeDiff(java.fst, android.fst));
		
		Assert.assertTrue(androidMethods.containsAll(javaMethods));		
	}
	
	@Test
	public void testJLex() throws ClassHierarchyException, IllegalArgumentException, IOException, CancelException, InterruptedException {
		test(false, TestConstants.JLEX_MAIN, TestConstants.JLEX);
	}

	@Test
	public void testJavaCup() throws ClassHierarchyException, IllegalArgumentException, IOException, CancelException, InterruptedException {
		test(false, TestConstants.JAVA_CUP_MAIN, TestConstants.JAVA_CUP);
	}

	@Test
	public void testBCEL() throws ClassHierarchyException, IllegalArgumentException, IOException, CancelException, InterruptedException {
		test(false, TestConstants.BCEL_VERIFIER_MAIN, TestConstants.BCEL);
	}
}

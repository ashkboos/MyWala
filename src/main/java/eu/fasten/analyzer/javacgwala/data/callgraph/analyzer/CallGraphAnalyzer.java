/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.fasten.analyzer.javacgwala.data.callgraph.analyzer;

import static eu.fasten.analyzer.javacgwala.data.core.CallType.DYNAMIC;
import static it.unimi.dsi.fastutil.objects.Object2ObjectMaps.emptyMap;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.shrikeBT.IInvokeInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import eu.fasten.analyzer.javacgwala.data.core.CallType;
import eu.fasten.analyzer.javacgwala.data.core.ExternalMethod;
import eu.fasten.analyzer.javacgwala.data.core.InternalMethod;
import eu.fasten.analyzer.javacgwala.data.core.Method;
import eu.fasten.core.data.CallPreservationStrategy;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.JavaScope;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class CallGraphAnalyzer {

    private final AnalysisContext analysisContext;

    private final CallGraph rawCallGraph;

    private final ClassHierarchyAnalyzer classHierarchyAnalyzer;

    public final Map<LongLongPair, Map<Object, Object>> graph;

    /**
     * Analyze raw call graph in Wala format.
     *
     * @param rawCallGraph           Raw call graph in Wala format
     * @param classHierarchyAnalyzer classHierarchyAnalyzer
     */
    public CallGraphAnalyzer(final CallGraph rawCallGraph,
                             final AnalysisContext analysisContext,
                             final ClassHierarchyAnalyzer classHierarchyAnalyzer) {
        this.rawCallGraph = rawCallGraph;
        this.analysisContext = analysisContext;
        this.classHierarchyAnalyzer = classHierarchyAnalyzer;
        this.graph = new ConcurrentHashMap<>();
    }

    /**
     * Iterate over nodes in Wala call graph and add calls that "belong" to application class
     * loader to lists of resolved / unresolved calls of partial call graph.
     *
     * @param strategy specifies if wrapper should only return call sites or resolved
     *                 edges of the call graph.
     */
    public void resolveCalls(final CallPreservationStrategy strategy) {
        this.rawCallGraph.stream().parallel().forEach(sourceNode -> {
            final var nodeReference = sourceNode.getMethod().getReference();

            if (applicationClassLoaderFilter.test(sourceNode)) {
                return;
            }

            final var source = analysisContext.findOrCreate(nodeReference);
            source.oroginalLoader = Optional.of(sourceNode.getMethod());

            for (final var callSites = sourceNode.iterateCallSites(); callSites.hasNext(); ) {
                final var callSite = callSites.next();
                switch (strategy) {
                    case INCLUDING_ALL_SUBTYPES:
                        final var targets = rawCallGraph.getPossibleTargets(sourceNode, callSite);
                        for (final var possibleTarget : targets) {
                            final var targetCallSite = analysisContext.findOrCreate(
                                correctClassLoader(possibleTarget.getMethod().getReference()));
                            addCallAndType(source, targetCallSite, emptyMap());
                        }
                        break;
                    case ONLY_STATIC_CALLSITES:
                        final var targetCallSite = analysisContext.findOrCreate(
                            correctClassLoader(callSite.getDeclaredTarget()));
                        final var callSiteMetadata =
                            getCallSiteMetadata(callSite, sourceNode.getMethod());
                        addCallAndType(source, targetCallSite, callSiteMetadata);
                        break;
                }
            }
        });
    }

    private Map<Object, Object> getCallSiteMetadata(final CallSiteReference callSite,
                                                    final IMethod method) {
        final var callType = getInvocationLabel(callSite);
        final var pc = callSite.getProgramCounter();

        final var type = Method.getType(callSite.getDeclaredTarget().getDeclaringClass());
        final var lineNumber = method.getLineNumber(pc);
        final var pcMetadata =
            new HashMap<>() {{
                put(Constants.CALLSITE_LINE, lineNumber);
                put(Constants.INVOCATION_TYPE, callType);
                put(Constants.RECEIVER_TYPE, "[" + type + "]");
            }};
        return Map.of(pc, pcMetadata);
    }

    /**
     * Add call to partial call graph.
     *
     * @param source   Caller
     * @param target   Callee
     * @param metadata call-site metadata
     */
    private void addCallAndType(final Method source, final Method target,
                                final Map<Object, Object> metadata) {

        int sourceID;
        int targetID;
        if (source instanceof InternalMethod && target instanceof InternalMethod) {
            sourceID = classHierarchyAnalyzer.addMethodToScope(source,
                source.getReference().getDeclaringClass(), JavaScope.internalTypes);
            targetID = classHierarchyAnalyzer.addMethodToScope(target,
                target.getReference().getDeclaringClass(), JavaScope.internalTypes);
        } else if (source instanceof ExternalMethod && target instanceof InternalMethod) {
            sourceID = classHierarchyAnalyzer.addMethodToScope(source,
                source.getReference().getDeclaringClass(), JavaScope.externalTypes);
            targetID = classHierarchyAnalyzer.addMethodToScope(target,
                target.getReference().getDeclaringClass(), JavaScope.internalTypes);
        } else {
            sourceID = classHierarchyAnalyzer.addMethodToScope(source,
                source.getReference().getDeclaringClass(), JavaScope.internalTypes);
            targetID = classHierarchyAnalyzer.addMethodToScope(target,
                target.getReference().getDeclaringClass(), JavaScope.externalTypes);
        }
        addCall(sourceID, targetID, metadata);
    }

    private synchronized void addCall(int sourceID, int targetID,
                                      final Map<Object, Object> metadata) {
        final var old = this.graph
            .getOrDefault(LongLongPair.of(sourceID, targetID), new HashMap<>());
        old.putAll(metadata);
        this.graph.put(LongLongPair.of(sourceID, targetID), old);
    }

    /**
     * True if node "belongs" to application class loader.
     */
    private Predicate<CGNode> applicationClassLoaderFilter = node -> !node.getMethod()
        .getDeclaringClass()
        .getClassLoader()
        .getReference()
        .equals(ClassLoaderReference.Application);

    /**
     * Get class loader with correct class loader.
     *
     * @param reference Method reference
     * @return Method reference with correct class loader
     */
    private MethodReference correctClassLoader(final MethodReference reference) {
        IClass klass = rawCallGraph.getClassHierarchy().lookupClass(reference.getDeclaringClass());

        if (klass == null) {
            return MethodReference.findOrCreate(ClassLoaderReference.Extension,
                reference.getDeclaringClass().getName().toString(),
                reference.getName().toString(),
                reference.getDescriptor().toString());
        }

        return MethodReference.findOrCreate(klass.getReference(), reference.getSelector());

    }

    /**
     * Get call type.
     *
     * @param callSite Call site
     * @return Call type
     */
    private String getInvocationLabel(final CallSiteReference callSite) {

        switch ((IInvokeInstruction.Dispatch) callSite.getInvocationCode()) {
            case INTERFACE:
                return CallType.INTERFACE.label;
            case VIRTUAL:
                return CallType.VIRTUAL.label;
            case SPECIAL:
                return CallType.SPECIAL.label;
            case STATIC:
                return CallType.STATIC.label;
            default:
                return DYNAMIC.label;
        }
    }
}

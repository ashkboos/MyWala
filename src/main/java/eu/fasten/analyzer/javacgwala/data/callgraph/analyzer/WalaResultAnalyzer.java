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

import com.ibm.wala.ipa.callgraph.CallGraph;
import eu.fasten.analyzer.javacgwala.data.callgraph.PartialCallGraphGenerator;
import eu.fasten.core.data.CallPreservationStrategy;
import eu.fasten.core.data.JavaGraph;
import eu.fasten.core.data.PartialJavaCallGraph;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.EnumMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WalaResultAnalyzer {

    private static Logger logger = LoggerFactory.getLogger(WalaResultAnalyzer.class);

    /**
     * Convert raw Wala call graph to {@link PartialCallGraphGenerator}.
     *
     * @param rawCallGraph Raw call graph in Wala format
     * @param preservationStrategy specifies if wrapper should only return call sites or resolved
     *                            edges of the call graph.
     */
    public static void wrap(final CallGraph rawCallGraph,
                            final PartialJavaCallGraph result,
                            final CallPreservationStrategy preservationStrategy) {
        final NumberFormat timeFormatter = new DecimalFormat("#0.000");
        logger.info("Wrapping call graph with {} nodes...", rawCallGraph.getNumberOfNodes());
        final long startTime = System.currentTimeMillis();

        final var analysisContext = new AnalysisContext(rawCallGraph.getClassHierarchy());

        final var classHierarchyAnalyzer =
            new ClassHierarchyAnalyzer(rawCallGraph, analysisContext);
        classHierarchyAnalyzer.resolveCHA();
        final var callGraphAnalyzer = new CallGraphAnalyzer(rawCallGraph, analysisContext, classHierarchyAnalyzer);
        callGraphAnalyzer.resolveCalls(preservationStrategy);

        result.setClassHierarchy(new EnumMap<>(classHierarchyAnalyzer.classHierarchy));
        result.setGraph(new JavaGraph(callGraphAnalyzer.graph));

        logger.info("Wrapped call graph in {} seconds [calls/callsites: {}]",
            timeFormatter.format((System.currentTimeMillis() - startTime) / 1000d),
            callGraphAnalyzer.graph.size());

    }

    public static void wrap(final CallGraph rawCallGraph, final PartialJavaCallGraph pcg) {
        wrap(rawCallGraph, pcg, CallPreservationStrategy.ONLY_STATIC_CALLSITES);
    }
}

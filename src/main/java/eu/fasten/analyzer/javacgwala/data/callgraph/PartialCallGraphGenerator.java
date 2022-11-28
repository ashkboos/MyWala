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

package eu.fasten.analyzer.javacgwala.data.callgraph;

import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.CancelException;
import eu.fasten.analyzer.javacgwala.core.MavenCoordinate;
import eu.fasten.analyzer.javacgwala.data.callgraph.analyzer.WalaResultAnalyzer;
import eu.fasten.core.data.CallPreservationStrategy;
import eu.fasten.core.data.JavaGraph;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.PartialJavaCallGraph;
import java.io.IOException;
import java.util.EnumMap;

public class PartialCallGraphGenerator {

    /**
     * Construct a partial call graph with empty lists of resolved / unresolved calls.
     */
    public static PartialJavaCallGraph generateEmptyPCG(String forge, String product,
                                                          String version,
                                                          long timestamp, String generator) {
        return new PartialJavaCallGraph(forge, product, version, timestamp, generator,
            new EnumMap<>(JavaScope.class), new JavaGraph());
    }

    public static void generateFromCoordinate(final MavenCoordinate coordinate,
                                              final Algorithm algorithm,
                                              final PartialJavaCallGraph result,
                                              final CallPreservationStrategy strategy) {
        try {
            CallGraphConstructor.build(coordinate, algorithm, result, strategy);
        } catch (IOException | CancelException | ClassHierarchyException e) {
            throw new RuntimeException(e);
        }
    }

    public static void generateFromFile(final String path,
                                        final Algorithm algorithm,
                                        final PartialJavaCallGraph result,
                                        final CallPreservationStrategy strategy) {
        try {
            final var callgraph = CallGraphConstructor.generateCallGraph(path, algorithm);
            WalaResultAnalyzer.wrap(callgraph, result, strategy);

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static PartialJavaCallGraph generateEmptyPCG(String forge, String coord,
                                                        long timestamp, String generator) {
        final var coordinate = MavenCoordinate.fromString(coord);
        return new PartialJavaCallGraph(forge, coordinate.getProduct(), coordinate.getVersionConstraint(), timestamp,
            generator,
            new EnumMap<>(JavaScope.class), new JavaGraph());    }
}

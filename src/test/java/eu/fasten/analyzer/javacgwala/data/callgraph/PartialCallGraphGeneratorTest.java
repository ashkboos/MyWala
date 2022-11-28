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

import static eu.fasten.analyzer.javacgwala.data.core.CallType.SPECIAL;
import static eu.fasten.analyzer.javacgwala.data.core.CallType.STATIC;
import static eu.fasten.core.data.Constants.PUBLIC;
import static eu.fasten.core.data.Constants.mvnForge;
import static eu.fasten.core.data.Constants.walaGenerator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.CancelException;
import eu.fasten.analyzer.javacgwala.testhelpers.CallAssertions;
import eu.fasten.analyzer.javacgwala.testhelpers.CallTestData;
import eu.fasten.analyzer.javacgwala.testhelpers.NodeExpectation;
import eu.fasten.analyzer.javacgwala.data.callgraph.analyzer.WalaResultAnalyzer;
import eu.fasten.core.data.PartialJavaCallGraph;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PartialCallGraphGeneratorTest {

    public static Algorithm ALG;
    private static PartialJavaCallGraph graph;

    @BeforeAll
    static void setUp() throws ClassHierarchyException, CancelException, IOException {
        graph = PartialCallGraphGenerator.generateEmptyPCG(mvnForge,
            "SingleSourceToTarget", "0.0.0", -1, walaGenerator);

        ALG = Algorithm.CHA;
        var path = Paths.get(new File(Thread.currentThread().getContextClassLoader()
            .getResource("SingleSourceToTarget.jar").getFile()).getAbsolutePath());

        WalaResultAnalyzer.wrap(CallGraphConstructor.generateCallGraph(path.toString(),
            ALG), graph);
    }

    @Test
    void testDefaultInit()
        throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {

        final var sourceExpectation = new NodeExpectation("/name.space/SingleSourceToTarget",
            "%3Cinit%3E()%2Fjava.lang%2FVoidType", 5, 7, PUBLIC, true, false);
        final var targetExpectation = new NodeExpectation("/java.lang/Object",
            "%3Cinit%3E()VoidType", 5, 7, PUBLIC, true, false);

        final var call = new CallTestData(graph, 0);

        assertEquals(2, graph.getGraph().size());
        assertEquals(3, graph.getClassHierarchy().size());

        CallAssertions.assertCall(sourceExpectation, targetExpectation, SPECIAL.label, 5,
                        "[/java.lang/Object]", call, 1);
        CallAssertions.assertNode(call, "source", sourceExpectation);

        assertEquals("<init>()/java.lang/VoidType", call.targetSignature());
        assertTrue(call.targetMetadata().isEmpty());
    }

    @Test
    void testStaticCall()
        throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {

        final var sourceExpectation = new NodeExpectation("/name.space/SingleSourceToTarget",
            "sourceMethod()%2Fjava.lang%2FVoidType", 10, 11,
            PUBLIC, true, false);
        final var targetExpectation = new NodeExpectation("/name.space/SingleSourceToTarget",
            "targetMethod()%2Fjava.lang%2FVoidType",
            15, 15, PUBLIC, true, false);

        final var call = new CallTestData(graph, 1);

        CallAssertions.assertCall(sourceExpectation, targetExpectation, STATIC.label, 10,
            "[/name.space/SingleSourceToTarget]", call, 0);
        CallAssertions.assertNode(call, "source", sourceExpectation);
        CallAssertions.assertNode(call, "target", targetExpectation);
    }

//    @Test
//    public void testRTJARCG() throws ClassHierarchyException, IOException, CancelException {
//        ALG = Algorithm.CHA;
//        PartialJavaCallGraph pcg = PartialCallGraphGenerator.generateEmptyPCG(mvnForge,
//            "java", "8", -1, walaGenerator);;
//
//        var path = Paths.get(new File(Thread.currentThread().getContextClassLoader()
//            .getResource("RtJar1.8.0-221.jar").getFile()).getAbsolutePath());
//        WalaResultAnalyzer.wrap(CallGraphConstructor.generateCallGraph(path.toString(),
//            ALG), pcg);
//        System.out.println();
//
//    }

}
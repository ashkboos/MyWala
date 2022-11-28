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

import static eu.fasten.analyzer.javacgwala.data.core.CallType.INTERFACE;
import static eu.fasten.analyzer.javacgwala.data.core.CallType.STATIC;
import static eu.fasten.core.data.Constants.PACKAGE_PRIVATE;
import static eu.fasten.core.data.Constants.PUBLIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.CancelException;
import eu.fasten.analyzer.javacgwala.data.callgraph.Algorithm;
import eu.fasten.analyzer.javacgwala.data.callgraph.CallGraphConstructor;
import eu.fasten.analyzer.javacgwala.data.callgraph.PartialCallGraphGenerator;
import eu.fasten.analyzer.javacgwala.data.core.ExternalMethod;
import eu.fasten.analyzer.javacgwala.data.core.Method;
import eu.fasten.analyzer.javacgwala.testhelpers.CallAssertions;
import eu.fasten.analyzer.javacgwala.testhelpers.CallTestData;
import eu.fasten.analyzer.javacgwala.testhelpers.NodeExpectation;
import eu.fasten.core.data.CallPreservationStrategy;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.PartialJavaCallGraph;
import eu.fasten.core.utils.FastenUriUtils;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
public class ClassHierarchyAnalyzerTest {

    private static CallGraph cigraph, lambdagraph, arraygraph, aegraph, metadataGraph, ssttgraph,
        callpreservation;
    private static PartialJavaCallGraph CLASS_INIT_PCG, LambdaExample_PCG, ARRAY_PCG,
        ARRAY_EXTENSIVE_PCG, Metadata_PCG, CallP_PCG;

    @BeforeAll
    public static void setUp() throws ClassHierarchyException, CancelException, IOException {
        Algorithm ALG = Algorithm.CHA;
        final var ssttPath = new File(Thread.currentThread().getContextClassLoader()
            .getResource("SingleSourceToTarget.jar").getFile()).getAbsolutePath();
        ssttgraph = CallGraphConstructor.generateCallGraph(ssttPath, ALG);

        /**
         * ClassInit:
         *
         * package name.space;
         *
         * public class ClassInit{
         *
         * public static void targetMethod(){}
         *
         *     static{
         *         targetMethod();
         *     }
         * }
         *
         */
        CLASS_INIT_PCG = getEmptyPCGWithProductName("ClassInit");
        var cipath = new File(Thread.currentThread().getContextClassLoader()
            .getResource("ClassInit.jar")
            .getFile()).getAbsolutePath();
        cigraph = CallGraphConstructor.generateCallGraph(cipath, ALG);

        /**
         * LambdaExample:
         *
         * package name.space;
         *
         * import java.util.function.Function;
         *
         * public class LambdaExample {
         *     Function f = (it) -> "Hello";
         * }
         */
        LambdaExample_PCG = getEmptyPCGWithProductName("LambdaExample");
        var lambdapath = new File(Thread.currentThread().getContextClassLoader()
            .getResource("LambdaExample.jar").getFile()).getAbsolutePath();
        lambdagraph = CallGraphConstructor.generateCallGraph(lambdapath, ALG);

        /**
         * ArrayExample:
         *
         *package name.space;
         *
         * public class ArrayExample{
         *     public static void sourceMethod() {
         *         Object[] object = new Object[1];
         *         targetMethod(object);
         *     }
         *
         *     public static Object[] targetMethod(Object[] obj) {
         *         return obj;
         *     }
         * }
         */
        ARRAY_PCG = getEmptyPCGWithProductName("ArrayExample");
        var arraypath = new File(Thread.currentThread().getContextClassLoader()
            .getResource("ArrayExample.jar").getFile()).getAbsolutePath();
        arraygraph = CallGraphConstructor.generateCallGraph(arraypath, ALG);

        /**
         * Contains arrays of all primitive types including two arrays of types Integer and Object.
         */
        var aepath = new File(Thread.currentThread().getContextClassLoader()
            .getResource("ArrayExtensiveTest.jar").getFile()).getAbsolutePath();
        aegraph = CallGraphConstructor.generateCallGraph(aepath, ALG);
        ARRAY_EXTENSIVE_PCG = getEmptyPCGWithProductName("ArrayExtensiveTest");

        /**
         * Contains different types of method and class metadata values.
         */
        var metadataExamplePath = new File(Thread.currentThread().getContextClassLoader()
            .getResource("MetadataExample.jar").getFile()).getAbsolutePath();
        metadataGraph = CallGraphConstructor.generateCallGraph(metadataExamplePath, Algorithm.CHA);
        Metadata_PCG = getEmptyPCGWithProductName("MetadataExample");


    }

    @NotNull
    private static PartialJavaCallGraph getEmptyPCGWithProductName(final String product) {
        return PartialCallGraphGenerator.generateEmptyPCG(Constants.mvnForge, product, "0.0.0",
            -1, Constants.walaGenerator);
    }

    @Test
    public void toCanonicalJSONClassInitTest()
        throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {

        WalaResultAnalyzer.wrap(cigraph, CLASS_INIT_PCG);

        final var sourceExpectation = new NodeExpectation("/name.space/ClassInit",
            "%3Cclinit%3E()%2Fjava.lang%2FVoidType", 14, 15,
            PACKAGE_PRIVATE, true, false);
        final var targetExpectation = new NodeExpectation("/name.space/ClassInit",
            "targetMethod()%2Fjava.lang%2FVoidType",
            11, 11, PUBLIC, true, false);

        final var call = new CallTestData(CLASS_INIT_PCG, 1);

        CallAssertions.assertCall(sourceExpectation, targetExpectation, STATIC.label, 14,
            "[/name.space/ClassInit]", call, 0);
        CallAssertions.assertNode(call, "source", sourceExpectation);
        CallAssertions.assertNode(call, "target", targetExpectation);
    }

    @Test
    public void toCanonicalJSONLambdaTest()
        throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {

        WalaResultAnalyzer.wrap(lambdagraph, LambdaExample_PCG);
        final var call = new CallTestData(LambdaExample_PCG, 1);

        final var sourceExpectation = new NodeExpectation("/name.space/LambdaExample",
            "%3Cinit%3E()%2Fjava.lang%2FVoidType", 10, 11,
            PUBLIC, true, false);
        final var targetExpectation = new NodeExpectation("/java.lang.invoke/LambdaMetafactory",
            "apply()%2Fjava.util.function%2FFunction",
            // The rest of parameters don't matter
            0, 0, null, false, false);

        assertEquals(2, LambdaExample_PCG.getGraph().size());
        CallAssertions.assertCall(sourceExpectation, targetExpectation, STATIC.label, 6,
            "[/java.lang.invoke/LambdaMetafactory]", call, 5);
        CallAssertions.assertNode(call, "source", sourceExpectation);
        assertTrue(call.targetMetadata().isEmpty());
    }

    @Test
    public void toCanonicalJSONArrayTest()
        throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {

        WalaResultAnalyzer.wrap(arraygraph, ARRAY_PCG);
        assertEquals(2, ARRAY_PCG.getCallSites().size());

        final var call = new CallTestData(ARRAY_PCG, 1);

        final var sourceExpectation = new NodeExpectation("/name.space/ArrayExample",
            "sourceMethod()%2Fjava.lang%2FVoidType", 8, 10,
            PUBLIC, true, false);
        final var targetExpectation = new NodeExpectation("/name.space/ArrayExample",
            "targetMethod(%2Fjava.lang%2FObject%25255B%25255D)%2Fjava.lang%2FObject%25255B%25255D",
            // The rest of parameters don't matter
            13, 13, PUBLIC, true, false);

        CallAssertions.assertCall(sourceExpectation, targetExpectation, STATIC.label, 9,
            "[/name.space/ArrayExample]", call, 6);
        CallAssertions.assertNode(call, "source", sourceExpectation);
        CallAssertions.assertNode(call, "target", targetExpectation);
    }

    @Test
    public void testExampleMetadataInterface()
        throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        WalaResultAnalyzer.wrap(metadataGraph, Metadata_PCG);

        final var artificialEdge = new CallTestData(Metadata_PCG, "IMetadata.iMethod",
            "defaultMethod");
        final var sourceExpectation = new NodeExpectation("/name.space/IMetadata",
            "iMethod()%2Fjava.lang%2FVoidType", -1, -1,
            PUBLIC, false, false);
        final var targetExpectation = new NodeExpectation("/name.space/IMetadata",
            "defaultMethod()%2Fjava.lang%2FVoidType",
            37, 37, PUBLIC, true, false);
        CallAssertions.assertNode(artificialEdge, "source", sourceExpectation);
        CallAssertions.assertNode(artificialEdge, "target", targetExpectation);

    }

    @Test
    public void testExampleMetadataAbstractAndPackagePrivate()
        throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        WalaResultAnalyzer.wrap(metadataGraph, Metadata_PCG);

        final var artificialEdge =
            new CallTestData(Metadata_PCG, "abstractMethod", "packagePrivateMethod");
        final var sourceExpectation = new NodeExpectation("/name.space/AbstractClass",
            "abstractMethod()%2Fjava.lang%2FVoidType", -1, -1,
            PUBLIC, false, false);
        final var targetExpectation = new NodeExpectation("/name.space/MetadataExample",
            "packagePrivateMethod()%2Fjava.lang%2FVoidType",
            14, 14, PACKAGE_PRIVATE, true, false);

        CallAssertions.assertNode(artificialEdge, "source", sourceExpectation);
        CallAssertions.assertNode(artificialEdge, "target", targetExpectation);

    }

    @Test
    public void testExampleMetadataSuperClassAndInterfaces() {
        WalaResultAnalyzer.wrap(metadataGraph, Metadata_PCG);
        final var extendingClass = "/name.space/ExtendingClass";

        final var internalTypes = Metadata_PCG.getClassHierarchy().get(JavaScope.internalTypes);

        final var extendingClassType = internalTypes.get(extendingClass);
        final var actualSuperClassesOfExtendingClass = extendingClassType.getSuperClasses();
        final var actualSuperInterfacesOfExtendingClass = extendingClassType.getSuperInterfaces();

        final var implementingClass = internalTypes.get("/name.space/ImplementingClass");
        final var actualSuperClassesOfImplementingClass = implementingClass.getSuperClasses();
        final var actualSuperInterfacesOfImplementingClass = implementingClass.getSuperInterfaces();

        assertEquals(List.of(FastenURI.create("/name.space/MetadataExample"),
            FastenURI.create("/java.lang/Object")), actualSuperClassesOfExtendingClass);
        assertTrue(actualSuperInterfacesOfExtendingClass.isEmpty());
        assertEquals(
            List.of(FastenURI.create(extendingClass),
                FastenURI.create("/name.space/MetadataExample"),
                FastenURI.create("/java.lang/Object")), actualSuperClassesOfImplementingClass);
        assertEquals(List.of(FastenURI.create("/name.space/IMetadata")),
            actualSuperInterfacesOfImplementingClass);
    }

    @Disabled
    @Test
    public void testCallPreservationStrategy()
        throws ClassHierarchyException, IOException, CancelException {
        /**
         * Contains dynamic dispatch callSites.
         */
        final var callSites = getCallTestData(CallPreservationStrategy.ONLY_STATIC_CALLSITES);

        final var parentMethod =
            new NodeExpectation("/name.space/Parent", "method()%2Fjava.lang%2FVoidType", 18, 18,
                PUBLIC, false, false);
        final var callMethod =
            new NodeExpectation("/name.space/CallerClass", "callMethod()%2Fjava.lang%2FVoidType", 8,
                10, PUBLIC, true, false);

        assertEquals(6, CallP_PCG.getCallSites().size());
        CallAssertions.assertAllCallsExceptInterface(callSites, callMethod);

        CallAssertions.assertCall(callMethod, parentMethod, INTERFACE.label, 6, "[/name.space/Parent]",
            callSites.get("CallerClass.callMethod-Parent.method-9"), 9);
        CallAssertions.assertCall(callMethod, parentMethod, INTERFACE.label, 9, "[/name.space/Parent]",
            callSites.get("CallerClass.callMethod-Parent.method-23"), 23);

        final var calls = getCallTestData(CallPreservationStrategy.INCLUDING_ALL_SUBTYPES);

        assertEquals(7, CallP_PCG.getCallSites().size());
        assertTrue(calls.containsKey("CallerClass.callMethod-FirstChild.method"));
        assertTrue(calls.containsKey("CallerClass.callMethod-SecondChild.method"));
    }

    private Map<String, CallTestData> getCallTestData(
        final CallPreservationStrategy callPreservationStrategy)
        throws IOException, ClassHierarchyException, CancelException {
        var cpPath = new File(Thread.currentThread().getContextClassLoader()
            .getResource("CallPreservation.jar").getFile()).getAbsolutePath();
        callpreservation = CallGraphConstructor.generateCallGraph(cpPath, Algorithm.CHA);
        CallP_PCG = getEmptyPCGWithProductName("CallPreservation");
        WalaResultAnalyzer.wrap(callpreservation, CallP_PCG, callPreservationStrategy);
        final Map<String, CallTestData> calls = new HashMap<>();
        for (int i = 0; i <= CallP_PCG.getCallSites().size() - 1; i++) {
            final var call = new CallTestData(CallP_PCG, i);

            if (call.callMetadata().size() > 1) {
                for (final var pcMetadata : call.callMetadata().entrySet()) {
                    calls.put(getKey(call) + "-" + pcMetadata.getKey(), call);
                }
            } else {
                calls.put(getKey(call), call);
            }
        }
        return calls;
    }

    private String getKey(CallTestData call) {
        final var source = FastenUriUtils.parsePartialFastenUri(call.sourceUri());
        final var target = FastenUriUtils.parsePartialFastenUri(call.targetUri());
        return source.get(1) + "." + source.get(2) + "-" + target.get(1) + "." + target.get(2);
    }

    @Test
    public void toCanonicalJSONArrayExtensiveTest() {
        WalaResultAnalyzer.wrap(aegraph, ARRAY_EXTENSIVE_PCG);

        List<String> listOfMethodNames = new ArrayList<>();
        listOfMethodNames.add("short");
        listOfMethodNames.add("integer");
        listOfMethodNames.add("int");
        listOfMethodNames.add("object");
        listOfMethodNames.add("bool");
        listOfMethodNames.add("long");
        listOfMethodNames.add("double");
        listOfMethodNames.add("float");
        listOfMethodNames.add("char");
        listOfMethodNames.add("byte");

        List<String> listOfMethodTypes = new ArrayList<>();
        listOfMethodTypes.add("ShortType");
        listOfMethodTypes.add("Integer");
        listOfMethodTypes.add("IntegerType");
        listOfMethodTypes.add("Object");
        listOfMethodTypes.add("BooleanType");
        listOfMethodTypes.add("LongType");
        listOfMethodTypes.add("DoubleType");
        listOfMethodTypes.add("FloatType");
        listOfMethodTypes.add("CharacterType");
        listOfMethodTypes.add("ByteType");

        var methods =
            ARRAY_EXTENSIVE_PCG.getClassHierarchy().get(JavaScope.internalTypes).values().iterator()
                .next().getMethods();
        for (int pos = 0; pos < listOfMethodNames.size(); pos++) {
            var method = methods.get(pos + 1).toString();

            if (method.contains(listOfMethodNames.get(pos))) {
                assertTrue(method.contains(
                    "%2Fjava.lang%2F" + listOfMethodTypes.get(pos) + "%25255B%25255D"));
            }
        }
    }

    @Test
    public void equalsTest() {
        final var analysisContext = new AnalysisContext(ssttgraph.getClassHierarchy());

        List<Method> methods = new ArrayList<>();

        for (final CGNode node : ssttgraph) {
            final var nodeReference = node.getMethod().getReference();
            methods.add(analysisContext.findOrCreate(nodeReference));
        }


        final var refMethod = methods.get(3);
        final var methodSameNamespaceDiffSymbol = methods.get(5);
        final var methodDiffNamespaceDiffSymbol = methods.get(0);
        final var methodDiffNamespaceSameSymbol = methods.get(7);
        final var methodSameReference = new ExternalMethod(refMethod.getReference());


        assertEquals(refMethod, refMethod);
        assertEquals(refMethod, methodSameReference);
        assertNotEquals(refMethod, null);
        assertNotEquals(refMethod, new Object());
        assertNotEquals(refMethod, methodSameNamespaceDiffSymbol);
        assertNotEquals(refMethod, methodDiffNamespaceDiffSymbol);
        assertNotEquals(refMethod, methodDiffNamespaceSameSymbol);
    }


}
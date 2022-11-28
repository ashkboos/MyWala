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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.CancelException;
import eu.fasten.analyzer.javacgwala.data.callgraph.analyzer.WalaResultAnalyzer;
import eu.fasten.analyzer.javacgwala.data.core.CallType;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.PartialJavaCallGraph;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WalaResultAnalyzerTest {

    private static PartialJavaCallGraph PCG;
    private static CallGraph graph;

    @BeforeAll
    static void setUp() throws ClassHierarchyException, CancelException, IOException {
        PCG = PartialCallGraphGenerator.generateEmptyPCG(Constants.mvnForge, "SingleSourceToTarget",
            "0.0.0",
            -1, Constants.walaGenerator);
        Algorithm ALG = Algorithm.CHA;
        var path = new File(Thread.currentThread().getContextClassLoader()
            .getResource("SingleSourceToTarget.jar").getFile()).getAbsolutePath();

        graph = CallGraphConstructor.generateCallGraph(path, ALG);
    }

    @Test
    void wrap() {
        WalaResultAnalyzer.wrap(graph, PCG);

        assertEquals(2, PCG.getGraph().getCallSites().size());

        var source =
            "/name.space/SingleSourceToTarget.%3Cinit%3E()%2Fjava.lang%2FVoidType";
        var target = "/java.lang/Object.%3Cinit%3E()VoidType";

        final var metadataIterator = PCG.getGraph().getCallSites().values().iterator();
        var callMetadata = metadataIterator.next();
        final var edgeIterator = PCG.getGraph().getCallSites().keySet().iterator();
        var callValues = edgeIterator.next();
        final var methods = PCG.mapOfAllMethods();
        final var metadata = (Map<?, ?>) callMetadata.get(1);

        assertEquals(source, methods.get(callValues.firstLong()).getUri().toString());
        assertEquals(target, methods.get(callValues.secondLong()).getUri().toString());
        assertEquals(CallType.SPECIAL.label, metadata.get(Constants.INVOCATION_TYPE));
        assertEquals("[/java.lang/Object]", metadata.get(Constants.RECEIVER_TYPE));

        source = "/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoidType";
        target = "/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoidType";
        var resolvedCall = edgeIterator.next();

        assertEquals(source, methods.get(resolvedCall.firstLong()).getUri().toString());
        assertEquals(target, methods.get(resolvedCall.secondLong()).getUri().toString());
    }

    @Test
    public void test() {
        final var all = Arrays.asList(63594L, 548, 297, 13891976, 3334639, 6961, 2656, 15349, 346,
            323,
            2536, 1151, 4721, 404, 1973, 3934, 7431, 18245, 37291, 342, 12881, 9805, 24423, 65142,
            5454, 297,
            11536757, 43626, 12468, 881, 826572, 6252, 6396, 18728, 4984, 334, 4677, 8021427,
            113192, 839, 1956
            , 18293, 4994, 39849, 52757, 7371, 928, 5833, 861, 15888, 848038, 89884, 5054, 15794,
            7494, 326, 589, 8369, 644, 14547, 5822, 5145, 6667761, 3566, 3242, 249877, 2041, 566,
            3213, 328, 10045, 110597, 4465, 2014, 286, 37296, 14505, 68349, 4609, 9033,
            5372, 3364, 296, 4384, 929, 1468, 12936, 13206, 5399, 151941, 3171, 3499, 5419, 8866,
            1023, 6865432, 8441, 10795, 15974, 378, 438, 18236, 88514, 29628, 882492,
            2585, 2441, 10223, 103426, 568, 516, 5182, 3247, 45091, 826, 2791, 291, 7595,
            2810174, 4211, 77661, 62338, 162836, 29927, 299, 1141, 1688, 1594, 4543,
            77055, 21186, 481, 3601, 517, 124373, 10156, 208999, 695, 278, 12663, 998, 2199, 140649,
            11027, 6573111, 80683905, 3101, 24287, 9723, 571, 2583, 58615208, 1153, 5194, 1315,
            42699, 169183, 4823, 7954, 3339, 26438, 564, 1876, 398, 2293, 20329, 15368, 3608,
            18694425, 2883, 5612, 2355, 13762, 16131, 2216, 10557, 9269291, 10081,
            436, 23788, 2496, 11557, 27733, 8578, 1502, 997, 125277, 1785,
            63378, 1378, 15503063, 11918, 2849, 10866, 67961, 73823, 9245, 20254744, 6218, 17501,
            992, 39412, 19173, 133872, 31094, 501, 3052, 2376, 1757, 482, 2996, 38052, 29999462,
            309, 21173, 6576, 21491, 1932, 1058, 5091, 18686, 70795, 12019, 14615031, 334204, 2963,
            11127, 5825, 1669, 1572, 2241, 3988, 2714, 4461, 858, 49864353, 1382, 11839, 884, 8118,
            131864, 22324, 4749, 43011, 7414, 2683, 606, 10069, 1128, 2624, 96491,
            283, 4276, 2934, 1389, 9239, 998, 41086, 4321, 8297, 713, 26492, 2495, 3732, 3652,
            5915, 22052, 15217, 8673, 19636, 1115, 15333, 29, 194498, 3103, 1319, 3317, 5219, 24706,
            678, 558, 7377, 4889, 110771, 314, 9083, 15686, 20572, 3403, 19281,
            2334, 2953, 7956, 17312, 16712695, 724, 1218, 1567, 17634, 24375, 4922, 881,
            54012, 5073277, 2062, 9423, 5761, 8244, 22757, 8751,
            19744, 3039457, 5402, 77027, 1855, 28036, 5743, 15608, 4509, 5647, 8563, 15661, 838,
            3324044, 265, 11038, 15092, 475, 16421292, 10738, 5370918, 3179, 41251743, 15087, 1696,
            3538, 8094, 2038, 14485, 8091, 3276, 649, 4612097, 30361, 8372, 3252, 3417, 10535, 9841,
            7102, 12904, 15529, 15177, 811, 7222, 11509, 281, 2282912, 28308, 481, 4474, 5118, 329,
            133755, 242, 789, 22136805, 2483, 46314, 5469, 11267, 2171, 525, 56392, 12237, 868,
            8032, 12106, 3009, 9066, 236, 3931, 1319, 3907, 40349, 11412, 6042, 1118,
            3148, 42475, 1863, 5914, 4392, 585, 523074, 30899, 12771, 237236, 11966, 797, 213466,
            12382, 836, 376, 604, 1382, 3123, 20584, 669, 4357, 14215, 2186, 13399, 1132, 11108,
            1085, 5274, 8827, 47435, 51158, 69107165, 13509, 10563, 606, 15211521, 8404, 26715,
            3622, 7692, 11614, 9315, 7203, 16579, 3808, 56231, 135769, 96207, 487, 14869, 1412,
            2356, 12623, 3866, 84088, 3285, 4142, 2368, 8194, 1764, 9394, 9815, 37217, 73904,
            6302, 548, 16406, 64483, 3071, 686, 1809, 2308, 8757, 791, 3347, 2816, 321,
            22717, 16092, 7431, 1267, 49761, 887, 11986, 19696, 3310817, 15853, 9674, 923,
            104247, 42628, 18011, 3508, 1522, 1411, 833, 6355);


        final var min = all.stream().mapToLong(Number::longValue).min().orElse(0);
        final var max = all.stream().mapToLong(Number::longValue).max().orElse(0);
        final var percentile = (max - min) / 100;
        final var nfPercentile = min + (95 * percentile);
        System.out.printf(String.valueOf(min), String.valueOf(max), String.valueOf(percentile),
            String.valueOf(nfPercentile));
    }
}
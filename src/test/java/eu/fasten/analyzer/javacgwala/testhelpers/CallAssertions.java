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
package eu.fasten.analyzer.javacgwala.testhelpers;

import static eu.fasten.analyzer.javacgwala.data.core.CallType.SPECIAL;
import static eu.fasten.core.data.Constants.PUBLIC;
import static org.junit.jupiter.api.Assertions.assertEquals;

import eu.fasten.analyzer.javacgwala.data.core.CallType;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public class CallAssertions {
    public static void assertCall(NodeExpectation sourceExpectation, NodeExpectation targetExpectation,
                                  String expectedCallType, int expectedLine, String expectedReceiver,
                                  CallTestData actualCall, int pc) {
        assertEquals(sourceExpectation.uri(), actualCall.sourceUri());
        assertEquals(targetExpectation.uri(), actualCall.targetUri());
        assertCallSiteMetadata(actualCall, expectedCallType, expectedLine, expectedReceiver, pc);
    }

    public static void assertNode(CallTestData call, String sourceOrTarget, NodeExpectation expectation)
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        assertEquals(expectation.decodedSignature(),
            invokeMethod(call, sourceOrTarget, "Signature"));
        assertEquals(expectation.firstLine, invokeMethod(call, sourceOrTarget, "FirstLine"));
        assertEquals(expectation.lastLine, invokeMethod(call, sourceOrTarget, "LastLine"));
        assertEquals(expectation.accessModifier,
            invokeMethod(call, sourceOrTarget, "AccessModifier"));
        assertEquals(expectation.isDefined, invokeMethod(call, sourceOrTarget, "IsDefined"));
        assertEquals(expectation.isWalaSynthetic,
            invokeMethod(call, sourceOrTarget, "IsWalaSynthetic"));
    }

    static Object invokeMethod(CallTestData call, String sourceOrTarget, String methodName)
        throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        return CallTestData.class.getMethod(sourceOrTarget + methodName).invoke(call);
    }

    public static void assertCallSiteMetadata(CallTestData call, String expectecCallType,
                                              int expectedLine, String expectedReceiver, int pc) {
        assertEquals(expectecCallType, call.invocation(pc));
        assertEquals(expectedLine, call.line(pc));
        assertEquals(expectedReceiver, call.receiver(pc));
    }

    public static void assertAllCallsExceptInterface(Map<String, CallTestData> calls,
                                                     NodeExpectation callMethod) {
        final var callerInit = new NodeExpectation("/name.space/CallerClass",
            "%3Cinit%3E()%2Fjava.lang%2FVoidType", 8, 10,
            PUBLIC, true, false);
        final var firstChildInit = new NodeExpectation("/name.space/FirstChild",
            "%3Cinit%3E()%2Fjava.lang%2FVoidType", 21, 21, PUBLIC, true, false);
        final var secondChildInit = new NodeExpectation("/name.space/SecondChild",
            "%3Cinit%3E()%2Fjava.lang%2FVoidType", 27, 27, PUBLIC, true, true);
        final var objectInit = new NodeExpectation("/java.lang/Object",
            "%3Cinit%3E()VoidType", 13, 13, PUBLIC, true, false);

        assertCall(callMethod, firstChildInit, SPECIAL.label, 5,
            "[/name.space/FirstChild]", calls.get("CallerClass.callMethod-FirstChild.<init>"), 4);
        assertCall(callMethod, secondChildInit, SPECIAL.label, 8,
            "[/name.space/SecondChild]", calls.get("CallerClass.callMethod-SecondChild.<init>"), 18);
        assertCall(firstChildInit, objectInit, SPECIAL.label, 21,
            "[/java.lang/Object]", calls.get("FirstChild.<init>-Object.<init>"), 1);
        assertCall(callerInit, objectInit, SPECIAL.label, 3,
            "[/java.lang/Object]", calls.get("CallerClass.<init>-Object.<init>"), 1);
        assertCall(secondChildInit, objectInit, SPECIAL.label, 27,
            "[/java.lang/Object]", calls.get("SecondChild.<init>-Object.<init>"), 1);
    }
}

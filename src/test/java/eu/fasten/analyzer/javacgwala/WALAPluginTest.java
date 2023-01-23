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

package eu.fasten.analyzer.javacgwala;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.fasten.analyzer.javacgwala.core.MavenCoordinate;
import eu.fasten.analyzer.javacgwala.data.callgraph.Algorithm;
import eu.fasten.analyzer.javacgwala.data.callgraph.PartialCallGraphGenerator;
import eu.fasten.core.data.CallPreservationStrategy;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.JavaType;
import eu.fasten.core.data.PartialJavaCallGraph;
import eu.fasten.core.data.callableindex.SourceCallSites;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WALAPluginTest {

    public static final CallPreservationStrategy STRATEGY =
        CallPreservationStrategy.ONLY_STATIC_CALLSITES;
    public static Algorithm ALG;
    static WALAPlugin.WALA walaPlugin;

    @BeforeAll
    public static void setUp() {
        ALG = Algorithm.CHA;
        walaPlugin = new WALAPlugin.WALA();
    }

    @Test
    public void testConsumerTopic() {
        assertTrue(walaPlugin.consumeTopic().isPresent());
        assertEquals("fasten.maven.pkg", walaPlugin.consumeTopic().get().get(0));
    }

    @Test
    public void testSetTopic() {
        String topicName = "fasten.mvn.pkg";
        walaPlugin.setTopic(topicName);
        assertTrue(walaPlugin.consumeTopic().isPresent());
        assertEquals(topicName, walaPlugin.consumeTopic().get().get(0));
    }

    @Test
    public void testConsume()
        throws JSONException {

        JSONObject coordinateJSON = new JSONObject("{\n" +
            "    \"groupId\": \"org.slf4j\",\n" +
            "    \"artifactId\": \"slf4j-api\",\n" +
            "    \"version\": \"1.7.29\",\n" +
            "    \"date\":\"1574072773\"\n" +
            "}");

        walaPlugin.consume(coordinateJSON.toString());

        final PartialJavaCallGraph expected =
            generateForCoordinate("org.slf4j", "slf4j-api", "1.7.29");

        assertTrue(walaPlugin.produce().isPresent());
        final var actual = new PartialJavaCallGraph(new JSONObject(walaPlugin.produce().get()));
        pcgAssert(expected, actual);
    }

    private PartialJavaCallGraph generateForCoordinate(final String groupID,
                                                       final String artifactID,
                                                       final String version) {
        var coordinate = new MavenCoordinate(groupID, artifactID, version);
        final var PCG =
            PartialCallGraphGenerator.generateEmptyPCG(Constants.mvnForge, coordinate.getProduct(),
                coordinate.getVersionConstraint(), -1, Constants.walaGenerator);
        PartialCallGraphGenerator.generateFromCoordinate(coordinate, ALG, PCG, STRATEGY);
        return PCG;
    }

    @Test
    public void testDefinedMethodNum(){
        final var pcg = generateForCoordinate("org.springframework", "spring-expression", "4.1.7.RELEASE");
        int size = 0;
        for (JavaType type : pcg.getClassHierarchy().get(JavaScope.internalTypes).values()) {
            size += type.getDefinedMethods().size();
        }
        assertEquals(920, size);
    }

    @Test
    public void testNullSig(){
        final var pcg = generateForCoordinate("io.netty","netty-all","4.1.53.Final");
        pcg.setSourceCallSites();
    }

    @Test
    public void testEmptyCallGraph() {
        JSONObject emptyCGCoordinate = new JSONObject("{\n" +
            "    \"groupId\": \"activemq\",\n" +
            "    \"artifactId\": \"activemq\",\n" +
            "    \"version\": \"release-1.5\",\n" +
            "    \"date\":\"1574072773\"\n" +
            "}");

        walaPlugin.consume(emptyCGCoordinate.toString());

        assertTrue(walaPlugin.produce().isEmpty());
    }

    @Test
    public void testFileNotFoundException() {
        JSONObject noJARFile = new JSONObject("{\n" +
            "    \"groupId\": \"com.visionarts\",\n" +
            "    \"artifactId\": \"power-jambda-pom\",\n" +
            "    \"version\": \"0.9.10\",\n" +
            "    \"date\":\"1521511260\"\n" +
            "}");

        walaPlugin.consume(noJARFile.toString());
        var error = walaPlugin.getPluginError();

        assertFalse(walaPlugin.produce().isPresent());
        assertEquals(FileNotFoundException.class.getSimpleName(),
            error.getCause().getClass().getSimpleName());
    }

    @Test
    public void testShouldNotFaceClassReadingError()
        throws JSONException {

        JSONObject coordinateJSON1 = new JSONObject("{\n" +
            "    \"groupId\": \"com.zarbosoft\",\n" +
            "    \"artifactId\": \"coroutines-core\",\n" +
            "    \"version\": \"0.0.3\",\n" +
            "    \"date\":\"1574072773\"\n" +
            "}");

        walaPlugin.consume(coordinateJSON1.toString());
        final var expected = generateForCoordinate("com.zarbosoft", "coroutines-core", "0.0.3");

        assertTrue(walaPlugin.produce().isPresent());
        final var actual = new PartialJavaCallGraph(new JSONObject(walaPlugin.produce().get()));
        pcgAssert(expected, actual);
    }

    @Test
    public void testName() {
        assertEquals("eu.fasten.analyzer.javacgwala.WALAPlugin.WALA", walaPlugin.name());
    }

    @Test
    public void sendToKafkaTest() {
        KafkaProducer<Object, String> producer = Mockito.mock(KafkaProducer.class);

        Mockito.when(producer.send(Mockito.any())).thenReturn(null);

        JSONObject coordinateJSON = new JSONObject("{\n" +
            "    \"groupId\": \"org.slf4j\",\n" +
            "    \"artifactId\": \"slf4j-api\",\n" +
            "    \"version\": \"1.7.29\",\n" +
            "    \"date\":\"1574072773\"\n" +
            "}");

        walaPlugin.consume(coordinateJSON.toString());

        final var expected = generateForCoordinate("org.slf4j", "slf4j-api", "1.7.29");

        assertTrue(walaPlugin.produce().isPresent());

        final var actual = new PartialJavaCallGraph(new JSONObject(walaPlugin.produce().get()));
        pcgAssert(expected, actual);
    }

    private void pcgAssert(PartialJavaCallGraph expected, PartialJavaCallGraph actual) {
        assertEquals(expected.getCallSites().size(), actual.getCallSites().size());
        assertEquals(expected.getClassHierarchy().get(JavaScope.internalTypes).size(),
            actual.getClassHierarchy().get(JavaScope.internalTypes).size());
        assertEquals(expected.getClassHierarchy().get(JavaScope.externalTypes).size(),
            actual.getClassHierarchy().get(JavaScope.externalTypes).size());
        assertEquals(expected.getClassHierarchy().get(JavaScope.resolvedTypes).size(),
            actual.getClassHierarchy().get(JavaScope.resolvedTypes).size());
    }

}
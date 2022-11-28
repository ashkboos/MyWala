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

import eu.fasten.analyzer.javacgwala.core.MavenCoordinate;
import eu.fasten.analyzer.javacgwala.data.callgraph.Algorithm;
import eu.fasten.analyzer.javacgwala.data.callgraph.PartialCallGraphGenerator;
import eu.fasten.core.data.CallPreservationStrategy;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.JSONUtils;
import eu.fasten.core.data.JavaNode;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.PartialJavaCallGraph;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

/**
 * Makes javacg-wala module runnable from command line.
 */
@CommandLine.Command(name = "JavaCGWala")
public class Main implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    public static final CallPreservationStrategy STRATEGY = CallPreservationStrategy.INCLUDING_ALL_SUBTYPES;

    @CommandLine.ArgGroup()
    SetRunner setRunner;

    @CommandLine.Option(names = {"-r"},
        paramLabel = "REPOS",
        description = "Maven repositories",
        split = ",")
    List<String> repos;

    @CommandLine.Option(names = {"-t", "--timestamp"},
        paramLabel = "TS",
        description = "Release TS",
        defaultValue = "0")
    String timestamp;

    @CommandLine.Option(names = {"-o", "--output"},
        paramLabel = "OUT",
        description = "Output path")
    String outputPath;

    @CommandLine.Option(names = {"--stdout"},
        paramLabel = "STDOUT",
        description = "Write to stdout")
    boolean writeToStdout;

    @CommandLine.Option(names = {"--cgAlg"},
        paramLabel = "CGALG",
        description = "Call graph generation algorithm {CHA, RTA}", defaultValue = "CHA")
    String algorithm;

    static class Input {
        @CommandLine.Option(names = {"-f", "--path"},
            paramLabel = "PATH",
            description = "Path to file")
        String path;

        @CommandLine.Option(names = {"-c", "--coord"},
            paramLabel = "COORD",
            description = "Maven coordinates string",
            required = true)
        String mavenCoordStr;

    }

    static class SetRunner {

        @CommandLine.ArgGroup(exclusive = true)
        Input input;

        @CommandLine.Option(names = {"-s", "--set"},
            paramLabel = "Set",
            description = "Set of maven coordinates",
            required = true)
        String set;
    }

    /**
     * Generates RevisionCallGraphs using Opal for the specified artifact in the command line
     * parameters.
     */
    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }

    /**
     * Runs Wala plugin.
     */
    public void run() {
        MavenCoordinate mavenCoordinate;
        final var pcg = getEmptyPCG();
        if (setRunner != null && setRunner.input != null && setRunner.input.path != null ) {

            PartialCallGraphGenerator.generateFromFile(setRunner.input.path,
                Algorithm.valueOf(algorithm), pcg, STRATEGY);

            try {
                writeCallgraph(pcg);
            } catch (IOException e) {
                logger.info("Couldn't write to the file");
            }

        } else if (setRunner != null && setRunner.set != null) {
            consumeSet(setRunner.set);
        } else if (setRunner != null && setRunner.input.mavenCoordStr != null) {
            mavenCoordinate = MavenCoordinate
                .fromString(this.setRunner.input.mavenCoordStr);
            if (repos != null && repos.size() > 0) {
                mavenCoordinate.setMavenRepos(repos);
            }

            try {
                PartialCallGraphGenerator.generateFromCoordinate(mavenCoordinate,
                    Algorithm.valueOf(algorithm), pcg, STRATEGY);
                try {
                    writeCallgraph(pcg);
                } catch (IOException e) {
                    logger.info("Couldn't write to the file");
                }

            } catch (Throwable e) {
                logger.error("Failed to generate a call graph for Maven coordinate: {}, Error: {}",
                    mavenCoordinate.getCoordinate(), e);
            }
        }
        Long2ObjectMap<JavaNode> result = new Long2ObjectOpenHashMap<>();
        for (final var entry : pcg.getClassHierarchy()
            .get(JavaScope.externalTypes).entrySet()) {
            try {
                result.putAll(entry.getValue().getMethods());
            }
            catch (Exception e){
                System.out.println();
            }
            var klass = entry;

        }

    }

    private PartialJavaCallGraph getEmptyPCG() {
        return PartialCallGraphGenerator.generateEmptyPCG(Constants.mvnForge,
            setRunner.input.mavenCoordStr, Long.parseLong(timestamp), Constants.walaGenerator);
    }

    /**
     * Consume a set of maven coordinates and generate call graphs for them.
     *
     * @param path Path to the file containing maven coordinates.
     */
    private void consumeSet(String path) {
        List<String> successfulRecords = new ArrayList<>();
        Map<String, String> failedRecords = new HashMap<>();
        Map<String, Integer> errorOccurrences = new HashMap<>();

        for (var coordinate : getCoordinates(path)) {
            final var mavenCoordinate = getMavenCoordinate(coordinate);
            if (mavenCoordinate != null && repos != null && repos.size() > 0) {
                mavenCoordinate.setMavenRepos(repos);
            }
            if (mavenCoordinate == null) {
                continue;
            }
            try {
                final var pcg = getEmptyPCG();
                PartialCallGraphGenerator.generateFromCoordinate(mavenCoordinate,
                    Algorithm.valueOf(algorithm), pcg, STRATEGY);


                successfulRecords.add("Number of calls: " + pcg.getGraph().getCallSites().size()
                    + " COORDINATE: " + mavenCoordinate.getCoordinate());

                logger.info("Call graph successfully generated for {}!",
                    mavenCoordinate.getCoordinate());

                writeCallgraph(pcg);

            } catch (IOException e) {
                logger.info("Couldn't write to the file");
            } catch (Throwable e) {
                JSONObject error = new JSONObject().put("plugin", this.getClass().getSimpleName())
                    .put("msg", e.getMessage())
                    .put("trace", e.getStackTrace())
                    .put("type", e.getClass().getSimpleName());

                String errorType = error.get("type").toString();

                if (errorOccurrences.containsKey(errorType)) {
                    errorOccurrences.put(errorType, errorOccurrences.get(errorType) + 1);
                } else {
                    errorOccurrences.put(errorType, 1);
                }
            }
        }

        printStats(successfulRecords, failedRecords, errorOccurrences);
    }

    /**
     * Print statistics of call graph generation.
     *
     * @param successfulRecords Records that were successfully processed
     * @param failedRecords     Failed records
     * @param errorOccurrences  Map of error and number of their occurrences
     */
    private void printStats(List<String> successfulRecords, Map<String, String> failedRecords,
                            Map<String, Integer> errorOccurrences) {
        for (var record : successfulRecords) {
            System.out.println(record);
        }

        for (var record : failedRecords.entrySet()) {
            System.out.println(record.getKey() + " ERROR: " + record.getValue());
        }

        int total = successfulRecords.size() + failedRecords.size();

        System.out.println();
        System.out.println("===================SUMMARY=================");
        System.out.println("Total number of analyzed coordinates: \t" + total);
        System.out.println("Total number of successful: \t\t\t" + successfulRecords.size());
        System.out.println("Total number of failed: \t\t\t\t" + failedRecords.size());
        System.out.println("Most common exceptions: ");

        var sortedErrorMap = errorOccurrences.entrySet()
            .stream()
            .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
            .collect(Collectors.toMap(Map.Entry::getKey,
                Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        for (var entry : sortedErrorMap.entrySet()) {
            System.out.println("\t [" + entry.getKey() + " - " + entry.getValue() + "]");
        }

        if (total > 0) {
            System.out.println("Success rate: \t\t\t\t\t\t\t"
                + 100 * successfulRecords.size() / total + "%");
        }
    }

    /**
     * Parse JSON representation of MavenCoordinate.
     *
     * @param kafkaConsumedJson Json maven coordinate
     * @return MavenCoordinate
     */
    private MavenCoordinate getMavenCoordinate(final JSONObject kafkaConsumedJson) {

        try {
            return new MavenCoordinate(
                kafkaConsumedJson.get("groupId").toString(),
                kafkaConsumedJson.get("artifactId").toString(),
                kafkaConsumedJson.get("version").toString());
        } catch (JSONException e) {
            logger.error("Could not parse input coordinate: {}", kafkaConsumedJson);
        }
        return null;
    }

    /**
     * Process the file containing maven coordinates and convert it to list of JSON maven
     * coordinates.
     *
     * @param path Path to the file
     * @return List of Json objects
     */
    private List<JSONObject> getCoordinates(String path) {
        try {
            File file = new File(path);
            BufferedReader br = new BufferedReader(new FileReader(file));
            return br.lines()
                .map(JSONObject::new)
                .collect(Collectors.toList());


        } catch (IOException | StringIndexOutOfBoundsException e) {
            logger.error("Couldn't parse a file with coordinates");
        }

        return new ArrayList<>();
    }

    /**
     * Writes a callgraph to a specified path.
     *
     * @param graph a callgraph to write to file
     * @throws IOException cannot write to a file
     */
    private void writeCallgraph(final PartialJavaCallGraph graph) throws IOException {
        if (this.outputPath != null) {
            final BufferedWriter writer = new BufferedWriter(
                new FileWriter(outputPath + "/" + graph.product + "-v" + graph.version + ".json"));
            writer.write(JSONUtils.toJSONString(graph));
            writer.close();
            logger.info("Successfully written the call graph into a file");
        }
        if (writeToStdout) {
            System.out.println(graph.toJSON().toString());
        }
    }
}



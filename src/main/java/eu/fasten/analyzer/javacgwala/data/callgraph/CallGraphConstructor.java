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

import com.ibm.wala.classLoader.Language;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.cha.CHACallGraph;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.config.AnalysisScopeReader;
import eu.fasten.analyzer.javacgwala.core.MavenCoordinate;
import eu.fasten.analyzer.javacgwala.data.callgraph.analyzer.WalaResultAnalyzer;
import eu.fasten.core.data.CallPreservationStrategy;
import eu.fasten.core.data.PartialJavaCallGraph;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CallGraphConstructor {

    private static Logger logger = LoggerFactory.getLogger(CallGraphConstructor.class);

    /**
     * Build a {@link PartialJavaCallGraph} given classpath.
     *
     * @param strategy   call preservation strategy
     * @param coordinate Coordinate
     * @param algorithm  call graph generation algorithm
     */
    public static void build(final MavenCoordinate coordinate,
                             final Algorithm algorithm,
                             final PartialJavaCallGraph result,
                             final CallPreservationStrategy strategy)
        throws IOException, ClassHierarchyException, CancelException {
        final NumberFormat timeFormatter = new DecimalFormat("#0.000");
        logger.info("Generating call graph for the Maven coordinate using WALA: {}",
            coordinate.getCoordinate());
        final long startTime = System.currentTimeMillis();

        final var rawGraph = generateCallGraph(MavenCoordinate.MavenResolver
            .downloadJar(coordinate).orElseThrow(RuntimeException::new)
            .getAbsolutePath(), algorithm);

        logger.info("Generated the call graph in {} seconds.",
            timeFormatter.format((System.currentTimeMillis() - startTime) / 1000d));
        WalaResultAnalyzer.wrap(rawGraph, result, strategy);
    }

    /**
     * Create a call graph instance given a class path.
     *
     * @param classpath Path to class or jar file
     * @return Call Graph
     */
    public static CallGraph generateCallGraph(String classpath, Algorithm alg)
        throws IOException, ClassHierarchyException, CancelException {
        final var classLoader = Thread.currentThread().getContextClassLoader();
        final var exclusionFile = new File(Objects.requireNonNull(classLoader
            .getResource("Java60RegressionExclusions.txt")).getFile());

        classpath = correctFileNameIfWrong(classpath);

        final var scope = AnalysisScopeReader
            .makeJavaBinaryAnalysisScope(classpath, exclusionFile);

        final var ch = ClassHierarchyFactory.makeWithRoot(scope);

        final var entryPointsGenerator = new EntryPointsGenerator(ch);
        final var entryPoints = entryPointsGenerator.getEntryPoints();
        CallGraph cg = null;
        switch (alg) {
            case CHA:
                cg = new CHACallGraph(ch);
                ((CHACallGraph) cg).init(entryPoints);
                break;
            case RTA:
                final var options = new AnalysisOptions(scope, entryPoints);
                final var cache = new AnalysisCacheImpl();
                final var builder =
                    Util.makeZeroCFABuilder(Language.JAVA, options, cache, ch, scope);
                cg = builder.makeCallGraph(options, null);
                break;
        }

        return cg;
    }


    public static String correctFileNameIfWrong(String classpath) throws IOException {
        if (!classpath.endsWith(".jar")) {
            return classpath;
        }
        final var jarFile = new JarFile(classpath);
        if (jarFile.getManifest() == null) {
            return classpath;
        }
        String cp = jarFile.getManifest().getMainAttributes().getValue("Class-Path");
        if (cp == null) {
            return classpath;
        }
        final var cpInManifest = jarFile.getManifest().getMainAttributes().getValue("Class-Path");

        if (cpInManifest.endsWith(".jar")) {
            classpath = classpath.replace(".jar", "");
            extractJar(jarFile, classpath);
        }
        return classpath;
    }

    public static void extractJar(final JarFile jar, final String destDirPath) {
        new File(destDirPath).mkdir();

        jar.stream().forEach(jarEntry -> {
            if (jarEntry.getName().endsWith(".MF")) {
                return;
            }
            try {
                java.io.File f =
                    new java.io.File(destDirPath + java.io.File.separator + jarEntry.getName());
                if (jarEntry.isDirectory()) { // if its a directory, create it
                    f.mkdir();
                    return;
                }
                java.io.InputStream is =
                    jar.getInputStream(jarEntry); // get the input stream
                java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
                while (is.available() > 0) {  // write contents of 'is' to 'fos'
                    fos.write(is.read());
                }
                fos.close();
                is.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        });
    }
}

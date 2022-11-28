package eu.fasten.analyzer.javacgwala.data.callgraph;

import static eu.fasten.analyzer.javacgwala.data.callgraph.CallGraphConstructor.correctFileNameIfWrong;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.strings.Atom;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class EntryPointsGeneratorTest {

    @Test
    void testGetEntryPoints() throws IOException, ClassHierarchyException {
        final var exclusionFile = new File("src/main/resources/Java60RegressionExclusions.txt");
        var classpath = new File(Thread.currentThread().getContextClassLoader()
            .getResource("MissingNode.jar").getFile()).getAbsolutePath();
        classpath = correctFileNameIfWrong(classpath);

        final var scope = AnalysisScopeReader
            .makeJavaBinaryAnalysisScope(classpath, exclusionFile);

        final var ch = ClassHierarchyFactory.makeWithRoot(scope);

        final var entryPointsGenerator = new EntryPointsGenerator(ch);
        final var entryPoints = entryPointsGenerator.getEntryPoints();
        assertTrue(entryPoints.stream()
            .anyMatch(entrypoint -> entrypoint.getMethod().getName().toString().contains(
                "attemptUnmanagePooledDataSource")));
    }

}
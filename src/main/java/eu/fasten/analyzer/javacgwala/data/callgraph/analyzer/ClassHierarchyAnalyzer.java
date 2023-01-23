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

import static java.util.Collections.emptyMap;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import eu.fasten.analyzer.javacgwala.data.core.InternalMethod;
import eu.fasten.analyzer.javacgwala.data.core.Method;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.JavaNode;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.JavaType;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ClassHierarchyAnalyzer {

    private final CallGraph rawCallGraph;
    private final AnalysisContext analysisContext;
    private int counter;
    public final Map<JavaScope, Map<String, JavaType>> classHierarchy;
    public Set<Integer> methodsWithMetadata;
    public Map<Integer, Integer> methodKeys;
    /**
     * Construct class hierarchy analyzer.
     *
     * @param rawCallGraph Call graph in Wala format
     */
    public ClassHierarchyAnalyzer(final CallGraph rawCallGraph,
                                  final AnalysisContext analysisContext) {
        this.rawCallGraph = rawCallGraph;
        this.analysisContext = analysisContext;
        this.counter = -1;
        this.classHierarchy = new ConcurrentHashMap<>();
        this.classHierarchy.put(JavaScope.internalTypes, new ConcurrentHashMap<>());
        this.classHierarchy.put(JavaScope.externalTypes, new ConcurrentHashMap<>());
        this.classHierarchy.put(JavaScope.resolvedTypes, new ConcurrentHashMap<>());
        this.methodsWithMetadata = ConcurrentHashMap.newKeySet();
        this.methodKeys = new ConcurrentHashMap<>();
    }

    /**
     * Add all classes in application scope to class hierarchy.
     */
    public void resolveCHA() throws NullPointerException {
        IClassLoader classLoader = rawCallGraph.getClassHierarchy()
            .getLoader(ClassLoaderReference.Application);
        for (Iterator<IClass> it = classLoader.iterateAllClasses(); it.hasNext(); ) {
            IClass klass = it.next();
            processClass(klass);
        }
    }

    /**
     * Adds new method to internal class hierarchy.
     * In case class in which given method is defined already exists in CHA -
     * method is being appended to the list of methods of this class,
     * otherwise a new class is created.
     *
     * @param method   Method to add
     * @param klassRef Class reference
     * @param scope    scope of class hierarchy
     */
    public synchronized int addMethodToScope(final Method method, final TypeReference klassRef,
                                final JavaScope scope) {
        final var klass = this.rawCallGraph.getClassHierarchy().lookupClass(klassRef);

        final var typeMap = classHierarchy.get(scope);
        final var classURI = getClassURI(method);

        if (!typeMap.containsKey(classURI)) {
            if (klass != null) {
                addClassToCHA(klass, scope, classURI);
            } else {
                addClassRefToCHA(scope, classURI);
            }
        }
        final var javaTypeOfKlass = typeMap.get(classURI);

        return addMethodToTypeIfNotExists(method, javaTypeOfKlass);
    }

    private void addClassRefToCHA(final JavaScope scope,
                                  final String classURI) {
        classHierarchy.get(scope).put(
            classURI,
            new JavaType(classURI, "",
                new Long2ObjectOpenHashMap<>(), new Object2ObjectOpenHashMap<>(),
                new LinkedList<>(),
                new ArrayList<>(), "", false, emptyMap()));
    }

    private JavaNode getJavaNodeWithEmptyMetadata(final Method method) {
        final var methodUri = method.toSchemalessURI();
        return new JavaNode(methodUri, emptyMap());
    }

    /**
     * Process class.
     *
     * @param klass Class
     */
    private void processClass(IClass klass) {
        Map<Selector, List<IMethod>> interfaceMethods = klass.getDirectInterfaces()
            .stream()
            .flatMap(o -> o.getDeclaredMethods().stream())
            .collect(
                Collectors.groupingBy(IMethod::getSelector)
            );

        for (IMethod declaredMethod : klass.getAllMethods()) {

            List<IMethod> methodInterfaces = interfaceMethods.get(declaredMethod.getSelector());

            processMethod(klass, declaredMethod, methodInterfaces);
        }
    }

    /**
     * Process method, it's super methods and interfaces.
     *
     * @param klass          Class
     * @param declaredMethod Method
     * @param interfaces     Interfaces implemented by method
     */
    private void processMethod(IClass klass, IMethod declaredMethod, List<IMethod> interfaces) {
//        if (declaredMethod.isPrivate()) {
//            return;
//        }
        IClass superKlass = klass.getSuperclass();
        addMethod(declaredMethod);

        IMethod superMethod = superKlass.getMethod(declaredMethod.getSelector());
        if (superMethod != null) {
            addMethod(superMethod);
        }

        if (interfaces != null) {
            for (IMethod interfaceMethod : interfaces) {
                addMethod(interfaceMethod);
            }
        }

        if (superKlass.isAbstract() && superMethod == null && interfaces == null) {

            Map<Selector, List<IMethod>> derivedInterfaces = superKlass.getDirectInterfaces()
                .stream()
                .flatMap(o -> o.getDeclaredMethods().stream())
                .collect(Collectors.groupingBy(IMethod::getSelector));

            List<IMethod> derivedInterfacesMethods =
                derivedInterfaces.get(declaredMethod.getSelector());

            if (derivedInterfacesMethods != null
                && derivedInterfacesMethods.size() > 0) {
                for (IMethod method : derivedInterfacesMethods) {
                    addMethod(method);
                }
            }
        }
    }

    /**
     * Add method to class hierarchy.
     *
     * @param method Method
     */
    private void addMethod(final IMethod method) {
        Method methodNode = analysisContext.findOrCreate(method.getReference());
        final var isInternal = AnalysisContext.inApplicationScope(method.getReference());
        JavaScope scope = JavaScope.externalTypes;
        if (isInternal) {
            scope = JavaScope.internalTypes;
            methodNode.oroginalLoader = Optional.of(method);
        }
        if (methodNode instanceof InternalMethod) {
            final var classURI = getClassURI(method.getDeclaringClass().getReference().getInnermostElementType());
            if (!classHierarchy.get(scope).containsKey(classURI)) {
                addClassToCHA(method.getDeclaringClass(), scope, classURI);
            }
            var type = classHierarchy.get(scope).get(classURI);
            addMethodToTypeIfNotExists(methodNode, type);
        }
    }

    private int addMethodToTypeIfNotExists(final Method method, JavaType type) {

        final var hashCode = method.hashCode();
        int key;

        if (methodsWithMetadata.contains(hashCode)) {
            return methodKeys.get(hashCode);
        }

        if (methodKeys.containsKey(hashCode)) {
            key = methodKeys.get(hashCode);
        } else {
            key = ++counter;
            methodKeys.put(hashCode, key);
        }

        if (method.oroginalLoader.isPresent()) {
            final var metadata = extractNodeMetadata(method.oroginalLoader.get());
            final var javaNode = new JavaNode(method.toSchemalessURI(), metadata);
            addMethodToType(type, javaNode, key);
            methodsWithMetadata.add(hashCode);
            if (((boolean) metadata.getOrDefault(Constants.IS_DEFINED, "false"))) {
                type.addDefinedMethod(javaNode.getSignature(), javaNode);
            }
        } else {
            addMethodToType(type, getJavaNodeWithEmptyMetadata(method), key);
        }
        return key;
    }

    private synchronized void addMethodToType(JavaType type, JavaNode method, int key) {
        type.addMethod(method, key);
    }

    private Map<String, Object> extractNodeMetadata(final IMethod loader) {
        return Map.of(
        Constants.FIRST_LINE, loader.getLineNumber(0),
        Constants.LAST_LINE, findLastLineNumber(loader),
        Constants.ACCESS_MODIFIER, findAccessModifier(loader),
        Constants.IS_DEFINED, !loader.isAbstract(),
        "walaSynthetic", loader.isWalaSynthetic()
        );
    }

    private String findAccessModifier(final IMethod method) {
        if (method.isPrivate()) {
            return Constants.PRIVATE;
        } else if (method.isPublic()) {
            return Constants.PUBLIC;
        } else if (method.isProtected()) {
            return Constants.PROTECTED;
        }
        return Constants.PACKAGE_PRIVATE;
    }

    private int findLastLineNumber(final IMethod method) {
        //TODO find a better solution
        int lastLine = -1;
        for (int i = 0; i < 60; i++) {
            try {
                lastLine = method.getLineNumber(i);
            } catch (ArrayIndexOutOfBoundsException e) {
                return lastLine;
            }
        }
        return -1;
    }

    /**
     * Find super classes, interfaces and source file name of a given class.
     *
     * @param klass Class
     * @param scope scope of class hierarchy
     * @param classURI
     */
    private void addClassToCHA(final IClass klass, final JavaScope scope, final String classURI) {
        String className = Method.getClassName(klass.getReference());

        final List<FastenURI> interfaces = new ArrayList<>();

        for (final var implementedInterface : klass.getAllImplementedInterfaces()) {
            interfaces.add(FastenURI.create(getClassURI(implementedInterface.getReference())));
        }

        final var superclass = klass.getSuperclass();
        LinkedList<FastenURI> superClasses = new LinkedList<>();
        if (superclass != null) {
            superClasses = superClassHierarchy(superclass, new LinkedList<>());
        }

        //TODO write proper access, final and annotations
        final var sourceFileName = klass.getSourceFileName();
        classHierarchy.get(scope).put(classURI,
            new JavaType(className, sourceFileName == null ? "" : sourceFileName,
                new Long2ObjectOpenHashMap<>(),
                new Object2ObjectOpenHashMap<>(), superClasses, interfaces,
                "", false, emptyMap()));
    }

    /**
     * Recursively creates a list of super classes of a given class in the order of inheritance.
     *
     * @param klass Class
     * @param aux   Auxiliary list
     * @return List of super classes
     */
    private LinkedList<FastenURI> superClassHierarchy(final IClass klass,
                                                      final LinkedList<FastenURI> aux) {
        aux.add(FastenURI.create(getClassURI(klass.getReference())));
        if (klass.getSuperclass() == null) {
            return aux;
        }

        return superClassHierarchy(klass.getSuperclass(), aux);
    }

    /**
     * Convert class to FastenURI format.
     *
     * @param typeReference
     * @return URI of class
     */
    private String getClassURI(TypeReference typeReference) {
        final String packageName = Method.getPackageName(typeReference);
        final String className = Method.getClassName(typeReference);
        return "/" + packageName + "/" + className;
    }

    /**
     * Convert class to FastenURI format.
     *
     * @param method Method in declaring class
     * @return URI of class
     */
    public static String getClassURI(final Method method) {
        return "/" + method.getPackageName() + "/" + method.getReference().getDeclaringClass().getInnermostElementType().getName().getClassName();
    }

    public boolean isAdded(long id){
        for (final var scope : classHierarchy.entrySet()) {
            for (JavaType type : scope.getValue().values()) {
                if (type.getMethods().containsKey(id)) {
                    return true;
                }
            }
        }
        return false;
    }

}

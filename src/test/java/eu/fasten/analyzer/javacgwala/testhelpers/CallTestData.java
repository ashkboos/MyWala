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

import com.google.common.collect.Multiset;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.JavaNode;
import eu.fasten.core.data.JavaType;
import eu.fasten.core.data.PartialJavaCallGraph;
import eu.fasten.core.utils.FastenUriUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.util.HashMap;
import java.util.Map;

public class CallTestData {
    public static final String WALA_SYNTHETIC = "walaSynthetic";
    public final Map.Entry<LongLongPair, Map<Object, Object>> call;
    private PartialJavaCallGraph graph;
    private Long2ObjectMap<JavaNode> methods;

    public CallTestData(PartialJavaCallGraph graph, int callNumber) {
        this.call = getCall(graph, callNumber);
        this.graph = graph;
        this.methods = this.graph.mapOfAllMethods();
    }

    public CallTestData(PartialJavaCallGraph graph, String sourceName, String targetName) {
        long sourceId = findNode(graph, sourceName);
        long targetId = findNode(graph, targetName);

        this.call = Map.entry(LongLongPair.of(sourceId, targetId), new HashMap<>());
        this.graph = graph;
        this.methods = this.graph.mapOfAllMethods();
    }

    private long findNode(PartialJavaCallGraph graph, String methodName) {
        final var methods = graph.mapOfAllMethods().long2ObjectEntrySet();
        for (final var node : methods) {
            if (node.getValue().getUri().toString().contains(methodName)) {
                return node.getLongKey();
            }
        }
        return -1;
    }

    public String sourceTypeString(){
        return getNodeType(sourceId());
    }

    public String targetTypeString(){
        return getNodeType(targetId());
    }

    public JavaType targetType(){
        return getJavaType(targetId());
    }

    public JavaType sourceType(){
        return getJavaType(sourceId());
    }

    public JavaType getJavaType(long nodeId) {
        Map<String, JavaType> allTypes = new HashMap<>();
        for (final var scope : graph.getClassHierarchy().entrySet()) {
            allTypes.putAll(scope.getValue());
        }
        return allTypes.get(getNodeType(nodeId));
    }

    public String getNodeType(long id) {
        final var entities =
            FastenUriUtils.parsePartialFastenUri(String.valueOf(methods.get(id).getUri()));
        return "/" + entities.get(0) + "/" + entities.get(1);
    }

    public Object invocation(int pc){
        return callMetadata(pc).get(Constants.INVOCATION_TYPE);
    }

    public Object line(int pc){
        return callMetadata(pc).get(Constants.CALLSITE_LINE);
    }

    public Object receiver(int pc){
        return callMetadata(pc).get(Constants.RECEIVER_TYPE);
    }

    public String sourceUri(){
        return methods.get(sourceId()).getUri().toString();
    }

    public String sourceSignature(){
        return sourceNode().getSignature();
    }

    public Object targetFirstLine(){
        return targetMetadata().get(Constants.FIRST_LINE);
    }

    public Object targetLastLine(){
        return targetMetadata().get(Constants.LAST_LINE);
    }

    public Object targetIsDefined(){
        return targetMetadata().get(Constants.IS_DEFINED);
    }

    public Object targetAccessModifier(){
        return targetMetadata().get(Constants.ACCESS_MODIFIER);
    }

    public Object targetIsWalaSynthetic(){
        return targetMetadata().get(WALA_SYNTHETIC);
    }

    public Map<String, Object> targetMetadata() {
        return targetNode().getMetadata();
    }

    public Object sourceFirstLine(){
        return sourceMetadata().get(Constants.FIRST_LINE);
    }

    public Object sourceLastLine(){
        return sourceMetadata().get(Constants.LAST_LINE);
    }

    public Object sourceIsDefined(){
        return sourceMetadata().get(Constants.IS_DEFINED);
    }

    public Object sourceAccessModifier(){
        return sourceMetadata().get(Constants.ACCESS_MODIFIER);
    }

    public Object sourceIsWalaSynthetic(){
        return sourceMetadata().get(WALA_SYNTHETIC);
    }

    private Map<String, Object> sourceMetadata() {
        return sourceNode().getMetadata();
    }

    public String targetSignature(){
        return targetNode().getSignature();
    }

    private long sourceId() {
        return this.call.getKey().firstLong();
    }

    public String targetUri(){
        return methods.get(targetId()).getUri().toString();
    }

    private long targetId() {
        return this.call.getKey().secondLong();
    }

    public JavaNode sourceNode() {
        return methods.get(sourceId());
    }

    public JavaNode targetNode() {
        return methods.get(targetId());
    }

    public Map<?,?> callMetadata(int pc){
        return (Map<?, ?>) call.getValue().get(pc);
    }

    public Map<String, Object> nodeMetadata(){
        return this.sourceNode().getMetadata();
    }

    private long getId(boolean sourceOrTarget) {
        if (sourceOrTarget) {
            return call.getKey().firstLong();
        }
        return call.getKey().secondLong();
    }

    private Map.Entry<LongLongPair, Map<Object, Object>> getCall(PartialJavaCallGraph graph,
                                                               int callNumber) {
        final var iterator = graph.getCallSites().entrySet().iterator();
        for (int i = 0; i < callNumber; i++) {
            iterator.next();
        }
        return iterator.next();
    }

    public Map<?,?> callMetadata() {
        return call.getValue();
    }
}

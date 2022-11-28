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

import eu.fasten.core.merge.CallGraphUtils;

public class NodeExpectation {
    public final int firstLine;
    public final int lastLine;
    public final String accessModifier;
    public final boolean isDefined;
    public final boolean isWalaSynthetic;
    public String nodeType;
    public String encodedSignature;

    public NodeExpectation(String nodeType, String encodedSignature, int firstLine,
                           int lastLine, String accessModifier, boolean isDefined,
                           boolean isWalaSynthetic) {
        this.nodeType = nodeType;
        this.encodedSignature = encodedSignature;
        this.firstLine = firstLine;
        this.lastLine = lastLine;
        this.accessModifier = accessModifier;
        this.isDefined = isDefined;
        this.isWalaSynthetic = isWalaSynthetic;
    }

    public String uri(){
        return this.nodeType + "." + this.encodedSignature;
    }

    public String decodedSignature() {
        return CallGraphUtils.decode(this.encodedSignature);
    }
}

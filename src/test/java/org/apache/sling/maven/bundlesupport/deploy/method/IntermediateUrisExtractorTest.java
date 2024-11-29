/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.maven.bundlesupport.deploy.method;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class IntermediateUrisExtractorTest {

    @Test
    public void extractPaths() {

        doTest(
                "http://localhost:8080/apps/slingshot/install",
                Arrays.asList(
                        URI.create("http://localhost:8080/apps/slingshot/install"),
                        URI.create("http://localhost:8080/apps/slingshot"),
                        URI.create("http://localhost:8080/apps")));
    }

    private void doTest(String input, List<URI> expectedOutput) {
        List<URI> paths = IntermediateUrisExtractor.extractIntermediateUris(URI.create(input));
        assertThat(paths, equalTo(expectedOutput));
    }

    @Test
    public void extractPaths_trailingSlash() {

        doTest(
                "http://localhost:8080/apps/slingshot/install/",
                Arrays.asList(
                        URI.create("http://localhost:8080/apps/slingshot/install"),
                        URI.create("http://localhost:8080/apps/slingshot"),
                        URI.create("http://localhost:8080/apps")));
    }

    @Test
    public void extractPaths_empty() {

        doTest("http://localhost:8080", Collections.<URI>emptyList());
    }
}

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
import java.net.URISyntaxException;

import org.junit.Test;

import static org.junit.Assert.*;

public class SlingPostDeployMethodTest {

    @Test
    public void testGetURLWithFilename() throws URISyntaxException {
        // all URLs are given with trailing slash
        assertEquals(
                new URI("http://localhost:5602/test/filename"),
                SlingPostDeployMethod.getURLWithFilename(new URI("http://localhost:5602/test/"), "filename"));
        assertEquals(
                new URI("http://localhost:5602/filename"),
                SlingPostDeployMethod.getURLWithFilename(new URI("http://localhost:5602/"), "filename"));
    }

    @Test
    public void testStripTrailingSlash() throws URISyntaxException {
        assertEquals(
                new URI("http://localhost"), SlingPostDeployMethod.stripTrailingSlash(new URI("http://localhost")));
        assertEquals(
                new URI("http://localhost"), SlingPostDeployMethod.stripTrailingSlash(new URI("http://localhost/")));
        assertEquals(
                new URI("http://user:pw@localhost:5602/test?key=value#fragment"),
                SlingPostDeployMethod.stripTrailingSlash(
                        new URI("http://user:pw@localhost:5602/test/?key=value#fragment")));
    }
}

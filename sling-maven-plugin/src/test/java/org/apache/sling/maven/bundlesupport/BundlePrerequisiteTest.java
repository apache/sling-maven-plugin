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
package org.apache.sling.maven.bundlesupport;

import static org.junit.Assert.assertEquals;

import org.apache.sling.maven.bundlesupport.BundlePrerequisite.Bundle;
import org.junit.Test;

public class BundlePrerequisiteTest {

    @Test
    public void testGetOsgiVersion() {
        Bundle bundle1 = new Bundle();
        bundle1.setVersion("1.2.3");
        assertEquals("1.2.3", bundle1.getVersion());
        assertEquals("1.2.3", bundle1.getOsgiVersion());
    }

    @Test
    public void testGetOsgiVersion_ShortVersion() {
        Bundle bundle1 = new Bundle();
        bundle1.setVersion("1.2");
        assertEquals("1.2", bundle1.getVersion());
        assertEquals("1.2.0", bundle1.getOsgiVersion());
    }

    @Test
    public void testGetOsgiVersion_SnapshotVersion() {
        Bundle bundle1 = new Bundle();
        bundle1.setVersion("1.2.3-SNAPSHOT");
        assertEquals("1.2.3-SNAPSHOT", bundle1.getVersion());
        assertEquals("1.2.3-SNAPSHOT", bundle1.getOsgiVersion());
    }

}

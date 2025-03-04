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
import java.util.ArrayList;
import java.util.List;

/**
 * Helper which extracts intermediate URIs from an URI
 */
final class IntermediateUrisExtractor {

    private IntermediateUrisExtractor() {
        // static methods only
    }

    /**
     * Extracts a list of intermediate paths from an URL.
     *
     * <p>For instance, <tt>http://localhost:8080/apps/slingshot/install</tt> would have the following intermediate
     * URIs:
     * <ol>
     *   <li>http://localhost:8080/apps</li>
     *   <li>http://localhost:8080/apps/slingshot</li>
     *   <li>http://localhost:8080/apps/slingshot/install</li>
     * </ol>
     * </p>
     *
     * @param url the url to extract paths from
     * @return the intermediate paths, possibly empty
     */
    public static List<URI> extractIntermediateUris(URI uri) {

        List<URI> paths = new ArrayList<>();

        String path = uri.getPath();
        StringBuilder accu = new StringBuilder();
        for (String segment : path.split("/")) {

            // ensure we have a trailing slash to join with the next segment
            if (accu.length() == 0 || accu.charAt(accu.length() - 1) != '/') {
                accu.append('/');
            }

            accu.append(segment);

            // don't add the root segment ( / )
            if (segment.length() != 0) {
                paths.add(0, uri.resolve(accu.toString()));
            }
        }

        return paths;
    }
}

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
package org.apache.sling.maven.bundlesupport.deploy;

import java.io.File;
import java.io.IOException;
import java.net.URI;

/**
 * Deploys/installs and undeploys/uninstalls bundles on a Sling instance.
 */
public interface DeployMethod {

    /**
     * Deploy/install a bundle on a Sling instance.
     * @param targetURL Target URL
     * @param file Bundle file
     * @param bundleSymbolicName Bundle symbolic name
     * @param context Deploy context parameters
     * @throws IOException in case of failure
     */
    void deploy(URI targetURL, File file, String bundleSymbolicName, DeployContext context) throws IOException;

    /**
     * Undeploy/uninstall a bundle on a Sling instance.
     * @param targetURL Target URL
     * @param bundleName Bundle symbolic name or file name (for all methods except for
     *      {@link org.apache.sling.maven.bundlesupport.deploy.method.FelixPostDeployMethod})
     * @param context Deploy context parameters
     * @throws IOException in case of failure
     */
    void undeploy(URI targetURL, String bundleName, DeployContext context) throws IOException;
}

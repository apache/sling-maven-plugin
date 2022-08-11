/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.sling.maven.bundlesupport;

import java.io.File;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Install the project's artifact to a running Sling instance (in case it is an OSGi bundle).
 * It uses the first valid OSGi bundle file for deployment from the primary artifact and all secondary ones.
 * For details refer to <a href="bundle-installation.html">Bundle Installation</a>.
 * To install an arbitrary bundle not attached to the current Maven project use goal <a href="install-file-mojo.html">install-file</a>.
 */
@Mojo(name = "install", defaultPhase = LifecyclePhase.INSTALL)
public class BundleInstallMojo extends AbstractBundleInstallMojo {

    /**
     * Whether to skip this step even though it has been configured in the
     * project to be executed.
     */
    @Parameter(property = "sling.install.skip", defaultValue = "false", required = true)
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        // don't do anything, if this step is to be skipped
        if (skip) {
            getLog().debug("Skipping bundle installation as instructed");
            return;
        }
        super.execute();
    }

    @Override
    protected File getBundleFileName() {
        File file = project.getArtifact().getFile();
        if (isBundleFile(file)) {
            return file;
        } else {
            getLog().debug("No bundle found in primary artifact " + file + ", checking secondary ones...");
            for (Artifact artifact : project.getAttachedArtifacts()) {
                if (isBundleFile(artifact.getFile())) {
                    return file;
                }
                getLog().debug("No bundle found in secondary artifact " + file);
            }
        }
        return null;
    }

    private boolean isBundleFile(File file) {
        if (file == null) {
            return false;
        }
        return getBundleSymbolicName(file) != null;
    }
}
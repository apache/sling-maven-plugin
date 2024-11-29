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

import java.io.File;
import java.io.IOException;
import java.net.URI;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.sling.maven.bundlesupport.deploy.BundleDeploymentMethod;
import org.apache.sling.maven.bundlesupport.deploy.DeployContext;
import org.apache.sling.maven.bundlesupport.fsresource.SlingInitialContentMounter;

/**
 * Uninstall an OSGi bundle from a running Sling instance.
 *
 * For details refer to <a href="bundle-installation.html">Bundle Uninstallation</a>.
 */
@Mojo(name = "uninstall")
public class BundleUninstallMojo extends AbstractBundleInstallMojo {

    /**
     * The path of bundle file to uninstall.
     * The file is only used to determine the file name or bundle symbolic name.
     * This parameter is only effective if {@link #bundleName} is not set.
     * @deprecated Use {@link #bundleName} instead
     */
    @Deprecated
    @Parameter(property = "sling.file", defaultValue = "${project.build.directory}/${project.build.finalName}.jar")
    private File bundleFileName;

    /**
     * The bundles's file/resource name without path (for all {@link #deploymentMethod}s except for {@code WebConsole}) or
     * its symbolic name.
     * If this parameter is set, it takes precedence over {@link #bundleFileName}.
     */
    @Parameter(property = "sling.bundle.name")
    private String bundleName;

    @Override
    protected File getBundleFileName() {
        return bundleFileName;
    }

    /**
     * @see org.apache.maven.plugin.AbstractMojo#execute()
     */
    @Override
    public void execute() throws MojoExecutionException {
        final String bundleName;
        if (this.bundleName == null) {
            // only uninstall if file is really an OSGi bundle
            final File bundleFile = getBundleFileName();
            String bundleSymbolicName = getBundleSymbolicName(bundleFile);
            if (bundleSymbolicName == null) {
                getLog().info(bundleFile + " is not an OSGi Bundle, not uploading");
                return;
            }
            if (getDeploymentMethod() != BundleDeploymentMethod.WebConsole) {
                bundleName = bundleFile.getName();
            } else {
                bundleName = bundleSymbolicName;
            }
        } else {
            bundleName = this.bundleName;
        }

        URI targetURL = getTargetURL();

        BundleDeploymentMethod deployMethod = getDeploymentMethod();

        try (CloseableHttpClient httpClient = getHttpClient()) {
            if (mountByFS) {
                configure(httpClient, targetURL, null);
            }
            getLog().info("Uninstalling Bundle " + bundleName + " from " + targetURL + " via " + deployMethod + "...");
            deployMethod
                    .execute()
                    .undeploy(
                            targetURL,
                            bundleName,
                            new DeployContext()
                                    .log(getLog())
                                    .httpClient(httpClient)
                                    .failOnError(failOnError)
                                    .mimeType(mimeType));
            getLog().info("Bundle uninstalled successfully!");
        } catch (IOException e) {
            String msg = "Uninstall from " + targetURL + " failed, cause: " + e.getMessage();
            if (failOnError) {
                throw new MojoExecutionException(msg, e);
            } else {
                getLog().error(msg, e);
            }
        }
    }

    @Override
    protected void configure(CloseableHttpClient httpClient, final URI targetURL, final File file)
            throws MojoExecutionException {
        new SlingInitialContentMounter(getLog(), httpClient, getRequestConfigBuilder(), project).unmount(targetURL);
    }
}

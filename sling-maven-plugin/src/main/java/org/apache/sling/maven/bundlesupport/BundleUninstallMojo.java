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
 * The plugin places by default an HTTP POST request to <a href="http://felix.apache.org/documentation/subprojects/apache-felix-web-console/web-console-restful-api.html#post-requests">
 * Felix Web Console</a> to uninstall the bundle.
 * It's also possible to use HTTP DELETE leveraging the <a href="http://sling.apache.org/documentation/development/repository-based-development.html">WebDAV bundle from Sling</a>.
 * or the <a href="http://sling.apache.org/documentation/bundles/manipulating-content-the-slingpostservlet-servlets-post.html">Sling POST servlet</a> to uninstall the bundle. 
 * The chosen method depends on the parameter {@link #deploymentMethod}.
 */
@Mojo(name = "uninstall")
public class BundleUninstallMojo extends AbstractBundleInstallMojo {

    /**
     * The name of the generated JAR file.
     */
    @Parameter(property = "sling.file", defaultValue = "${project.build.directory}/${project.build.finalName}.jar")
    private File bundleFileName;

    @Override
    protected File getBundleFileName() {
        return bundleFileName;
    }

    /**
     * @see org.apache.maven.plugin.AbstractMojo#execute()
     */
    @Override
    public void execute() throws MojoExecutionException {
        // only uninstall if packaging as an osgi-bundle
        final File bundleFile = getBundleFileName();
        final String bundleName = getBundleSymbolicName(bundleFile);
        if (bundleName == null) {
            getLog().info(bundleFile + " is not an OSGi Bundle, not uploading");
            return;
        }

        URI targetURL = getTargetURL();

        BundleDeploymentMethod deployMethod = getDeploymentMethod();


        try (CloseableHttpClient httpClient = getHttpClient()){
            configure(httpClient, targetURL, bundleFile);
            getLog().info(
                    "Uninstalling Bundle " + bundleName + " from "
                            + targetURL + " via " + deployMethod + "...");
            deployMethod.execute().undeploy(targetURL, bundleFile, bundleName, new DeployContext()
                    .log(getLog())
                    .httpClient(httpClient)
                    .failOnError(failOnError)
                    .mimeType(mimeType));
            getLog().info("Bundle uninstalled successfully!");
        } catch (IOException e) {
            String msg = "Uninstall from " + targetURL
                    + " failed, cause: " + e.getMessage();
            if (failOnError) {
                throw new MojoExecutionException(msg, e);
            } else {
                getLog().error(msg, e);
            }
        }
    }

    @Override
    protected void configure(CloseableHttpClient httpClient, final URI targetURL, final File file) throws MojoExecutionException {
        new SlingInitialContentMounter(getLog(), httpClient, project).unmount(targetURL, file);
    }
    
}
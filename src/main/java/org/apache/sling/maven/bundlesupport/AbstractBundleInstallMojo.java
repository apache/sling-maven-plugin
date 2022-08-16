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
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.sling.maven.bundlesupport.deploy.BundleDeploymentMethod;
import org.apache.sling.maven.bundlesupport.deploy.DeployContext;
import org.apache.sling.maven.bundlesupport.fsresource.SlingInitialContentMounter;

abstract class AbstractBundleInstallMojo extends AbstractBundleRequestMojo {

    /**
     * If a PUT via WebDAV should be used instead of the standard POST to the
     * Felix Web Console. In the <code>uninstall</code> goal, a HTTP DELETE will be
     * used.
     * 
     * @deprecated Use {@link #deploymentMethod} instead.
     */
    @Deprecated
    @Parameter(property="sling.usePut", defaultValue = "false")
    protected boolean usePut;

    /**
     * Bundle deployment method. One of the following three values are allowed
     * <ol>
     *  <li><strong>WebConsole</strong>, uses the <a href="http://felix.apache.org/documentation/subprojects/apache-felix-web-console/web-console-restful-api.html#post-requests">
     *  Felix Web Console REST API</a> for deployment (HTTP POST). This is the default. 
     *  Make sure that {@link #slingUrl} points to the Felix Web Console in that case.</li>
     *  <li><strong>WebDAV</strong>, uses <a href="https://sling.apache.org/documentation/development/repository-based-development.html">
     *  WebDAV</a> for deployment (HTTP PUT). Make sure that {@link #slingUrl} points to the entry path of 
     *  the Sling WebDAV bundle (defaults to {@code /dav/default} in the Sling starter). Issues a HTTP Delete for the uninstall goal.</li>
     *  <li><strong>SlingPostServlet</strong>, uses the
     *  <a href="https://sling.apache.org/documentation/bundles/manipulating-content-the-slingpostservlet-servlets-post.html">Sling Post Servlet</a> for deployment (HTTP POST).
     *  Make sure that {@link #slingUrl} points a path which is handled by the Sling POST Servlet (usually below regular Sling root URL).</li>
     * </ol>
     * For more details refer to <a href="bundle-installation.html">Bundle Installation</a>.
     * 
     * This has precedence over the deprecated parameter {@link #usePut}.
     * If nothing is set the default is either {@code WebConsole} or {@code WebDAV} (when {@link #usePut} is {@code true}).
     */
    @Parameter(property="sling.deploy.method")
    protected BundleDeploymentMethod deploymentMethod;

    /**
     * The content type / mime type used for WebDAV or Sling POST deployment.
     */
    @Parameter(property="sling.mimeType", defaultValue = "application/java-archive")
    protected String mimeType;

    /**
     * The start level to set on the installed bundle. If the bundle is already installed and therefore is only 
     * updated this parameter is ignored. The parameter is also ignored if the running Sling instance has no 
     * StartLevel service (which is unusual actually). Only applies when POSTing to Felix Web Console.
     */
    @Parameter(property="sling.bundle.startlevel", defaultValue = "20")
    private String bundleStartLevel;

    /**
     * Whether to start the uploaded bundle or not. Only applies when POSTing
     * to Felix Web Console
     */
    @Parameter(property="sling.bundle.start", defaultValue = "true")
    private boolean bundleStart;

    /**
     * Whether to refresh the packages after installing the uploaded bundle.
     * Only applies when POSTing to Felix Web Console
     */
    @Parameter(property="sling.refreshPackages", defaultValue = "true")
    private boolean refreshPackages;

    /**
     * Whether to add (for install)/remove (for uninstall) the mapping for the
     * <a href="https://sling.apache.org/documentation/bundles/accessing-filesystem-resources-extensions-fsresource.html">Apache Sling File System Resource Provider</a>
     * for the <a href="https://sling.apache.org/documentation/bundles/content-loading-jcr-contentloader.html#initial-content-loading">bundle's initial content</a>.
     * This parameter must not be {@code true} with bundles resolved from the Maven repository.
     */
    @Parameter(property="sling.mountByFS", defaultValue = "false")
    boolean mountByFS;

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    protected MavenProject project;

    public AbstractBundleInstallMojo() {
        super();
    }

    protected abstract File getBundleFileName() throws MojoExecutionException;

    @Override
    public void execute() throws MojoExecutionException {

        // get the file to upload
        File bundleFile = getBundleFileName();

        // only upload if packaging as an osgi-bundle
        if (!bundleFile.exists()) {
            throw new MojoExecutionException("The given bundle file " + bundleFile + " does not exist!");
        }

        String bundleName = getBundleSymbolicName(bundleFile);
        if (bundleName == null) {
            throw new MojoExecutionException("The given file " + bundleFile + " is no OSGi bundle");
        }

        URI targetURL = getTargetURL();

        BundleDeploymentMethod deploymentMethod = getDeploymentMethod();
        getLog().info(
            "Installing Bundle " + bundleName + "(" + bundleFile + ") to "
                + targetURL + " via " + deploymentMethod + "...");

        try (CloseableHttpClient httpClient = getHttpClient()){
            deploymentMethod.execute().deploy(targetURL, bundleFile, bundleName, new DeployContext()
                .log(getLog())
                .httpClient(httpClient)
                .failOnError(failOnError)
                .bundleStartLevel(bundleStartLevel)
                .bundleStart(bundleStart)
                .mimeType(mimeType)
                .refreshPackages(refreshPackages));
            getLog().info("Bundle installed successfully");
            if (mountByFS) {
                configure(httpClient, getConsoleTargetURL(), bundleFile);
            }
        } catch (IOException e) {
            String msg = "Installation failed, cause: "
                + e.getMessage();
            if (failOnError) {
                throw new MojoExecutionException(msg, e);
            } else {
                getLog().error(msg, e);
            }
        }
    }
    
    protected void configure(CloseableHttpClient httpClient, final URI consoleTargetURL, final File file) throws MojoExecutionException {
        new SlingInitialContentMounter(getLog(), httpClient, getRequestConfigBuilder(), project).mount(consoleTargetURL, file);
    }

    /**
     * Retrieve the bundle deployment method matching the configuration.
     * @return bundle deployment method matching the plugin configuration.
     * @throws MojoExecutionException Exception
     */
    protected BundleDeploymentMethod getDeploymentMethod() throws MojoExecutionException {
        if (this.deploymentMethod == null) {
            if (usePut) {
                getLog().warn("Using deprecated configuration parameter 'usePut=true', please instead use the new parameter 'deploymentMethod=WebDAV'!");
                return BundleDeploymentMethod.WebDAV;
            } else {
                return BundleDeploymentMethod.WebConsole;
            }
        } else {
            return deploymentMethod;
        }
    }

}

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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.sling.maven.bundlesupport.deploy.BundleDeploymentMethod;
import org.apache.sling.maven.bundlesupport.deploy.DeployContext;
import org.apache.sling.maven.bundlesupport.deploy.DeployMethod;

import java.io.File;
import java.util.Optional;

/**
 * Upload a non-bundle artifact to the JCR repository of a running Sling instance.
 * This goal mirrors the behavior of the <code>install</code> goal for uploading bundles when <code>deploymentMethod</code>
 * is set to either <code>WebDAV</code> or <code>SlingPostServlet</code>, and is skipped when <code>deploymentMethod</code>
 * is set to <code>WebConsole</code>.
 * For <code>WebDAV</code> the goal will use HTTP PUT to leverage the <a href="http://sling.apache.org/documentation/development/repository-based-development.html">WebDAV bundle from Sling</a>.
 * For <code>SlingPostServlet</code>, the goal will leverage the <a href="http://sling.apache.org/documentation/bundles/manipulating-content-the-slingpostservlet-servlets-post.html">Sling POST servlet</a>
 * instead. The chosen method depends on the parameter {@link #deploymentMethod}.
 * <br>
 * <p><strong>Intermediate Node Creation</strong></p>
 * <p>
 * For supported <code>deploymentMethod</code>s, the artifact is not directly deployed within the OSGi container,
 * but rather is uploaded to the JCR and from there on being picked up by the
 * <a href="https://sling.apache.org/documentation/bundles/jcr-installer-provider.html">JCR Installer Provider</a> asynchronously, which takes care 
 * of performing any installable resource transformation supported by the OSGi container. For both supported deployment methods, intermediate nodes (i.e. inexisting parent nodes)
 * are automatically created. The primary type of those intermediate nodes depends on the deployment method.
 * </p>
 * <ul>
 * <li>
 *  WebDAV, uses the configured collection node type, by default <code>sling:Folder</code>
 *  (see also <a href="https://sling.apache.org/documentation/development/repository-based-development.html">WebDAV Configuration</a>)</li>
 * <li>
 *  SlingPostServlet, uses internally <code>ResourceResolverFactory.create(...)</code> without setting any <code>jcr:primaryType</code>.
 *  Therefore the <code>JcrResourceProviderFactory</code> will call <code>Node.addNode(String relPath)</code> which determines a fitting 
 *  node type automatically, depending on the parents node type definition (see <a href="https://docs.adobe.com/docs/en/spec/jsr170/javadocs/jcr-2.0/javax/jcr/Node.html#addNode(java.lang.String)">Javadoc</a>).
 *  So in most of the cases this should be a <code>sling:Folder</code>, as this is the first allowed child node definition in <code>sling:Folder</code>.
 *  This only may differ, if your existing parent node is not of type <code>sling:Folder</code> itself.
 * </li>
 * </ul>
 *
 * @since 2.5.0
 */
@Mojo(name = "jcr-install", defaultPhase = LifecyclePhase.INSTALL)
public class JcrInstallMojo extends AbstractBundlePostMojo {

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
     * JCR deployment method. One of the following three values are allowed
     * <ol>
     *  <li><strong>WebConsole</strong>, is not supported by this goal. Execution will be skipped.</li>
     *  <li><strong>WebDAV</strong>, uses <a href="https://sling.apache.org/documentation/development/repository-based-development.html">
     *  WebDAV</a> for deployment (HTTP PUT). Make sure that {@link #slingUrl} points to the entry path of
     *  the Sling WebDAV bundle (defaults to <tt>/dav/default</tt> in the Sling starter).
     *  <li><strong>SlingPostServlet</strong>, uses the
     *  <a href="https://sling.apache.org/documentation/bundles/manipulating-content-the-slingpostservlet-servlets-post.html">Sling Post Servlet</a> for deployment (HTTP POST).
     *  Make sure that {@link #slingUrl} points a path which is handled by the Sling POST Servlet (usually below regular Sling root URL).</li>
     * </ol>
     *
     * This has precedence over the deprecated parameter {@link #usePut}.
     */
    @Parameter(property="sling.deploy.method", required = false)
    protected BundleDeploymentMethod deploymentMethod;

    /**
     * The content type / mime type used for WebDAV or Sling POST deployment.
     */
    @Parameter(defaultValue = "application/octet-stream", required = true)
    protected String mimeType;

    /**
     * Whether to skip this step even though it has been configured in the
     * project to be executed. This property may be set by the
     * <code>sling.install.skip</code> comparable to the <code>maven.test.skip</code>
     * property to prevent running the unit tests.
     */
    @Parameter(property = "sling.install.skip", defaultValue = "false", required = true)
    boolean skip;

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    protected MavenProject project;

    public JcrInstallMojo() {
        super();
    }

    @Override
    public void execute() throws MojoExecutionException {
        // don't do anything, if this step is to be skipped
        if (skip) {
            getLog().debug("Skipping JCR installation as instructed");
            return;
        }

        // only upload if packaging as an osgi-bundle
        File artifactFile = Optional.ofNullable(project)
                .map(MavenProject::getArtifact)
                .map(Artifact::getFile)
                .orElse(null);
        if (artifactFile == null || !artifactFile.exists()) {
            getLog().info(artifactFile + " does not exist, no uploading");
            return;
        }

        if (artifactFile.isDirectory()) {
            getLog().debug(artifactFile + " is directory, no uploading");
            return;
        }

        if (isBundleFile(artifactFile)) {
            getLog().info(artifactFile + " is an OSGi Bundle, not uploading. Please use sling:install goal instead.");
            return;
        }

        BundleDeploymentMethod method = getDeploymentMethod();
        if (method == BundleDeploymentMethod.WebConsole) {
            getLog().debug("Skipping JCR installation because deploymentMethod=WebConsole");
            return;
        }

        String targetURL = getTargetURL();

        getLog().info(
                "Installing Artifact file " + project.getArtifactId() + "(" + artifactFile + ") to "
                        + targetURL + " via " + method);

        doExecuteMethod(method).deploy(targetURL, artifactFile, project.getArtifactId(), new DeployContext()
                .log(getLog())
                .httpClient(getHttpClient())
                .failOnError(failOnError)
                .mimeType(mimeType));
    }

    protected DeployMethod doExecuteMethod(BundleDeploymentMethod bundleDeploymentMethod) {
        return bundleDeploymentMethod.execute();
    }

    protected boolean isBundleFile(File artifactFile) {
        return artifactFile.getName().endsWith(".jar") && getBundleSymbolicName(artifactFile) != null;
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
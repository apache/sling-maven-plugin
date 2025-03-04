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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.sling.maven.bundlesupport.BundlePrerequisite.Bundle;
import org.apache.sling.maven.bundlesupport.deploy.BundleDeploymentMethod;
import org.apache.sling.maven.bundlesupport.deploy.DeployContext;
import org.apache.sling.maven.bundlesupport.fsresource.FileVaultXmlMounter;
import org.apache.sling.maven.bundlesupport.fsresource.SlingInitialContentMounter;
import org.eclipse.aether.artifact.DefaultArtifact;

/**
 * Create OSGi configurations for the
 * <a href="https://sling.apache.org/documentation/bundles/accessing-filesystem-resources-extensions-fsresource.html">Apache Sling File System Resource Provider</a>.
 * In case a bundle file is supplied via {@link AbstractFsMountMojo#bundleFileName} the configuration for its <a href="https://sling.apache.org/documentation/bundles/content-loading-jcr-contentloader.html#initial-content-loading">initial content</a> is created.
 * Otherwise it tries to detect a FileVault content package layout starting at {@link AbstractFsMountMojo#fileVaultJcrRootFile} or the project's resource directories and potentially creates a configuration for each path in the package's {@code filter.xml}.
 * @since 2.2.0
 */
@Mojo(name = "fsmount", requiresProject = true)
public class FsMountMojo extends AbstractFsMountMojo {

    private static final String BUNDLE_GROUP_ID = "org.apache.sling";

    private static final String FS_BUNDLE_ARTIFACT_ID = "org.apache.sling.fsresource";
    // TODO: update to 2.3.0 when released
    private static final String FS_BUNDLE_DEFAULT_VERSION = "2.2.1-SNAPSHOT";
    private static final String FS_BUNDLE_LEGACY_DEFAULT_VERSION = "2.2.0";
    private static final String FS_BUNDLE_LEGACY_ACNIENT_DEFAULT_VERSION = "1.4.8";

    private static final String SLING_API_BUNDLE_ARTIFACT_ID = "org.apache.sling.api";
    private static final String SLING_API_BUNDLE_MIN_VERSION = "2.25.4";

    private static final String RESOURCE_RESOLVER_BUNDLE_ARTIFACT_ID = "org.apache.sling.resourceresolver";
    private static final String RESOURCE_RESOLVER_BUNDLE_LEGACY_MIN_VERSION = "1.5.18";

    private static final String JOHNZON_BUNDLE_ARTIFACT_ID = "org.apache.sling.commons.johnzon";
    private static final String JOHNZON_2_BUNDLE_MIN_VERSION = "2.0.0";
    private static final String JOHNZON_1_BUNDLE_MIN_VERSION = "1.2.6";

    private static final String COMMONS_COLLECTIONS4_BUNDLE_GROUP_ID = "org.apache.commons";
    private static final String COMMONS_COLLECTIONS4_BUNDLE_ARTIFACT_ID = "commons-collections4";
    private static final String COMMONS_COLLECTIONS4_BUNDLE_MIN_VERSION = "4.1";
    private static final String COMMONS_COLLECTIONS4_BUNDLE_SYMBOLICNAME = "org.apache.commons.collections4";

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
     * @since 2.3.0
     */
    @Parameter(property = "sling.deploy.method", required = false, defaultValue = "WebConsole")
    private BundleDeploymentMethod deploymentMethod;

    /**
     * Deploy <code>org.apache.sling.fsresource</code> to Sling instance bundle when it is not deployed already.
     * @since 2.3.0
     */
    @Parameter(required = false, defaultValue = "true")
    private boolean deployFsResourceBundle;

    /**
     * Bundles that have to be installed as prerequisites to execute this goal.
     * With multiple entries in the list different bundles with different preconditions can be defined.<br/>
     * <strong>Default value is:</strong>:
     * <pre>
     * &lt;deployFsResourceBundlePrerequisites&gt;
     *   &lt;bundlePrerequisite&gt;
     *     &lt;bundles&gt;
     *       &lt;bundle&gt;
     *         &lt;groupId&gt;org.apache.sling&lt;/groupId&gt;
     *         &lt;artifactId&gt;org.apache.sling.fsresource&lt;/artifactId&gt;
     *         &lt;version&gt;2.3.0&lt;/version&gt;
     *       &lt;/bundle&gt;
     *     &lt;/bundles&gt;
     *     &lt;preconditions&gt;
     *       &lt;bundle&gt;
     *         &lt;groupId&gt;org.apache.sling&lt;/groupId&gt;
     *         &lt;artifactId&gt;org.apache.sling.commons.johnzon&lt;/artifactId&gt;
     *         &lt;version&gt;2.0.0&lt;/version&gt;
     *       &lt;/bundle&gt;
     *       &lt;bundle&gt;
     *         &lt;groupId&gt;org.apache.sling&lt;/groupId&gt;
     *         &lt;artifactId&gt;org.apache.sling.api&lt;/artifactId&gt;
     *         &lt;version&gt;2.25.4&lt;/version&gt;
     *       &lt;/bundle&gt;
     *       &lt;bundle&gt;
     *         &lt;groupId&gt;org.apache.commons&lt;/groupId&gt;
     *         &lt;artifactId&gt;commons-collections4&lt;/artifactId&gt;
     *         &lt;version&gt;4.1&lt;/version&gt;
     *         &lt;symbolicName&gt;org.apache.commons.collections4&lt;/symbolicName&gt;
     *       &lt;/bundle&gt;
     *     &lt;/preconditions&gt;
     *   &lt;/bundlePrerequisite&gt;
     *   &lt;bundlePrerequisite&gt;
     *     &lt;bundles&gt;
     *       &lt;bundle&gt;
     *         &lt;groupId&gt;org.apache.sling&lt;/groupId&gt;
     *         &lt;artifactId&gt;org.apache.sling.commons.johnzon&lt;/artifactId&gt;
     *         &lt;version&gt;1.2.6&lt;/version&gt;
     *       &lt;/bundle&gt;
     *       &lt;bundle&gt;
     *         &lt;groupId&gt;org.apache.commons&lt;/groupId&gt;
     *         &lt;artifactId&gt;commons-collections4&lt;/artifactId&gt;
     *         &lt;version&gt;4.1&lt;/version&gt;
     *         &lt;symbolicName&gt;org.apache.commons.collections4&lt;/symbolicName&gt;
     *       &lt;/bundle&gt;
     *       &lt;bundle&gt;
     *         &lt;groupId&gt;org.apache.sling&lt;/groupId&gt;
     *         &lt;artifactId&gt;org.apache.sling.fsresource&lt;/artifactId&gt;
     *         &lt;version&gt;2.2.0&lt;/version&gt;
     *       &lt;/bundle&gt;
     *     &lt;/bundles&gt;
     *     &lt;preconditions&gt;
     *       &lt;bundle&gt;
     *         &lt;groupId&gt;org.apache.sling&lt;/groupId&gt;
     *         &lt;artifactId&gt;org.apache.sling.resourceresolver&lt;/artifactId&gt;
     *         &lt;version&gt;1.5.18&lt;/version&gt;
     *       &lt;/bundle&gt;
     *     &lt;/preconditions&gt;
     *   &lt;/bundlePrerequisite&gt;
     *   &lt;bundlePrerequisite&gt;
     *     &lt;bundles&gt;
     *       &lt;bundle&gt;
     *         &lt;groupId&gt;org.apache.sling&lt;/groupId&gt;
     *         &lt;artifactId&gt;org.apache.sling.fsresource&lt;/artifactId&gt;
     *         &lt;version&gt;1.4.8&lt;/version&gt;
     *       &lt;/bundle&gt;
     *     &lt;/bundles&gt;
     *   &lt;/bundlePrerequisite&gt;
     * &lt;/deployFsResourceBundlePrerequisites&gt;
     * </pre>
     * @since 2.3.0
     */
    @Parameter(required = false)
    private List<BundlePrerequisite> deployFsResourceBundlePrerequisites;

    public void addDeployFsResourceBundlePrerequisite(BundlePrerequisite item) {
        if (this.deployFsResourceBundlePrerequisites == null) {
            this.deployFsResourceBundlePrerequisites = new ArrayList<>();
        }
        this.deployFsResourceBundlePrerequisites.add(item);
    }

    @Override
    protected void configureSlingInitialContent(
            CloseableHttpClient httpClient, final URI consoleTargetUrl, final File bundleFile)
            throws MojoExecutionException {
        new SlingInitialContentMounter(getLog(), getHttpClient(), getRequestConfigBuilder(), project)
                .mount(consoleTargetUrl, bundleFile);
    }

    @Override
    protected void configureFileVaultXml(
            CloseableHttpClient httpClient, URI consoleTargetUrl, File jcrRootFile, File filterXmlFile)
            throws MojoExecutionException {
        new FileVaultXmlMounter(getLog(), httpClient, getRequestConfigBuilder(), project)
                .mount(consoleTargetUrl, jcrRootFile, filterXmlFile);
    }

    @Override
    protected void ensureBundlesInstalled(CloseableHttpClient httpClient, URI consoleTargetUrl)
            throws MojoExecutionException {
        if (!deployFsResourceBundle) {
            return;
        }

        if (deployFsResourceBundlePrerequisites == null) {
            BundlePrerequisite latestJakartaJson = new BundlePrerequisite();
            latestJakartaJson.addBundle(new Bundle(BUNDLE_GROUP_ID, FS_BUNDLE_ARTIFACT_ID, FS_BUNDLE_DEFAULT_VERSION));
            latestJakartaJson.addPrecondition(
                    new Bundle(BUNDLE_GROUP_ID, JOHNZON_BUNDLE_ARTIFACT_ID, JOHNZON_2_BUNDLE_MIN_VERSION));
            latestJakartaJson.addPrecondition(
                    new Bundle(BUNDLE_GROUP_ID, SLING_API_BUNDLE_ARTIFACT_ID, SLING_API_BUNDLE_MIN_VERSION));
            latestJakartaJson.addPrecondition(new Bundle(
                    COMMONS_COLLECTIONS4_BUNDLE_GROUP_ID,
                    COMMONS_COLLECTIONS4_BUNDLE_ARTIFACT_ID,
                    COMMONS_COLLECTIONS4_BUNDLE_MIN_VERSION,
                    COMMONS_COLLECTIONS4_BUNDLE_SYMBOLICNAME));
            addDeployFsResourceBundlePrerequisite(latestJakartaJson);

            BundlePrerequisite legacyJavaxJson = new BundlePrerequisite();
            legacyJavaxJson.addBundle(
                    new Bundle(BUNDLE_GROUP_ID, JOHNZON_BUNDLE_ARTIFACT_ID, JOHNZON_1_BUNDLE_MIN_VERSION));
            legacyJavaxJson.addBundle(new Bundle(
                    COMMONS_COLLECTIONS4_BUNDLE_GROUP_ID,
                    COMMONS_COLLECTIONS4_BUNDLE_ARTIFACT_ID,
                    COMMONS_COLLECTIONS4_BUNDLE_MIN_VERSION,
                    COMMONS_COLLECTIONS4_BUNDLE_SYMBOLICNAME));
            legacyJavaxJson.addBundle(
                    new Bundle(BUNDLE_GROUP_ID, FS_BUNDLE_ARTIFACT_ID, FS_BUNDLE_LEGACY_DEFAULT_VERSION));
            legacyJavaxJson.addPrecondition(new Bundle(
                    BUNDLE_GROUP_ID,
                    RESOURCE_RESOLVER_BUNDLE_ARTIFACT_ID,
                    RESOURCE_RESOLVER_BUNDLE_LEGACY_MIN_VERSION));
            addDeployFsResourceBundlePrerequisite(legacyJavaxJson);

            BundlePrerequisite legacyAncient = new BundlePrerequisite();
            legacyAncient.addBundle(
                    new Bundle(BUNDLE_GROUP_ID, FS_BUNDLE_ARTIFACT_ID, FS_BUNDLE_LEGACY_ACNIENT_DEFAULT_VERSION));
            addDeployFsResourceBundlePrerequisite(legacyAncient);
        }

        boolean foundMatch = false;
        for (BundlePrerequisite bundlePrerequisite : deployFsResourceBundlePrerequisites) {
            if (isBundlePrerequisitesPreconditionsMet(httpClient, bundlePrerequisite, consoleTargetUrl)) {
                for (Bundle bundle : bundlePrerequisite.getBundles()) {
                    deployBundle(httpClient, bundle, consoleTargetUrl);
                }
                foundMatch = true;
                break;
            }
        }
        if (!foundMatch) {
            throw new MojoExecutionException(
                    "Target server does not meet any of the prerequisites for this goal. Haven't found the necessary bundles: "
                            + deployFsResourceBundlePrerequisites.stream()
                                    .map(bundlePrerequisite -> bundlePrerequisite.getPreconditions().stream()
                                            .map(BundlePrerequisite.Bundle::toString)
                                            .collect(Collectors.joining(", ")))
                                    .collect(Collectors.joining(" OR ")));
        }
    }

    private void deployBundle(CloseableHttpClient httpClient, Bundle bundle, URI consoleTargetUrl)
            throws MojoExecutionException {
        try {
            if (isBundleInstalled(httpClient, bundle, consoleTargetUrl)) {
                getLog().debug("Bundle " + bundle.getSymbolicName() + " " + bundle.getOsgiVersion()
                        + " (or higher) already installed.");
                return;
            }
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Error getting installation status of " + bundle + " via " + consoleTargetUrl + ": "
                            + e.getMessage(),
                    e);
        }
        try {
            getLog().info("Installing Bundle " + bundle.getSymbolicName() + " " + bundle.getOsgiVersion() + " to "
                    + consoleTargetUrl + " via " + deploymentMethod);

            File file = getArtifactFile(bundle, "jar");
            deploymentMethod
                    .execute()
                    .deploy(
                            getTargetURL(),
                            file,
                            bundle.getSymbolicName(),
                            new DeployContext()
                                    .log(getLog())
                                    .httpClient(httpClient)
                                    .failOnError(failOnError));
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Error deploying bundle " + bundle + " to " + getTargetURL() + ": " + e.getMessage(), e);
        }
    }

    private boolean isBundlePrerequisitesPreconditionsMet(
            CloseableHttpClient httpClient, BundlePrerequisite bundlePrerequisite, URI targetUrl)
            throws MojoExecutionException {
        for (Bundle precondition : bundlePrerequisite.getPreconditions()) {
            try {
                if (!isBundleInstalled(httpClient, precondition, targetUrl)) {
                    getLog().debug("Bundle " + precondition.getSymbolicName() + " " + precondition.getOsgiVersion()
                            + " (or higher) is not installed.");
                    return false;
                }
            } catch (IOException e) {
                throw new MojoExecutionException(
                        "Reading bundle data for bundle " + precondition + " failed, cause: " + e.getMessage(), e);
            }
        }
        return true;
    }

    private boolean isBundleInstalled(CloseableHttpClient httpClient, Bundle bundle, URI consoleTargetUrl)
            throws IOException {
        String installedVersionString =
                getBundleInstalledVersion(httpClient, bundle.getSymbolicName(), consoleTargetUrl);
        if (StringUtils.isBlank(installedVersionString)) {
            return false;
        }
        DefaultArtifactVersion installedVersion = new DefaultArtifactVersion(installedVersionString);
        DefaultArtifactVersion requiredVersion = new DefaultArtifactVersion(bundle.getOsgiVersion());
        return (installedVersion.compareTo(requiredVersion) >= 0);
    }

    /**
     * Get version of fsresource bundle that is installed in the instance.
     * @param httpClient the http client to use
     * @param targetUrl Target URL
     * @return Version number or null if non installed
     * @throws MojoExecutionException
     */
    private String getBundleInstalledVersion(
            CloseableHttpClient httpClient, final String bundleSymbolicName, final URI consoleTargetUrl)
            throws IOException {
        final URI getUrl = consoleTargetUrl.resolve("bundles/" + bundleSymbolicName + ".json");
        getLog().debug("Get bundle data via request to " + getUrl);
        final HttpGet get = new HttpGet(getUrl);

        try {
            final String jsonText = httpClient.execute(get, new BasicHttpClientResponseHandler());
            JsonObject response = JsonSupport.parseObject(jsonText);
            JsonArray data = response.getJsonArray("data");
            if (!data.isEmpty()) {
                JsonObject bundleData = data.getJsonObject(0);
                return bundleData.getString("version");
            }

        } catch (JsonException ex) {
            throw new IOException("Reading bundle data from " + getUrl + " failed, cause: " + ex.getMessage(), ex);
        } catch (HttpResponseException e) {
            // accept 404 response
            if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                return null;
            }
            throw e;
        }
        // no version detected, bundle is not installed
        return null;
    }

    private File getArtifactFile(Bundle bundle, String extension) throws MojoExecutionException {
        return resolveArtifact(
                new DefaultArtifact(bundle.getGroupId(), bundle.getArtifactId(), extension, bundle.getVersion()));
    }
}

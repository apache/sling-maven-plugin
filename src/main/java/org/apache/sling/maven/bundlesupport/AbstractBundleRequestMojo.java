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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Timeout;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.osgi.framework.Constants;

abstract class AbstractBundleRequestMojo extends AbstractMojo {

    /**
     * The URL of the running Sling instance.
     *
     * <p>The default is only useful for <strong>WebConsole</strong> deployment.</p>
     *
     * <p>For <strong>WebDAV</strong> deployment it is recommended to include the <a href="https://sling.apache.org/documentation/development/repository-based-development.html#separate-uri-space-webdav">Sling Simple WebDAV servlet root</a>, for instance <a href="http://localhost:8080/dav/default/libs/sling/install">http://localhost:8080/dav/default/libs/sling/install</a>. Omitting the {@code dav/default} segment can lead to conflicts with other servlets.</p>
     */
    @Parameter(property = "sling.url", defaultValue = "http://localhost:8080/system/console", required = true)
    protected URI slingUrl;

    /**
     * The WebConsole URL of the running Sling instance. This is required for file system provider operations.
     * If not configured the value of slingUrl is used.
     */
    @Parameter(property = "sling.console.url")
    protected URI slingConsoleUrl;

    /**
     * An optional url suffix which will be appended to the <code>sling.url</code>
     * for use as the real target url. This allows to configure different target URLs
     * in each POM, while using the same common <code>sling.url</code> in a parent
     * POM (eg. <code>sling.url=http://localhost:8080</code> and
     * <code>sling.urlSuffix=/project/specific/path</code>). This is typically used
     * in conjunction with WebDAV or SlingPostServlet deployment methods.
     */
    @Parameter(property = "sling.urlSuffix")
    protected String slingUrlSuffix;

    /**
     * The user name to authenticate at the running Sling instance.
     */
    @Parameter(property = "sling.user", defaultValue = "admin")
    private String user;

    /**
     * The password to authenticate at the running Sling instance.
     */
    @Parameter(property = "sling.password", defaultValue = "admin")
    private String password;

    /**
     * Determines whether or not to fail the build if
     * the HTTP POST or PUT returns an non-OK response code.
     */
    @Parameter(property = "sling.failOnError", defaultValue = "true")
    protected boolean failOnError;

    /**
     * HTTP connection timeout (in seconds). Determines the timeout until a new connection is fully established.
     * This may also include transport security negotiation exchanges
     * such as {@code SSL} or {@code TLS} protocol negotiation).
     * @since 3.0.0
     */
    @Parameter(property = "sling.httpConnectTimeoutSec", defaultValue = "10")
    private int httpConnectTimeoutSec;

    /**
     * HTTP response timeout (in seconds). Determines the timeout until arrival of a response from the opposite
     * endpoint.
     * @since 3.0.0
     */
    @Parameter(property = "sling.httpResponseTimeoutSec", defaultValue = "60")
    private int httpResponseTimeoutSec;

    @Component
    protected RepositorySystem repoSystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    protected RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    protected List<RemoteRepository> repositories;

    /**
     * Returns the symbolic name of the given bundle. If the
     * <code>jarFile</code> does not contain a manifest with a
     * <code>Bundle-SymbolicName</code> header <code>null</code> is
     * returned. Otherwise the value of the <code>Bundle-SymbolicName</code>
     * header is returned.
     * <p>
     * This method may also be used to check whether the file is a bundle at all
     * as it is assumed, that only if the file contains an OSGi bundle will the
     * <code>Bundle-SymbolicName</code> manifest header be set.
     *
     * @param jarFile The file providing the bundle whose symbolic name is
     *            requested.
     * @return The bundle's symbolic name from the
     *         <code>Bundle-SymbolicName</code> manifest header or
     *         <code>null</code> if no manifest exists in the file or the
     *         header is not contained in the manifest. However, if
     *         <code>null</code> is returned, the file may be assumed to not
     *         contain an OSGi bundle.
     */
    protected String getBundleSymbolicName(File jarFile) {

        if (!jarFile.exists()) {
            return null;
        }

        try (JarFile jaf = new JarFile(jarFile)) {
            Manifest manif = jaf.getManifest();
            if (manif == null) {
                getLog().debug("getBundleSymbolicName: Missing manifest in " + jarFile);
                return null;
            }

            String symbName = manif.getMainAttributes().getValue(Constants.BUNDLE_SYMBOLICNAME);
            if (symbName == null) {
                getLog().debug("getBundleSymbolicName: No Bundle-SymbolicName in " + jarFile);
                return null;
            }

            return symbName;
        } catch (IOException ioe) {
            getLog().warn("getBundleSymbolicName: Problem checking " + jarFile, ioe);
        }
        // fall back to not being a bundle
        return null;
    }

    /**
     * @return Returns the combination of <code>sling.url</code> and <code>sling.urlSuffix</code>. Always ends with "/".
     */
    protected URI getTargetURL() {
        final URI targetURL;
        if (slingUrlSuffix != null) {
            targetURL = slingUrl.resolve(slingUrlSuffix);
        } else {
            targetURL = slingUrl;
        }
        return addTrailingSlash(targetURL);
    }

    /**
     * @return Returns the combination of <code>sling.console.url</code> and <code>sling.urlSuffix</code>. Always ends with "/".
     */
    protected URI getConsoleTargetURL() {
        URI targetURL = slingConsoleUrl != null ? slingConsoleUrl : slingUrl;
        if (slingUrlSuffix != null) {
            targetURL = targetURL.resolve(slingUrlSuffix);
        }
        return addTrailingSlash(targetURL);
    }

    public static URI addTrailingSlash(URI targetURL) {
        if (!targetURL.getPath().endsWith("/")) {
            String path = targetURL.getPath() + "/";
            try {
                return new URI(
                        targetURL.getScheme(),
                        targetURL.getUserInfo(),
                        targetURL.getHost(),
                        targetURL.getPort(),
                        path,
                        targetURL.getQuery(),
                        targetURL.getFragment());
            } catch (URISyntaxException e) {
                throw new IllegalStateException("Could not create new URI from existing one", e); // should never happen
            }
        } else {
            return targetURL;
        }
    }

    /**
     * @return Get the http client
     */
    protected CloseableHttpClient getHttpClient() {
        // Generate Basic scheme object
        final BasicScheme basicAuth = new BasicScheme();
        basicAuth.initPreemptive(new UsernamePasswordCredentials(user, password.toCharArray()));

        // restrict to the Sling URL only
        final HttpHost target = new HttpHost(
                getTargetURL().getScheme(),
                getTargetURL().getHost(),
                getTargetURL().getPort());

        return HttpClients.custom()
                .setDefaultRequestConfig(getRequestConfigBuilder().build())
                .addRequestInterceptorFirst(new PreemptiveBasicAuthInterceptor(basicAuth, target, getLog()))
                .build();
    }

    protected RequestConfig.Builder getRequestConfigBuilder() {
        return RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(httpConnectTimeoutSec))
                .setResponseTimeout(Timeout.ofSeconds(httpResponseTimeoutSec));
    }

    protected File resolveArtifact(org.eclipse.aether.artifact.Artifact artifact) throws MojoExecutionException {
        ArtifactRequest req = new ArtifactRequest(
                artifact,
                getRemoteRepositoriesWithUpdatePolicy(repositories, RepositoryPolicy.UPDATE_POLICY_ALWAYS),
                null);
        ArtifactResult resolutionResult;
        try {
            resolutionResult = repoSystem.resolveArtifact(repoSession, req);
            return resolutionResult.getArtifact().getFile();
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("Artifact " + artifact + " could not be resolved.", e);
        }
    }

    private List<RemoteRepository> getRemoteRepositoriesWithUpdatePolicy(
            List<RemoteRepository> repositories, String updatePolicy) {
        List<RemoteRepository> newRepositories = new ArrayList<>();
        for (RemoteRepository repo : repositories) {
            RemoteRepository.Builder builder = new RemoteRepository.Builder(repo);
            RepositoryPolicy newPolicy = new RepositoryPolicy(
                    repo.getPolicy(false).isEnabled(),
                    updatePolicy,
                    repo.getPolicy(false).getChecksumPolicy());
            builder.setPolicy(newPolicy);
            newRepositories.add(builder.build());
        }
        return newRepositories;
    }

    private static final class PreemptiveBasicAuthInterceptor implements HttpRequestInterceptor {

        private final BasicScheme basicAuth;
        private final HttpHost targetHost;
        private final Log log;

        public PreemptiveBasicAuthInterceptor(BasicScheme basicAuth, HttpHost targetHost, Log log) {
            super();
            this.basicAuth = basicAuth;
            this.targetHost = targetHost;
            this.log = log;
        }

        @Override
        public void process(HttpRequest request, EntityDetails entity, HttpContext context)
                throws HttpException, IOException {
            if (!(context instanceof HttpClientContext)) {
                throw new IllegalStateException(
                        "This interceptor only supports HttpClientContext but context is of type "
                                + context.getClass());
            }
            HttpClientContext httpClientContext = (HttpClientContext) context;

            log.debug("Adding preemptive authentication to request for target host " + targetHost);
            // as the AuthExchange object is already retrieved by the client when the interceptor kicks in, it needs to
            // modify the existing object
            httpClientContext.getAuthExchange(targetHost).select(basicAuth);
        }
    }
}

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
import java.net.URI;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.sling.maven.bundlesupport.fsresource.FileVaultXmlMounter;
import org.apache.sling.maven.bundlesupport.fsresource.SlingInitialContentMounter;

/**
 * Remove OSGi configurations for the
 * <a href="https://sling.apache.org/documentation/bundles/accessing-filesystem-resources-extensions-fsresource.html">Apache Sling File System Resource Provider</a>.
 * In case a bundle file is supplied via {@link AbstractFsMountMojo#bundleFileName} the configuration for its <a href="https://sling.apache.org/documentation/bundles/content-loading-jcr-contentloader.html#initial-content-loading">initial content</a> is created.
 * Otherwise it tries to detect a FileVault content package layout starting at {@link AbstractFsMountMojo#fileVaultJcrRootFile} or the project's resource directories and potentially creates a configuration for each path in the package's {@code filter.xml}.
 * @since 2.2.0
 */
@Mojo(name = "fsunmount", requiresProject = true)
public class FsUnMountMojo extends AbstractFsMountMojo {

    @Override
    protected void configureSlingInitialContent(
            CloseableHttpClient httpClient, final URI targetUrl, final File bundleFile) throws MojoExecutionException {
        new SlingInitialContentMounter(getLog(), httpClient, getRequestConfigBuilder(), project).unmount(targetUrl);
    }

    @Override
    protected void configureFileVaultXml(
            CloseableHttpClient httpClient, URI targetUrl, File jcrRootFile, File filterXmlFile)
            throws MojoExecutionException {
        new FileVaultXmlMounter(getLog(), httpClient, getRequestConfigBuilder(), project)
                .unmount(targetUrl, jcrRootFile, filterXmlFile);
    }

    @Override
    protected void ensureBundlesInstalled(CloseableHttpClient httpClient, URI targetUrl) throws MojoExecutionException {
        // nothing to do on uninstall
    }
}

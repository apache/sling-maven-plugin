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
package org.apache.sling.maven.bundlesupport.fsresource;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.sling.commons.osgi.ManifestHeader;
import org.apache.sling.commons.osgi.ManifestHeader.Entry;

/**
 * Manages OSGi configurations for File System Resource Provider for Sling-Initial-Content.
 */
public final class SlingInitialContentMounter {

    /** Header containing the sling initial content information. */
    private static final String HEADER_INITIAL_CONTENT = "Sling-Initial-Content";

    private final Log log;
    private final MavenProject project;
    private final FsMountHelper helper;

    public SlingInitialContentMounter(
            Log log, CloseableHttpClient httpClient, RequestConfig.Builder requestConfigBuilder, MavenProject project) {
        this.log = log;
        this.project = project;
        this.helper = new FsMountHelper(log, httpClient, requestConfigBuilder, project);
    }

    /**
     * Add configurations to a running OSGi instance for initial content.
     * @param consoleTargetUrl The web console base url
     * @param bundleFile The artifact (bundle)
     * @throws MojoExecutionException Exception
     */
    public void mount(final URI consoleTargetUrl, final File bundleFile) throws MojoExecutionException {
        // first, let's get the manifest and see if initial content is configured
        ManifestHeader header = null;
        try {
            final Manifest mf = getManifest(bundleFile);
            final String value = mf.getMainAttributes().getValue(HEADER_INITIAL_CONTENT);
            if (value == null) {
                log.warn("Bundle has no initial content - no file system provider config created.");
                return;
            }
            header = ManifestHeader.parse(value);
            if (header == null || header.getEntries().length == 0) {
                log.warn("Unable to parse header or header is empty: " + value);
                return;
            }
        } catch (IOException ioe) {
            throw new MojoExecutionException("Unable to read manifest from file " + bundleFile, ioe);
        }

        log.info("Trying to configure file system provider for Sling initial content...");
        // quick check if resources are configured
        final List<Resource> resources = project.getResources();
        if (resources == null || resources.isEmpty()) {
            throw new MojoExecutionException("No resources configured for this project.");
        }

        final List<FsResourceConfiguration> cfgs = new ArrayList<>();
        final Entry[] entries = header.getEntries();
        for (final Entry entry : entries) {
            String path = entry.getValue();
            if (path != null && !path.endsWith("/")) {
                path += "/";
            }
            // check if we should ignore this
            final String ignoreValue = entry.getDirectiveValue("maven:mount");
            if (ignoreValue != null && ignoreValue.equalsIgnoreCase("false")) {
                log.debug("Ignoring " + path);
                continue;
            }
            String installPath = entry.getDirectiveValue("path");
            if (installPath == null) {
                installPath = "/";
            }
            // search the path in the resources (usually this should be the first resource
            // entry but this might be reconfigured
            File dir = null;
            final Iterator<Resource> i = resources.iterator();
            while (dir == null && i.hasNext()) {
                final Resource rsrc = i.next();
                String child = path;
                // if resource mapping defines a target path: remove target path from checked resource path
                String targetPath = rsrc.getTargetPath();
                if (targetPath != null && !targetPath.endsWith("/")) {
                    targetPath = targetPath + "/";
                }
                log.debug("Checking if project resource '" + rsrc.getDirectory() + "' with target path '" + targetPath
                        + "' is a potential mount point for " + path + " ...");
                if (targetPath != null && path != null && path.startsWith(targetPath)) {
                    child = child.substring(targetPath.length());
                }
                dir = new File(rsrc.getDirectory(), child);
                if (!dir.exists()) {
                    dir = null;
                }
            }
            if (dir == null) {
                throw new MojoExecutionException("No resource entry found containing " + path);
            }
            // check for root mapping - which we don't support atm
            if ("/".equals(installPath)) {
                throw new MojoExecutionException(
                        "Mapping to root path not supported by fs provider at the moment. Please adapt your initial content configuration.");
            }

            // check further initial content directives
            StringBuilder importOptions = new StringBuilder();
            String overwriteValue = entry.getDirectiveValue("overwrite");
            if (StringUtils.isNotBlank(overwriteValue)) {
                importOptions.append("overwrite:=" + overwriteValue);
            }
            String ignoreImportProvidersValue = entry.getDirectiveValue("ignoreImportProviders");
            if (StringUtils.isNotBlank(overwriteValue)) {
                if (importOptions.length() > 0) {
                    importOptions.append(";");
                }
                importOptions.append("ignoreImportProviders:=\"" + ignoreImportProvidersValue + "\"");
            }
            cfgs.add(new FsResourceConfiguration()
                    .fsMode(FsMode.INITIAL_CONTENT)
                    .fsRootPath(dir.getAbsoluteFile())
                    .resourceRootPath(installPath)
                    .initialContentImportOptions(importOptions.toString()));
            log.info("Created new configuration for resource path " + installPath);
        }

        if (!cfgs.isEmpty()) {
            helper.addConfigurations(consoleTargetUrl, cfgs);
        }
    }

    /**
     * Remove configurations from a running OSGi instance for initial content.
     * @param consoleTargetUrl The web console base url
     * @throws MojoExecutionException Exception
     */
    public void unmount(final URI consoleTargetUrl) throws MojoExecutionException {
        log.info("Removing file system provider configurations...");

        // remove all current configs for this project
        final Map<String, FsResourceConfiguration> oldConfigs = helper.getCurrentConfigurations(consoleTargetUrl);
        helper.removeConfigurations(consoleTargetUrl, oldConfigs);
    }

    /**
     * Get the manifest from the File.
     * @param bundleFile The bundle jar
     * @return The manifest.
     * @throws IOException Exception
     */
    private Manifest getManifest(final File bundleFile) throws IOException {
        try (JarFile file = new JarFile(bundleFile)) {
            return file.getManifest();
        }
    }
}

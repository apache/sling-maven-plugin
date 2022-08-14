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
import java.util.List;
import java.util.Map;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

/**
 * Manages OSGi configurations for File System Resource Provider for File Vault XML.
 */
public final class FileVaultXmlMounter {
    
    private final Log log;
    private final FsMountHelper helper;

    public FileVaultXmlMounter(Log log, CloseableHttpClient httpClient, RequestConfig.Builder requestConfigBuilder, MavenProject project) {
        this.log = log;
        this.helper = new FsMountHelper(log, httpClient, requestConfigBuilder, project);
    }

    /**
     * Add configurations to a running OSGi instance for FileVault XML
     * @param consoleTargetUrl The web console base url
     * @param jcrRootFile jcr_root directory
     * @param filterXmlFile FileVault Filter XML file
     * @throws MojoExecutionException Exception
     */
    public void mount(final URI consoleTargetUrl, final File jcrRootFile, final File filterXmlFile) throws MojoExecutionException {
        log.info("Trying to configure file system provider for FileVault...");

        // create config for each path defined in filter
        final List<FsResourceConfiguration> cfgs = new ArrayList<>();
        WorkspaceFilter workspaceFilter = getWorkspaceFilter(filterXmlFile);
        for (PathFilterSet filterSet : workspaceFilter.getFilterSets()) {
            cfgs.add(new FsResourceConfiguration()
                    .fsMode(FsMode.FILEVAULT_XML)
                    .fsRootPath(jcrRootFile.getAbsoluteFile())
                    .resourceRootPath(filterSet.getRoot())
                    .fileVaultFilterXml(filterXmlFile.getAbsolutePath()));
            log.info("Created new configuration for resource path " + filterSet.getRoot());
        }
     
        if (!cfgs.isEmpty()) {
            helper.addConfigurations(consoleTargetUrl, cfgs);
        }
    }
    
    /**
     * Remove configurations to a running OSGi instance for FileVault XML
     * @param consoleTargetUrl The web console base url
     * @param jcrRootFile jcr_root directory
     * @param filterXmlFile FileVault Filter XML file
     * @throws MojoExecutionException Exception
     */
    public void unmount(final URI consoleTargetUrl, final File jcrRootFile, final File filterXmlFile) throws MojoExecutionException {
        log.info("Removing file system provider configurations...");

        // remove all current configs for this project
        final Map<String,FsResourceConfiguration> oldConfigs = helper.getCurrentConfigurations(consoleTargetUrl);
        helper.removeConfigurations(consoleTargetUrl, oldConfigs);
    }
        
    private WorkspaceFilter getWorkspaceFilter(final File filterXmlFile) throws MojoExecutionException {
        try {
            DefaultWorkspaceFilter workspaceFilter = new DefaultWorkspaceFilter();
            workspaceFilter.load(filterXmlFile);
            return workspaceFilter;
        }
        catch (IOException | ConfigurationException ex) {
            throw new MojoExecutionException("Unable to parse workspace filter: " + filterXmlFile.getPath(), ex);
        }
    }
    
}

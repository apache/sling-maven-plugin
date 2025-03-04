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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.sling.maven.bundlesupport.JsonSupport;
import org.apache.sling.maven.bundlesupport.deploy.method.ResponseCodeEnforcingResponseHandler;

/**
 * Manages OSGi configurations for File System Resource Provider.
 */
final class FsMountHelper {

    /** The fs resource provider factory. */
    private static final String FS_FACTORY = "org.apache.sling.fsprovider.internal.FsResourceProvider";
    /** Http header for content type. */
    private static final String HEADER_CONTENT_TYPE = "Content-Type";

    private static final String PROPERTY_FSMODE = "provider.fs.mode";
    private static final String PROPERTY_ROOT = "provider.root";
    private static final String PROPERTY_ROOTS = "provider.roots";
    private static final String PROPERTY_PATH = "provider.file";
    private static final String PROPERTY_INITIAL_CONTENT_IMPORT_OPTIONS = "provider.initial.content.import.options";
    private static final String PROPERTY_FILEVAULT_FILTER_XML = "provider.filevault.filterxml.path";

    private final Log log;
    private final CloseableHttpClient httpClient;
    private final RequestConfig.Builder requestConfigBuilder;
    private final MavenProject project;

    public FsMountHelper(
            Log log, CloseableHttpClient httpClient, RequestConfig.Builder requestConfigBuilder, MavenProject project) {
        this.log = log;
        this.httpClient = httpClient;
        this.requestConfigBuilder = requestConfigBuilder;
        this.project = project;
    }

    /**
     * Adds as set of new configurations and removes old ones.
     */
    public void addConfigurations(final URI targetUrl, Collection<FsResourceConfiguration> cfgs)
            throws MojoExecutionException {
        final Map<String, FsResourceConfiguration> oldConfigs = getCurrentConfigurations(targetUrl);

        for (FsResourceConfiguration cfg : cfgs) {
            log.debug("Found mapping " + cfg.getFsRootPath() + " to " + cfg.getResourceRootPath());

            // check if this is already configured
            boolean found = false;
            final Iterator<Map.Entry<String, FsResourceConfiguration>> entryIterator =
                    oldConfigs.entrySet().iterator();
            while (!found && entryIterator.hasNext()) {
                final Map.Entry<String, FsResourceConfiguration> current = entryIterator.next();
                final FsResourceConfiguration oldcfg = current.getValue();
                log.debug("Comparing " + oldcfg.getFsRootPath() + " with " + oldcfg);
                if (Objects.equals(oldcfg.getFsRootPath(), cfg.getFsRootPath())) {
                    if (cfg.equals(oldcfg)) {
                        log.info("Using existing configuration for " + cfg.getFsRootPath() + " mounted at "
                                + cfg.getResourceRootPath());
                        found = true;
                    } else {
                        // remove old config
                        log.info("Removing old configuration for " + oldcfg);
                        removeConfiguration(targetUrl, current.getKey());
                    }
                    entryIterator.remove();
                }
            }
            if (!found) {
                log.debug("Adding new configuration for " + cfg.getFsRootPath() + " mounted at "
                        + cfg.getResourceRootPath());
                addConfiguration(targetUrl, cfg);
                log.info("Added new configuration for resource path " + cfg.getResourceRootPath() + " to server");
            }
        }

        // finally remove old configs
        removeConfigurations(targetUrl, oldConfigs);
    }

    /**
     * Add a new configuration for the file system provider
     */
    private void addConfiguration(final URI consoleTargetUrl, FsResourceConfiguration cfg)
            throws MojoExecutionException {
        final URI postUrl = consoleTargetUrl.resolve("configMgr/" + FS_FACTORY);
        final HttpPost post = new HttpPost(postUrl);
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("apply", "true"));
        params.add(new BasicNameValuePair("factoryPid", FS_FACTORY));
        /*
         * The pid parameter is mandatory but is replaced with another value upon save() for factories
         * For that it must have the magic value from
         * https://github.com/apache/felix-dev/blob/6603d69977f4cea8b9b9dd2faf5d320906b43368/webconsole/src/main/java/org/apache/felix/webconsole/internal/configuration/ConfigurationUtil.java#L36
         * This value is also used for performing a redirect and is no valid URI, therefore disable redirect handling
         */
        params.add(new BasicNameValuePair(
                "pid", "[Temporary PID replaced by real PID upon save]")); // this is replaced with a generated one,
        // upon save, still this is used for the
        // redirect
        Map<String, String> props = toMap(cfg);
        for (Map.Entry<String, String> entry : props.entrySet()) {
            params.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        }
        params.add(new BasicNameValuePair("propertylist", StringUtils.join(props.keySet(), ",")));
        post.setEntity(new UrlEncodedFormEntity(params));

        // do not follow redirects
        RequestConfig requestConfig =
                requestConfigBuilder.setRedirectsEnabled(false).build();
        HttpClientContext httpContext = HttpClientContext.create();
        httpContext.setRequestConfig(requestConfig);
        try {
            // accept also 302
            httpClient.execute(
                    post, httpContext, new ResponseCodeEnforcingResponseHandler(HttpStatus.SC_MOVED_TEMPORARILY));
            log.debug("New configuration created POST to " + postUrl);
        } catch (IOException ex) {
            throw new MojoExecutionException("Configuration on " + postUrl + " failed, cause: " + ex.getMessage(), ex);
        }
    }

    private Map<String, String> toMap(FsResourceConfiguration cfg) {
        Map<String, String> props = new HashMap<>();
        if (cfg.getFsMode() != null) {
            props.put(PROPERTY_FSMODE, cfg.getFsMode().name());
        }
        if (cfg.getFsRootPath() != null) {
            props.put(PROPERTY_PATH, cfg.getFsRootPath().toString());
        }
        if (cfg.getResourceRootPath() != null) {
            // save property value to both "provider.roots" and "provider.root" because the name has changed between
            // fsresource 1.x and 2.x
            props.put(PROPERTY_ROOT, cfg.getResourceRootPath());
            props.put(PROPERTY_ROOTS, cfg.getResourceRootPath());
        }
        if (cfg.getInitialContentImportOptions() != null) {
            props.put(PROPERTY_INITIAL_CONTENT_IMPORT_OPTIONS, cfg.getInitialContentImportOptions());
        }
        if (cfg.getStringVaultFilterXml() != null) {
            props.put(PROPERTY_FILEVAULT_FILTER_XML, cfg.getStringVaultFilterXml());
        }
        return props;
    }

    /**
     * Remove all configurations contained in the given config map.
     */
    public void removeConfigurations(final URI targetUrl, Map<String, FsResourceConfiguration> configs)
            throws MojoExecutionException {
        for (Map.Entry<String, FsResourceConfiguration> current : configs.entrySet()) {
            log.debug("Removing configuration for " + current.getValue());
            // remove old config
            removeConfiguration(targetUrl, current.getKey());
            log.info("Removed configuration for resource path "
                    + current.getValue().getResourceRootPath() + " (PID: " + current.getKey() + ") from server");
        }
    }

    /**
     * Remove configuration.
     */
    private void removeConfiguration(final URI consoleTargetUrl, final String pid) throws MojoExecutionException {
        final URI postUrl = consoleTargetUrl.resolve("configMgr/" + pid);
        final HttpPost post = new HttpPost(postUrl);
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("apply", "true"));
        params.add(new BasicNameValuePair("delete", "true"));
        post.setEntity(new UrlEncodedFormEntity(params));

        try {
            JsonObject expectedJsonResponse =
                    Json.createObjectBuilder().add("status", true).build();
            // https://github.com/apache/felix-dev/blob/6603d69977f4cea8b9b9dd2faf5d320906b43368/webconsole/src/main/java/org/apache/felix/webconsole/internal/configuration/ConfigManager.java#L145
            httpClient.execute(
                    post,
                    new ResponseCodeEnforcingResponseHandler(
                            "application/json",
                            response -> JsonSupport.parseObject(response).equals(expectedJsonResponse),
                            HttpStatus.SC_OK));
        } catch (IOException ex) {
            throw new MojoExecutionException(
                    "Removing configuration at " + postUrl + " failed, cause: " + ex.getMessage(), ex);
        }
    }

    /**
     * Return all file provider configs for this project
     * @param consoleTargetUrl The web console base url
     * @return A map (may be empty) with the pids as keys and the configurations as values
     * @throws MojoExecutionException
     */
    public Map<String, FsResourceConfiguration> getCurrentConfigurations(final URI consoleTargetUrl)
            throws MojoExecutionException {
        final Map<String, FsResourceConfiguration> result = new HashMap<>();
        final URI getUrl = consoleTargetUrl.resolve("configMgr/(service.factoryPid=" + FS_FACTORY + ").json");
        log.debug("Getting current file provider configurations via GET from " + getUrl);
        final HttpGet get = new HttpGet(getUrl);

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            final int status = response.getCode();
            if (status == HttpStatus.SC_OK) {
                String contentType = response.getHeader(HEADER_CONTENT_TYPE).getValue();
                int pos = contentType.indexOf(';');
                if (pos != -1) {
                    contentType = contentType.substring(0, pos);
                }
                if (!JsonSupport.JSON_MIME_TYPE.equals(contentType)) {
                    log.debug("Response type from web console is not JSON, but " + contentType);
                    throw new MojoExecutionException("The Apache Felix Web Console is too old to mount "
                            + "the initial content through file system provider configs. "
                            + "Either upgrade the web console or disable this feature.");
                }
                final String jsonText = EntityUtils.toString(response.getEntity());
                try {
                    JsonArray array = JsonSupport.parseArray(jsonText);
                    for (int i = 0; i < array.size(); i++) {
                        final JsonObject obj = array.getJsonObject(i);
                        final String pid = obj.getString("pid");
                        final JsonObject properties = obj.getJsonObject("properties");
                        final String fsmode = getConfigPropertyValue(properties, PROPERTY_FSMODE);
                        final String path = getConfigPropertyValue(properties, PROPERTY_PATH);
                        final String initialContentImportOptions =
                                getConfigPropertyValue(properties, PROPERTY_INITIAL_CONTENT_IMPORT_OPTIONS);
                        final String fileVaultFilterXml =
                                getConfigPropertyValue(properties, PROPERTY_FILEVAULT_FILTER_XML);
                        String root = getConfigPropertyValue(properties, PROPERTY_ROOTS);
                        if (root == null) {
                            root = getConfigPropertyValue(properties, PROPERTY_ROOT);
                        }
                        if (path != null
                                && path.startsWith(this.project.getBasedir().getAbsolutePath())
                                && root != null) {
                            FsResourceConfiguration cfg = new FsResourceConfiguration()
                                    .fsMode(fsmode)
                                    .resourceRootPath(root)
                                    .fsRootPath(new File(path))
                                    .initialContentImportOptions(initialContentImportOptions)
                                    .fileVaultFilterXml(fileVaultFilterXml);
                            log.debug("Found configuration with pid: " + pid + ", " + cfg);
                            result.put(pid, cfg);
                        }
                    }
                    if (array.isEmpty()) {
                        log.info("Found no existing configurations for factory PID " + FS_FACTORY);
                    }
                } catch (JsonException ex) {
                    throw new MojoExecutionException(
                            "Reading configuration from " + getUrl + " failed, cause: " + ex.getMessage(), ex);
                }
            } else {
                throw new HttpResponseException(
                        response.getCode(),
                        "Unexpected status code " + response.getCode() + ": " + response.getReasonPhrase());
            }
        } catch (IOException | ProtocolException ex) {
            throw new MojoExecutionException(
                    "Reading configuration from " + getUrl + " failed, cause: " + ex.getMessage(), ex);
        }
        return result;
    }

    private String getConfigPropertyValue(JsonObject obj, String subKey) {
        if (obj.containsKey(subKey)) {
            JsonObject subObj = obj.getJsonObject(subKey);
            if (subObj.containsKey("value")) {
                return subObj.getString("value");
            } else if (subObj.containsKey("values")) {
                JsonArray array = subObj.getJsonArray("values");
                if (array.size() > 0) {
                    // use only first property value from array
                    return array.getString(0);
                }
            }
        }
        return null;
    }
}

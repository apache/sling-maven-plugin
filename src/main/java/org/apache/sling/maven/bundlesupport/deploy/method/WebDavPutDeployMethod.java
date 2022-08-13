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
package org.apache.sling.maven.bundlesupport.deploy.method;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.apache.sling.maven.bundlesupport.deploy.DeployContext;
import org.apache.sling.maven.bundlesupport.deploy.DeployMethod;

public class WebDavPutDeployMethod implements DeployMethod {

    @Override
    public void deploy(URI targetURL, File file, String bundleSymbolicName, DeployContext context) throws IOException {
        try {
            performPut(targetURL, file, context);
        } catch (HttpResponseException e) {
            if (e.getStatusCode() == HttpStatus.SC_CONFLICT) {
                context.getLog().debug("Bundle not installed due missing parent folders. Attempting to create parent structure.");
                createIntermediaryPaths(targetURL, context);

                context.getLog().debug("Re-attempting bundle install after creating parent folders.");
                performPut(targetURL, file, context);
            }
        }
    }

    @Override
    public void undeploy(URI targetURL, String bundleName, DeployContext context) throws IOException {
        final HttpDelete delete = new HttpDelete(SlingPostDeployMethod.getURLWithFilename(targetURL, bundleName));
        // sanity check on response
        // must answer 204 (no content)
        // https://github.com/apache/jackrabbit/blob/88490006e6bdba0b0ad52d209b1bfa040477c2ec/jackrabbit-webdav/src/main/java/org/apache/jackrabbit/webdav/server/AbstractWebdavServlet.java#L763
        Integer status = context.getHttpClient().execute(delete, new ResponseCodeEnforcingResponseHandler(HttpStatus.SC_NO_CONTENT));
        context.getLog().debug("Received status code " + status);
    }

    private void performPut(URI targetURL, File file, DeployContext context) throws IOException {
        HttpPut filePut = new HttpPut(SlingPostDeployMethod.getURLWithFilename(targetURL, file.getName()));
        filePut.setEntity(new FileEntity(file, ContentType.create(context.getMimeType())));
        // sanity check on response (has really the right servlet answered?)
        // check status code, must be either 201 (created) for new resources or 204 (no content) for updated existing resources
        // https://github.com/apache/jackrabbit/blob/88490006e6bdba0b0ad52d209b1bfa040477c2ec/jackrabbit-webdav/src/main/java/org/apache/jackrabbit/webdav/server/AbstractWebdavServlet.java#L707
        Integer status = context.getHttpClient().execute(filePut, new ResponseCodeEnforcingResponseHandler(HttpStatus.SC_NO_CONTENT, HttpStatus.SC_CREATED));
        context.getLog().debug("Received status code " + status);
    }

    private void performHead(URI uri, DeployContext context) throws IOException {
        HttpHead head = new HttpHead(uri);
        context.getHttpClient().execute(head, new BasicHttpClientResponseHandler());
        // this never returns a body
    }

    private void performMkCol(URI uri, DeployContext context) throws IOException {
        WebDavMkCol mkCol = new WebDavMkCol(uri);
        String response = context.getHttpClient().execute(mkCol, new BasicHttpClientResponseHandler());
        context.getLog().info("Received response: " + response);
        // must be 201 (created)
        // https://github.com/apache/jackrabbit/blob/88490006e6bdba0b0ad52d209b1bfa040477c2ec/jackrabbit-webdav/src/main/java/org/apache/jackrabbit/webdav/server/AbstractWebdavServlet.java#L746
        
    }

    private void createIntermediaryPaths(URI targetURL, DeployContext context) throws IOException {
        // extract all intermediate URIs (longest one first)
        List<URI> intermediateUris = IntermediateUrisExtractor.extractIntermediateUris(targetURL);

        // 1. go up to the node in the repository which exists already (HEAD request towards the root node)
        URI existingIntermediateUri = null;
        // go through all intermediate URIs (longest first)
        for (URI intermediateUri : intermediateUris) {
            // until one is existing
            try {
                try {
                    performHead(intermediateUri, context);
                    // if the result is 200 (in case the default get servlet allows returning index files)
                    existingIntermediateUri = intermediateUri;
                    break;
                } catch (HttpResponseException e) {
                    // or 403 (in case the default get servlet does no allow returning index files)
                    if (e.getStatusCode() == HttpStatus.SC_FORBIDDEN) {
                        // we assume that the intermediate node exists already
                        existingIntermediateUri = intermediateUri;
                        break;
                    } else if (e.getStatusCode() != HttpStatus.SC_NOT_FOUND) {
                        throw e;
                    }
                }
            }
            catch (IOException e) {
                throw new IOException("Failed getting intermediate path at " + intermediateUri + "."
                        + " Reason: " + e.getMessage(), e);
            }
        }

        if (existingIntermediateUri == null) {
            throw new IOException(
                    "Could not find any intermediate path up until the root of " + targetURL + ".");
        }

        // 2. now create from that level on each intermediate node individually towards the target path
        int startOfInexistingIntermediateUri = intermediateUris.indexOf(existingIntermediateUri);
        if (startOfInexistingIntermediateUri == -1) {
            throw new IllegalStateException(
                    "Could not find intermediate uri " + existingIntermediateUri + " in the list");
        }

        for (int index = startOfInexistingIntermediateUri - 1; index >= 0; index--) {
            // use MKCOL to create the intermediate paths
            URI intermediateUri = intermediateUris.get(index);
            try {
                performMkCol(intermediateUri, context);
                context.getLog().debug("Intermediate path at " + intermediateUri + " successfully created");
            } catch (IOException e) {
                throw new IOException("Failed creating intermediate path at '" + intermediateUri + "'."
                        + " Reason: " + e.getMessage());
            }
        }
    }

}

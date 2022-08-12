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

import static org.apache.sling.maven.bundlesupport.JsonSupport.JSON_MIME_TYPE;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.sling.maven.bundlesupport.deploy.DeployContext;
import org.apache.sling.maven.bundlesupport.deploy.DeployMethod;

public class SlingPostDeployMethod implements DeployMethod {

    @Override
    public void deploy(URI targetURL, File file, String bundleSymbolicName, DeployContext context) throws IOException {
        /* truncate off trailing slash as this has special behaviorisms in
         * the SlingPostServlet around created node name conventions */
        targetURL = stripTrailingSlash(targetURL);
        
        // append pseudo path after root URL to not get redirected for nothing
        final HttpPost filePost = new HttpPost(targetURL);

        /* Request JSON response from Sling instead of standard HTML, to
         * reduce the payload size (if the PostServlet supports it). */
        filePost.setHeader("Accept", JSON_MIME_TYPE);
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addTextBody("*@TypeHint", "nt:file");
        builder.addBinaryBody("*", file, ContentType.create(context.getMimeType()), file.getName());
        filePost.setEntity(builder.build());
        
        String response = context.getHttpClient().execute(filePost, new BasicHttpClientResponseHandler());
        context.getLog().debug("Received response: " + response);
    }

    @Override
    public void undeploy(URI targetURL, String bundleName, DeployContext context) throws IOException {
        final HttpPost post = new HttpPost(getURLWithFilename(targetURL, bundleName));

        List<NameValuePair> params = new ArrayList<>();
        // Request JSON response from Sling instead of standard HTML
        post.setHeader("Accept", JSON_MIME_TYPE);
        params.add(new BasicNameValuePair(":operation", "delete"));
        post.setEntity(new UrlEncodedFormEntity(params));
        
        String response = context.getHttpClient().execute(post, new BasicHttpClientResponseHandler());
        context.getLog().debug("Received response: " + response);
    }

    /**
     * Returns the URL with the filename appended to it.
     * @param targetURL the original requested targetURL to append fileName to
     * @param fileName the name of the file to append to the targetURL.
     */
    static URI getURLWithFilename(URI targetURL, String fileName) {
        return targetURL.resolve(fileName);
    }

    static URI stripTrailingSlash(URI targetURL) {
        if (targetURL.getPath().endsWith("/")) {
            String path = targetURL.getPath().substring(0, targetURL.getPath().length()-1);
            try {
                return new URI(targetURL.getScheme(), targetURL.getUserInfo(), targetURL.getHost(), targetURL.getPort(), path, targetURL.getQuery(), targetURL.getFragment());
            } catch (URISyntaxException e) {
                throw new IllegalStateException("Could not create new URI from existing one", e); // should never happen
            }
        } else {
            return targetURL;
        }
    }
}

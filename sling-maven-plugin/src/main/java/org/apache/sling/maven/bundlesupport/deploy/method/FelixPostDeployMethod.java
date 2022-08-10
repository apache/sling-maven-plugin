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
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.sling.maven.bundlesupport.deploy.DeployContext;
import org.apache.sling.maven.bundlesupport.deploy.DeployMethod;

public class FelixPostDeployMethod implements DeployMethod {

    @Override
    public void deploy(URI targetURL, File file, String bundleSymbolicName, DeployContext context) throws IOException {

        // append pseudo path after root URL to not get redirected for nothing
        final HttpPost filePost = new HttpPost(targetURL.resolve("/install"));

        // set referrer
        filePost.setHeader("referer", "about:blank");
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addTextBody("action", "install");
        builder.addTextBody("_noredir_", "_noredir_");
        builder.addTextBody("bundlestartlevel", context.getBundleStartLevel());
        if (context.isBundleStart()) {
            builder.addTextBody("bundlestart", "start");
        }
        if (context.isRefreshPackages()) {
            builder.addTextBody("refreshPackages", "true");
        }
        builder.addBinaryBody(file.getName(), file);
        filePost.setEntity(builder.build());
        String response = context.getHttpClient().execute(filePost, new BasicHttpClientResponseHandler());
        context.getLog().debug("Received response: " + response);
    }

    @Override
    public void undeploy(URI targetURL, File file, String bundleSymbolicName, DeployContext context) throws IOException {
        final HttpPost post = new HttpPost(targetURL.resolve("/bundles/" + bundleSymbolicName));
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("action", "uninstall"));
        post.setEntity(new UrlEncodedFormEntity(params));

        String response = context.getHttpClient().execute(post, new BasicHttpClientResponseHandler());
        context.getLog().debug("Received response: " + response);
    }

}

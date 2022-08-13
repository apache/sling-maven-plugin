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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;

/**
 * Throws {@link HttpResponseException} for all response codes except for the accepted ones.
 * In addition optionally checks for a certain content type and content of the response.
 * Returns the response code if all checks pass.
 *
 */
public final class ResponseCodeEnforcingResponseHandler implements HttpClientResponseHandler<Integer> {

    private final List<Integer> allowedCodes;
    private final String expectedContentType; // may be null
    private final Predicate<String> responseStringPredicate; // may be null

    public ResponseCodeEnforcingResponseHandler(Integer... allowedCodes) {
        this(null, allowedCodes);
    }

    public ResponseCodeEnforcingResponseHandler(String expectedContentType, Integer... allowedCodes) {
        this(expectedContentType, null, allowedCodes);
    }

    public ResponseCodeEnforcingResponseHandler(String expectedContentType, Predicate<String> responseStringPredicate, Integer... allowedCodes) {
        this.expectedContentType = expectedContentType;
        this.responseStringPredicate = responseStringPredicate;
        this.allowedCodes = Arrays.asList(allowedCodes);
    }

    @Override
    public Integer handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
        final HttpEntity entity = response.getEntity();
        try {
            if (responseStringPredicate != null) {
                String responseContent = EntityUtils.toString(entity);
                if (!responseStringPredicate.test(responseContent)) {
                    throw new ClientProtocolException("Unexpected response content returned: " + responseContent);
                }
            }
            if (!allowedCodes.contains(response.getCode())) {
                throw new HttpResponseException(response.getCode(), "Unexpected response code " + response.getCode() + ": " + response.getReasonPhrase());
            }
            String actualContentType = Optional.ofNullable(response.getHeader(HttpHeaders.CONTENT_TYPE)).map(Header::getValue).orElse(null);
            if (expectedContentType != null && expectedContentType.equals(actualContentType)) {
                throw new ClientProtocolException("Unexpected content type returned, expected " + expectedContentType + " but was " + actualContentType);
            }
        } finally {
            EntityUtils.consume(entity);
        }
        return response.getCode();
    }
}
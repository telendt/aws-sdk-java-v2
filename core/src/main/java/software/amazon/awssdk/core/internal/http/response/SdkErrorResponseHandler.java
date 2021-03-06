/*
 * Copyright 2010-2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.core.internal.http.response;

import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.core.exception.ErrorType;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.core.http.HttpResponse;
import software.amazon.awssdk.core.http.HttpResponseHandler;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.http.HttpStatusFamily;

/**
 * Wrapper around protocol specific error handler to deal with some default scenarios and fill in common information.
 */
@SdkInternalApi
public class SdkErrorResponseHandler implements HttpResponseHandler<SdkException> {

    private final HttpResponseHandler<? extends SdkException> delegate;

    public SdkErrorResponseHandler(HttpResponseHandler<? extends SdkException> errorResponseHandler) {
        this.delegate = errorResponseHandler;
    }

    @Override
    public SdkServiceException handle(HttpResponse response,
                                      ExecutionAttributes executionAttributes) throws Exception {
        final SdkServiceException exception = (SdkServiceException) handleServiceException(response, executionAttributes);
        exception.statusCode(response.getStatusCode());
        exception.serviceName(executionAttributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME));
        return exception;
    }

    private SdkException handleServiceException(HttpResponse response, ExecutionAttributes executionAttributes) throws Exception {
        final int statusCode = response.getStatusCode();
        try {
            return delegate.handle(response, executionAttributes);
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            // If the errorResponseHandler doesn't work, then check for error responses that don't have any content
            if (statusCode == HttpStatusCode.REQUEST_TOO_LONG) {
                SdkServiceException exception = new SdkServiceException("Request entity too large");
                exception.statusCode(statusCode);
                exception.errorCode("Request entity too large");
                exception.errorType(ErrorType.CLIENT);
                return exception;
            } else if (HttpStatusFamily.of(statusCode) == HttpStatusFamily.SERVER_ERROR) {
                SdkServiceException exception = new SdkServiceException(response.getStatusText());
                exception.statusCode(statusCode);
                exception.errorType(ErrorType.SERVICE);
                exception.errorCode(response.getStatusText());
                return exception;
            } else {
                throw e;
            }
        }
    }

    @Override
    public boolean needsConnectionLeftOpen() {
        return delegate.needsConnectionLeftOpen();
    }
}

/**
 * Copyright (C) 2006-2019 Talend Inc. - www.talend.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.talend.sdk.component.runtime.server.vault.proxy.endpoint.jaxrs;

import static org.talend.sdk.component.runtime.server.vault.proxy.endpoint.jaxrs.Responses.decorate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.client.CompletionStageRxInvoker;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.talend.sdk.component.runtime.server.vault.proxy.service.http.Http;

@ApplicationScoped
public class RequestForwarder {

    private final Object[] emptyObjectsArray = new Object[0];

    private final MediaType[] emptyMediaTypesArray = new MediaType[0];

    @Inject
    @Http(Http.Type.TALEND_COMPONENT_KIT)
    private WebTarget client;

    @Inject
    @Context
    private Request request;

    @Inject
    @Context
    private UriInfo uriInfo;

    @Inject
    @Context
    private HttpHeaders headers;

    public CompletionStage<Response> forward() {
        WebTarget target = client.path(uriInfo.getPath());
        for (final Map.Entry<String, List<String>> query : uriInfo.getQueryParameters().entrySet()) {
            target = target.queryParam(query.getKey(), query.getValue().toArray(emptyObjectsArray));
        }
        final MultivaluedMap<String, String> requestHeaders = headers.getRequestHeaders();
        final MediaType[] types = headers.getAcceptableMediaTypes().toArray(emptyMediaTypesArray);
        final CompletionStageRxInvoker invoker =
                target.request(types).headers(MultivaluedMap.class.cast(requestHeaders)).rx();
        return decorate(invoker.method(request.getMethod()));
    }
}

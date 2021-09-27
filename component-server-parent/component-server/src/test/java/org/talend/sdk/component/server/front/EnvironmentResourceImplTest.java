/**
 * Copyright (C) 2006-2021 Talend Inc. - www.talend.com
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
package org.talend.sdk.component.server.front;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.ws.rs.client.WebTarget;

import org.apache.meecrowave.junit5.MonoMeecrowaveConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.talend.sdk.component.server.front.model.Environment;

@MonoMeecrowaveConfig
class EnvironmentResourceImplTest {

    @Inject
    private WebTarget base;

    @Test
    void environment() {
        final Environment environment = base.path("environment").request(APPLICATION_JSON_TYPE).get(Environment.class);
        assertEquals(1, environment.getLatestApiVersion());
        Stream
                .of(environment.getCommit(), environment.getTime(), environment.getVersion())
                .forEach(Assertions::assertNotNull);
        assertTrue(environment.getLastUpdated().compareTo(new Date(0)) > 0);
        assertTrue(("1.2.3").equals(environment.getConnectors())
                || ("1.26.0-SNAPSHOT").equals(environment.getConnectors()));
    }
}

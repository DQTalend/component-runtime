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
package org.talend.sdk.component.server.front;

import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import javax.inject.Inject;

import org.apache.meecrowave.junit5.MonoMeecrowaveConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.talend.sdk.component.server.front.model.ConfigTypeNode;
import org.talend.sdk.component.server.front.model.ConfigTypeNodes;
import org.talend.sdk.component.server.test.websocket.WebsocketClient;

@MonoMeecrowaveConfig
class ConfigurationTypeResourceTest {

    @Inject
    private WebsocketClient ws;

    @Test
    void webSocketGetIndex() {
        final ConfigTypeNodes index = ws.read(ConfigTypeNodes.class, "get", "/configurationtype/index", "");
        assertIndex(index);
        validateJdbcHierarchy(index);
    }

    @Test
    void ensureConsistencyBetweenPathsAndNames() {
        final ConfigTypeNodes index =
                ws.read(ConfigTypeNodes.class, "get", "/configurationtype/index?lightPayload=false", "");
        validateConsistencyBetweenNamesAndKeys(index.getNodes().get("amRiYy1jb21wb25lbnQjamRiYyNkYXRhc2V0I2pkYmM"));
    }

    @Test
    void webSocketDetail() {
        final ConfigTypeNodes index = ws
                .read(ConfigTypeNodes.class, "get",
                        "/configurationtype/details?identifiers=amRiYy1jb21wb25lbnQjamRiYyNkYXRhc3RvcmUjamRiYw", "");
        assertEquals(1, index.getNodes().size());
        final ConfigTypeNode jdbcConnection = index.getNodes().get("amRiYy1jb21wb25lbnQjamRiYyNkYXRhc3RvcmUjamRiYw");
        assertNotNull(jdbcConnection);
        assertEquals("[{\"description\":\"D1\",\"driver\":\"d1\"},{\"description\":\"D2\",\"driver\":\"d2\"}]",
                jdbcConnection
                        .getProperties()
                        .stream()
                        .filter(p -> "configuration.configurations".equals(p.getPath()))
                        .findFirst()
                        .get()
                        .getDefaultValue());

    }

    @Test
    void migrate() {
        final Map<String, String> config = ws
                .read(Map.class, "post", "/configurationtype/migrate/amRiYy1jb21wb25lbnQjamRiYyNkYXRhc2V0I2pkYmM/-2",
                        "{}");
        assertEquals("true", config.get("configuration.migrated"));
        assertEquals("1", config.get("configuration.size"));
    }

    private void assertIndex(final ConfigTypeNodes index) {
        assertEquals(5, index.getNodes().size());
        index.getNodes().keySet().forEach(Assertions::assertNotNull); // assert no null ids
        // assert there is at least one parent node
        assertTrue(index.getNodes().values().stream().anyMatch(n -> n.getParentId() == null));
        // assert all edges nodes are in the index
        index
                .getNodes()
                .values()
                .stream()
                .filter(n -> !n.getEdges().isEmpty())
                .flatMap(n -> n.getEdges().stream())
                .forEach(e -> assertTrue(index.getNodes().containsKey(e)));

    }

    private void validateJdbcHierarchy(final ConfigTypeNodes index) {
        final ConfigTypeNode jdbcRoot = index.getNodes().get("amRiYy1jb21wb25lbnQjamRiYw");
        assertNotNull(jdbcRoot);
        assertEquals(singleton("amRiYy1jb21wb25lbnQjamRiYyNkYXRhc3RvcmUjamRiYw"), jdbcRoot.getEdges());
        assertEquals("jdbc", jdbcRoot.getName());

        final ConfigTypeNode jdbcConnection = index.getNodes().get("amRiYy1jb21wb25lbnQjamRiYyNkYXRhc3RvcmUjamRiYw");
        assertNotNull(jdbcConnection);
        assertEquals(singleton("amRiYy1jb21wb25lbnQjamRiYyNkYXRhc2V0I2pkYmM"), jdbcConnection.getEdges());
        assertEquals("jdbc", jdbcConnection.getName());
        assertEquals("JDBC DataStore", jdbcConnection.getDisplayName());
        assertEquals("datastore", jdbcConnection.getConfigurationType());

        final ConfigTypeNode jdbcDataSet = index.getNodes().get("amRiYy1jb21wb25lbnQjamRiYyNkYXRhc2V0I2pkYmM");
        assertNotNull(jdbcDataSet);
        assertEquals(-1, jdbcDataSet.getVersion());
        assertEquals("jdbc", jdbcDataSet.getName());
        assertEquals("JDBC DataSet", jdbcDataSet.getDisplayName());
        assertEquals("dataset", jdbcDataSet.getConfigurationType());
    }

    private void validateConsistencyBetweenNamesAndKeys(final ConfigTypeNode node) {
        // we had a bug where the paths were not rebased and therefore this test was failing
        assertTrue(node.getProperties().stream().anyMatch(it -> it.getName().equals(it.getPath())));
    }
}

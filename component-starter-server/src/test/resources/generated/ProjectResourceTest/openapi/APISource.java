package com.test.openapi.source;

import java.io.Serializable;

import javax.json.JsonObject;

import org.talend.sdk.component.api.component.Icon;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.input.Emitter;
import org.talend.sdk.component.api.input.Producer;
import org.talend.sdk.component.api.meta.Documentation;

import com.test.openapi.client.APIClient;
import com.test.openapi.dataset.APIDataSet;

@Version
@Icon(Icon.IconType.STAR) // todo use CUSTOM
@Emitter(name = "Source")
@Documentation("A source emitting the result of the configured called.")
public class APISource implements Serializable {
    private final APIConfiguration configuration;
    private final APIClient client;

    private boolean called = false;

    public APISource(@Option final APIConfiguration configuration, final APIClient client) {
        this.configuration = configuration;
        this.client = client;
    }

    @Producer
    public JsonObject data() {
        if (called) {
            return null;
        }
        final APIDataSet dataset = configuration.getDataSet();
        return dataset.getAPI().call(dataset, client).body();
    }
}
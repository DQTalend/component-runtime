package com.test.openapi.source;

import java.io.Serializable;

import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.ui.OptionsOrder;
import org.talend.sdk.component.api.meta.Documentation;

import com.test.openapi.dataset.APIDataSet;

@OptionsOrder("dataset")
public class APIConfiguration implements Serializable {
    @Option
    @Documentation("The API dataset.")
    private APIDataSet dataset;

    public APIDataSet getDataSet() {
        return dataset;
    }
}
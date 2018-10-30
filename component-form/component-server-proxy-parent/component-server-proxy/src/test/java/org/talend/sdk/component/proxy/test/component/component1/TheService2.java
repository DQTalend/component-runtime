/**
 * Copyright (C) 2006-2018 Talend Inc. - www.talend.com
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
package org.talend.sdk.component.proxy.test.component.component1;

import static java.util.Arrays.asList;

import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.completion.SuggestionValues;
import org.talend.sdk.component.api.service.completion.Suggestions;

@Service
public class TheService2 {

    @Suggestions(value = "suggestions-values")
    public SuggestionValues suggestions(@Option final Connection1 connection) {
        if (connection == null) {
            throw new NullPointerException();
        }
        return new SuggestionValues(true, asList(new SuggestionValues.Item("1", connection.getUrl()),
                new SuggestionValues.Item("2", connection.getUsername())));
    }

}

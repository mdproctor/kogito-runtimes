/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.kogito.persistence.quarkus;

import java.util.Map;
import java.util.Set;

import org.eclipse.microprofile.config.spi.ConfigSource;

public class KogitoAddOnPersistenceJDBCConfigSource implements ConfigSource {

    static final Integer ORDINAL = Integer.MIN_VALUE;

    private final Map<String, String> configuration;

    public KogitoAddOnPersistenceJDBCConfigSource(Map<String, String> configuration) {
        this.configuration = configuration;
    }

    @Override
    public Set<String> getPropertyNames() {
        return configuration.keySet();
    }

    @Override
    public int getOrdinal() {
        return ORDINAL;
    }

    @Override
    public String getValue(String propertyName) {
        return configuration.get(propertyName);
    }

    @Override
    public String getName() {
        return KogitoAddOnPersistenceJDBCConfigSource.class.getSimpleName();
    }
}

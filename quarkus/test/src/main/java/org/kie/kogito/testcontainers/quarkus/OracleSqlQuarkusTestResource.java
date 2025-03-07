/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
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
package org.kie.kogito.testcontainers.quarkus;

import java.util.HashMap;
import java.util.Map;

import org.kie.kogito.test.resources.ConditionalQuarkusTestResource;
import org.kie.kogito.testcontainers.KogitoOracleSqlContainer;

/**
 * Oracle SQL quarkus resource that works within the test lifecycle.
 */
public class OracleSqlQuarkusTestResource extends ConditionalQuarkusTestResource<KogitoOracleSqlContainer> {

    public static final String QUARKUS_DATASOURCE_JDBC_URL = "quarkus.datasource.jdbc.url";
    public static final String QUARKUS_DATASOURCE_USERNAME = "quarkus.datasource.username";
    public static final String QUARKUS_DATASOURCE_PASSWORD = "quarkus.datasource.password";

    public OracleSqlQuarkusTestResource() {
        super(new KogitoOracleSqlContainer());
    }

    @Override
    protected Map<String, String> getProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put(QUARKUS_DATASOURCE_JDBC_URL, getTestResource().getJdbcUrl());
        properties.put(QUARKUS_DATASOURCE_USERNAME, getTestResource().getUsername());
        properties.put(QUARKUS_DATASOURCE_PASSWORD, getTestResource().getPassword());
        return properties;
    }

    public static class Conditional extends OracleSqlQuarkusTestResource {

        public Conditional() {
            enableConditional();
        }
    }
}
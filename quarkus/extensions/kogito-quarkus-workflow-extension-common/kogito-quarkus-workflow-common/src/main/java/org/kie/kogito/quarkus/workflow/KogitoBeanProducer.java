/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates.
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
package org.kie.kogito.quarkus.workflow;

import javax.enterprise.inject.Produces;

import org.kie.kogito.config.ConfigBean;
import org.kie.kogito.correlation.CorrelationService;
import org.kie.kogito.event.correlation.DefaultCorrelationService;
import org.kie.kogito.process.ProcessVersionResolver;
import org.kie.kogito.process.version.ProjectVersionProcessVersionResolver;

import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.properties.IfBuildProperty;

public class KogitoBeanProducer {

    @DefaultBean
    @Produces
    CorrelationService correlationService() {
        return new DefaultCorrelationService();
    }

    @Produces
    @IfBuildProperty(name = "kogito.workflow.version-strategy", stringValue = "project")
    ProcessVersionResolver projectVersionResolver(ConfigBean configBean) {
        return new ProjectVersionProcessVersionResolver(configBean.getGav().orElseThrow(() -> new RuntimeException("Unable to use kogito.workflow.version-strategy without a project GAV")));
    }
}

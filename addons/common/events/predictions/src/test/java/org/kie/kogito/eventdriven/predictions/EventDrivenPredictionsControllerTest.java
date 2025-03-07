/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
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
package org.kie.kogito.eventdriven.predictions;

import org.junit.jupiter.api.Test;
import org.kie.kogito.config.ConfigBean;
import org.kie.kogito.event.EventEmitter;
import org.kie.kogito.event.EventReceiver;
import org.kie.kogito.prediction.PredictionModels;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

class EventDrivenPredictionsControllerTest {

    @Test
    void testSubscribe() {
        PredictionModels predictionModelsMock = mock(PredictionModels.class);
        ConfigBean configMock = mock(ConfigBean.class);
        EventEmitter eventEmitterMock = mock(EventEmitter.class);
        EventReceiver eventReceiverMock = mock(EventReceiver.class);

        // option #1: parameters via constructor + parameterless setup
        EventDrivenPredictionsController controller1 = new EventDrivenPredictionsController(predictionModelsMock, configMock, eventEmitterMock, eventReceiverMock);
        controller1.subscribe();
        verify(eventReceiverMock).subscribe(any(), any());

        reset(eventReceiverMock);

        // option #2: parameterless via constructor + parameters via setup (introduced for Quarkus CDI)
        EventDrivenPredictionsController controller2 = new EventDrivenPredictionsController();
        controller2.init(predictionModelsMock, configMock, eventEmitterMock, eventReceiverMock);
        controller2.subscribe();
        verify(eventReceiverMock).subscribe(any(), any());
    }

}

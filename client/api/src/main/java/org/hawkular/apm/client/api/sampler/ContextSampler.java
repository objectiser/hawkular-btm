/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.hawkular.apm.client.api.sampler;

import org.hawkular.apm.api.model.config.ReportingLevel;
import org.hawkular.apm.api.model.trace.Trace;

/**
 * This sampler takes into decision reporting level and result of the supplied sampler.
 * e.g. in case of propagated reporting level is {@link ReportingLevel#All} then it
 * returns always true.
 *
 * @author Pavol Loffay
 */
public final class ContextSampler {

    private ContextSampler() {}

    public static boolean isSampled(Sampler sampler, Trace trace, ReportingLevel reportingLevel) {
        if (reportingLevel != null) {
            switch (reportingLevel) {
                case Ignore:
                case None:
                    return false;
                case All:
                default:
                    return true;
            }
        }

        return sampler.isSampled(trace);
    }
}

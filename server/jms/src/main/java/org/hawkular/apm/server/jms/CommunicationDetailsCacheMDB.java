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
package org.hawkular.apm.server.jms;

import java.util.List;

import javax.annotation.PostConstruct;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.inject.Inject;
import javax.jms.MessageListener;

import org.hawkular.apm.api.model.events.CommunicationDetails;
import org.hawkular.apm.processor.tracecompletiontime.CommunicationDetailsCache;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * This MDB is provided to populate the CommunicationDetailsCache when Communication Details.
 * Each clustered APM server node will receive the communication details data, so each cache
 * is expected to only be a local cache.
 *
 * Discussion regarding potential future use of a distributed cache is associated with HWKAPM-479.
 * If this change happens, then this MDB should be converted back to a durable (load balanced) subscriber.
 *
 * @author gbrown
 */
@MessageDriven(name = "CommunicationDetails_CommunicationDetailsCache", messageListenerInterface = MessageListener.class,
        activationConfig =
        {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "CommunicationDetails"),
                @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable"),
                @ActivationConfigProperty(propertyName = "clientID", propertyValue = "apm-${jboss.node.name}"),
                @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = "CommunicationDetailsCache")
        })
public class CommunicationDetailsCacheMDB extends BulkProcessingMDB<CommunicationDetails> {

    @Inject
    private CommunicationDetailsPublisherJMS communicationDetailsPublisher;

    @Inject
    private CommunicationDetailsCache communicationDetailsCache;

    @PostConstruct
    public void init() {
        setRetryPublisher(communicationDetailsPublisher);
        setTypeReference(new TypeReference<java.util.List<CommunicationDetails>>() {
        });
    }

    /* (non-Javadoc)
     * @see org.hawkular.apm.server.jms.BulkProcessingMDB#bulkProcess(java.lang.String, java.util.List, int)
     */
    @Override
    protected void bulkProcess(String tenantId, List<CommunicationDetails> items, int retryCount) throws Exception {
        communicationDetailsCache.store(tenantId, items);
    }

}
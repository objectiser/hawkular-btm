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
package org.hawkular.btm.tests.client.camel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.XPathBuilder;
import org.hawkular.btm.api.model.btxn.BusinessTransaction;
import org.hawkular.btm.api.model.btxn.Consumer;
import org.hawkular.btm.api.model.btxn.Producer;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * @author gbrown
 */
public class ClientCamelSplitterParallelTest extends ClientCamelTestBase {

    @Override
    public RouteBuilder getRouteBuilder() {
        XPathBuilder xPathBuilder = new XPathBuilder("/order/item");

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:src/test/data/camel/splitter?noop=true")
                .split(xPathBuilder)
                .parallelProcessing()
                .setHeader("LineItemId")
                .xpath("/item/@id", String.class)
                .to("file:target/data/camel/splitter?fileName="
                        + "${in.header.LineItemId}-${date:now:yyyyMMddHHmmssSSSSS}.xml");
            }
        };
    }

    @Test
    public void testFileSplitNotParallel() {
        try {
            synchronized (this) {
                wait(5000);
            }
        } catch (Exception e) {
            fail("Failed to wait for btxns to store");
        }

        List<BusinessTransaction> btxns=getTestBTMServer().getBusinessTransactions();

        for (BusinessTransaction btxn : btxns) {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            try {
                System.out.println("BTXN=" + mapper.writeValueAsString(btxn));
            } catch (JsonProcessingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        // Check stored business transactions (including 1 for the test client)
        assertEquals(6, btxns.size());

        BusinessTransaction parent=null;
        Producer producer=null;
        List<BusinessTransaction> spawned=new ArrayList<BusinessTransaction>();

        for (BusinessTransaction btxn : btxns) {
            List<Consumer> consumers = new ArrayList<Consumer>();
            findNodes(btxn.getNodes(), Consumer.class, consumers);

            List<Producer> producers = new ArrayList<Producer>();
            findNodes(btxn.getNodes(), Producer.class, producers);

            if (consumers.isEmpty()) {
                if (producers.isEmpty()) {
                    fail("Expected producer");
                }
                if (producers.size() > 1) {
                    fail("Expected only 1 producer");
                }
                if (parent != null) {
                    fail("Already have a producer btxn");
                }
                parent = btxn;
                producer = producers.get(0);
            } else if (!producers.isEmpty()) {
                fail("Should not have both consumers and producer");
            } else if (consumers.size() > 1) {
                fail("Only 1 consumer expected per btxn, got: "+consumers.size());
            } else {
                spawned.add(btxn);
            }
        }

        assertEquals(5, spawned.size());

        assertNotNull(parent);

        // Check 'btm_publish' set on producer
        assertTrue(producer.getDetails().containsKey("btm_publish"));
        assertEquals(producer.getDetails().get("btm_publish"), "true");
    }

}

/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.btm.client.collector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hawkular.btm.api.client.BusinessTransactionCollector;
import org.hawkular.btm.api.model.btxn.BusinessTransaction;
import org.hawkular.btm.api.model.btxn.CorrelationIdentifier;
import org.hawkular.btm.api.model.btxn.Node;
import org.hawkular.btm.api.model.btxn.Service;
import org.hawkular.btm.api.services.BusinessTransactionCriteria;
import org.hawkular.btm.api.services.BusinessTransactionService;
import org.junit.Test;

/**
 * @author gbrown
 */
public class DefaultBusinessTransactionCollectorTest {

    /**  */
    private static final String TEST_TENANT = "TestTenant";
    /**  */
    private static final String OPERATION = "Operation";
    /**  */
    private static final String SERVICE_TYPE = "ServiceType";

    @Test
    public void testSetStartTimeAndDuration() {
        DefaultBusinessTransactionCollector collector = new DefaultBusinessTransactionCollector();
        TestBTxnService btxnService = new TestBTxnService();
        collector.setBusinessTransactionService(btxnService);

        collector.serviceStart(SERVICE_TYPE, OPERATION, null);

        // Delay, to provide a reasonable value for duration
        synchronized (this) {
            try {
                wait(100);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        collector.serviceEnd(SERVICE_TYPE, OPERATION, null);

        // Delay necessary as reporting the business transaction is performed in a separate
        // thread
        synchronized (this) {
            try {
                wait(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        List<BusinessTransaction> btxns = btxnService.getBusinessTransactions();

        assertEquals("Only 1 business transaction expected", 1, btxns.size());

        BusinessTransaction btxn = btxns.get(0);

        assertEquals("Single node", 1, btxn.getNodes().size());

        Node node = btxn.getNodes().get(0);

        assertTrue("Start time not set", node.getStartTime() > 0);
        assertTrue("Duration not set", node.getDuration() > 0);
    }

    @Test
    public void testTenantIdSystemProperty() {
        System.setProperty("hawkular-btm.tenantId", TEST_TENANT);

        DefaultBusinessTransactionCollector collector = new DefaultBusinessTransactionCollector();
        TestBTxnService btxnService = new TestBTxnService();
        collector.setBusinessTransactionService(btxnService);

        collector.serviceStart(SERVICE_TYPE, OPERATION, null);

        collector.serviceEnd(SERVICE_TYPE, OPERATION, null);

        // Delay necessary as reporting the business transaction is performed in a separate
        // thread
        synchronized (this) {
            try {
                wait(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Clear property
        System.getProperties().remove("hawkular-btm.tenantId");

        assertNotNull("TenantId should not be null", btxnService.getTenantId());

        assertEquals("TenantId incorrect", TEST_TENANT, btxnService.getTenantId());
    }

    @Test
    public void testIncludeHeaders() {
        DefaultBusinessTransactionCollector collector = new DefaultBusinessTransactionCollector();
        TestBTxnService btxnService = new TestBTxnService();
        collector.setBusinessTransactionService(btxnService);

        Map<String, String> reqHeaders = new HashMap<String, String>();
        reqHeaders.put("hello", "world");

        Map<String, String> respHeaders = new HashMap<String, String>();
        respHeaders.put("joe", "bloggs");

        collector.serviceStart(SERVICE_TYPE, OPERATION, reqHeaders);

        collector.serviceEnd(SERVICE_TYPE, OPERATION, respHeaders);

        // Delay necessary as reporting the business transaction is performed in a separate
        // thread
        synchronized (this) {
            try {
                wait(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        List<BusinessTransaction> btxns = btxnService.getBusinessTransactions();

        assertEquals("Only 1 business transaction expected", 1, btxns.size());

        BusinessTransaction btxn = btxns.get(0);

        assertEquals("Single node", 1, btxn.getNodes().size());

        Node node = btxn.getNodes().get(0);

        Service service = (Service) node;

        assertEquals(service.getRequest().getHeaders().get("hello"), "world");
        assertEquals(service.getResponse().getHeaders().get("joe"), "bloggs");
    }

    @Test
    public void testIncludeHeaderBTMID() {
        DefaultBusinessTransactionCollector collector = new DefaultBusinessTransactionCollector();
        TestBTxnService btxnService = new TestBTxnService();
        collector.setBusinessTransactionService(btxnService);

        Map<String, String> reqHeaders = new HashMap<String, String>();
        reqHeaders.put("hello", "world");
        reqHeaders.put(BusinessTransactionCollector.BTM_ID, "myid");

        collector.serviceStart(SERVICE_TYPE, OPERATION, reqHeaders);

        collector.serviceEnd(SERVICE_TYPE, OPERATION, null);

        // Delay necessary as reporting the business transaction is performed in a separate
        // thread
        synchronized (this) {
            try {
                wait(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        List<BusinessTransaction> btxns = btxnService.getBusinessTransactions();

        assertEquals("Only 1 business transaction expected", 1, btxns.size());

        BusinessTransaction btxn = btxns.get(0);

        assertEquals("Single node", 1, btxn.getNodes().size());

        Node node = btxn.getNodes().get(0);

        assertTrue("Should be 1 correlation id", node.getCorrelationIds().size() == 1);

        CorrelationIdentifier cid = node.getCorrelationIds().iterator().next();
        assertEquals(CorrelationIdentifier.Scope.Interaction, cid.getScope());
        assertEquals("myid", cid.getValue());
    }

    public static class TestBTxnService implements BusinessTransactionService {

        private List<BusinessTransaction> businessTransactions = new ArrayList<BusinessTransaction>();
        private String tenantId;

        /* (non-Javadoc)
         * @see org.hawkular.btm.api.services.BusinessTransactionService#store(java.lang.String, java.util.List)
         */
        @Override
        public void store(String tenantId, List<BusinessTransaction> btxns) throws Exception {
            this.tenantId = tenantId;
            businessTransactions.addAll(btxns);
        }

        /* (non-Javadoc)
         * @see org.hawkular.btm.api.services.BusinessTransactionService#get(java.lang.String, java.lang.String)
         */
        @Override
        public BusinessTransaction get(String tenantId, String id) {
            return null;
        }

        /* (non-Javadoc)
         * @see org.hawkular.btm.api.services.BusinessTransactionService#query(java.lang.String,
         *              org.hawkular.btm.api.services.BusinessTransactionCriteria)
         */
        @Override
        public List<BusinessTransaction> query(String tenantId, BusinessTransactionCriteria criteria) {
            return null;
        }

        /**
         * @return the businessTransactions
         */
        public List<BusinessTransaction> getBusinessTransactions() {
            return businessTransactions;
        }

        /**
         * @param businessTransactions the businessTransactions to set
         */
        public void setBusinessTransactions(List<BusinessTransaction> businessTransactions) {
            this.businessTransactions = businessTransactions;
        }

        /**
         * @return the tenantId
         */
        public String getTenantId() {
            return tenantId;
        }

        /**
         * @param tenantId the tenantId to set
         */
        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

    }
}
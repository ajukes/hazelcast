/*
 * Copyright (c) 2008-2020, Hazelcast, Inc. All Rights Reserved.
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
package com.hazelcast.internal.util.phonehome;

import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.instance.BuildInfoProvider;
import com.hazelcast.instance.impl.Node;
import com.hazelcast.spi.properties.ClusterProperty;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.Map;
import java.util.UUID;

import static com.hazelcast.test.Accessors.getNode;
import static java.lang.System.getenv;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class PhoneHomeTest extends HazelcastTestSupport {

    @Test
    public void testPhoneHomeParameters() {
        HazelcastInstance hz = createHazelcastInstance();
        Node node = getNode(hz);
        PhoneHome phoneHome = new PhoneHome(node);
        sleepAtLeastMillis(1);
        Map<String, String> parameters = phoneHome.phoneHome(true);
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        OperatingSystemMXBean osMxBean = ManagementFactory.getOperatingSystemMXBean();
        assertEquals(parameters.get("version"), BuildInfoProvider.getBuildInfo().getVersion());
        assertEquals(UUID.fromString(parameters.get("m")), node.getLocalMember().getUuid());
        assertNull(parameters.get("e"));
        assertNull(parameters.get("oem"));
        assertNull(parameters.get("l"));
        assertNull(parameters.get("hdgb"));
        assertEquals(parameters.get("p"), "source");
        assertEquals(parameters.get("crsz"), "A");
        assertEquals(parameters.get("cssz"), "A");
        assertEquals(parameters.get("ccpp"), "0");
        assertEquals(parameters.get("cdn"), "0");
        assertEquals(parameters.get("cjv"), "0");
        assertEquals(parameters.get("cnjs"), "0");
        assertEquals(parameters.get("cpy"), "0");
        assertEquals(parameters.get("cgo"), "0");
        assertEquals(parameters.get("jetv"), "");
        assertFalse(Integer.parseInt(parameters.get("cuptm")) < 0);
        assertNotEquals(parameters.get("nuptm"), "0");
        assertNotEquals(parameters.get("nuptm"), parameters.get("cuptm"));
        assertEquals(parameters.get("osn"), osMxBean.getName());
        assertEquals(parameters.get("osa"), osMxBean.getArch());
        assertEquals(parameters.get("osv"), osMxBean.getVersion());
        assertEquals(parameters.get("jvmn"), runtimeMxBean.getVmName());
        assertEquals(parameters.get("jvmv"), System.getProperty("java.version"));
    }

    @Test
    public void testScheduling_whenPhoneHomeIsDisabled() {
        Config config = new Config()
                .setProperty(ClusterProperty.PHONE_HOME_ENABLED.getName(), "false");

        HazelcastInstance hz = createHazelcastInstance(config);
        Node node = getNode(hz);

        PhoneHome phoneHome = new PhoneHome(node);
        phoneHome.check();
        assertNull(phoneHome.phoneHomeFuture);
    }

    @Test
    public void testShutdown() {
        assumeFalse("Skipping. The PhoneHome is disabled by the Environment variable",
                "false".equals(getenv("HZ_PHONE_HOME_ENABLED")));
        Config config = new Config()
                .setProperty(ClusterProperty.PHONE_HOME_ENABLED.getName(), "true");

        HazelcastInstance hz = createHazelcastInstance(config);
        Node node = getNode(hz);

        PhoneHome phoneHome = new PhoneHome(node);
        phoneHome.check();
        assertNotNull(phoneHome.phoneHomeFuture);
        assertFalse(phoneHome.phoneHomeFuture.isDone());
        assertFalse(phoneHome.phoneHomeFuture.isCancelled());

        phoneHome.shutdown();
        assertTrue(phoneHome.phoneHomeFuture.isCancelled());
    }

    @Test
    public void testConvertToLetter() {
        assertEquals("A", MetricsCollector.convertToLetter(4));
        assertEquals("B", MetricsCollector.convertToLetter(9));
        assertEquals("C", MetricsCollector.convertToLetter(19));
        assertEquals("D", MetricsCollector.convertToLetter(39));
        assertEquals("E", MetricsCollector.convertToLetter(59));
        assertEquals("F", MetricsCollector.convertToLetter(99));
        assertEquals("G", MetricsCollector.convertToLetter(149));
        assertEquals("H", MetricsCollector.convertToLetter(299));
        assertEquals("J", MetricsCollector.convertToLetter(599));
        assertEquals("I", MetricsCollector.convertToLetter(1000));
    }

    @Test
    public void testMapCount() {
        HazelcastInstance hz = createHazelcastInstance();
        Node node = getNode(hz);
        PhoneHome phoneHome = new PhoneHome(node);
        Map<String, String> parameters = phoneHome.phoneHome(true);
        assertEquals(parameters.get("mpct"), "0");
        Map<String, String> map1 = hz.getMap("hazelcast");
        Map<String, String> map2 = hz.getMap("phonehome");
        parameters = phoneHome.phoneHome(true);
        assertEquals(parameters.get("mpct"), "2");
        Map<String, String> map3 = hz.getMap("maps");
        parameters = phoneHome.phoneHome(true);
        assertEquals(parameters.get("mpct"), "3");
    }

    @Test
    public void testMapCountWithBackupReadEnabled() {
        HazelcastInstance hz = createHazelcastInstance();
        Node node = getNode(hz);
        PhoneHome phoneHome = new PhoneHome(node);
        Map<String, String> parameters;
        parameters = phoneHome.phoneHome(true);
        assertEquals(parameters.get("mpbrct"), "0");

        Map<String, String> map1 = hz.getMap("hazelcast");
        parameters = phoneHome.phoneHome(true);
        assertEquals(parameters.get("mpbrct"), "0");

        node.getConfig().getMapConfig("hazelcast").setReadBackupData(true);
        parameters = phoneHome.phoneHome(true);
        assertEquals(parameters.get("mpbrct"), "1");
    }
}


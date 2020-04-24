/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Collections;
import java.util.Iterator;
import java.util.Random;
import javax.cache.Cache;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.lang.IgniteFuture;
import org.apache.ignite.lang.IgniteRunnable;
import org.apache.ignite.resources.IgniteInstanceResource;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.apache.ignite.spi.metric.jmx.JmxMetricExporterSpi;

/**
 * The demo showcases how to enable metrics exporters available in Ignite 2.8 and later versions and observer the cluster
 * state via metrics registries and system views. Follow these steps to get the demo working on your end:
 * <ul>
 *     <li>
 *         Start an Ignite server node with {@link ServerNodeStartup}. By default, the server enables
 *         {@link JmxMetricExporterSpi} only but has a commented-out lines that show how to switch on other types of
 *         exporters.
 *     </li>
 *     <li>
 *         Launch VisualVM - you can simply execute {@code jvisualvm} command in your terminal or might need to pass a
 *         direct path to the tool executable {@code /Library/Java/JavaVirtualMachines/jdk1.8.0_191.jdk/Contents/Home/bin/jvisualvm}.
 *     </li>
 *     <li>
 *         Ensure that VisualVM-MBeans plugin is installed - navigate to Tools -> Plugins of VisualVM app.
 *     </li>
 *     <li>
 *         Select {@code ServerNodeStartup} in VisualVM to connect to the running process. Go to {@code MBeans} tab and
 *         unfold the structure under {@code org.apache.<node_id>} path.
 *     </li>
 *     <li>
 *         Launch {@link IgniteMetricsDemo} that creates a sample caches and executes key-value requests with compute tasks
 *         indefinitely.
 *     </li>
 * </ul>
 *
 * <p>
 * Use VisualVM connected to the {@code ServerNodeStartup} process to observer how the following metrics are changing
 * under {@code org.apache.<node_id>} path (click "Refresh" button on the VisualVM screens to get the interface updated):
 * </p>
 * <ul>
 *     <li>
 *         cache.RecordsCache - check CacheGets, CacheHits, CachePuts and other attributes.
 *     </li>
 *     <li>
 *         compute.jobs - check active, finished, executiontime and other attributes.
 *     </li>
 *     <li>
 *         views."scan.queries" - double-click on "Value" cell of the "views" row to see detailed output.
 *     </li>
 * </ul>
 */
public class IgniteMetricsDemo {
    private static String SAMPLE_CACHE_NAME = "RecordsCache";

    public static void main(String args[]) {
        IgniteConfiguration cfg = createConfiguration();

        // Starting a client node instance
        Ignite clientNode = Ignition.start(cfg);

        // Creating a sample cache with metrics collection enabled.
        CacheConfiguration<Integer, Integer> cacheCfg = new CacheConfiguration(SAMPLE_CACHE_NAME);
        cacheCfg.setStatisticsEnabled(true);

        IgniteCache cache = clientNode.getOrCreateCache(cacheCfg);

        new Thread(new CacheUpdater(cache)).start();
        new Thread(new ComputeTasksSender(clientNode)).start();
    }

    /**
     * Prepares and returns an Ignite configuration for a client node.
     *
     * @return IgniteConfiguration object.
     */
    private static IgniteConfiguration createConfiguration() {
        IgniteConfiguration cfg = new IgniteConfiguration();

        //Preparing the network configuration.
        TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
        ipFinder.setAddresses(Collections.singletonList("127.0.0.1:47500..47509"));
        cfg.setDiscoverySpi(new TcpDiscoverySpi().setIpFinder(ipFinder));

        // Enabling the client mode.
        cfg.setClientMode(true);

        return cfg;
    }

    /**
     * The class that requests and updates data from the servers in a loop.
     */
    private static class CacheUpdater implements Runnable {
        /** Thread sleep/park time. */
        private static int PARK_TIME = 2000;

        /** Reference to an existing Ignite cache. */
        private IgniteCache<Integer, Integer> cache;

        public CacheUpdater(IgniteCache<Integer, Integer> cache) {
            this.cache = cache;
        }

        public void run() {
            Random randKey = new Random();
            Random randVal = new Random();

            while (true) {
                for (int i = 0; i < 1000; i++)
                    cache.put(randKey.nextInt(5000), randVal.nextInt(5000) +
                        randVal.nextInt(10_000));

                try {
                    Thread.sleep(PARK_TIME);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }

                for (int i = 0; i < 500; i++)
                    cache.get(randKey.nextInt(5000));

                try {
                    Thread.sleep(PARK_TIME);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * The class that broadcast Ignite compute tasks to server nodes.
     */
    private static class ComputeTasksSender implements Runnable {
        private Ignite clientNode;

        public ComputeTasksSender(Ignite clientNode) {
            this.clientNode = clientNode;
        }

        @Override public void run() {
            while (true) {
                IgniteFuture future1 =
                    clientNode.compute(clientNode.cluster().forServers()).broadcastAsync(new SampleComputeTask());

                IgniteFuture future2 =
                    clientNode.compute(clientNode.cluster().forServers()).broadcastAsync(new SampleComputeTask());

                IgniteFuture future3 =
                    clientNode.compute(clientNode.cluster().forServers()).broadcastAsync(new SampleComputeTask());

                // Wait while the tasks complete before scheduling a next series.
                future1.get();
                future2.get();
                future3.get();
            }
        }

        private static class SampleComputeTask implements IgniteRunnable {
            @IgniteInstanceResource
            private Ignite node;

            @Override public void run() {
                IgniteCache cache = node.cache(SAMPLE_CACHE_NAME);

                ScanQuery<Integer, Integer> scanQuery = new ScanQuery<>();

                Iterator<Cache.Entry<Integer, Integer>> iterator = cache.query(scanQuery).iterator();

                int sum = 0;

                while (iterator.hasNext()) {
                    Cache.Entry<Integer, Integer> entry = iterator.next();

                    sum += entry.getValue();
                }
            }
        }
    }
}

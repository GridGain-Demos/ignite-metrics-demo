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
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.apache.ignite.spi.metric.jmx.JmxMetricExporterSpi;
import org.apache.ignite.spi.metric.log.LogExporterSpi;

/**
 * The class starts an instance of the server node with the metrics configured. Launch the class more than once to spawn
 * a multiple-node cluster.
 *
 * Make sure '-Dcom.sun.management.jmxremote' parameter is passed to the VM options of your SeverNodeStartup
 * configuration (done by default) to enable JMX.
 */
public class ServerNodeStartup {
    /**
     * Preparing a configuration for a server node with the metrics enabled. The method starts a single instance of the
     * server node.
     * @param args
     */
    public static void main(String args[]) {
        IgniteConfiguration cfg = createConfiguration();

        Ignition.start(cfg);

        System.out.println("The server node is up and running");
    }

    /**
     * Prepares and returns an Ignite configuration for the server nodes.
     *
     * @return IgniteConfiguration object.
     */
    private static IgniteConfiguration createConfiguration() {
        IgniteConfiguration cfg = new IgniteConfiguration();

        //Preparing the network configuration.
        TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
        ipFinder.setAddresses(Collections.singletonList("127.0.0.1:47500..47509"));
        cfg.setDiscoverySpi(new TcpDiscoverySpi().setIpFinder(ipFinder));

        //Setting up the Log Exporter
        LogExporterSpi logExporterSpi = new LogExporterSpi();
        logExporterSpi.setPeriod(5000);

        //Activating exporters that will report metrics via the logging system and JMX.
        cfg.setMetricExporterSpi(
            new JmxMetricExporterSpi()
            // Activate other exporters if needed.
            //, new SqlViewMetricExporterSpi()
            , logExporterSpi
        );

        //Printing metrics to the logs every 5000 seconds.
        cfg.setMetricsLogFrequency(5000);

        //Collecting and updating metrics every second.
        cfg.setMetricsUpdateFrequency(1000);

        return cfg;
    }

}

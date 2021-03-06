/*
 * Copyright 2013 Thomas Memenga - Syscrest GmbH
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.syscrest.oozie.graphite;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import junit.framework.Assert;

import org.apache.hadoop.conf.Configuration;
import org.apache.log4j.Logger;
import org.apache.oozie.action.ActionExecutor;
import org.apache.oozie.action.ActionExecutorException;
import org.apache.oozie.service.Services;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

public class GraphiteMRCounterExecutorExecutorTest {

	private final Logger logger = Logger.getLogger(getClass());

	GraphiteMRCounterExecutor fixture = null;

	String graphiteData = null;

	private Thread server = null;

	@Before
	public void setUp() throws Exception {
		System.setProperty(Services.OOZIE_HOME_DIR, "/tmp");
		final Configuration oozieConf = new Configuration();
		oozieConf.setInt(ActionExecutor.MAX_RETRIES, 1);

		fixture = new GraphiteMRCounterExecutor() {

			@Override
			public Configuration getOozieConf() {
				return oozieConf;
			}

		};
	}

	public void setupUDP() {

		server = new Thread() {

			@Override
			public void run() {
				try {
					DatagramSocket serverSocket = new DatagramSocket(2003,
							InetAddress.getByName("localhost"));
					byte[] receiveData = new byte[1024];

					DatagramPacket receivePacket = new DatagramPacket(
							receiveData, receiveData.length);
					serverSocket.receive(receivePacket);
					graphiteData = new String(receivePacket.getData(), 0,
							receivePacket.getLength(), Charsets.UTF_8);
					serverSocket.close();

				} catch (Exception e) {
					logger.error("", e);
				}
			}

		};

		server.start();

	}

	public void setupTCP() {
		server = new Thread(new Runnable() {

			@Override
			public void run() {

				try {
					ServerSocket serverSocket = new ServerSocket(2004);
					byte[] receiveData = new byte[1024];

					Socket connectionSocket = serverSocket.accept();
					int length = connectionSocket.getInputStream().read(
							receiveData);
					graphiteData = new String(receiveData, 0, length,
							Charsets.UTF_8);

					connectionSocket.close();
					serverSocket.close();

				} catch (Exception e) {
					logger.error("", e);
				}
			}
		});

		server.start();
	}

	@SuppressWarnings("deprecation")
	@After
	public void tearDown() throws Exception {
		graphiteData = null;
		if (server != null) {
			server.stop();
		}
	}

	@Test(expected = ActionExecutorException.class)
	public void testStart_graphite_host_empty() throws ActionExecutorException,
			IOException {

		final String configuration = Resources.toString(Resources.getResource(
				this.getClass(),
				"GraphiteMRCounterExecutor_graphite-host_missing.xml"),
				Charsets.UTF_8);
		final TestWorkflowAction action = new TestWorkflowAction(configuration);

		fixture.start(new TestContext(), action);
	}

	private void runTestUDP(String fileName, String... ExpectedgraphiteData)
			throws IOException, ActionExecutorException, InterruptedException {
		setupUDP();

		final String configuration = Resources.toString(
				Resources.getResource(this.getClass(), fileName),
				Charsets.UTF_8);
		final TestWorkflowAction action = new TestWorkflowAction(configuration);

		fixture.start(new TestContext(), action);
		Thread.sleep(500);

		String[] parts = ((graphiteData != null) ? graphiteData.split("\n", -1)
				: new String[] { "\n   " });

		Assert.assertEquals(ExpectedgraphiteData.length, parts.length - 1);
		for (int i = 0; i < ExpectedgraphiteData.length; i++) {
			Assert.assertEquals(ExpectedgraphiteData[i], parts[i]);
		}
		Assert.assertEquals("", parts[parts.length - 1].trim());
	}

	private void runTestTCP(String fileName, String... expectedGraphiteData)
			throws IOException, ActionExecutorException, InterruptedException {
		setupTCP();
		final String configuration = Resources.toString(
				Resources.getResource(this.getClass(), fileName),
				Charsets.UTF_8);
		final TestWorkflowAction action = new TestWorkflowAction(configuration);

		fixture.start(new TestContext(), action);
		Thread.sleep(500);

		String[] parts = ((graphiteData != null) ? graphiteData.split("\n", -1)
				: new String[] { "\n" });

		Assert.assertEquals(expectedGraphiteData.length, parts.length - 1);
		for (int i = 0; i < expectedGraphiteData.length; i++) {
			Assert.assertEquals(expectedGraphiteData[i], parts[i]);
		}
		Assert.assertEquals("", parts[parts.length - 1].trim());
	}

	@Test
	public void testGraphiteMRCounterExecutor_with_static_mapping()
			throws ActionExecutorException, IOException, InterruptedException {

		runTestUDP("GraphiteMRCounterExecutor_with_static_mapping.xml",
				"graphite-prefix.static-name 1234 1369263600");

	}

	@Test
	public void testGraphiteMRCounterExecutor_with_static_mapping_tcp()
			throws ActionExecutorException, IOException, InterruptedException {

		runTestTCP("GraphiteMRCounterExecutor_with_static_mapping_tcp.xml",
				"graphite-prefix.static-name 1234 1369263600");

	}

	@Test
	public void testGraphiteMRCounterExecutor_with_implicit_mapping()
			throws ActionExecutorException, IOException, InterruptedException {

		runTestUDP("GraphiteMRCounterExecutor_with_implicit_mapping.xml",
				new String[] { "graphite-prefix.counter1 1234 1369263600",
						"graphite-prefix.counter2 56 1369263600" });

	}

	@Test
	public void testGraphiteMRCounterExecutor_with_rename_mapping()
			throws ActionExecutorException, IOException, InterruptedException {

		runTestUDP("GraphiteMRCounterExecutor_with_rename_mapping.xml",
				new String[] {
						"graphite-prefix.countByVersions.v1 1234 1369263600",
						"graphite-prefix.countByVersions.v2 456 1369263600",
						"graphite-prefix.countByVersions.v3 89 1369263600" });

	}

	@Test
	public void testGraphiteMRCounterExecutor_with_bad_counter_names()
			throws ActionExecutorException, IOException, InterruptedException {

		runTestUDP("GraphiteMRCounterExecutor_with_bad_counter_names.xml",
				new String[] {
						"graphite-prefix.countByVersions.__v3_ 89 1369263600",
						"graphite-prefix.countByVersions._v1 1234 1369263600",
						"graphite-prefix.countByVersions._v2 456 1369263600" });

	}

	@Test
	public void testGraphiteMRCounterExecutor_with_empty_value()
			throws ActionExecutorException, IOException, InterruptedException {

		runTestUDP("GraphiteMRCounterExecutor_with_empty_value.xml",
				new String[] {});

	}

}

/*
 * Copyright 2014-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.redis.connection.jedis;

import static org.hamcrest.core.IsEqual.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.Collections;
import java.util.Map.Entry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.redis.connection.AbstractConnectionUnitTestBase;
import org.springframework.data.redis.connection.RedisServerCommands.ShutdownOption;
import org.springframework.data.redis.connection.RedisZSetCommands.Tuple;
import org.springframework.data.redis.connection.jedis.JedisConnectionUnitTestSuite.JedisConnectionPipelineUnitTests;
import org.springframework.data.redis.connection.jedis.JedisConnectionUnitTestSuite.JedisConnectionUnitTests;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

import redis.clients.jedis.Client;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

/**
 * @author Christoph Strobl
 */
@RunWith(Suite.class)
@SuiteClasses({ JedisConnectionUnitTests.class, JedisConnectionPipelineUnitTests.class })
public class JedisConnectionUnitTestSuite {

	public static class JedisConnectionUnitTests extends AbstractConnectionUnitTestBase<Client> {

		protected JedisConnection connection;
		private Jedis jedisSpy;

		@Before
		public void setUp() {

			jedisSpy = spy(new MockedClientJedis("http://localhost:1234", getNativeRedisConnectionMock()));
			connection = new JedisConnection(jedisSpy);
		}

		/**
		 * @see DATAREDIS-184
		 */
		@Test
		public void shutdownWithNullShouldDelegateCommandCorrectly() {

			connection.shutdown(null);

			verifyNativeConnectionInvocation().shutdown();
		}

		/**
		 * @see DATAREDIS-184
		 */
		@Test
		public void shutdownNosaveShouldBeSentCorrectlyUsingLuaScript() {

			connection.shutdown(ShutdownOption.NOSAVE);

			ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
			verifyNativeConnectionInvocation().eval(captor.capture(), any(byte[].class), any(byte[][].class));

			assertThat(captor.getValue(), equalTo("return redis.call('SHUTDOWN','NOSAVE')".getBytes()));
		}

		/**
		 * @see DATAREDIS-184
		 */
		@Test
		public void shutdownSaveShouldBeSentCorrectlyUsingLuaScript() {

			connection.shutdown(ShutdownOption.SAVE);

			ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
			verifyNativeConnectionInvocation().eval(captor.capture(), any(byte[].class), any(byte[][].class));

			assertThat(captor.getValue(), equalTo("return redis.call('SHUTDOWN','SAVE')".getBytes()));
		}

		/**
		 * @see DATAREDIS-267
		 */
		@Test
		public void killClientShouldDelegateCallCorrectly() {

			connection.killClient("127.0.0.1", 1001);
			verifyNativeConnectionInvocation().clientKill(eq("127.0.0.1:1001"));
		}

		/**
		 * @see DATAREDIS-270
		 */
		@Test
		public void getClientNameShouldSendRequestCorrectly() {

			connection.getClientName();
			verifyNativeConnectionInvocation().clientGetname();
		}

		/**
		 * @see DATAREDIS-277
		 */
		@Test(expected = IllegalArgumentException.class)
		public void slaveOfShouldThrowExectpionWhenCalledForNullHost() {
			connection.slaveOf(null, 0);
		}

		/**
		 * @see DATAREDIS-277
		 */
		@Test
		public void slaveOfShouldBeSentCorrectly() {

			connection.slaveOf("127.0.0.1", 1001);
			verifyNativeConnectionInvocation().slaveof(eq("127.0.0.1"), eq(1001));
		}

		/**
		 * @see DATAREDIS-277
		 */
		@Test
		public void slaveOfNoOneShouldBeSentCorrectly() {

			connection.slaveOfNoOne();
			verifyNativeConnectionInvocation().slaveofNoOne();
		}

		/**
		 * @see DATAREDIS-330
		 */
		@Test(expected = InvalidDataAccessResourceUsageException.class)
		public void shouldThrowExceptionWhenAccessingRedisSentinelsCommandsWhenNoSentinelsConfigured() {
			connection.getSentinelConnection();
		}

		/**
		 * @see DATAREDIS-472
		 */
		@Test(expected = IllegalArgumentException.class)
		public void restoreShouldThrowExceptionWhenTtlInMillisExceedsIntegerRange() {
			connection.restore("foo".getBytes(), new Long(Integer.MAX_VALUE) + 1L, "bar".getBytes());
		}

		/**
		 * @see DATAREDIS-472
		 */
		@Test(expected = IllegalArgumentException.class)
		public void setExShouldThrowExceptionWhenTimeExceedsIntegerRange() {
			connection.setEx("foo".getBytes(), new Long(Integer.MAX_VALUE) + 1L, "bar".getBytes());
		}

		/**
		 * @see DATAREDIS-472
		 */
		@Test(expected = IllegalArgumentException.class)
		public void getRangeShouldThrowExceptionWhenStartExceedsIntegerRange() {
			connection.getRange("foo".getBytes(), new Long(Integer.MAX_VALUE) + 1L, Integer.MAX_VALUE);
		}

		/**
		 * @see DATAREDIS-472
		 */
		@Test(expected = IllegalArgumentException.class)
		public void getRangeShouldThrowExceptionWhenEndExceedsIntegerRange() {
			connection.getRange("foo".getBytes(), Integer.MAX_VALUE, new Long(Integer.MAX_VALUE) + 1L);
		}

		/**
		 * @see DATAREDIS-472
		 */
		@Test(expected = IllegalArgumentException.class)
		public void sRandMemberShouldThrowExceptionWhenCountExceedsIntegerRange() {
			connection.sRandMember("foo".getBytes(), new Long(Integer.MAX_VALUE) + 1L);
		}

		/**
		 * @see DATAREDIS-472
		 */
		@Test(expected = IllegalArgumentException.class)
		public void zRangeByScoreShouldThrowExceptionWhenOffsetExceedsIntegerRange() {
			connection.zRangeByScore("foo".getBytes(), "foo", "bar", new Long(Integer.MAX_VALUE) + 1L, Integer.MAX_VALUE);
		}

		/**
		 * @see DATAREDIS-472
		 */
		@Test(expected = IllegalArgumentException.class)
		public void zRangeByScoreShouldThrowExceptionWhenCountExceedsIntegerRange() {
			connection.zRangeByScore("foo".getBytes(), "foo", "bar", Integer.MAX_VALUE, new Long(Integer.MAX_VALUE) + 1L);
		}

		/**
		 * @see DATAREDIS-531
		 */
		@Test
		public void scanShouldKeepTheConnectionOpen() {

			doReturn(new ScanResult<String>("0", Collections.<String> emptyList())).when(jedisSpy).scan(anyString(),
					any(ScanParams.class));

			connection.scan();

			verify(jedisSpy, never()).quit();
		}

		/**
		 * @see DATAREDIS-531
		 */
		@Test
		public void scanShouldCloseTheConnectionWhenCursorIsClosed() throws IOException {

			doReturn(new ScanResult<String>("0", Collections.<String> emptyList())).when(jedisSpy).scan(anyString(),
					any(ScanParams.class));

			Cursor<byte[]> cursor = connection.scan();
			cursor.close();

			verify(jedisSpy, times(1)).quit();
		}

		/**
		 * @see DATAREDIS-531
		 */
		@Test
		public void sScanShouldKeepTheConnectionOpen() {

			doReturn(new ScanResult<String>("0", Collections.<String> emptyList())).when(jedisSpy).sscan(any(byte[].class),
					any(byte[].class), any(ScanParams.class));

			connection.sScan("foo".getBytes(), ScanOptions.NONE);

			verify(jedisSpy, never()).quit();
		}

		/**
		 * @see DATAREDIS-531
		 */
		@Test
		public void sScanShouldCloseTheConnectionWhenCursorIsClosed() throws IOException {

			doReturn(new ScanResult<String>("0", Collections.<String> emptyList())).when(jedisSpy).sscan(any(byte[].class),
					any(byte[].class), any(ScanParams.class));

			Cursor<byte[]> cursor = connection.sScan("foo".getBytes(), ScanOptions.NONE);
			cursor.close();

			verify(jedisSpy, times(1)).quit();
		}

		/**
		 * @see DATAREDIS-531
		 */
		@Test
		public void zScanShouldKeepTheConnectionOpen() {

			doReturn(new ScanResult<String>("0", Collections.<String> emptyList())).when(jedisSpy).zscan(any(byte[].class),
					any(byte[].class), any(ScanParams.class));

			connection.zScan("foo".getBytes(), ScanOptions.NONE);

			verify(jedisSpy, never()).quit();
		}

		/**
		 * @see DATAREDIS-531
		 */
		@Test
		public void zScanShouldCloseTheConnectionWhenCursorIsClosed() throws IOException {

			doReturn(new ScanResult<String>("0", Collections.<String> emptyList())).when(jedisSpy).zscan(any(byte[].class),
					any(byte[].class), any(ScanParams.class));

			Cursor<Tuple> cursor = connection.zScan("foo".getBytes(), ScanOptions.NONE);
			cursor.close();

			verify(jedisSpy, times(1)).quit();
		}

		/**
		 * @see DATAREDIS-531
		 */
		@Test
		public void hScanShouldKeepTheConnectionOpen() {

			doReturn(new ScanResult<String>("0", Collections.<String> emptyList())).when(jedisSpy).hscan(any(byte[].class),
					any(byte[].class), any(ScanParams.class));

			connection.hScan("foo".getBytes(), ScanOptions.NONE);

			verify(jedisSpy, never()).quit();
		}

		/**
		 * @see DATAREDIS-531
		 */
		@Test
		public void hScanShouldCloseTheConnectionWhenCursorIsClosed() throws IOException {

			doReturn(new ScanResult<String>("0", Collections.<String> emptyList())).when(jedisSpy).hscan(any(byte[].class),
					any(byte[].class), any(ScanParams.class));

			Cursor<Entry<byte[], byte[]>> cursor = connection.hScan("foo".getBytes(), ScanOptions.NONE);
			cursor.close();

			verify(jedisSpy, times(1)).quit();
		}

	}

	public static class JedisConnectionPipelineUnitTests extends JedisConnectionUnitTests {

		@Before
		public void setUp() {
			super.setUp();
			connection.openPipeline();
		}

		/**
		 * @see DATAREDIS-184
		 */
		@Override
		@Test(expected = UnsupportedOperationException.class)
		public void shutdownNosaveShouldBeSentCorrectlyUsingLuaScript() {
			super.shutdownNosaveShouldBeSentCorrectlyUsingLuaScript();
		}

		/**
		 * @see DATAREDIS-184
		 */
		@Override
		@Test(expected = UnsupportedOperationException.class)
		public void shutdownSaveShouldBeSentCorrectlyUsingLuaScript() {
			super.shutdownSaveShouldBeSentCorrectlyUsingLuaScript();
		}

		/**
		 * @see DATAREDIS-267
		 */
		@Test(expected = UnsupportedOperationException.class)
		public void killClientShouldDelegateCallCorrectly() {
			super.killClientShouldDelegateCallCorrectly();
		}

		/**
		 * @see DATAREDIS-270
		 */
		@Override
		@Test(expected = UnsupportedOperationException.class)
		public void getClientNameShouldSendRequestCorrectly() {
			super.getClientNameShouldSendRequestCorrectly();
		}

		/**
		 * @see DATAREDIS-277
		 */
		@Override
		@Test(expected = UnsupportedOperationException.class)
		public void slaveOfShouldBeSentCorrectly() {
			super.slaveOfShouldBeSentCorrectly();
		}

		/**
		 * @see DATAREDIS-277
		 */
		@Test(expected = UnsupportedOperationException.class)
		public void slaveOfNoOneShouldBeSentCorrectly() {
			super.slaveOfNoOneShouldBeSentCorrectly();
		}

		/**
		 * @see DATAREDIS-531
		 */
		@Test(expected = UnsupportedOperationException.class)
		public void scanShouldKeepTheConnectionOpen() {
			super.scanShouldKeepTheConnectionOpen();
		}

		/**
		 * @see DATAREDIS-531
		 */
		@Test(expected = UnsupportedOperationException.class)
		public void scanShouldCloseTheConnectionWhenCursorIsClosed() throws IOException {
			super.scanShouldCloseTheConnectionWhenCursorIsClosed();
		}

		/**
		 * @see DATAREDIS-531
		 */
		@Test(expected = UnsupportedOperationException.class)
		public void sScanShouldKeepTheConnectionOpen() {
			super.sScanShouldKeepTheConnectionOpen();
		}

		/**
		 * @see DATAREDIS-531
		 */
		@Test(expected = UnsupportedOperationException.class)
		public void sScanShouldCloseTheConnectionWhenCursorIsClosed() throws IOException {
			super.sScanShouldCloseTheConnectionWhenCursorIsClosed();
		}

		/**
		 * @see DATAREDIS-531
		 */
		@Test(expected = UnsupportedOperationException.class)
		public void zScanShouldKeepTheConnectionOpen() {
			super.zScanShouldKeepTheConnectionOpen();
		}

		/**
		 * @see DATAREDIS-531
		 */
		@Test(expected = UnsupportedOperationException.class)
		public void zScanShouldCloseTheConnectionWhenCursorIsClosed() throws IOException {
			super.zScanShouldCloseTheConnectionWhenCursorIsClosed();
		}

		/**
		 * @see DATAREDIS-531
		 */
		@Test(expected = UnsupportedOperationException.class)
		public void hScanShouldKeepTheConnectionOpen() {
			super.hScanShouldKeepTheConnectionOpen();
		}

		/**
		 * @see DATAREDIS-531
		 */
		@Test(expected = UnsupportedOperationException.class)
		public void hScanShouldCloseTheConnectionWhenCursorIsClosed() throws IOException {
			super.hScanShouldCloseTheConnectionWhenCursorIsClosed();
		}

	}

	/**
	 * {@link Jedis} extension allowing to use mocked object as {@link Client}.
	 */
	private static class MockedClientJedis extends Jedis {

		public MockedClientJedis(String host, Client client) {
			super(host);
			this.client = client;
		}
	}
}

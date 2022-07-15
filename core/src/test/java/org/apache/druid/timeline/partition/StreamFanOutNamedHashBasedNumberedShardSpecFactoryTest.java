/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.timeline.partition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.druid.TestObjectMapper;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class StreamFanOutNamedHashBasedNumberedShardSpecFactoryTest
{
  @Test
  public void testSerde() throws Exception
  {
    testSerdeHelper(
        StreamFanOutNamedHashBasedNumberedShardSpecFactory.instance("ns1"),
        "{\"type\":\"stream_fanout_named_hashed\",\"partitionName\":\"ns1\"}"
    );
    testSerdeHelper(
        new StreamFanOutNamedHashBasedNumberedShardSpecFactory(ImmutableList.of("partner_id"), ImmutableSet.of(1, 3, 5), 10, 3, "ns2"),
        "{\"type\":\"stream_fanout_named_hashed\",\"partitionDimensions\":[\"partner_id\"],"
        + "\"streamPartitionIds\":[1,3,5],\"streamPartitions\":10,\"fanOutSize\":3,\"partitionName\":\"ns2\"}"
    );
  }

  void testSerdeHelper(StreamFanOutNamedHashBasedNumberedShardSpecFactory factory, String expectedSerializedString)
      throws IOException
  {
    final ObjectMapper objectMapper = new TestObjectMapper();
    // Verify serialized format
    Assert.assertEquals(expectedSerializedString, objectMapper.writeValueAsString(factory));
    // Verify round trip
    final ShardSpecFactory deserializedFactory = objectMapper.readValue(
        objectMapper.writeValueAsBytes(factory),
        ShardSpecFactory.class
    );
    Assert.assertEquals(
        factory.getPartitionDimensions(),
        ((StreamFanOutNamedHashBasedNumberedShardSpecFactory) deserializedFactory).getPartitionDimensions()
    );
    Assert.assertEquals(
        factory.getStreamPartitionIds(),
        ((StreamFanOutNamedHashBasedNumberedShardSpecFactory) deserializedFactory).getStreamPartitionIds()
    );
    Assert.assertEquals(
        factory.getStreamPartitions(),
        ((StreamFanOutNamedHashBasedNumberedShardSpecFactory) deserializedFactory).getStreamPartitions()
    );
    Assert.assertEquals(
        factory.getFanOutSize(),
        ((StreamFanOutNamedHashBasedNumberedShardSpecFactory) deserializedFactory).getFanOutSize()
    );
    Assert.assertEquals(
        factory.getPartitionName(),
        ((StreamFanOutNamedHashBasedNumberedShardSpecFactory) deserializedFactory).getPartitionName()
    );
  }
}

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

public class BloomFilterStreamFanOutHashBasedNumberedShardSpecFactoryTest
{
  @Test
  public void testSerde() throws Exception
  {
    testSerdeHelper(
        BloomFilterStreamFanOutHashBasedNumberedShardSpecFactory.instance(),
        "{\"type\":\"bloom_filter_stream_fanout_hashed\"}"
    );
    testSerdeHelper(
        new BloomFilterStreamFanOutHashBasedNumberedShardSpecFactory(ImmutableList.of("partner_id"), ImmutableSet.of(1, 3, 5), 10, 3),
        "{\"type\":\"bloom_filter_stream_fanout_hashed\",\"partitionDimensions\":[\"partner_id\"],\"streamPartitionIds\":[1,3,5],\"streamPartitions\":10,\"fanOutSize\":3}"
    );
  }

  void testSerdeHelper(BloomFilterStreamFanOutHashBasedNumberedShardSpecFactory factory, String expectedSerializedString)
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
        ((BloomFilterStreamFanOutHashBasedNumberedShardSpecFactory) deserializedFactory).getPartitionDimensions()
    );
    Assert.assertEquals(
        factory.getStreamPartitionIds(),
        ((BloomFilterStreamFanOutHashBasedNumberedShardSpecFactory) deserializedFactory).getStreamPartitionIds()
    );
    Assert.assertEquals(
        factory.getStreamPartitions(),
        ((BloomFilterStreamFanOutHashBasedNumberedShardSpecFactory) deserializedFactory).getStreamPartitions()
    );
    Assert.assertEquals(
        factory.getFanOutSize(),
        ((BloomFilterStreamFanOutHashBasedNumberedShardSpecFactory) deserializedFactory).getFanOutSize()
    );
  }
}

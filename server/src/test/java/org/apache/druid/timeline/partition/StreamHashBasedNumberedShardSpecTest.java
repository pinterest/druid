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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import org.apache.druid.server.ServerTestHelper;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class StreamHashBasedNumberedShardSpecTest
{

  private final ObjectMapper objectMapper = ShardSpecTestUtils.initObjectMapper();

  @Test
  public void testSerdeRoundTrip() throws Exception
  {
    testSerdeHelper(
        new StreamHashBasedNumberedShardSpec(
            1,
            2,
            0,
            1,
            ImmutableList.of("partner_id"),
            ImmutableSet.of(1, 3, 5),
            null,
            10,
            ServerTestHelper.MAPPER
        ),
        "{\"type\":\"stream_hashed\",\"partitionNum\":1,\"partitions\":2,\"partitionDimensions\":[\"partner_id\"],\"streamPartitionIds\":[1,3,5],\"streamPartitions\":10}"
    );
  }

  void testSerdeHelper(StreamHashBasedNumberedShardSpec spec, String expectedSerializedString) throws IOException
  {
    // Verify serialized format
    Assert.assertEquals(expectedSerializedString, ServerTestHelper.MAPPER.writeValueAsString(spec));
    // Verify round trip
    final ShardSpec deserializedSpec = ServerTestHelper.MAPPER.readValue(
        ServerTestHelper.MAPPER.writeValueAsBytes(spec),
        ShardSpec.class
    );
    Assert.assertEquals(spec.getPartitionNum(), deserializedSpec.getPartitionNum());
    Assert.assertEquals(spec.getNumCorePartitions(), ((StreamHashBasedNumberedShardSpec) deserializedSpec).getNumCorePartitions());
    Assert.assertEquals(
        spec.getPartitionDimensions(),
        ((StreamHashBasedNumberedShardSpec) deserializedSpec).getPartitionDimensions()
    );
    Assert.assertEquals(
        spec.getStreamPartitionIds(),
        ((StreamHashBasedNumberedShardSpec) deserializedSpec).getStreamPartitionIds()
    );
    Assert.assertEquals(
        spec.getStreamPartitions(),
        ((StreamHashBasedNumberedShardSpec) deserializedSpec).getStreamPartitions()
    );
  }

  @Test
  public void testCompatible()
  {
    final StreamHashBasedNumberedShardSpec spec = new StreamHashBasedNumberedShardSpec(
        1,
        2,
        0,
        1,
        ImmutableList.of("partner_id"),
        ImmutableSet.of(1, 3, 5),
        null,
        10,
        ServerTestHelper.MAPPER
    );

    Assert.assertTrue(spec.isCompatible(NumberedShardSpec.class));
    Assert.assertTrue(spec.isCompatible(NumberedOverwriteShardSpec.class));
    Assert.assertTrue(spec.isCompatible(StreamHashBasedNumberedShardSpec.class));
  }

  @Test
  public void testPossibleInDomain()
  {
    final RangeSet<String> rangeSet = TreeRangeSet.create();
    rangeSet.add(Range.closed("123", "123"));
    final Map<String, RangeSet<String>> domain = ImmutableMap.of("partner_id", rangeSet);

    // With partition info and matching partition dimensions
    final int streamPartitions = 10;
    List<StreamHashBasedNumberedShardSpec> shardSpecs = ImmutableList.of(
        new StreamHashBasedNumberedShardSpec(
            1,
            0,
            0,
            1,
            ImmutableList.of("partner_id"),
            ImmutableSet.of(1, 3, 5),
            null,
            streamPartitions,
            ServerTestHelper.MAPPER
        ),
        new StreamHashBasedNumberedShardSpec(
            2,
            0,
            0,
            1,
            ImmutableList.of("partner_id"),
            ImmutableSet.of(2, 4, 6),
            null,
            streamPartitions,
            ServerTestHelper.MAPPER
        ),
        new StreamHashBasedNumberedShardSpec(
            3,
            0,
            0,
            1,
            ImmutableList.of("partner_id"),
            ImmutableSet.of(7, 8, 9, 10),
            null,
            streamPartitions,
            ServerTestHelper.MAPPER
        )
    );
    Assert.assertEquals(1, shardSpecs.stream().filter(s -> s.possibleInDomain(domain)).count());

    // Partition dimensions not match
    final Map<String, RangeSet<String>> domain1 = ImmutableMap.of("vistor_id", rangeSet);
    Assert.assertEquals(shardSpecs.size(), shardSpecs.stream().filter(s -> s.possibleInDomain(domain1)).count());
  }
}

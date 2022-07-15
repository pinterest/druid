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

public class StreamFanOutNamedHashBasedNumberedShardSpecTest
{
  @Test
  public void testSerdeRoundTrip() throws Exception
  {
    testSerdeHelper(
        new StreamFanOutNamedHashBasedNumberedShardSpec(
            1,
            2,
            ImmutableList.of("partner_id"),
            ImmutableSet.of(1, 3, 5),
            10,
            5,
            "ns1",
            ServerTestHelper.MAPPER
        ),
        "{\"type\":\"stream_fanout_named_hashed\",\"partitionNum\":1,\"partitions\":2,"
        + "\"partitionDimensions\":[\"partner_id\"],\"streamPartitionIds\":[1,3,5],\"streamPartitions\":10,"
        + "\"fanOutSize\":5,\"partitionName\":\"ns1\"}"
    );
  }

  void testSerdeHelper(StreamFanOutNamedHashBasedNumberedShardSpec spec, String expectedSerializedString) throws IOException
  {
    // Verify serialized format
    Assert.assertEquals(expectedSerializedString, ServerTestHelper.MAPPER.writeValueAsString(spec));
    // Verify round trip
    final ShardSpec deserializedSpec = ServerTestHelper.MAPPER.readValue(
        ServerTestHelper.MAPPER.writeValueAsBytes(spec),
        ShardSpec.class
    );
    Assert.assertEquals(spec.getPartitionNum(), deserializedSpec.getPartitionNum());
    Assert.assertEquals(spec.getNumCorePartitions(), ((StreamFanOutNamedHashBasedNumberedShardSpec) deserializedSpec).getNumCorePartitions());
    Assert.assertEquals(
        spec.getPartitionDimensions(),
        ((StreamFanOutNamedHashBasedNumberedShardSpec) deserializedSpec).getPartitionDimensions()
    );
    Assert.assertEquals(
        spec.getStreamPartitionIds(),
        ((StreamFanOutNamedHashBasedNumberedShardSpec) deserializedSpec).getStreamPartitionIds()
    );
    Assert.assertEquals(
        spec.getStreamPartitions(),
        ((StreamFanOutNamedHashBasedNumberedShardSpec) deserializedSpec).getStreamPartitions()
    );
    Assert.assertEquals(
        spec.getFanOutSize(),
        ((StreamFanOutNamedHashBasedNumberedShardSpec) deserializedSpec).getFanOutSize()
    );

    Assert.assertEquals(spec.getPartitionName(), ((StreamFanOutNamedHashBasedNumberedShardSpec) deserializedSpec).getPartitionName());
  }

  @Test
  public void testCompatible()
  {
    final StreamFanOutNamedHashBasedNumberedShardSpec spec = new StreamFanOutNamedHashBasedNumberedShardSpec(
        1,
        2,
        ImmutableList.of("partner_id"),
        ImmutableSet.of(1, 3, 5),
        10,
        null,
        "ns2",
        ServerTestHelper.MAPPER
    );

    Assert.assertTrue(spec.isCompatible(NumberedShardSpec.class));
    Assert.assertTrue(spec.isCompatible(NumberedOverwriteShardSpec.class));
    Assert.assertTrue(spec.isCompatible(StreamFanOutHashBasedNumberedShardSpec.class));
    Assert.assertTrue(spec.isCompatible(StreamFanOutNamedHashBasedNumberedShardSpec.class));
  }

  @Test
  public void testPossibleInDomain()
  {
    final RangeSet<String> rangeSet = TreeRangeSet.create();
    rangeSet.add(Range.closed("123", "123"));
    final Map<String, RangeSet<String>> domain = ImmutableMap.of("partner_id", rangeSet);

    // With partition info and matching partition dimensions
    final int streamPartitions = 10;
    List<StreamFanOutNamedHashBasedNumberedShardSpec> shardSpecs = ImmutableList.of(
        new StreamFanOutNamedHashBasedNumberedShardSpec(
            1,
            0,
            ImmutableList.of("partner_id"),
            ImmutableSet.of(1, 3, 5),
            streamPartitions,
            null,
            "ns3",
            ServerTestHelper.MAPPER
        ),
        new StreamFanOutNamedHashBasedNumberedShardSpec(
            2,
            0,
            ImmutableList.of("partner_id"),
            ImmutableSet.of(2, 4, 6),
            streamPartitions,
            null,
            "ns3",
            ServerTestHelper.MAPPER
        ),
        new StreamFanOutNamedHashBasedNumberedShardSpec(
            3,
            0,
            ImmutableList.of("partner_id"),
            ImmutableSet.of(7, 8, 9, 10),
            streamPartitions,
            null,
            "ns3",
            ServerTestHelper.MAPPER
        )
    );
    Assert.assertEquals(1, shardSpecs.stream().filter(s -> s.possibleInDomain(domain)).count());

    // Partition dimensions not match
    final Map<String, RangeSet<String>> domain1 = ImmutableMap.of("vistor_id", rangeSet);
    Assert.assertEquals(shardSpecs.size(), shardSpecs.stream().filter(s -> s.possibleInDomain(domain1)).count());
  }

  @Test
  public void testPossibleInDomainWithFanout()
  {
    final RangeSet<String> rangeSet = TreeRangeSet.create();
    rangeSet.add(Range.closed("123", "123"));
    final Map<String, RangeSet<String>> domain = ImmutableMap.of("partner_id", rangeSet);

    // With partition info and matching partition dimensions
    final int streamPartitions = 10;
    final int fanoutSize = 2;
    List<StreamFanOutNamedHashBasedNumberedShardSpec> shardSpecs = ImmutableList.of(
        new StreamFanOutNamedHashBasedNumberedShardSpec(
            1,
            0,
            ImmutableList.of("partner_id"),
            ImmutableSet.of(1, 5),
            streamPartitions,
            fanoutSize,
            "ns3",
            ServerTestHelper.MAPPER
        ),
        new StreamFanOutNamedHashBasedNumberedShardSpec(
            2,
            0,
            ImmutableList.of("partner_id"),
            ImmutableSet.of(2, 6),
            streamPartitions,
            fanoutSize,
            "ns3",
            ServerTestHelper.MAPPER
        ),
        new StreamFanOutNamedHashBasedNumberedShardSpec(
            3,
            0,
            ImmutableList.of("partner_id"),
            ImmutableSet.of(3, 7, 9),
            streamPartitions,
            fanoutSize,
            "ns3",
            ServerTestHelper.MAPPER
        ),
        new StreamFanOutNamedHashBasedNumberedShardSpec(
            4,
            0,
            ImmutableList.of("partner_id"),
            ImmutableSet.of(4, 8, 10),
            streamPartitions,
            fanoutSize,
            "ns3",
            ServerTestHelper.MAPPER
        )
    );
    Assert.assertEquals(2, shardSpecs.stream().filter(s -> s.possibleInDomain(domain)).count());

    // Partition dimensions not match
    final Map<String, RangeSet<String>> domain1 = ImmutableMap.of("vistor_id", rangeSet);
    Assert.assertEquals(shardSpecs.size(), shardSpecs.stream().filter(s -> s.possibleInDomain(domain1)).count());
  }
}

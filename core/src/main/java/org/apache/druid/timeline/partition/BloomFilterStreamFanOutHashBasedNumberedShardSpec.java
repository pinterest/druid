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

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import gnu.trove.set.hash.THashSet;
import org.apache.druid.data.input.InputRow;
import org.apache.druid.java.util.common.IAE;
import org.apache.druid.java.util.emitter.EmittingLogger;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Set;

public class BloomFilterStreamFanOutHashBasedNumberedShardSpec extends StreamFanOutHashBasedNumberedShardSpec
{
  private static final EmittingLogger log = new EmittingLogger(BloomFilterStreamFanOutHashBasedNumberedShardSpec.class);

  // Consts related to serialized file format of bloom filter
  public static final byte V1_VERSION = 0x1;
  public static final int CURRENT_VERSION_ID = V1_VERSION;
  public static final String BLOOM_FILTER_DIR = "partition_dimensions_bloom_filter";
  public static final String BLOOM_FILTER_BIN_FILE = "bloom_filter.bin";
  public static final String BLOOM_FILTER_VERSION_FILE = "version.bin";

  private final ObjectMapper jsonMapper;

  @JsonIgnore
  private BloomFilter<byte[]> bloomFilter;

  // A temporary set to buffer values before inserting into the bloom filter when no more new values will arrive
  // The reason we need to buffer all the values until time to construct the bloom filter in a shot is because bloom
  // filter construction expects an expected number of insertions (ideal number would be the number of values to insert)
  // to ensure the desired false positive probability doesn't degrade too much
  @JsonIgnore
  private THashSet<byte[]> partitionDimensionValuesSet;

  @JsonCreator
  public BloomFilterStreamFanOutHashBasedNumberedShardSpec(
      @JsonProperty("partitionNum") int partitionNum,
      @JsonProperty("partitions") int partitions,
      @JsonProperty("partitionDimensions") @Nullable List<String> partitionDimensions,
      @JsonProperty("streamPartitionIds") @Nullable Set<Integer> streamPartitionIds,
      @JsonProperty("streamPartitions") @Nullable Integer streamPartitions,
      @JsonProperty("fanOutSize") @Nullable Integer fanOutSize,
      @JacksonInject ObjectMapper jsonMapper
  )
  {
    super(partitionNum, partitions, partitionDimensions, streamPartitionIds, streamPartitions, fanOutSize, jsonMapper);
    this.jsonMapper = jsonMapper;
  }

  public void updateBloomFilter(InputRow inputRow)
  {
    if (getPartitionDimensions().isEmpty()) {
      return;
    }

    if (partitionDimensionValuesSet == null) {
      this.partitionDimensionValuesSet = new THashSet<>();
    }

    /*try {
      List<Object> groupKey = getGroupKey(0, inputRow);
      partitionDimensionValuesSet.add(jsonMapper.writeValueAsBytes(groupKey));
    }
    catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }*/
  }

  /**
   * Flush buffered values to create the bloom filter with default desired false positive probability
   */
  public void completeBloomFilter()
  {
    completeBloomFilter(null);
  }

  /**
   * Flush buffered values to create the bloom filter with custom desired false positive probability
   *
   * @param desiredFalsePositiveProbability The custom desired false positive probability
   */
  public void completeBloomFilter(Double desiredFalsePositiveProbability)
  {
    if (partitionDimensionValuesSet == null || partitionDimensionValuesSet.isEmpty()) {
      return;
    }

    int expectedInsertions = partitionDimensionValuesSet.size();
    log.info("Inserting [%d] unique values of partition dimensions into bloom filter.", expectedInsertions);
    bloomFilter = desiredFalsePositiveProbability == null ?
                  BloomFilter.create(Funnels.byteArrayFunnel(), expectedInsertions) :
                  BloomFilter.create(Funnels.byteArrayFunnel(), expectedInsertions, desiredFalsePositiveProbability);
    for (byte[] v : partitionDimensionValuesSet) {
      bloomFilter.put(v);
    }
    partitionDimensionValuesSet.clear();
  }

  public void deserializeBloomFilter(final byte version, final byte[] bloomFilter)
  {
    if (bloomFilter == null || bloomFilter.length == 0) {
      return;
    }

    if (version != V1_VERSION) {
      throw new IAE("Expected version[%d], got[%d]", V1_VERSION, version);
    }

    try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bloomFilter);
         ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream)) {
      this.bloomFilter = (BloomFilter<byte[]>) objectInputStream.readObject();
    }
    catch (ClassNotFoundException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Nullable
  public byte[] serializeBloomFilter()
  {
    if (bloomFilter == null) {
      return null;
    } else {
      byte[] bytes;
      try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
           ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
        objectOutputStream.writeObject(bloomFilter);
        bytes = byteArrayOutputStream.toByteArray();
      }
      catch (IOException ex) {
        throw new RuntimeException(ex);
      }

      log.info("Serialized [%d] bytes of bloom filter with strategy of version [%d]", bytes.length, CURRENT_VERSION_ID);
      return bytes;
    }
  }

  public BloomFilter<byte[]> getBloomFilter()
  {
    return bloomFilter;
  }

  public void setBloomFilter(BloomFilter<byte[]> bloomFilter)
  {
    this.bloomFilter = bloomFilter;
  }

  @Override
  public boolean isCompatible(Class<? extends ShardSpec> other)
  {
    return other == NumberedShardSpec.class ||
           other == NumberedOverwriteShardSpec.class ||
           other == StreamHashBasedNumberedShardSpec.class ||
           other == StreamFanOutHashBasedNumberedShardSpec.class ||
           other == BloomFilterStreamFanOutHashBasedNumberedShardSpec.class;
  }

  @Override
  public String toString()
  {
    return "BloomFilterStreamFanOutHashBasedNumberedShardSpec{" +
           "partitionNum=" + getPartitionNum() +
           ", partitions=" + getNumCorePartitions() +
           ", partitionDimensions=" + getPartitionDimensions() +
           ", streamPartitionIds=" + getStreamPartitionIds() +
           ", streamPartitions=" + getStreamPartitions() +
           ", fanOutSize=" + getFanOutSize() +
           '}';
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) {
      return true;
    }

    if (!(o instanceof BloomFilterStreamFanOutHashBasedNumberedShardSpec)) {
      return false;
    }

    return super.equals(o);
  }

  @Override
  protected boolean groupKeyIsInChunk(List<Object> groupKey)
  {
    // Return true if and only if both hashing and bloom filter checks pass
    try {
      if (!super.groupKeyIsInChunk(groupKey)) {
        return false;
      } else {
        return bloomFilter != null ? bloomFilter.mightContain(jsonMapper.writeValueAsBytes(groupKey)) : true;
      }
    }
    catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}

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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.druid.java.util.common.logger.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.apache.druid.timeline.partition.ShardSpec.Type.STREAM_FANOUT_HASHED;

public class StreamFanOutHashBasedNumberedShardSpec extends StreamHashBasedNumberedShardSpec
{
  private static final Logger log = new Logger(StreamFanOutHashBasedNumberedShardSpec.class);

  private static final int DEFAULT_FAN_OUT_SIZE = 1;

  private final ObjectMapper jsonMapper;

  @JsonIgnore
  private final Integer fanOutSize;

  @JsonCreator
  public StreamFanOutHashBasedNumberedShardSpec(
      @JsonProperty("partitionNum") int partitionNum,
      @JsonProperty("partitions") int partitions,
      @JsonProperty("bucketId") @Nullable Integer bucketId,
      @JsonProperty("numBuckets") @Nullable Integer numBuckets,
      @JsonProperty("partitionDimensions") @Nullable List<String> partitionDimensions,
      @JsonProperty("streamPartitionIds") @Nullable Set<Integer> streamPartitionIds,
      @JsonProperty("streamPartitions") @Nullable Integer streamPartitions,
      @JsonProperty("partitionFunction") @Nullable HashPartitionFunction partitionFunction,
      @JsonProperty("fanOutSize") @Nullable Integer fanOutSize,
      @JacksonInject ObjectMapper jsonMapper
  )
  {
    super(partitionNum, partitions, bucketId, numBuckets, partitionDimensions, streamPartitionIds, partitionFunction, streamPartitions,
        jsonMapper);
    this.jsonMapper = jsonMapper;
    this.fanOutSize = fanOutSize == null ? DEFAULT_FAN_OUT_SIZE : fanOutSize;
  }

  @JsonProperty("fanOutSize")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public Integer getFanOutSize()
  {
    return fanOutSize;
  }

  @Override
  public boolean isCompatible(Class<? extends ShardSpec> other)
  {
    return other == NumberedShardSpec.class ||
           other == NumberedOverwriteShardSpec.class ||
           other == StreamHashBasedNumberedShardSpec.class ||
           other == StreamFanOutHashBasedNumberedShardSpec.class;
  }

  @Override
  public String toString()
  {
    return "StreamFanOutHashBasedNumberedShardSpec{" +
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

    if (!(o instanceof StreamFanOutHashBasedNumberedShardSpec)) {
      return false;
    }

    final StreamFanOutHashBasedNumberedShardSpec that = (StreamFanOutHashBasedNumberedShardSpec) o;
    if (getPartitionNum() != that.getPartitionNum()) {
      return false;
    }
    return getNumCorePartitions() == that.getNumCorePartitions();
  }

  @Override
  public int hashCode()
  {
    // partitionDimensions, streamPartitions and streamPartitionIds and fanOutSize are intentionally
    // left unincluded in equals, and hashCode.
    return Objects.hash(getPartitionNum(), getNumCorePartitions());
  }

  /*@Override*/
  public String getType()
  {
    return STREAM_FANOUT_HASHED;
  }


  /*@Override*/
  protected boolean groupKeyIsInChunk(List<Object> groupKey)
  {
    try {
      Integer streamPartitions = getStreamPartitions();
      Set streamPartitionIds = getStreamPartitionIds();
      Integer fanOutSize = getFanOutSize();
      int basePartitionId = Math.abs(Objects.hash(jsonMapper, groupKey) % streamPartitions);

      for (int i = 0; i < fanOutSize; i++) {
        if (streamPartitionIds.contains((basePartitionId + i) % streamPartitions)) {
          return true;
        }
      }
      return false;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}

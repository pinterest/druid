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
import java.util.Set;

public class StreamFanOutNamedHashBasedNumberedShardSpec extends StreamFanOutHashBasedNumberedShardSpec
{
  private static final Logger log = new Logger(StreamFanOutNamedHashBasedNumberedShardSpec.class);

  private final ObjectMapper jsonMapper;

  @JsonIgnore
  private final String partitionName;

  @JsonCreator
  public StreamFanOutNamedHashBasedNumberedShardSpec(
      @JsonProperty("partitionNum") int partitionNum,
      @JsonProperty("partitions") int partitions,
      @JsonProperty("partitionDimensions") @Nullable List<String> partitionDimensions,
      @JsonProperty("streamPartitionIds") @Nullable Set<Integer> streamPartitionIds,
      @JsonProperty("streamPartitions") @Nullable Integer streamPartitions,
      @JsonProperty("fanOutSize") @Nullable Integer fanOutSize,
      @JsonProperty("partitionName") String partitionName,
      @JacksonInject ObjectMapper jsonMapper
  )
  {
    super(partitionNum, partitions, partitionDimensions, streamPartitionIds, streamPartitions, fanOutSize, jsonMapper);
    this.jsonMapper = jsonMapper;
    this.partitionName = partitionName;
  }

  @JsonProperty("partitionName")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String getPartitionName()
  {
    return partitionName;
  }

  @Override
  public boolean isCompatible(Class<? extends ShardSpec> other)
  {
    return other == NumberedShardSpec.class ||
           other == NumberedOverwriteShardSpec.class ||
           other == StreamHashBasedNumberedShardSpec.class ||
           other == StreamFanOutHashBasedNumberedShardSpec.class ||
           other == StreamFanOutNamedHashBasedNumberedShardSpec.class;
  }

  @Override
  public <T> PartitionChunk<T> createChunk(T obj)
  {
    return NamedNumberedPartitionChunk.make(getPartitionNum(), getNumCorePartitions(), partitionName, obj);
  }

  @Override
  public Object getIdentifier()
  {
    return this.partitionName + "_" + this.getPartitionNum();
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
           ", partitionName=" + getPartitionName() +
           '}';
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) {
      return true;
    }

    if (!(o instanceof StreamFanOutNamedHashBasedNumberedShardSpec)) {
      return false;
    }

    final StreamFanOutNamedHashBasedNumberedShardSpec that = (StreamFanOutNamedHashBasedNumberedShardSpec) o;
    return (getPartitionNum() == that.getPartitionNum() &&
            getNumCorePartitions() == that.getNumCorePartitions() &&
            getPartitionName().compareTo(that.getPartitionName()) == 0);
  }
}

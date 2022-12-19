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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import org.apache.druid.java.util.common.logger.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class StreamFanOutNamedHashBasedNumberedShardSpecFactory implements PartialShardSpec
{
  private static final Logger log = new Logger(StreamFanOutNamedHashBasedNumberedShardSpecFactory.class);

  private static final ConcurrentHashMap<String, StreamFanOutNamedHashBasedNumberedShardSpecFactory> INSTANCES =
      new ConcurrentHashMap<>();

  @JsonIgnore
  private List<String> partitionDimensions;
  @JsonIgnore
  private Set<Integer> streamPartitionIds;
  @JsonIgnore
  private Integer streamPartitions;
  @JsonIgnore
  private Integer fanOutSize;
  @JsonIgnore
  private String partitionName;

  public static StreamFanOutNamedHashBasedNumberedShardSpecFactory instance(String partitionName)
  {
    Preconditions.checkNotNull(partitionName, "partitionName is required");
    return INSTANCES.computeIfAbsent(partitionName, StreamFanOutNamedHashBasedNumberedShardSpecFactory::new);
  }

  private StreamFanOutNamedHashBasedNumberedShardSpecFactory(String partitionName)
  {
    this.partitionDimensions = null;
    this.streamPartitionIds = null;
    this.streamPartitions = null;
    this.fanOutSize = null;
    this.partitionName = partitionName;
  }

  @JsonCreator
  public StreamFanOutNamedHashBasedNumberedShardSpecFactory(
      @JsonProperty("partitionDimensions") @Nullable List<String> partitionDimensions,
      @JsonProperty("streamPartitionIds") @Nullable Set<Integer> streamPartitionIds,
      @JsonProperty("streamPartitions") @Nullable Integer streamPartitions,
      @JsonProperty("fanOutSize") @Nullable Integer fanOutSize,
      @JsonProperty("partitionName") String partitionName
  )
  {
    this.partitionDimensions = partitionDimensions;
    this.streamPartitionIds = streamPartitionIds;
    this.streamPartitions = streamPartitions;
    this.fanOutSize = fanOutSize;
    this.partitionName = partitionName;
  }

  public StreamFanOutNamedHashBasedNumberedShardSpecFactory set(List<String> partitionDimensions,
                                                                Set<Integer> streamPartitionIds,
                                                                Integer streamPartitions,
                                                                Integer fanOutSize
  )
  {
    this.partitionDimensions = partitionDimensions;
    this.streamPartitionIds = streamPartitionIds;
    this.streamPartitions = streamPartitions;
    this.fanOutSize = fanOutSize;
    return this;
  }

  @JsonProperty("partitionDimensions")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public List<String> getPartitionDimensions()
  {
    return partitionDimensions;
  }

  @JsonProperty("streamPartitionIds")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public Set<Integer> getStreamPartitionIds()
  {
    return streamPartitionIds;
  }

  @JsonProperty("streamPartitions")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public Integer getStreamPartitions()
  {
    return streamPartitions;
  }

  @JsonProperty("fanOutSize")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public Integer getFanOutSize()
  {
    return fanOutSize;
  }

  @JsonProperty("partitionName")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String getPartitionName()
  {
    return partitionName;
  }

  //@Override
  public ShardSpec create(ObjectMapper objectMapper, @Nullable ShardSpec specOfPreviousMaxPartitionId)
  {
    if (specOfPreviousMaxPartitionId == null) {
      return new StreamFanOutNamedHashBasedNumberedShardSpec(
          0,
          0,
          partitionDimensions,
          streamPartitionIds,
          streamPartitions,
          fanOutSize,
          partitionName,
          objectMapper
      );
    } else {
      final NumberedShardSpec prevSpec = (NumberedShardSpec) specOfPreviousMaxPartitionId;
      return new StreamFanOutNamedHashBasedNumberedShardSpec(
          prevSpec.getPartitionNum() + 1,
          prevSpec.getNumCorePartitions(),
          partitionDimensions,
          streamPartitionIds,
          streamPartitions,
          fanOutSize,
          partitionName,
          objectMapper
      );
    }
  }

  //@Override
  public ShardSpec create(ObjectMapper objectMapper, int partitionId)
  {
    return new StreamFanOutNamedHashBasedNumberedShardSpec(
        partitionId,
        0,
        partitionDimensions,
        streamPartitionIds,
        streamPartitions,
        fanOutSize,
        partitionName,
        objectMapper
    );
  }

  @Override
  public ShardSpec complete(ObjectMapper objectMapper, int partitionId, int numCorePartitions) {
    return new StreamFanOutNamedHashBasedNumberedShardSpec(
            partitionId,
            numCorePartitions,
            partitionDimensions,
            streamPartitionIds,
            streamPartitions,
            fanOutSize,
            partitionName,
            objectMapper
    );
  }

  @Override
  public Class<? extends ShardSpec> getShardSpecClass()
  {
    return StreamFanOutNamedHashBasedNumberedShardSpec.class;
  }

  @Override
  public boolean useNonRootGenerationPartitionSpace() {
    return PartialShardSpec.super.useNonRootGenerationPartitionSpace();
  }
}

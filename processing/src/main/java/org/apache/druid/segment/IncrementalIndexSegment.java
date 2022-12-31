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

package org.apache.druid.segment;

import org.apache.druid.java.util.common.logger.Logger;
import org.apache.druid.segment.incremental.IncrementalIndex;
import org.apache.druid.segment.incremental.IncrementalIndexStorageAdapter;
import org.apache.druid.timeline.SegmentId;
import org.joda.time.Interval;

/**
 */
public class IncrementalIndexSegment implements Segment
{
  private static final Logger log = new Logger(IncrementalIndexSegment.class);
  private final IncrementalIndex index;
  private final SegmentId segmentId;
  private static int countLogPrints =0;

  public IncrementalIndexSegment(IncrementalIndex index, SegmentId segmentId)
  {
    this.index = index;
    this.segmentId = segmentId;
  }

  @Override
  public SegmentId getId()
  {
    return segmentId;
  }

  @Override
  public Interval getDataInterval()
  {
    return index.getInterval();
  }

  @Override
  public QueryableIndex asQueryableIndex()
  {
    return null;
  }

  @Override
  public StorageAdapter asStorageAdapter()
  {
    countLogPrints++;
    if (countLogPrints< 6000) {
      //log.error("debasatwa: first line asStorageAdapter creating new IncrementalIndexStorageAdapter");
    }
    return new IncrementalIndexStorageAdapter(index);
  }

  @Override
  public void close()
  {
    index.close();
  }
}

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

package org.apache.druid.query.groupby.zerofill;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.apache.druid.java.util.common.Intervals;
import org.apache.druid.java.util.common.granularity.Granularity;
import org.joda.time.Interval;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Set;

public class ZeroFilledTimestampedDimensionIteratorTest
{
  private static final ImmutableList<Object> FIRST_DIM_SET = ImmutableList.of("a", 1, 1.0);
  private static final ImmutableList<Object> SECOND_DIM_SET = ImmutableList.of("b", 2, 2.0);
  private static final List<List<Object>> DIMENSIONS_TO_ZERO_FILL = ImmutableList.of(FIRST_DIM_SET, SECOND_DIM_SET);
  private static final Granularity GRANULARITY_ALL = Granularity.fromString("all");
  private static final Granularity GRANULARITY_YEAR = Granularity.fromString("year");
  private static final Granularity GRANULARITY_MONTH = Granularity.fromString("month");
  private static final Granularity GRANULARITY_DAY = Granularity.fromString("day");
  private static final Interval INTERVAL_1 = Intervals.of("2020-05-01T00:00:00Z/2020-05-03T00:00:00Z");
  private static final Interval INTERVAL_2 = Intervals.of("2020-05-10T00:00:00Z/2020-05-11T00:00:00Z");
  private static final List<Interval> INTERVALS = ImmutableList.of(INTERVAL_1, INTERVAL_2);


  @Test
  public void testGetTimestampsForIntervalsForGranularityYear()
  {
    Set<Long> timestamps = ZeroFilledTimestampedDimensionIterator.getTimestampsForIntervals(GRANULARITY_YEAR, INTERVALS);
    Assert.assertEquals(1, timestamps.size());
    Assert.assertEquals(Sets.newHashSet(1577836800000L), timestamps);
  }

  @Test
  public void testGetTimestampsForIntervalsForGranularityMonth()
  {
    Set<Long> timestamps = ZeroFilledTimestampedDimensionIterator.getTimestampsForIntervals(GRANULARITY_MONTH, INTERVALS);
    Assert.assertEquals(1, timestamps.size());
    Assert.assertEquals(Sets.newHashSet(1588291200000L), timestamps);
  }

  @Test
  public void testGetTimestampsForIntervalsForGranularityDay()
  {
    Set<Long> timestamps = ZeroFilledTimestampedDimensionIterator.getTimestampsForIntervals(GRANULARITY_DAY, INTERVALS);
    Assert.assertEquals(3, timestamps.size());
    Assert.assertEquals(Sets.newHashSet(1588291200000L, 1588377600000L, 1589068800000L), timestamps);
  }

  @Test
  public void testZeroFilledTimestampedDimensionIteratorWithNoTimestampedRows()
  {
    ZeroFilledTimestampedDimensionIterator iterator = new ZeroFilledTimestampedDimensionIterator(
        false,
        GRANULARITY_ALL,
        INTERVALS,
        DIMENSIONS_TO_ZERO_FILL);
    Assert.assertTrue(iterator.hasNext());
    Assert.assertEquals(DIMENSIONS_TO_ZERO_FILL, iterator.next().toList());
    Assert.assertFalse(iterator.hasNext());
  }

  @Test
  public void testZeroFilledTimestampedDimensionIteratorWithTimestampedRows()
  {
    ZeroFilledTimestampedDimensionIterator iterator = new ZeroFilledTimestampedDimensionIterator(
        true,
        GRANULARITY_MONTH,
        INTERVALS,
        DIMENSIONS_TO_ZERO_FILL);
    Assert.assertTrue(iterator.hasNext());
    Assert.assertEquals(
        ImmutableList.of(
            new ImmutableList.Builder<>().add(1588291200000L).addAll(FIRST_DIM_SET).build(),
            new ImmutableList.Builder<>().add(1588291200000L).addAll(SECOND_DIM_SET).build()),
        iterator.next().toList());
    Assert.assertFalse(iterator.hasNext());
  }
}

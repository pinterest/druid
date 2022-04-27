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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.apache.druid.java.util.common.granularity.Granularity;
import org.apache.druid.java.util.common.guava.BaseSequence;
import org.apache.druid.java.util.common.guava.Sequence;
import org.joda.time.Interval;

import javax.annotation.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
*/
public class ZeroFilledTimestampedDimensionIterator implements Iterator<Sequence<ImmutableList<Object>>>
{
  private final Iterator<Long> timestamps;
  private final List<List<Object>> dimensionsToZeroFill;

  ZeroFilledTimestampedDimensionIterator(
      boolean resultRowsContainTimestamps,
      Granularity granularity,
      List<Interval> intervals,
      List<List<Object>> dimensionsToZeroFill
  )
  {
    if (resultRowsContainTimestamps) {
      this.timestamps = getTimestampsForIntervals(granularity, intervals).iterator();
    } else {
      // If we don't return timestamps in the result rows we also leave them out of the each set of dimensions to
      // zero fill. Alternatively we could add the queries universal timestamp (start time of the query) to timestamps
      // however this would mean storing an unnecessary timestamp in each set of dimmension values that potentially need
      // to be  zero filled. Rather than do this we have ZeroFilledDimensionIterator support null values for timestamp
      List<Long> ts = Collections.singletonList(null);
      this.timestamps = ts.iterator();
    }
    this.dimensionsToZeroFill = dimensionsToZeroFill;
  }

  @VisibleForTesting
  static Set<Long> getTimestampsForIntervals(Granularity granularity,
                                             List<Interval> intervals)
  {
    // Its possible to create duplicate intervals from granularity.getIterable so we add start times to a set to dedup
    Set<Long> timestamps = new HashSet<>();
    intervals.forEach(interval -> granularity.getIterable(interval).forEach(i -> timestamps.add(i.getStartMillis())));
    return timestamps;
  }

  @Override
  public boolean hasNext()
  {
    return timestamps.hasNext();
  }

  @Override
  public Sequence<ImmutableList<Object>> next()
  {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }

    @Nullable Long timestamp = timestamps.next();

    return new BaseSequence<>(
        new BaseSequence.IteratorMaker<
            ImmutableList<Object>, ZeroFilledDimensionIterator>() {
          @Override
          public ZeroFilledDimensionIterator make()
          {
            return new ZeroFilledDimensionIterator(timestamp, dimensionsToZeroFill);
          }

          @Override
          public void cleanup(
              ZeroFilledDimensionIterator iterFromMake)
          {
          }
        });
  }

  @Override
  public void remove()
  {
    throw new UnsupportedOperationException();
  }
}

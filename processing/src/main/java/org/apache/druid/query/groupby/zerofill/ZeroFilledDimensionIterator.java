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

import javax.annotation.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
*/
public class ZeroFilledDimensionIterator implements Iterator<ImmutableList<Object>>
{
  private final Long timestamp;
  private final List<List<Object>> dimensionsToZeroFill;

  private int pos;

  ZeroFilledDimensionIterator(
      // Timestamp will be null if ResultRows for the group by query will not include a timestamp
      @Nullable Long timestamp,
      List<List<Object>> dimensionsToZeroFill
  )
  {
    this.pos = 0;
    this.timestamp = timestamp;
    this.dimensionsToZeroFill = dimensionsToZeroFill;
  }

  @Override
  public boolean hasNext()
  {
    return pos < dimensionsToZeroFill.size();
  }

  @Override
  public ImmutableList<Object> next()
  {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }

    ImmutableList.Builder<Object> builder = new ImmutableList.Builder<>();

    if (timestamp != null) {
      builder.add(timestamp);
    }

    builder.addAll(dimensionsToZeroFill.get(pos));

    pos += 1;

    return builder.build();
  }

  @Override
  public void remove()
  {
    throw new UnsupportedOperationException();
  }
}

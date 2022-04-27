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
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class ZeroFilledDimensionIteratorTest
{
  private static final long TIMESTAMP = 12345L;
  private static final ImmutableList<Object> FIRST_DIM_SET = ImmutableList.of("a", 1, 1.0);
  private static final ImmutableList<Object> SECOND_DIM_SET = ImmutableList.of("b", 2, 2.0);
  private static final List<List<Object>> DIMENSIONS_TO_ZERO_FILL = ImmutableList.of(FIRST_DIM_SET, SECOND_DIM_SET);

  @Test
  public void testZeroFilledDimensionIteratorWhenTimestampIsNull()
  {
    ZeroFilledDimensionIterator zeroFilledDimensionIterator =
        new ZeroFilledDimensionIterator(null, DIMENSIONS_TO_ZERO_FILL);

    Assert.assertTrue(zeroFilledDimensionIterator.hasNext());
    Assert.assertEquals(FIRST_DIM_SET, zeroFilledDimensionIterator.next());
    Assert.assertTrue(zeroFilledDimensionIterator.hasNext());
    Assert.assertEquals(SECOND_DIM_SET, zeroFilledDimensionIterator.next());
    Assert.assertFalse(zeroFilledDimensionIterator.hasNext());
  }

  @Test
  public void testZeroFilledDimensionIteratorWhenTimestampIsNotNull()
  {
    ZeroFilledDimensionIterator zeroFilledDimensionIterator =
        new ZeroFilledDimensionIterator(TIMESTAMP, DIMENSIONS_TO_ZERO_FILL);

    Assert.assertTrue(zeroFilledDimensionIterator.hasNext());

    ImmutableList<Object> firstDimSetWithTimestamp = new ImmutableList.Builder<>().add(TIMESTAMP).addAll(FIRST_DIM_SET).build();
    Assert.assertEquals(firstDimSetWithTimestamp, zeroFilledDimensionIterator.next());

    Assert.assertTrue(zeroFilledDimensionIterator.hasNext());

    ImmutableList<Object> secondDimSetWithTimestamp = new ImmutableList.Builder<>().add(TIMESTAMP).addAll(SECOND_DIM_SET).build();
    Assert.assertEquals(secondDimSetWithTimestamp, zeroFilledDimensionIterator.next());

    Assert.assertFalse(zeroFilledDimensionIterator.hasNext());
  }
}

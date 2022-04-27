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
import com.google.common.collect.ImmutableSet;
import org.apache.druid.java.util.common.granularity.Granularities;
import org.apache.druid.java.util.common.guava.Sequence;
import org.apache.druid.java.util.common.guava.Sequences;
import org.apache.druid.query.NilColumnSelectorFactory;
import org.apache.druid.query.aggregation.AggregatorFactory;
import org.apache.druid.query.aggregation.DoubleSumAggregatorFactory;
import org.apache.druid.query.aggregation.LongSumAggregatorFactory;
import org.apache.druid.query.aggregation.PostAggregator;
import org.apache.druid.query.aggregation.post.ArithmeticPostAggregator;
import org.apache.druid.query.aggregation.post.FieldAccessPostAggregator;
import org.apache.druid.query.dimension.DefaultDimensionSpec;
import org.apache.druid.query.dimension.DimensionSpec;
import org.apache.druid.query.groupby.GroupByQuery;
import org.apache.druid.query.groupby.ResultRow;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ZeroFilledResultSetIteratorTest
{
  private static final long TIMESTAMP = 1577836800000L;
  private static final ImmutableList<Object> ROW_1_DIMENSIONS = ImmutableList.of("a", 1, 1.0);
  private static final ImmutableList<Object> ROW_2_DIMENSIONS = ImmutableList.of("b", 2, 2.0);
  private static final ImmutableList<Object> ROW_1_DIMENSIONS_WITH_TIMESTAMP =
      new ImmutableList.Builder<>().add(TIMESTAMP).addAll(ROW_1_DIMENSIONS).build();
  private static final ImmutableList<Object> ROW_2_DIMENSIONS_WITH_TIMESTAMP =
      new ImmutableList.Builder<>().add(TIMESTAMP).addAll(ROW_2_DIMENSIONS).build();
  private static final List<DimensionSpec> DIM_SPECS = ImmutableList.of(
      DefaultDimensionSpec.of("stringDim"),
      DefaultDimensionSpec.of("longDim"),
      DefaultDimensionSpec.of("doubleDim"));

  private static final ResultRow RESULT_ROW_1 = ResultRow.of(ROW_1_DIMENSIONS.toArray());
  private static final ResultRow RESULT_ROW_2 = ResultRow.of(ROW_2_DIMENSIONS.toArray());
  private static final ResultRow RESULT_ROW_WITH_TIMESTAMP_1 = ResultRow.of(ROW_1_DIMENSIONS_WITH_TIMESTAMP.toArray());
  private static final ResultRow RESULT_ROW_WITH_TIMESTAMP_2 = ResultRow.of(ROW_2_DIMENSIONS_WITH_TIMESTAMP.toArray());


  private static final List<AggregatorFactory> AGGREGATOR_FACTORIES = ImmutableList.of(
      new LongSumAggregatorFactory("longSum", "longDim"),
      new DoubleSumAggregatorFactory("doubleSum", "doubleDim"));
  private static final List<PostAggregator> POST_AGGREGATORS = ImmutableList.of(
      new ArithmeticPostAggregator("totalNumeric", "+", ImmutableList.of(
          new FieldAccessPostAggregator("longSum", "longSum"),
          new FieldAccessPostAggregator("doubleSum", "doubleSum"))));

  private static final GroupByQuery.Builder QUERY_BUILDER = GroupByQuery.builder()
      .setDataSource("dummy")
      .setInterval("2020/2021")
      .setDimensions(DIM_SPECS)
      .setAggregatorSpecs(AGGREGATOR_FACTORIES)
      .setPostAggregatorSpecs(POST_AGGREGATORS);

  @Test
  public void testGetResultRowDimensionsWhenNoTimestampPresent()
  {
    ImmutableList<Object> actual = ZeroFilledResultSetIterator.getResultRowDimensions(
        RESULT_ROW_1,
        false,
        0,
        DIM_SPECS.size());

    Assert.assertEquals(ROW_1_DIMENSIONS, actual);
  }

  @Test
  public void testGetResultRowDimensionsWhenTimestampIsPresent()
  {
    ImmutableList<Object> actual = ZeroFilledResultSetIterator.getResultRowDimensions(
        RESULT_ROW_WITH_TIMESTAMP_1,
        true,
        1,
        DIM_SPECS.size());

    Assert.assertEquals(ROW_1_DIMENSIONS_WITH_TIMESTAMP, actual);
  }

  @Test
  public void testGetReurnedDimsSet()
  {
    Set<ImmutableList<Object>> actual = ZeroFilledResultSetIterator.getReurnedDimsSet(
        Sequences.simple(ImmutableList.of(RESULT_ROW_1, RESULT_ROW_2)),
        false,
        0,
        DIM_SPECS.size());

    Assert.assertEquals(ImmutableSet.of(ROW_1_DIMENSIONS, ROW_2_DIMENSIONS), actual);
  }

  @Test
  public void testGetReurnedDimsSetWithTimestapsOnRows()
  {
    Set<ImmutableList<Object>> actual = ZeroFilledResultSetIterator.getReurnedDimsSet(
        Sequences.simple(ImmutableList.of(RESULT_ROW_WITH_TIMESTAMP_1, RESULT_ROW_WITH_TIMESTAMP_2)),
        true,
        1,
        DIM_SPECS.size());

    Assert.assertEquals(ImmutableSet.of(ROW_1_DIMENSIONS_WITH_TIMESTAMP, ROW_2_DIMENSIONS_WITH_TIMESTAMP), actual);
  }

  @Test
  public void testCreateZeroFilledResultRowWithGranularityAll()
  {
    ResultRow actual = ZeroFilledResultSetIterator.createZeroFilledResultRow(
        ROW_1_DIMENSIONS,
        QUERY_BUILDER.setGranularity(Granularities.ALL).build(),
        AGGREGATOR_FACTORIES.stream()
            .map(agg -> agg.factorize(NilColumnSelectorFactory.INSTANCE)).collect(Collectors.toList()));
    Assert.assertArrayEquals(
        // Verifies that result rows are zero filled
        new ImmutableList.Builder<>().addAll(ROW_1_DIMENSIONS).add(0L).add(0.0).add(0.0).build().toArray(new Object[0]),
        actual.getArray());
  }

  @Test
  public void testCreateZeroFilledResultRowWithTimestamps()
  {
    ResultRow actual = ZeroFilledResultSetIterator.createZeroFilledResultRow(
        ROW_1_DIMENSIONS_WITH_TIMESTAMP,
        QUERY_BUILDER.setGranularity(Granularities.YEAR).build(),
        AGGREGATOR_FACTORIES.stream()
            .map(agg -> agg.factorize(NilColumnSelectorFactory.INSTANCE)).collect(Collectors.toList()));
    Assert.assertArrayEquals(
        // Verifies that result rows are zero filled
        new ImmutableList.Builder<>().addAll(ROW_1_DIMENSIONS_WITH_TIMESTAMP).add(0L).add(0.0).add(0.0).build().toArray(new Object[0]),
        actual.getArray());
  }

  @Test
  public void testAddTimestampsToDimensionsToZerofillIfNecessaryWhenNotNecessary()
  {
    Sequence<ImmutableList<Object>> actual = ZeroFilledResultSetIterator.addTimestampsToDimensionsToZerofillIfNecessary(
        ImmutableList.of(ROW_1_DIMENSIONS, ROW_2_DIMENSIONS),
        false,
        Granularities.ALL,
        QUERY_BUILDER.setGranularity(Granularities.ALL).build().getIntervals());
    Assert.assertEquals(
        ImmutableList.of(ROW_1_DIMENSIONS, ROW_2_DIMENSIONS),
        actual.toList());
  }

  @Test
  public void testAddTimestampsToDimensionsToZerofillIfNecessaryWhenNecessary()
  {
    Sequence<ImmutableList<Object>> actual = ZeroFilledResultSetIterator.addTimestampsToDimensionsToZerofillIfNecessary(
        ImmutableList.of(ROW_1_DIMENSIONS, ROW_2_DIMENSIONS),
        true,
        Granularities.YEAR,
        QUERY_BUILDER.setGranularity(Granularities.YEAR).build().getIntervals());
    Assert.assertEquals(
        ImmutableList.of(ROW_1_DIMENSIONS_WITH_TIMESTAMP, ROW_2_DIMENSIONS_WITH_TIMESTAMP),
        actual.toList());
  }

  @Test
  public void testGenerateZeroFilledResultsWhenAllResultsPresent()
  {
    Sequence<ResultRow> actual = ZeroFilledResultSetIterator.generateZeroFilledResults(
        QUERY_BUILDER.setGranularity(Granularities.ALL).build(),
        ImmutableList.of(ROW_1_DIMENSIONS, ROW_2_DIMENSIONS),
        Sequences.simple(ImmutableList.of(ResultRow.of(ROW_1_DIMENSIONS.toArray()), ResultRow.of(ROW_2_DIMENSIONS.toArray()))));
    Assert.assertEquals(Collections.emptyList(), actual.toList());
  }

  @Test
  public void testGenerateZeroFilledResults()
  {
    Sequence<ResultRow> actual = ZeroFilledResultSetIterator.generateZeroFilledResults(
        QUERY_BUILDER.setGranularity(Granularities.ALL).build(),
        ImmutableList.of(ROW_1_DIMENSIONS, ROW_2_DIMENSIONS),
        Sequences.simple(ImmutableList.of(ResultRow.of(ROW_2_DIMENSIONS.toArray()))));
    Assert.assertEquals(
        ImmutableList.of(ResultRow.of(
            new ImmutableList.Builder<>().addAll(ROW_1_DIMENSIONS).add(0L).add(0.0).add(0.0).build().toArray())),
        actual.toList());
  }

  @Test
  public void testZeroFilledResultSetIterator()
  {
    ResultRow resultRow2 = ResultRow.of(
        new ImmutableList.Builder<>().addAll(ROW_2_DIMENSIONS).add(2L).add(2.0).add(4.0).build().toArray());
    ZeroFilledResultSetIterator iterator = new ZeroFilledResultSetIterator(
        Sequences.simple(ImmutableList.of(resultRow2)),
        ImmutableList.of(ROW_1_DIMENSIONS, ROW_2_DIMENSIONS),
        QUERY_BUILDER.setGranularity(Granularities.ALL).build());

    Assert.assertTrue(iterator.hasNext());
    Assert.assertEquals(ImmutableList.of(resultRow2), iterator.next().toList());
    Assert.assertTrue(iterator.hasNext());
    Assert.assertEquals(
        ImmutableList.of(ResultRow.of(
            new ImmutableList.Builder<>().addAll(ROW_1_DIMENSIONS).add(0L).add(0.0).add(0.0).build().toArray())),
        iterator.next().toList());
    Assert.assertFalse(iterator.hasNext());
  }
}

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
import com.google.common.collect.ImmutableSet;
import org.apache.druid.java.util.common.granularity.Granularity;
import org.apache.druid.java.util.common.guava.BaseSequence;
import org.apache.druid.java.util.common.guava.Sequence;
import org.apache.druid.java.util.common.guava.Sequences;
import org.apache.druid.query.NilColumnSelectorFactory;
import org.apache.druid.query.aggregation.Aggregator;
import org.apache.druid.query.dimension.DimensionSpec;
import org.apache.druid.query.groupby.GroupByQuery;
import org.apache.druid.query.groupby.ResultRow;
import org.joda.time.Interval;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/**
*/
public class ZeroFilledResultSetIterator implements Iterator<Sequence<ResultRow>>
{
  private boolean resultsAreAdded;
  private boolean zeroFilledDimensionsAreAdded;
  private final Sequence<ResultRow> results;
  private final List<List<Object>> dimensionsToZeroFillIfNecessary;
  private final GroupByQuery query;

  ZeroFilledResultSetIterator(
      Sequence<ResultRow> results,
      List<List<Object>> dimensionValuesToZeroFillIfNecessary,
      GroupByQuery query
  )
  {
    this.results = results;
    this.dimensionsToZeroFillIfNecessary = dimensionValuesToZeroFillIfNecessary;
    this.query = query;

    this.resultsAreAdded = false;
    this.zeroFilledDimensionsAreAdded = false;
  }

  @Override
  public boolean hasNext()
  {
    return !resultsAreAdded || !zeroFilledDimensionsAreAdded;
  }

  @Override
  public Sequence<ResultRow> next()
  {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }

    if (!resultsAreAdded) {
      resultsAreAdded = true;
      return results;
    }
    zeroFilledDimensionsAreAdded = true;
    return generateZeroFilledResults(query, dimensionsToZeroFillIfNecessary, results);
  }

  @Override
  public void remove()
  {
    throw new UnsupportedOperationException();
  }

  @VisibleForTesting
  static Sequence<ResultRow> generateZeroFilledResults(GroupByQuery query,
                                                       List<List<Object>> dimensionsToZeroFillIfNecessary,
                                                       Sequence<ResultRow> results)
  {
    List<Aggregator> nillAggregators = query.getAggregatorSpecs().stream()
        .map(agg -> agg.factorize(NilColumnSelectorFactory.INSTANCE)).collect(Collectors.toList());

    Set<ImmutableList<Object>> returnedDimsSet = getReurnedDimsSet(
        results,
        query.getResultRowHasTimestamp(),
        query.getResultRowDimensionStart(),
        query.getDimensions().size());

    Sequence<ImmutableList<Object>> dimsRequiringZeroFill = Sequences.filter(
        addTimestampsToDimensionsToZerofillIfNecessary(
            dimensionsToZeroFillIfNecessary,
            query.getResultRowHasTimestamp(),
            query.getGranularity(),
            query.getIntervals()),
        dimensions -> !returnedDimsSet.contains(dimensions));

    return dimsRequiringZeroFill.map(dimensions -> createZeroFilledResultRow(dimensions, query, nillAggregators));
  }

  @VisibleForTesting
  static Sequence<ImmutableList<Object>> addTimestampsToDimensionsToZerofillIfNecessary(
      List<List<Object>> dimensionValuesToZeroFillIfNecessary,
      boolean resultRowsContainTimestamps,
      Granularity granularity,
      List<Interval> intervals)
  {
    Sequence<Sequence<ImmutableList<Object>>> nestedDims = new BaseSequence<>(
        new BaseSequence.IteratorMaker<
            Sequence<ImmutableList<Object>>, ZeroFilledTimestampedDimensionIterator>() {
          @Override
          public ZeroFilledTimestampedDimensionIterator make()
          {
            return new ZeroFilledTimestampedDimensionIterator(
                resultRowsContainTimestamps,
                granularity,
                intervals,
                dimensionValuesToZeroFillIfNecessary);
          }

          @Override
          public void cleanup(
              ZeroFilledTimestampedDimensionIterator iterFromMake)
          {
          }
        });
    return Sequences.concat(nestedDims);
  }

  @VisibleForTesting
  static ResultRow createZeroFilledResultRow(ImmutableList<Object> dimensionsToZeroFill,
                                             GroupByQuery query,
                                             List<Aggregator> nilAggregators)
  {
    ResultRow resultRow = ResultRow.create(query.getResultRowSizeWithPostAggregators());

    if (query.getResultRowHasTimestamp()) {
      // if result row has a timestamp it should always be in the first position. In this case dimensionsToZeroFill
      // will have timestamp set in position 0 and dimensionStart will be set to 1
      resultRow.set(0, dimensionsToZeroFill.get(0));
    }

    final int dimensionStart = query.getResultRowDimensionStart();
    final List<DimensionSpec> dimensions = query.getDimensions();
    for (int i = 0; i < dimensions.size(); i++) {
      final int rowIndex = dimensionStart + i;
      resultRow.set(rowIndex, dimensionsToZeroFill.get(rowIndex));
    }

    final int aggregatorStart = query.getResultRowAggregatorStart();
    for (int i = 0; i < nilAggregators.size(); i++) {
      final int rowIndex = aggregatorStart + i;
      resultRow.set(rowIndex, nilAggregators.get(i).get());
    }

    Map<String, Object> resultRowMap = resultRow.toMap(query);
    final int postAggregatorStart = query.getResultRowPostAggregatorStart();
    for (int i = 0; i < query.getPostAggregatorSpecs().size(); i++) {
      final int rowIndex = postAggregatorStart + i;
      resultRow.set(rowIndex, query.getPostAggregatorSpecs().get(i).compute(resultRowMap));
    }

    return resultRow;
  }

  @VisibleForTesting
  static Set<ImmutableList<Object>> getReurnedDimsSet(Sequence<ResultRow> resultRows,
                                                      boolean resultRowHasTimestamp,
                                                      int resultRowDimensionsStart,
                                                      int dimensionsSize)
  {
    return new ImmutableSet.Builder<ImmutableList<Object>>()
        .addAll(resultRows.map(resultRow ->
            getResultRowDimensions(
                resultRow,
                resultRowHasTimestamp,
                resultRowDimensionsStart,
                dimensionsSize)).toList()).build();
  }

  @VisibleForTesting
  static ImmutableList<Object> getResultRowDimensions(ResultRow resultRow,
                                                      boolean resultRowHasTimestamp,
                                                      int resultRowDimensionsStart,
                                                      int dimensionsSize)
  {
    ImmutableList.Builder<Object> builder = new ImmutableList.Builder<>();

    if (resultRowHasTimestamp) {
      // Timestamp will always be in the first position of the result row if it exists
      builder.add(resultRow.get(0));
    }
    for (int i = 0; i < dimensionsSize; i++) {
      final int rowIndex = resultRowDimensionsStart + i;
      builder.add(resultRow.get(rowIndex));
    }
    return builder.build();
  }
}

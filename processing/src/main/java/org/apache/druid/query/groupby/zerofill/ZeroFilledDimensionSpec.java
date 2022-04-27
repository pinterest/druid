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

import org.apache.druid.java.util.common.IAE;
import org.apache.druid.java.util.common.guava.BaseSequence;
import org.apache.druid.java.util.common.guava.Sequence;
import org.apache.druid.query.dimension.DimensionSpec;
import org.apache.druid.query.groupby.GroupByQuery;
import org.apache.druid.query.groupby.ResultRow;

import javax.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ZeroFilledDimensionSpec
{
  public static final String ZERO_FILLED_DIMS_CONTEXT_KEY = "zeroFilledDimValues";

  private final List<List<Object>> dimensionValuesToZeroFillIfNecessary;

  public ZeroFilledDimensionSpec(
      Map<String, Object> context,
      @Nullable List<DimensionSpec> dimensions
  )
  {

    if (dimensions == null) {
      throw new IAE("dimensions cannot be null for zero filled GroupBy queries");
    }

    List<List<Object>> zeroFilledDimensions;

    try {
      zeroFilledDimensions = (List<List<Object>>) context.get(ZERO_FILLED_DIMS_CONTEXT_KEY);
    }
    catch (ClassCastException e) {
      throw new IAE("%s must be formatted as a list of lists of dimension values", ZERO_FILLED_DIMS_CONTEXT_KEY);
    }

    if (zeroFilledDimensions == null || zeroFilledDimensions.isEmpty()) {
      throw new IAE(
          "%s must be set on context for zero filled GroupBy queries", ZERO_FILLED_DIMS_CONTEXT_KEY);
    }

    zeroFilledDimensions.forEach(d -> {
      if (d.size() != dimensions.size()) {
        throw new IAE(
            "%s must match provided dimensions %s",
            ZERO_FILLED_DIMS_CONTEXT_KEY,
            dimensions.stream().map(DimensionSpec::getDimension).collect(Collectors.joining(", ")));
      }
    });

    // Note that we are assuming that each list of dimensions in zeroFilledDimValues are specified in the same order as
    // the dimensions in the query
    dimensionValuesToZeroFillIfNecessary = zeroFilledDimensions;
  }

  public Sequence<ResultRow> zeroFill(GroupByQuery query, Sequence<ResultRow> results)
  {
    return new BaseSequence<>(
        new BaseSequence.IteratorMaker<
            Sequence<ResultRow>, ZeroFilledResultSetIterator>()
        {
          @Override
          public ZeroFilledResultSetIterator make()
          {
            return new ZeroFilledResultSetIterator(
                results,
                dimensionValuesToZeroFillIfNecessary,
                query);
          }

          @Override
          public void cleanup(
              ZeroFilledResultSetIterator iterFromMake)
          {
          }
        }).flatMerge(Function.identity(), query.getResultOrdering());
  }
}

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
import com.google.common.collect.ImmutableMap;
import org.apache.druid.java.util.common.IAE;
import org.apache.druid.java.util.common.granularity.Granularities;
import org.apache.druid.java.util.common.guava.Sequences;
import org.apache.druid.query.dimension.DefaultDimensionSpec;
import org.apache.druid.query.dimension.DimensionSpec;
import org.apache.druid.query.groupby.GroupByQuery;
import org.apache.druid.query.groupby.ResultRow;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ZeroFilledDimensionSpecTest
{
  private static List<List<Object>> ZERO_FILLED_DIM_VALUES;
  private static Map<String, Object> CONTEXT;
  private static List<DimensionSpec> DIM_SPECS;
  private static GroupByQuery QUERY;
  private static Object[] ROW;
  private static List<ResultRow> RESULTS;

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setup()
  {
    ZERO_FILLED_DIM_VALUES = ImmutableList.of(
        ImmutableList.of("a", 1, 1.0), ImmutableList.of("b", 2, 2.0));
    CONTEXT = ImmutableMap.of(ZeroFilledDimensionSpec.ZERO_FILLED_DIMS_CONTEXT_KEY, ZERO_FILLED_DIM_VALUES);
    DIM_SPECS = ImmutableList.of(
        DefaultDimensionSpec.of("stringDim"),
        DefaultDimensionSpec.of("longDim"),
        DefaultDimensionSpec.of("doubleDim"));

    QUERY = GroupByQuery.builder()
        .setDataSource("dummy")
        .setGranularity(Granularities.ALL)
        .setInterval("2000/2001")
        .setDimensions(DIM_SPECS)
        .setContext(CONTEXT)
        .build();
    ROW = new Object[DIM_SPECS.size()];
    ROW[0] = "a";
    ROW[1] = 1;
    ROW[2] = 1.0;
    RESULTS = ImmutableList.of(ResultRow.of(ROW));
  }

  @Test
  public void testConstructionForValidZeroFillDimensions()
  {
    new ZeroFilledDimensionSpec(CONTEXT, DIM_SPECS);
  }

  @Test(expected = IAE.class)
  public void testConstructionWhenZeroFillDimensionsNotPresent()
  {
    new ZeroFilledDimensionSpec(new HashMap<>(), DIM_SPECS);
  }

  @Test(expected = IAE.class)
  public void testConstructionWhenZeroFillDimensionsIncorrectType()
  {
    new ZeroFilledDimensionSpec(
        ImmutableMap.of(
            ZeroFilledDimensionSpec.ZERO_FILLED_DIMS_CONTEXT_KEY,
            ZERO_FILLED_DIM_VALUES
                .stream().collect(Collectors.toMap(d -> d.get(0), d -> d))),
        DIM_SPECS);
  }

  @Test(expected = IAE.class)
  public void testConstructionWhenZeroFillDimensionsSizeNotCorrect()
  {
    new ZeroFilledDimensionSpec(
        ImmutableMap.of(
            ZeroFilledDimensionSpec.ZERO_FILLED_DIMS_CONTEXT_KEY,
        ZERO_FILLED_DIM_VALUES.stream().map(l -> l.subList(0, 1)).collect(Collectors.toList())), DIM_SPECS);
  }

  @Test
  public void testZeroFill()
  {
    Object[] zeroFilledRow = new Object[DIM_SPECS.size()];
    zeroFilledRow[0] = "b";
    zeroFilledRow[1] = 2;
    zeroFilledRow[2] = 2.0;
    List<ResultRow> expected = ImmutableList.of(ResultRow.of(ROW), ResultRow.of(zeroFilledRow));
    ZeroFilledDimensionSpec zeroFilledDimensionSpec = new ZeroFilledDimensionSpec(CONTEXT, DIM_SPECS);

    Assert.assertEquals(
        expected,
        zeroFilledDimensionSpec.zeroFill(QUERY, Sequences.simple(RESULTS)).toList());
  }
}

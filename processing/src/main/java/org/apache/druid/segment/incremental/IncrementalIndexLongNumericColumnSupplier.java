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

package org.apache.druid.segment.incremental;

import com.google.common.base.Supplier;
import org.apache.druid.query.monomorphicprocessing.RuntimeShapeInspector;
import org.apache.druid.segment.ColumnValueSelector;
import org.apache.druid.segment.column.NumericColumn;
import org.apache.druid.segment.data.ReadableOffset;

/**
 */
public class IncrementalIndexLongNumericColumnSupplier implements Supplier<NumericColumn>
{
  private final Supplier<IncrementalIndex.FactsHolder> factsHolderSupplier;

  public IncrementalIndexLongNumericColumnSupplier(Supplier<IncrementalIndex.FactsHolder> factsHolderSupplier)
  {
    this.factsHolderSupplier = factsHolderSupplier;
  }

  @Override
  public NumericColumn get()
  {
    return new NumericColumn(
    )
    {
      @Override
      public int length()
      {
        return factsHolderSupplier.get().getNumRows();
      }

      @Override
      public long getLongSingleValueRow(int rowNum)
      {
        throw new UnsupportedOperationException();
      }

      @Override
      public void close()
      {
      }

      @Override
      public ColumnValueSelector<?> makeColumnValueSelector(ReadableOffset offset)
      {
        throw new UnsupportedOperationException();
      }

      @Override
      public void inspectRuntimeShape(RuntimeShapeInspector inspector)
      {
      }
    };
  }
}

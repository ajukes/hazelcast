/*
 * Copyright (c) 2008-2020, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.sql.impl.exec.scan.index;

import com.hazelcast.config.IndexConfig;
import com.hazelcast.config.IndexType;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.query.impl.InternalIndex;
import com.hazelcast.sql.impl.expression.ExpressionEvalContext;
import com.hazelcast.sql.impl.expression.SimpleExpressionEvalContext;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class IndexInFilterIterationTest extends IndexFilterIteratorTestSupport {
    @Test
    public void test_sorted() {
        check(IndexType.SORTED);
    }

    @Test
    public void test_hash() {
        check(IndexType.HASH);
    }

    private void check(IndexType indexType) {
        HazelcastInstance instance = factory.newHazelcastInstance(getConfig());

        IMap<Integer, Value> map = instance.getMap(MAP_NAME);
        map.addIndex(new IndexConfig().setName(INDEX_NAME).setType(indexType).addAttribute("value1"));

        InternalIndex index = getIndex(instance);

        ExpressionEvalContext evalContext = SimpleExpressionEvalContext.create();

        map.put(1, new Value(null));
        map.put(2, new Value(0));
        map.put(3, new Value(1));

        // No values from both filters
        checkIterator(in(equals(2), equals(3)).getEntries(index, evalContext));
        checkIterator(in(equals(3), equals(2)).getEntries(index, evalContext));

        // No values from one filter
        checkIterator(in(equals(1), equals(2)).getEntries(index, evalContext), 3);
        checkIterator(in(equals(2), equals(1)).getEntries(index, evalContext), 3);

        checkIterator(in(equals(null, true), equals(2)).getEntries(index, evalContext), 1);
        checkIterator(in(equals(2), equals(null, true)).getEntries(index, evalContext), 1);

        // Values from both filters
        checkIterator(in(equals(0), equals(1)).getEntries(index, evalContext), 2, 3);
        checkIterator(in(equals(1), equals(0)).getEntries(index, evalContext), 2, 3);

        checkIterator(in(equals(null, true), equals(0)).getEntries(index, evalContext), 1, 2);
        checkIterator(in(equals(0), equals(null, true)).getEntries(index, evalContext), 1, 2);

        // One distinct value
        checkIterator(in(equals(0), equals(0)).getEntries(index, evalContext), 2);
        checkIterator(in(equals(null, true), equals(null, true)).getEntries(index, evalContext), 1);

        // One null value
        checkIterator(in(equals(0), equals(null, false)).getEntries(index, evalContext), 2);
        checkIterator(in(equals(null, false), equals(0)).getEntries(index, evalContext), 2);

        checkIterator(in(equals(null, false), equals(null, true)).getEntries(index, evalContext), 1);
        checkIterator(in(equals(null, true), equals(null, false)).getEntries(index, evalContext), 1);

        // Two null values
        checkIterator(in(equals(null, false), equals(null, false)).getEntries(index, evalContext));
    }

    private static IndexInFilter in(IndexEqualsFilter... filters) {
        assert filters != null;

        return new IndexInFilter(Arrays.asList(filters));
    }

    private static IndexEqualsFilter equals(Integer value) {
        return equals(value, false);
    }

    private static IndexEqualsFilter equals(Integer value, boolean allowNulls) {
        return new IndexEqualsFilter(intValue(value, allowNulls));
    }
}

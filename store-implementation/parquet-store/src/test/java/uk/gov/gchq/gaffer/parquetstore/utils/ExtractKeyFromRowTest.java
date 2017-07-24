/*
 * Copyright 2017. Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.gchq.gaffer.parquetstore.utils;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.Row$;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import scala.collection.Seq;
import uk.gov.gchq.gaffer.parquetstore.testutils.DataGen;
import uk.gov.gchq.gaffer.parquetstore.testutils.TestUtils;
import uk.gov.gchq.gaffer.store.SerialisationFactory;
import uk.gov.gchq.gaffer.store.StoreException;
import uk.gov.gchq.gaffer.store.schema.Schema;
import uk.gov.gchq.gaffer.store.schema.SchemaElementDefinition;
import uk.gov.gchq.gaffer.store.schema.SchemaOptimiser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.BinaryOperator;

import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class ExtractKeyFromRowTest {
    private LinkedHashSet<String> groupByColumns;
    private HashMap<String, String[]> columnsToPaths;
    private SchemaUtils utils;

    @Before
    public void setUp() throws StoreException {
        Logger.getRootLogger().setLevel(Level.WARN);
        groupByColumns = new LinkedHashSet<>();
        groupByColumns.add("double");
        groupByColumns.add("date");
        columnsToPaths = new HashMap<>();
        String[] prop2Paths = new String[1];
        prop2Paths[0] = "double";
        String[] prop7Paths = new String[1];
        prop7Paths[0] = "date";
        String[] vertPaths = new String[1];
        vertPaths[0] = ParquetStoreConstants.VERTEX;
        String[] srcPaths = new String[1];
        srcPaths[0] = ParquetStoreConstants.SOURCE;
        String[] dstPaths = new String[1];
        dstPaths[0] = ParquetStoreConstants.DESTINATION;
        columnsToPaths.put("double", prop2Paths);
        columnsToPaths.put("date", prop7Paths);
        columnsToPaths.put(ParquetStoreConstants.VERTEX, vertPaths);
        columnsToPaths.put(ParquetStoreConstants.SOURCE, srcPaths);
        columnsToPaths.put(ParquetStoreConstants.DESTINATION, dstPaths);
        final Schema schema = Schema.fromJson(getClass().getResourceAsStream("/schemaUsingStringVertexType/dataSchema.json"),
                getClass().getResourceAsStream("/schemaUsingStringVertexType/dataTypes.json"),
                getClass().getResourceAsStream("/schemaUsingStringVertexType/storeSchema.json"),
                getClass().getResourceAsStream("/schemaUsingStringVertexType/storeTypes.json"));
        final SchemaOptimiser optimiser = new SchemaOptimiser(new SerialisationFactory(ParquetStoreConstants.SERIALISERS));
        utils = new SchemaUtils(optimiser.optimise(schema, true));
    }

    @After
    public void cleanUp() {
        groupByColumns = null;
    }

    private HashMap<String, String> buildcolumnToAggregatorMap(final SchemaElementDefinition gafferSchema) {
        HashMap<String, String> columnToAggregatorMap = new HashMap<>();
        for (final String column : gafferSchema.getProperties()) {
            final BinaryOperator aggregateFunction = gafferSchema.getPropertyTypeDef(column).getAggregateFunction();
            if (aggregateFunction != null) {
                columnToAggregatorMap.put(column, aggregateFunction.getClass().getCanonicalName());
            }
        }
        return columnToAggregatorMap;
    }

    @Test
    public void testExtractKeyFromRowForEntity() throws Exception {
        final ExtractKeyFromRow entityConverter = new ExtractKeyFromRow(groupByColumns, columnsToPaths, true, buildcolumnToAggregatorMap(utils.getGafferSchema().getElement("BasicEntity")));
        final Row row = DataGen.generateEntityRow(utils, "BasicEntity","vertex", (byte) 'a', 0.2, 3f, TestUtils.TREESET1, 5L, (short) 6, TestUtils.DATE, TestUtils.FREQMAP1);
        final Seq<Object> results = entityConverter.call(row);
        final List<Object> actual = new ArrayList<>(3);
        for (int i = 0; i < results.length(); i++) {
            actual.add(results.apply(i));
        }
        final List<Object> expected = new ArrayList<>(3);
        expected.add(0.2);
        expected.add("vertex");
        expected.add(TestUtils.DATE.getTime());
        assertThat(expected, containsInAnyOrder(actual.toArray()));
    }

    @Test
    public void testExtractKeyFromRowForEdge() throws Exception {
        final ExtractKeyFromRow edgeConverter = new ExtractKeyFromRow(groupByColumns, columnsToPaths, false, buildcolumnToAggregatorMap(utils.getGafferSchema().getElement("BasicEdge")));
        final Row row = DataGen.generateEdgeRow(utils, "BasicEdge","src", "dst", true, (byte) 'a', 0.2, 3f, TestUtils.TREESET1, 5L, (short) 6, TestUtils.DATE, TestUtils.FREQMAP1);
        final Seq<Object> results = edgeConverter.call(row);
        final List<Object> actual = new ArrayList<>(5);
        for (int i = 0; i < results.length(); i++) {
            actual.add(results.apply(i));
        }
        final List<Object> expected = new ArrayList<>(5);
        expected.add(0.2);
        expected.add("dst");
        expected.add("src");
        expected.add(true);
        expected.add(TestUtils.DATE.getTime());
        assertThat(expected, containsInAnyOrder(actual.toArray()));
    }

    @Test
    public void testExtractKeyFromEmptyRow() {
        final ExtractKeyFromRow edgeConverter = new ExtractKeyFromRow(groupByColumns, columnsToPaths, false, buildcolumnToAggregatorMap(utils.getGafferSchema().getElement("BasicEntity")));
        try {
            edgeConverter.call(Row$.MODULE$.empty());
            fail();
        } catch (Exception ignored) {
        }
    }
}

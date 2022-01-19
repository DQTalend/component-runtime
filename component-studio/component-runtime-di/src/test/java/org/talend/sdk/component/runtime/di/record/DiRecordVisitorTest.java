/**
 * Copyright (C) 2006-2021 Talend Inc. - www.talend.com
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
package org.talend.sdk.component.runtime.di.record;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.talend.sdk.component.runtime.di.schema.StudioRecordProperties.STUDIO_TYPE;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.record.Schema.Type;
import org.talend.sdk.component.runtime.di.schema.StudioTypes;

class DiRecordVisitorTest extends VisitorsTest {

    @Test
    void visit() {
        final Record record = factory
                .newRecordBuilder()
                .withString("id", ":testing:")
                .withString("name", NAME)
                .withInt(factory
                        .newEntryBuilder()
                        .withName("shortP")
                        .withType(Type.INT)
                        .withProp(STUDIO_TYPE, StudioTypes.SHORT)
                        .build(), SHORT)
                .withInt(factory
                        .newEntryBuilder()
                        .withName("shortC")
                        .withType(Type.INT)
                        .withProp(STUDIO_TYPE, StudioTypes.SHORT)
                        .build(), SHORT)
                .withInt("intP", INT)
                .withInt("intC", INT)
                .withLong("longP", LONG)
                .withLong("longC", LONG)
                .withFloat("floatP", FLOAT)
                .withFloat("floatC", FLOAT)
                .withDouble("doubleP", DOUBLE)
                .withDouble("doubleC", DOUBLE)
                .withBytes("bytes0", BYTES0)
                .withString("bytes1", new String(BYTES1))
                .withDateTime("date0", DATE)
                .withString("date1", ZONED_DATE_TIME.toString())
                .withDateTime("date2", ZONED_DATE_TIME)
                .withLong("date3", ZONED_DATE_TIME.toInstant().toEpochMilli())
                .withString(factory
                        .newEntryBuilder()
                        .withName("bigDecimal0")
                        .withType(Type.STRING)
                        .withProp(STUDIO_TYPE, StudioTypes.BIGDECIMAL)
                        .build(), BIGDEC.toString())
                .withBoolean("bool1", true)
                .withString("dynString", "stringy")
                .withInt("dynInteger", INT)
                .withDouble("dynDouble", DOUBLE)
                .withBytes("dynBytes", BYTES0)
                .withBytes("dynBytesArray", BYTES0)
                .withBytes("dynBytesBuffer", ByteBuffer.allocate(100).wrap(BYTES0).array())
                .withBytes("dynBytesWString", String.valueOf(BYTES0).getBytes())
                .withString(factory
                        .newEntryBuilder()
                        .withName("dynBigDecimal")
                        .withType(Type.STRING)
                        .withProp(STUDIO_TYPE, StudioTypes.BIGDECIMAL)
                        .build(), BIGDEC.toString())
                .withInt(factory
                        .newEntryBuilder()
                        .withName("dynShort")
                        .withType(Type.INT)
                        .withProp(STUDIO_TYPE, StudioTypes.SHORT)
                        .build(), SHORT)
                .withString(factory
                        .newEntryBuilder()
                        .withName("dynChar")
                        .withType(Type.STRING)
                        .withProp(STUDIO_TYPE, StudioTypes.CHARACTER)
                        .build(), String.valueOf(Character.MAX_VALUE))

                .withRecord("object0", RECORD)
                .withRecord("RECORD", RECORD)
                .withArray(factory
                        .newEntryBuilder()
                        .withName("array0")
                        .withType(Type.ARRAY)
                        .withElementSchema(factory.newSchemaBuilder(Type.INT).build())
                        .build(), INTEGERS)
                .withArray(factory
                        .newEntryBuilder()
                        .withName("STRINGS")
                        .withType(Type.ARRAY)
                        .withElementSchema(factory.newSchemaBuilder(Type.STRING).build())
                        .build(), STRINGS)
                .withArray(factory
                        .newEntryBuilder()
                        .withName("LONGS")
                        .withType(Type.ARRAY)
                        .withElementSchema(factory.newSchemaBuilder(Type.LONG).build())
                        .build(), LONGS)
                .withArray(factory
                        .newEntryBuilder()
                        .withName("FLOATS")
                        .withType(Type.ARRAY)
                        .withElementSchema(factory.newSchemaBuilder(Type.FLOAT).build())
                        .build(), FLOATS)
                .withArray(factory
                        .newEntryBuilder()
                        .withName("DOUBLES")
                        .withType(Type.ARRAY)
                        .withElementSchema(factory.newSchemaBuilder(Type.DOUBLE).build())
                        .build(), DOUBLES)
                .withArray(factory
                        .newEntryBuilder()
                        .withName("BOOLEANS")
                        .withType(Type.ARRAY)
                        .withElementSchema(factory.newSchemaBuilder(Type.BOOLEAN).build())
                        .build(), BOOLEANS)
                .withArray(factory
                        .newEntryBuilder()
                        .withName("BYTES")
                        .withType(Type.ARRAY)
                        .withElementSchema(factory.newSchemaBuilder(Type.BYTES).build())
                        .build(), BYTES)
                .withArray(factory
                        .newEntryBuilder()
                        .withName("DATES")
                        .withType(Type.ARRAY)
                        .withElementSchema(factory.newSchemaBuilder(Type.DATETIME).build())
                        .build(), DATES)
                .withArray(factory
                        .newEntryBuilder()
                        .withName("RECORDS")
                        .withType(Type.ARRAY)
                        .withElementSchema(factory.newSchemaBuilder(Type.RECORD).build())
                        .build(), RECORDS)
                .withArray(factory
                        .newEntryBuilder()
                        .withName("BIG_DECIMALS")
                        .withType(Type.ARRAY)
                        .withElementSchema(factory.newSchemaBuilder(Type.STRING).build())
                        .build(), BIG_DECIMALS)
                //
                .build();
        //
        final DiRecordVisitor visitor = new DiRecordVisitor(RowStruct.class, Collections.emptyMap());
        final RowStruct rowStruct = RowStruct.class.cast(visitor.visit(record));
        assertNotNull(rowStruct);
        // asserts rowStruct::members
        assertEquals(":testing:", rowStruct.id);
        assertEquals(NAME, rowStruct.name);
        assertEquals(SHORT, rowStruct.shortP);
        assertEquals(SHORT, rowStruct.shortC);
        assertEquals(INT, rowStruct.intP);
        assertEquals(INT, rowStruct.intC);
        assertEquals(LONG, rowStruct.longP);
        assertEquals(LONG, rowStruct.longC);
        assertEquals(FLOAT, rowStruct.floatP);
        assertEquals(FLOAT, rowStruct.floatC);
        assertEquals(DOUBLE, rowStruct.doubleP);
        assertEquals(DOUBLE, rowStruct.doubleC);
        assertEquals(DATE.toInstant(), rowStruct.date0.toInstant());
        assertEquals(ZONED_DATE_TIME.toInstant(), rowStruct.date1.toInstant());
        assertEquals(ZONED_DATE_TIME.toInstant(), rowStruct.date2.toInstant());
        assertEquals(ZONED_DATE_TIME.toInstant(), rowStruct.date3.toInstant());
        assertEquals(BIGDEC.doubleValue(), rowStruct.bigDecimal0.doubleValue());
        assertEquals(BIGDEC, rowStruct.bigDecimal0);
        assertFalse(rowStruct.bool0);
        assertTrue(rowStruct.bool1);
        assertArrayEquals(BYTES0, rowStruct.bytes0);
        assertArrayEquals(BYTES1, rowStruct.bytes1);
        assertEquals(RECORD, rowStruct.object0);
        // asserts rowStruct::dynamic
        assertNotNull(rowStruct.dynamic);
        assertNotNull(rowStruct.dynamic.metadatas);
        Object dynObject = rowStruct.dynamic.getColumnValue("dynBytes");
        assertTrue(byte[].class.isInstance(dynObject));
        assertEquals(BYTES0, dynObject);
        assertArrayEquals(BYTES0, (byte[]) dynObject);
        dynObject = rowStruct.dynamic.getColumnValue("dynBytesArray");
        assertTrue(byte[].class.isInstance(dynObject));
        assertEquals(BYTES0, dynObject);
        assertArrayEquals(BYTES0, (byte[]) dynObject);
        dynObject = rowStruct.dynamic.getColumnValue("dynBytesBuffer");
        assertTrue(byte[].class.isInstance(dynObject));
        assertEquals(BYTES0, dynObject);
        assertArrayEquals(BYTES0, (byte[]) dynObject);
        dynObject = rowStruct.dynamic.getColumnValue("dynBytesWString");
        assertTrue(byte[].class.isInstance(dynObject));
        assertArrayEquals(String.valueOf(BYTES0).getBytes(), (byte[]) dynObject);
        dynObject = rowStruct.dynamic.getColumnValue("dynBigDecimal");
        assertTrue(BigDecimal.class.isInstance(dynObject));
        assertEquals(BIGDEC, dynObject);
        dynObject = rowStruct.dynamic.getColumnValue("dynShort");
        assertTrue(Short.class.isInstance(dynObject));
        assertEquals(SHORT, dynObject);
        dynObject = rowStruct.dynamic.getColumnValue("dynChar");
        assertTrue(Character.class.isInstance(dynObject));
        assertEquals(Character.MAX_VALUE, dynObject);
        //
        assertEquals(INTEGERS, rowStruct.array0);
        assertEquals(RECORD, rowStruct.dynamic.getColumnValue("RECORD"));
        assertEquals("one", ((Record) rowStruct.dynamic.getColumnValue("RECORD")).getString("str"));
        assertEquals(1, ((Record) rowStruct.dynamic.getColumnValue("RECORD")).getInt("ntgr"));
        assertEquals(STRINGS, rowStruct.dynamic.getColumnValue("STRINGS"));
        assertEquals(LONGS, rowStruct.dynamic.getColumnValue("LONGS"));
        assertEquals(FLOATS, rowStruct.dynamic.getColumnValue("FLOATS"));
        assertEquals(DOUBLES, rowStruct.dynamic.getColumnValue("DOUBLES"));
        assertEquals(BOOLEANS, rowStruct.dynamic.getColumnValue("BOOLEANS"));
        assertEquals(BYTES, rowStruct.dynamic.getColumnValue("BYTES"));
        assertEquals(DATES, rowStruct.dynamic.getColumnValue("DATES"));
        assertEquals(RECORDS, rowStruct.dynamic.getColumnValue("RECORDS"));
        final List<Record> records = (List<Record>) rowStruct.dynamic.getColumnValue("RECORDS");
        records.forEach(r -> {
            assertEquals(1, r.getInt("ntgr"));
            assertEquals("one", r.getString("str"));
        });
        assertEquals(BIG_DECIMALS, rowStruct.dynamic.getColumnValue("BIG_DECIMALS"));
    }

    @Test
    void visitWithMeta() {
        // preparation
        final Schema.Entry field1 =
                VisitorsTest.factory.newEntryBuilder().withName("field1").withType(Type.STRING).build();
        final Schema.Entry meta1 = VisitorsTest.factory
                .newEntryBuilder()
                .withName("meta1")
                .withType(Type.STRING)
                .withMetadata(true)
                .build();

        final Schema.Entry subField1 =
                VisitorsTest.factory.newEntryBuilder().withName("subField").withType(Type.STRING).build();
        final Schema subRecord = VisitorsTest.factory.newSchemaBuilder(Type.RECORD).withEntry(subField1).build();

        final Schema.Entry sub1 = VisitorsTest.factory.newEntryBuilder().withName("s1").withType(Type.STRING).build();
        final Schema.Entry sub2 = VisitorsTest.factory
                .newEntryBuilder()
                .withName("s2") //
                .withType(Type.RECORD) //
                .withElementSchema(subRecord)
                .build();

        final Schema subRecord2 = VisitorsTest.factory
                .newSchemaBuilder(Type.RECORD) //
                .withEntry(sub1) //
                .withEntry(sub2) //
                .build();

        final Schema.Entry subRecordEntry = VisitorsTest.factory
                .newEntryBuilder()
                .withName("sub") //
                .withType(Type.RECORD) //
                .withMetadata(true) //
                .withElementSchema(subRecord2) //
                .build();

        final Schema schema = VisitorsTest.factory
                .newSchemaBuilder(Type.RECORD) //
                .withEntry(field1) //
                .withEntry(meta1) //
                .withEntry(subRecordEntry)
                .build();

        final Record internalRecord =
                VisitorsTest.factory.newRecordBuilder(subRecord).withString("subField", "subFieldValue").build();

        final Record sub = VisitorsTest.factory
                .newRecordBuilder(subRecord2)
                .withString("s1", "values1")
                .withRecord(sub2, internalRecord)
                .build();

        final Record record = VisitorsTest.factory
                .newRecordBuilder(schema) //
                .withString("field1", "value1") //
                .withString("meta1", "valueMeta") //
                .withRecord(subRecordEntry, sub)
                .build();
        final DiRecordVisitor visitor = new DiRecordVisitor(RowStruct2.class, Collections.emptyMap());

        // call tested method
        final Object visit = visitor.visit(record);

        // Check
        Assertions.assertTrue(visit instanceof RowStruct2);
        RowStruct2 row = (RowStruct2) visit;
        Assertions.assertEquals("value1", row.field1);
        Assertions.assertEquals("valueMeta", row.meta1);
    }

    public static class RowStruct2 implements routines.system.IPersistableRow {

        public String field1;

        public String meta1;

        public Object sub;

        @Override
        public void writeData(ObjectOutputStream objectOutputStream) {
        }

        @Override
        public void readData(ObjectInputStream objectInputStream) {
        }
    }
}
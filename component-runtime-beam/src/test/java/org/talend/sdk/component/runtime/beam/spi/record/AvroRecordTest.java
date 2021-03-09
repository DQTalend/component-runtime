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
package org.talend.sdk.component.runtime.beam.spi.record;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.apache.beam.sdk.util.SerializableUtils.ensureSerializableByCoder;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import org.apache.avro.Schema.Field;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.IndexedRecord;
import org.apache.avro.util.Utf8;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.junit.jupiter.api.Test;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.record.Schema.Entry;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.runtime.beam.avro.AvroSchemas;
import org.talend.sdk.component.runtime.beam.coder.registry.SchemaRegistryCoder;
import org.talend.sdk.component.runtime.beam.spi.AvroRecordBuilderFactoryProvider;
import org.talend.sdk.component.runtime.beam.transform.RecordNormalizer;
import org.talend.sdk.component.runtime.manager.service.api.Unwrappable;
import org.talend.sdk.component.runtime.record.RecordImpl;
import org.talend.sdk.component.runtime.record.SchemaImpl;

class AvroRecordTest {

    @Test
    void recordEntryFromName() {
        assertEquals("{\"record\": {\"name\": \"ok\"}}",
                Unwrappable.class
                        .cast(new AvroRecordBuilder()
                                .withRecord("record", new AvroRecordBuilder().withString("name", "ok").build())
                                .build())
                        .unwrap(IndexedRecord.class)
                        .toString());
    }

    @Test
    void providedSchemaGetSchema() {
        final Schema schema = new AvroSchemaBuilder()
                .withType(Schema.Type.RECORD)
                .withEntry(new SchemaImpl.EntryImpl.BuilderImpl()
                        .withName("name")
                        .withNullable(true)
                        .withType(Schema.Type.STRING)
                        .build())
                .build();
        assertEquals(schema, new AvroRecordBuilder(schema).withString("name", "ok").build().getSchema());
    }

    @Test
    void providedSchemaNullable() {
        final Supplier<AvroRecordBuilder> builder = () -> new AvroRecordBuilder(new AvroSchemaBuilder()
                .withType(Schema.Type.RECORD)
                .withEntry(new SchemaImpl.EntryImpl.BuilderImpl()
                        .withName("name")
                        .withNullable(true)
                        .withType(Schema.Type.STRING)
                        .build())
                .build());
        { // normal/valued
            final Record record = builder.get().withString("name", "ok").build();
            assertEquals(1, record.getSchema().getEntries().size());
            assertEquals("ok", record.getString("name"));
        }
        { // null
            final Record record = builder.get().withString("name", null).build();
            assertEquals(1, record.getSchema().getEntries().size());
            assertNull(record.getString("name"));
        }
        { // missing entry
            assertThrows(IllegalArgumentException.class, () -> builder.get().withString("name2", null).build());
        }
        { // invalid type entry
            assertThrows(IllegalArgumentException.class, () -> builder.get().withInt("name", 2).build());
        }
    }

    @Test
    void providedSchemaNotNullable() {
        final Supplier<RecordImpl.BuilderImpl> builder = () -> new AvroRecordBuilder(new AvroSchemaBuilder()
                .withType(Schema.Type.RECORD)
                .withEntry(new SchemaImpl.EntryImpl.BuilderImpl()
                        .withName("name")
                        .withNullable(false)
                        .withType(Schema.Type.STRING)
                        .build())
                .build());
        { // normal/valued
            final Record record = builder.get().withString("name", "ok").build();
            assertEquals(1, record.getSchema().getEntries().size());
            assertEquals("ok", record.getString("name"));
        }
        { // null
            assertThrows(IllegalArgumentException.class, () -> builder.get().withString("name", null).build());
        }
    }

    @Test
    void bytes() {
        final byte[] array = { 0, 1, 2, 3, 4 };
        final Record record = new AvroRecordBuilder().withBytes("bytes", array).build();
        assertArrayEquals(array, record.getBytes("bytes"));

        final Record copy = ensureSerializableByCoder(SchemaRegistryCoder.of(), record, "test");
        assertArrayEquals(array, copy.getBytes("bytes"));
    }

    @Test
    void stringGetObject() {
        final GenericData.Record avro = new GenericData.Record(org.apache.avro.Schema
                .createRecord(getClass().getName() + ".StringTest", null, null, false,
                        singletonList(new org.apache.avro.Schema.Field("str",
                                org.apache.avro.Schema.create(org.apache.avro.Schema.Type.STRING), null, null))));
        avro.put(0, new Utf8("test"));
        final Record record = new AvroRecord(avro);
        final Object str = record.get(Object.class, "str");
        assertFalse(str.getClass().getName(), Utf8.class.isInstance(str));
        assertEquals("test", str);
    }

    @Test
    void testLabel() {
        final Field f = new org.apache.avro.Schema.Field("str",
                org.apache.avro.Schema.create(org.apache.avro.Schema.Type.STRING), null, null);
        f.addProp(KeysForAvroProperty.LABEL, "my label");
        final GenericData.Record avro = new GenericData.Record(org.apache.avro.Schema
                .createRecord(getClass().getName() + ".StringTest", null, null, false, singletonList(f)));
        avro.put(0, new Utf8("test"));
        final Record record = new AvroRecord(avro);

        final Schema schema = record.getSchema();
        final List<Schema.Entry> entries = schema.getEntries();
        assertEquals("my label", entries.get(0).getRawName());
    }

    @Test
    void schemaRegistryCoder() throws Exception {
        final org.apache.avro.Schema datetime = org.apache.avro.SchemaBuilder
                .record("datetimes")
                .prop("rootProp1", "rootValue1")
                .prop("rootProp2", "rootValue2")
                .fields()
                .name("f1")
                .prop("logicalType", "timestamp-millis")
                .prop("talend.component.DATETIME", "true")
                .prop("fieldProp1", "fieldValue1")
                .prop("fieldProp2", "fieldValue2")
                .type()
                .unionOf()
                .nullType()
                .and()
                .longType()
                .endUnion()
                .noDefault()
                //
                .name("f2")
                .prop("logicalType", "date")
                .prop("talend.component.DATETIME", "true")
                .type()
                .unionOf()
                .nullType()
                .and()
                .longType()
                .endUnion()
                .noDefault()
                //
                .name("f3")
                .prop("logicalType", "date")
                .prop("talend.component.DATETIME", "true")
                .type()
                .unionOf()
                .nullType()
                .and()
                .longType()
                .endUnion()
                .noDefault()
                //
                .endRecord();
        final ZonedDateTime zdt = ZonedDateTime.of(2020, 01, 24, 15, 0, 1, 0, ZoneId.of("UTC"));
        final Date date = new Date();
        final GenericData.Record avro = new GenericData.Record(datetime);
        avro.put(0, zdt.toInstant().toEpochMilli());
        avro.put(1, date.getTime());
        avro.put(2, null);
        final Record record = new AvroRecord(avro);
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        SchemaRegistryCoder.of().encode(record, buffer);
        final Record decoded = SchemaRegistryCoder.of().decode(new ByteArrayInputStream(buffer.toByteArray()));
        assertEquals(zdt, decoded.getDateTime("f1"));
        assertEquals(date.getTime(), decoded.getDateTime("f2").toInstant().toEpochMilli());
        assertNull(decoded.getDateTime("f3"));
        // schema props
        final Schema s = decoded.getSchema();
        assertEquals(2, s.getProps().size());
        assertEquals("rootValue1", s.getProp("rootProp1"));
        assertEquals("rootValue2", s.getProp("rootProp2"));
        // field props
        final Entry sf = s.getEntries().get(0);
        assertEquals("timestamp-millis", sf.getProp("logicalType"));
        assertEquals("true", sf.getProp("talend.component.DATETIME"));
        assertEquals("fieldValue1", sf.getProp("fieldProp1"));
        assertEquals("fieldValue2", sf.getProp("fieldProp2"));
    }

    @Test
    void pipelineDateTimeFields() throws Exception {
        final RecordBuilderFactory factory = new AvroRecordBuilderFactoryProvider().apply(null);
        final Record.Builder builder = factory.newRecordBuilder();
        final Date date = new Date(new java.text.SimpleDateFormat("yyyy-MM-dd").parse("2018-12-6").getTime());
        final Date datetime = new Date();
        final Date time = new Date(1000 * 60 * 60 * 15 + 1000 * 60 * 20 + 39000); // 15:20:39
        builder.withDateTime("t_date", date);
        builder.withDateTime("t_datetime", datetime);
        builder.withDateTime("t_time", time);
        final Record rec = builder.build();
        final Pipeline pipeline = Pipeline.create();
        final PCollection<Record> input = pipeline.apply(Create.of(asList(rec)).withCoder(SchemaRegistryCoder.of())); //
        final PCollection<Record> output = input.apply(new RecordToRecord());
        assertEquals(org.apache.beam.sdk.PipelineResult.State.DONE, pipeline.run().waitUntilFinish());
    }

    public class RecordToRecord extends PTransform<PCollection<Record>, PCollection<Record>> {

        private final RecordBuilderFactory factory;

        public RecordToRecord() {
            factory = new AvroRecordBuilderFactoryProvider().apply(null);
        }

        @Override
        public PCollection<Record> expand(final PCollection<Record> input) {
            return input.apply("RecordToRecord", ParDo.of(new RecordNormalizer(factory)));
        }
    }
}

/**
 * Copyright (C) 2006-2024 Talend Inc. - www.talend.com
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
package org.talend.sdk.component.junit.beam;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import lombok.extern.slf4j.Slf4j;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.values.PBegin;
import org.apache.beam.sdk.values.PCollection;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.input.Assessor;
import org.talend.sdk.component.api.input.Emitter;
import org.talend.sdk.component.api.input.PartitionMapper;
import org.talend.sdk.component.api.input.PartitionSize;
import org.talend.sdk.component.api.input.Producer;
import org.talend.sdk.component.api.input.Split;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.api.service.source.ProducerFinder;
import org.talend.sdk.component.container.Container;
import org.talend.sdk.component.junit.BaseComponentsHandler;
import org.talend.sdk.component.junit.environment.Environment;
import org.talend.sdk.component.junit.environment.builtin.ContextualEnvironment;
import org.talend.sdk.component.junit.environment.builtin.beam.SparkRunnerEnvironment;
import org.talend.sdk.component.junit5.Injected;
import org.talend.sdk.component.junit5.WithComponents;
import org.talend.sdk.component.junit5.environment.EnvironmentalTest;
import org.talend.sdk.component.runtime.base.Delegated;
import org.talend.sdk.component.runtime.beam.TalendIO;
import org.talend.sdk.component.runtime.beam.coder.registry.SchemaRegistryCoder;
import org.talend.sdk.component.runtime.input.Mapper;
import org.talend.sdk.component.runtime.manager.ComponentManager;
import org.talend.sdk.component.runtime.manager.ComponentManager.AllServices;
import org.talend.sdk.component.runtime.manager.chain.Job;

@Slf4j
@Environment(ContextualEnvironment.class)
@Environment(SparkRunnerEnvironment.class)
@WithComponents("org.talend.sdk.component.junit.beam.test")
public class ProducerFinderEnvironmentTest implements Serializable {

    @Injected
    private BaseComponentsHandler handler;

    private static ComponentManager manager;

    @Service
    private RecordBuilderFactory factory;

    /**
     * + * arrayblocking queue capacity
     * + * 7 fixed CAPACITY variable
     * + * recordCount value
     * + * 10 11sec 13sec
     * + * 100 16sec 13sec
     * + * 1000 57sec 16sec
     * + * 10000 ~ 7min55 52sec
     * + * 100000 1h34min 25min
     * +
     */
    private final Integer recordCount =
            Integer.valueOf(System.getProperty("ProducerFinderEnvironmentTest.recordCount", "1000"));

    @BeforeAll
    static void forceManagerInit() {
        // manager for non environmental tests
        if (manager == null) {
            manager = ComponentManager.instance();
            if (manager.find(Stream::of).count() == 0) {
                manager.addPlugin(new File("target/test-classes").getAbsolutePath());
            }
        }
    }

    @Test
    void finderWithTacokitFamily() {
        final Iterator<Record> recordIterator = getInterator(ComponentManager.instance(), "TckFamily", recordCount);
        List<Record> records =
                StreamSupport.stream(Spliterators.spliteratorUnknownSize(recordIterator, Spliterator.ORDERED), false)
                        .collect(toList());
        Assertions.assertEquals(recordCount, records.size());
        records.stream().forEach(e -> log.info("** Record : " + e));
    }

    @Test
    void interateWithBeamFamily() {
        final Iterator<Record> recordIterator = getInterator(ComponentManager.instance(), "BeamFamily", recordCount);
        List<Record> records =
                StreamSupport.stream(Spliterators.spliteratorUnknownSize(recordIterator, Spliterator.ORDERED), false)
                        .collect(toList());
        Assertions.assertEquals(recordCount, records.size());
        records.stream().forEach(e -> log.info("** Record : " + e));
    }

    @EnvironmentalTest
    void runPipelineBeam() {
        Mapper mapper =
                manager.findMapper("BeamFamily", "from", 1, singletonMap("count", recordCount.toString())).get();
        assertNotNull(mapper);
        final Object delegate = Delegated.class.cast(mapper).getDelegate();
        assertNotNull(delegate);
        runPipeline((PTransform<PBegin, PCollection<Record>>) delegate);
    }

    @EnvironmentalTest
    void runPipelineTacokt() {
        Mapper mapper = manager.findMapper("TckFamily", "from", 1, singletonMap("count", recordCount.toString())).get();
        assertNotNull(mapper);
        runPipeline(TalendIO.read(mapper));
    }

    private void runPipeline(PTransform<PBegin, PCollection<Record>> transform) {
        final Pipeline pipeline = Pipeline.create(PipelineOptionsFactory.create());
        final PTransform<PBegin, PCollection<Record>> start = transform;
        final PCollection<Record> out = pipeline.apply(start);
        List<Record> records = IntStream.range(0, recordCount)
                .mapToObj(i -> factory.newRecordBuilder()
                        .withString("id", "id_" + i)
                        .withString("id1", "id_" + i)
                        .withString("id2", "id_" + i)
                        .withString("id3", "id_" + i)
                        .withString("id4", "id_" + i)
                        .withString("id5", "id_" + i)
                        .withString("id6", "id_" + i)
                        .withString("id7", "id_" + i)
                        .withString("id8", "id_" + i)
                        .withString("id9", "id_" + i)
                        .build())
                .collect(toList());
        PAssert.that(out).containsInAnyOrder(records);
        Assert.assertEquals(PipelineResult.State.DONE, pipeline.run().waitUntilFinish());
    }

    @EnvironmentalTest
    void runJobWithTacokitFamily() {
        runJob(handler.asManager(), "TckFamily");
    }

    @EnvironmentalTest
    void runJobWithBeamFamily() {
        runJob(handler.asManager(), "BeamFamily");
    }

    private Iterator<Record> getInterator(final ComponentManager manager, final String family,
            final int expectedNumber) {
        final Container container = manager.findPlugin("test-classes").get();
        ProducerFinder finder = (ProducerFinder) container.get(AllServices.class)
                .getServices()
                .get(ProducerFinder.class);
        assertNotNull(finder);
        final Iterator<Record> recordIterator = finder.find(family, "from", 1,
                singletonMap("count", Integer.toString(expectedNumber)));
        assertNotNull(recordIterator);
        return recordIterator;
    }

    private void runJob(final ComponentManager manager, final String family) {
        final Iterator<Record> recordIterator = getInterator(manager, family, recordCount);
        assertNotNull(recordIterator);
        Iterable<Record> iterable = toIterable(recordIterator);
        handler.setInputData(iterable);
        Job
                .components()
                .component("emitter", "test://emitter")
                .component("output", "test://collector")
                .connections()
                .from("emitter")
                .to("output")
                .build()
                .run();
        List<Record> records = handler.getCollectedData(Record.class);
        assertEquals(recordCount, records.size());
    }

    static <T> Iterable<T> toIterable(Iterator<T> it) {
        return () -> {
            return it;
        };
    }

    @PartitionMapper(name = "from", family = "BeamFamily")
    public static class BeamFamilyFrom extends PTransform<PBegin, PCollection<Record>> {

        private final int count;

        @Service
        private final RecordBuilderFactory recordBuilderFactory;

        public BeamFamilyFrom(@Option("count") final int count, final RecordBuilderFactory factory) {
            this.count = count;
            recordBuilderFactory = factory;
        }

        @Override
        public PCollection<Record> expand(final PBegin input) {
            return input.apply(Create.of(IntStream.range(0, count)
                    .mapToObj(i -> recordBuilderFactory.newRecordBuilder()
                            .withString("id", "id_" + i)
                            .withString("id1", "id_" + i)
                            .withString("id2", "id_" + i)
                            .withString("id3", "id_" + i)
                            .withString("id4", "id_" + i)
                            .withString("id5", "id_" + i)
                            .withString("id6", "id_" + i)
                            .withString("id7", "id_" + i)
                            .withString("id8", "id_" + i)
                            .withString("id9", "id_" + i)
                            .build())
                    .collect(toList()))
                    .withCoder(SchemaRegistryCoder.of()));
        }
    }

    @PartitionMapper(name = "from", family = "TckFamily")
    public static class TckFamilyFrom implements Serializable {

        @Service
        private final RecordBuilderFactory recordBuilderFactory;

        private final int count;

        private int counted = 0;

        public TckFamilyFrom(@Option("count") final int count, final RecordBuilderFactory factory) {
            this.count = count;
            recordBuilderFactory = factory;
        }

        @Assessor
        public long estimateSize() {
            return 1;
        }

        @Split
        public List<TckFamilyFrom> split(@PartitionSize final long bundleSize) {
            return singletonList(this);
        }

        @Emitter
        public TckFamilyFrom create() {
            return this;
        }

        @Producer
        public Record next() {
            Record record = counted == count ? null
                    : recordBuilderFactory.newRecordBuilder()
                            .withString("id", "id_" + counted)
                            .withString("id1", "id_" + counted)
                            .withString("id2", "id_" + counted)
                            .withString("id3", "id_" + counted)
                            .withString("id4", "id_" + counted)
                            .withString("id5", "id_" + counted)
                            .withString("id6", "id_" + counted)
                            .withString("id7", "id_" + counted)
                            .withString("id8", "id_" + counted)
                            .withString("id9", "id_" + counted++)
                            .build();
            return record;
        }
    }
}

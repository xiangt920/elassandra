/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.mapper;

import java.nio.ByteBuffer;
import java.util.Map;

import org.apache.cassandra.utils.ByteBufferUtil;
import org.elasticsearch.Version;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.ParseFieldMatcher;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.index.analysis.AnalysisService;
import org.elasticsearch.index.similarity.SimilarityLookupService;

import com.google.common.collect.ImmutableMap;

public abstract class Mapper implements ToXContent, Iterable<Mapper> {

    public static enum CqlCollection {
        LIST, SET, SINGLETON
    }
    
    public static enum CqlStruct {
        UDT, MAP, TUPLE
    }
    
    public static class BuilderContext {
        private final Settings indexSettings;
        private final ContentPath contentPath;

        public BuilderContext(Settings indexSettings, ContentPath contentPath) {
            this.contentPath = contentPath;
            this.indexSettings = indexSettings;
        }

        public ContentPath path() {
            return this.contentPath;
        }

        @Nullable
        public Settings indexSettings() {
            return this.indexSettings;
        }

        @Nullable
        public Version indexCreatedVersion() {
            if (indexSettings == null) {
                return null;
            }
            return Version.indexCreated(indexSettings);
        }
    }

    public static abstract class Builder<T extends Builder, Y extends Mapper> {

        public String name;

        protected T builder;

        protected Builder(String name) {
            this.name = name;
        }

        public String name() {
            return this.name;
        }

        public abstract Y build(BuilderContext context);
    }

    public interface TypeParser {

        class ParserContext {

            private final String type;

            private final AnalysisService analysisService;

            private final SimilarityLookupService similarityLookupService;

            private final MapperService mapperService;

            private final Map<String, TypeParser> typeParsers;

            private final Version indexVersionCreated;

            private final ParseFieldMatcher parseFieldMatcher;

            public ParserContext(String type, AnalysisService analysisService, SimilarityLookupService similarityLookupService,
                                 MapperService mapperService, Map<String, TypeParser> typeParsers,
                                 Version indexVersionCreated, ParseFieldMatcher parseFieldMatcher) {
                this.type = type;
                this.analysisService = analysisService;
                this.similarityLookupService = similarityLookupService;
                this.mapperService = mapperService;
                this.typeParsers = typeParsers;
                this.indexVersionCreated = indexVersionCreated;
                this.parseFieldMatcher = parseFieldMatcher;
            }

            public String type() {
                return type;
            }

            public AnalysisService analysisService() {
                return analysisService;
            }

            public SimilarityLookupService similarityLookupService() {
                return similarityLookupService;
            }

            public MapperService mapperService() {
                return mapperService;
            }

            public TypeParser typeParser(String type) {
                return typeParsers.get(Strings.toUnderscoreCase(type));
            }

            public Version indexVersionCreated() {
                return indexVersionCreated;
            }

            public ParseFieldMatcher parseFieldMatcher() {
                return parseFieldMatcher;
            }

            public boolean isWithinMultiField() { return false; }

            protected Map<String, TypeParser> typeParsers() { return typeParsers; }

            public ParserContext createMultiFieldContext(ParserContext in) {
                return ParserContext.this.new MultiFieldParserContext(in) {
                    @Override
                    public boolean isWithinMultiField() { return true; }
                };
            }

            class MultiFieldParserContext extends ParserContext {
                MultiFieldParserContext(ParserContext in) {
                    super(in.type(), in.analysisService, in.similarityLookupService(), in.mapperService(), ImmutableMap.<String, TypeParser>builder().putAll(in.typeParsers()).build(), in.indexVersionCreated(), in.parseFieldMatcher());
                }
            }

        }

        Mapper.Builder<?,?> parse(String name, Map<String, Object> node, ParserContext parserContext) throws MapperParsingException;
    }

    private final String simpleName;
    private ByteBuffer   cqlName;

    public Mapper(String simpleName) {
        this.simpleName = simpleName;
    }

    /** Returns the simple name, which identifies this mapper against other mappers at the same level in the mappers hierarchy
     * TODO: make this protected once Mapper and FieldMapper are merged together */
    public final String simpleName() {
        return simpleName;
    }

    /** Returns the canonical name which uniquely identifies the mapper against other mappers in a type. */
    public abstract String name();
    
    public abstract void merge(Mapper mergeWith, MergeResult mergeResult) throws MergeMappingException;
    
    /**
     * @return cql column name as a ByteBuffer
     */
    public ByteBuffer cqlName() {
        if (cqlName == null) {
            cqlName = ByteBufferUtil.bytes(this.simpleName);
        }
        return cqlName;
    }
    
    public abstract CqlCollection cqlCollection();
    
    public abstract String cqlCollectionTag();

    public abstract CqlStruct cqlStruct();
    
    public abstract boolean cqlPartialUpdate();
    
    public abstract boolean cqlPartitionKey();
    
    public abstract boolean cqlStaticColumn();
    
    public abstract int cqlPrimaryKeyOrder();
}

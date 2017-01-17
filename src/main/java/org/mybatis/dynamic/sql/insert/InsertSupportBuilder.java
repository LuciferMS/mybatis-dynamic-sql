/**
 *    Copyright 2016-2017 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.mybatis.dynamic.sql.insert;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

public interface InsertSupportBuilder {

    static <T> InsertSupportBuildStep1<T> insert(T record) {
        return new InsertSupportBuildStep1<>(record);
    }
    
    static class InsertSupportBuildStep1<T> {
        private T record;
        
        public InsertSupportBuildStep1(T record) {
            this.record = record;
        }
        
        public InsertSupportBuildStep2<T> into(SqlTable table) {
            return new InsertSupportBuildStep2<>(record, table);
        }
    }
    
    static class InsertSupportBuildStep2<T> {
        private T record;
        private SqlTable table;
        private List<ColumnMapping<?>> columnMappings = new ArrayList<>();

        public InsertSupportBuildStep2(T record, SqlTable table) {
            this.record = record;
            this.table = table;
        }
        
        public <F> InsertSupportBuildStep2<T> withColumnMapping(SqlColumn<F> column, String property, F value) {
            columnMappings.add(ColumnMapping.of(column, property, value));
            return this;
        }
        
        public <F> InsertSupportBuildStep2<T> withColumnMapping(SqlColumn<F> column, String property, Optional<F> value) {
            value.ifPresent(v -> columnMappings.add(ColumnMapping.of(column, property, v)));
            return this;
        }
        
        public InsertSupport<T> build() {
            return columnMappings.stream().collect(Collector.of(
                    CollectorSupport::new,
                    CollectorSupport::add,
                    CollectorSupport::merge,
                    c -> c.toInsertSupport(record, table)));
        }
        
        /**
         * A little triplet to hold the column mapping.  Only intended for use in this builder. 
         *  
         * @param <F> the column type of this mapping
         */
        static class ColumnMapping<F> {
            SqlColumn<F> column;
            String property;
            F value;
            
            static <F> ColumnMapping<F> of(SqlColumn<F> column, String property, F value) {
                ColumnMapping<F> mapping = new ColumnMapping<>();
                mapping.column = column;
                mapping.property = property;
                mapping.value = value;
                return mapping;
            }
        }
        
        static class CollectorSupport {
            List<String> columnPhrases = new ArrayList<>();
            List<String> valuePhrases = new ArrayList<>();
            
            void add(ColumnMapping<?> mapping) {
                columnPhrases.add(mapping.column.name());
                valuePhrases.add(mapping.column.getFormattedJdbcPlaceholder("record", mapping.property)); //$NON-NLS-1$
            }
            
            CollectorSupport merge(CollectorSupport other) {
                columnPhrases.addAll(other.columnPhrases);
                valuePhrases.addAll(other.valuePhrases);
                return this;
            }
            
            String columnsPhrase() {
                return columnPhrases.stream()
                        .collect(Collectors.joining(", ", "(", ")")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$)
            }

            String valuesPhrase() {
                return valuePhrases.stream()
                        .collect(Collectors.joining(", ", "values (", ")")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }
            
            <T> InsertSupport<T> toInsertSupport(T record, SqlTable table) {
                return InsertSupport.of(columnsPhrase(), valuesPhrase(), record, table);
            }
        }
    }
}

package edu.uci.ics.texera.dataflow.fuzzytokenmatcher;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.uci.ics.texera.api.dataflow.IOperator;
import edu.uci.ics.texera.api.exception.PlanGenException;
import edu.uci.ics.texera.dataflow.common.PropertyNameConstants;

public class FuzzyTokenSourcePredicate extends FuzzyTokenPredicate {
    
    private final String tableName;

    public FuzzyTokenSourcePredicate(
            @JsonProperty(value = PropertyNameConstants.FUZZY_TOKEN_QUERY, required = true)
            String query, 
            @JsonProperty(value = PropertyNameConstants.ATTRIBUTE_NAMES, required = true)
            List<String> attributeNames,
            @JsonProperty(value = PropertyNameConstants.LUCENE_ANALYZER_STRING, required = true)
            String luceneAnalyzerStr,
            @JsonProperty(value = PropertyNameConstants.FUZZY_TOKEN_THRESHOLD_RATIO, required = true)
            Double thresholdRatio,
            @JsonProperty(value = PropertyNameConstants.TABLE_NAME, required = true)
            String tableName,
            @JsonProperty(value = PropertyNameConstants.SPAN_LIST_NAME, required = true)
            String spanListName) {
        super(query, attributeNames, luceneAnalyzerStr, thresholdRatio, spanListName);

        if (tableName == null || tableName.isEmpty()) {
            throw new PlanGenException(PropertyNameConstants.EMPTY_NAME_EXCEPTION);
        }
        this.tableName = tableName;
    }
    
    @JsonProperty(value = PropertyNameConstants.TABLE_NAME)
    public String getTableName() {
        return this.tableName;
    }
    
    @Override
    public IOperator newOperator() {
        return new FuzzyTokenMatcherSourceOperator(this);
    }

}

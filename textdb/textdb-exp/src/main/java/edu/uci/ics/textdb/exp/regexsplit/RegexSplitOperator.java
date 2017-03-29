package edu.uci.ics.textdb.exp.regexsplit;

import edu.uci.ics.textdb.api.exception.DataFlowException;
import edu.uci.ics.textdb.api.exception.TextDBException;
import edu.uci.ics.textdb.api.field.IField;
import edu.uci.ics.textdb.api.field.StringField;
import edu.uci.ics.textdb.api.field.TextField;
import edu.uci.ics.textdb.api.schema.AttributeType;
import edu.uci.ics.textdb.api.schema.Schema;
import edu.uci.ics.textdb.api.tuple.Tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import edu.uci.ics.textdb.api.dataflow.ISourceOperator;
import edu.uci.ics.textdb.dataflow.common.AbstractSingleInputOperator;
import junit.framework.Assert;

/**
 * @author Qinhua Huang
 * @author Zuozhi Wang
 * 
 * This class is to divide an attribute of TextField or StringField by using a regex in order to get multiple tuples, 
 * with the other attributes unchanged.
 * 
 * For example:
 * Source text: "Make America be Great America Again."
 * splitRegex = "America"
 * Split output can be one of the following three based on the SplitTypes:
 * 1. GROUP_RIGHT: ["Make ", "America be Great ", "America Again."]
 * 2. GROUP_LEFT: ["Make America", " be Great America"," Again."]
 * 3. STANDALONE: ["Make ", "America", " be Great ", "America", " Again."]
 * 
 * When a string contains multiple repeated patterns, this operator will only return the longest pattern.
 * For example:
 * Text = "banana"
 * regex = "b.*ana";
 * result list = <"banana">
 */
public class RegexSplitOperator extends AbstractSingleInputOperator implements ISourceOperator{

    private RegexSplitPredicate predicate;

    private List<Tuple> outputTupleBuffer;
    private int bufferCursor;
    private Schema inputSchema;

    public RegexSplitOperator(RegexSplitPredicate predicate) {
        this.predicate = predicate;
        this.outputTupleBuffer = null;
        this.bufferCursor = -1;
    }

    @Override
    protected void setUp() throws DataFlowException {
        inputSchema = inputOperator.getOutputSchema();
        outputSchema = inputSchema;
    }


    @Override
    protected Tuple computeNextMatchingTuple() throws TextDBException {
        // Keep fetching the next input tuple until finding a match.
        while (outputTupleBuffer == null || outputTupleBuffer.size() == 0) {
            Tuple inputTuple = inputOperator.getNextTuple();
            if (inputTuple == null) {
                return null;
            }

            populateOutputBuffer(inputTuple);
            if (this.outputTupleBuffer.size() > 0) {
                this.bufferCursor = 0;
                break;
            }
        }
        
        Assert.assertEquals(bufferCursor < outputTupleBuffer.size(), true);
        
        //If there is a buffer and cursor < bufferSize, get an output tuple.
        Tuple resultTuple = outputTupleBuffer.get(bufferCursor);
        bufferCursor++;
        // If it reaches the end of the buffer, reset the buffer cursor.
        if (bufferCursor == outputTupleBuffer.size()) {
            outputTupleBuffer = null;
            bufferCursor = 0;
        }
        
        return resultTuple;
    }

    private void populateOutputBuffer(Tuple inputTuple) throws TextDBException {
        if (inputTuple == null) {
            return;
        }
        
        AttributeType attributeType = this.inputSchema.getAttribute(predicate.getAttributeToSplit()).getAttributeType();
        if (attributeType != AttributeType.TEXT && attributeType != AttributeType.STRING) {
            return;
        }

        String strToSplit = inputTuple.getField(predicate.getAttributeToSplit()).getValue().toString();
        List<String> stringList = splitText(strToSplit);
        outputTupleBuffer = new ArrayList<>();
        for (String singleMatch : stringList) {
            List<IField> tupleFieldList = new ArrayList<>();
            for (String attributeName : inputSchema.getAttributeNames()) {
                if (attributeName.equals(predicate.getAttributeToSplit())) {
                    if (attributeType == AttributeType.TEXT) {
                        tupleFieldList.add(new TextField(singleMatch));
                    } else {
                        tupleFieldList.add(new StringField(singleMatch));
                    }
                } else {
                    tupleFieldList.add(inputTuple.getField(attributeName));
                }
            }
            outputTupleBuffer.add(new Tuple(inputSchema, tupleFieldList.stream().toArray(IField[]::new)));
        }
        
    }
    
    /*
     *  Process text into list.
     */
    private List<String> splitText(String strText) throws TextDBException {
        List<String> stringtList = new ArrayList<>();
        //Create a pattern using regex.
        Pattern pattern = Pattern.compile(predicate.getRegex());
        
        // Match the pattern in the text.
        Matcher regexMatcher = pattern.matcher(strText);
        List<Integer> splitIndex = new ArrayList<Integer>();
        splitIndex.add(0);
        
        while(regexMatcher.find()) {
            if (predicate.getSplitType() == RegexSplitPredicate.SplitType.GROUP_RIGHT) {
                splitIndex.add(regexMatcher.start());
            } else if (predicate.getSplitType() == RegexSplitPredicate.SplitType.GROUP_LEFT) {
                splitIndex.add(regexMatcher.end());
            } else if (predicate.getSplitType() == RegexSplitPredicate.SplitType.STANDALONE) {
                splitIndex.add(regexMatcher.start());
                splitIndex.add(regexMatcher.end());
            }
        }
        
        splitIndex.add(strText.length());
        
        for (int i = 0 ; i < splitIndex.size() - 1; i++) {
            if (splitIndex.get(i) < splitIndex.get(i+1)) {
                stringtList.add(strText.substring(splitIndex.get(i), splitIndex.get(i + 1)));
            } 
        }
        return stringtList;
    }
    
    @Override
    protected void cleanUp() throws TextDBException {
        outputTupleBuffer = null;
        bufferCursor = 0;
    }
    
    public RegexSplitPredicate getPredicate() {
        return this.predicate;
    }

    
    @Override
    public Tuple processOneInputTuple(Tuple inputTuple) throws TextDBException {
        throw new RuntimeException("RegexSplit does not support process one tuple");
    }

}

package edu.uci.ics.texera.workflow.operators.udf.pythonV2

import com.fasterxml.jackson.annotation.{JsonProperty, JsonPropertyDescription}
import com.google.common.base.Preconditions
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle
import edu.uci.ics.amber.engine.operators.OpExecConfig
import edu.uci.ics.texera.workflow.common.metadata.{
  InputPort,
  OperatorGroupConstants,
  OperatorInfo,
  OutputPort
}
import edu.uci.ics.texera.workflow.common.operators.{
  ManyToOneOpExecConfig,
  OneToOneOpExecConfig,
  OperatorDescriptor
}
import edu.uci.ics.texera.workflow.common.tuple.schema.{Attribute, OperatorSchemaInfo, Schema}

import java.util.Collections.singletonList
import scala.collection.JavaConverters._

class PythonUDFOpDescV2 extends OperatorDescriptor {
  @JsonProperty(
    required = true,
    defaultValue =
      "# Choose from the following templates:\n" +
        "# \n" +
        "# from pytexera import *\n" +
        "# \n" +
        "# class ProcessTupleOperator(UDFOperatorV2):\n" +
        "#     \n" +
        "#     @overrides\n" +
        "#     def process_tuple(self, tuple_: Tuple, port: int) -> Iterator[Optional[TupleLike]]:\n" +
        "#         yield tuple_\n" +
        "# \n" +
        "# class ProcessBatchOperator(UDFBatchOperator):\n" +
        "#     BATCH_SIZE = 10 # must be a positive integer\n" +
        "# \n" +
        "#     @overrides\n" +
        "#     def process_batch(self, batch: Batch, port: int) -> Iterator[Optional[BatchLike]]:\n" +
        "#         yield batch\n" +
        "# \n" +
        "# class ProcessTableOperator(UDFTableOperator):\n" +
        "# \n" +
        "#     @overrides\n" +
        "#     def process_table(self, table: Table, port: int) -> Iterator[Optional[TableLike]]:\n" +
        "#         yield table\n"
  )
  @JsonSchemaTitle("Python script")
  @JsonPropertyDescription("Input your code here")
  var code: String = ""
  @JsonProperty(required = true)
  @JsonSchemaTitle("Worker count")
  @JsonPropertyDescription("Specify how many parallel workers to lunch")
  var workers: Int = Int.box(1)
  @JsonProperty(required = true, defaultValue = "true")
  @JsonSchemaTitle("Retain input columns")
  @JsonPropertyDescription("Keep the original input columns?")
  var retainInputColumns: Boolean = Boolean.box(false)
  @JsonProperty
  @JsonSchemaTitle("Extra output column(s)")
  @JsonPropertyDescription(
    "Name of the newly added output columns that the UDF will produce, if any"
  )
  var outputColumns: List[Attribute] = List()

  override def operatorExecutor(operatorSchemaInfo: OperatorSchemaInfo): OpExecConfig = {
    val exec = (i: Any) => new PythonUDFOpExecV2(code, operatorSchemaInfo.outputSchemas.head)
    Preconditions.checkArgument(workers >= 1, "Need at least 1 worker.", Array())
    if (workers > 1) new OneToOneOpExecConfig(operatorIdentifier, exec, workers)
    else new ManyToOneOpExecConfig(operatorIdentifier, exec)
  }

  override def operatorInfo: OperatorInfo =
    OperatorInfo(
      "Python UDF",
      "User-defined function operator in Python script",
      OperatorGroupConstants.UDF_GROUP,
      asScalaBuffer(singletonList(new InputPort("", true))).toList,
      asScalaBuffer(singletonList(new OutputPort(""))).toList,
      true,
      true
    )

  override def getOutputSchema(schemas: Array[Schema]): Schema = {
    Preconditions.checkArgument(schemas.length == 1)
    val inputSchema = schemas(0)
    val outputSchemaBuilder = Schema.newBuilder
    // keep the same schema from input
    if (retainInputColumns) outputSchemaBuilder.add(inputSchema)
    // for any pythonUDFType, it can add custom output columns (attributes).
    if (outputColumns != null) {
      if (retainInputColumns) { // check if columns are duplicated

        for (column <- outputColumns) {
          if (inputSchema.containsAttribute(column.getName))
            throw new RuntimeException("Column name " + column.getName + " already exists!")
        }
      }
      outputSchemaBuilder.add(outputColumns.asJava).build
    }
    outputSchemaBuilder.build
  }
}

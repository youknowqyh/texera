package texera.common.workflow.common

import Engine.Common.tuple.texera.schema.Schema
import com.google.common.base.Preconditions
import texera.common.workflow.TexeraOperatorDescriptor

abstract class FilterOpDesc extends TexeraOperatorDescriptor {

  override def transformSchema(schemas: Schema*): Schema = {
    Preconditions.checkArgument(schemas.length == 1)
    schemas(0)
  }

}

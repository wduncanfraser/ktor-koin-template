package com.example.generated.api.models

import kotlin.Boolean
import kotlin.String
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class UpdateTodoRequestContract(
  /**
   * The name of the Todo.
   */
  @SerialName("name")
  public val name: String,
  /**
   * Is the Todo completed.
   */
  @SerialName("completed")
  public val completed: Boolean,
)

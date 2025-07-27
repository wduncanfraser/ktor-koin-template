package com.example.generated.api.models

import kotlin.String
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class CreateTodoRequestContract(
  /**
   * The name of the Todo.
   */
  @SerialName("name")
  public val name: String,
)

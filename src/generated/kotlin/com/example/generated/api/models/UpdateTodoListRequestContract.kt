package com.example.generated.api.models

import kotlin.String
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class UpdateTodoListRequestContract(
  /**
   * The name of the Todo list.
   */
  @SerialName("name")
  public val name: String,
  /**
   * An optional description of the Todo list.
   */
  @SerialName("description")
  public val description: String? = null,
)

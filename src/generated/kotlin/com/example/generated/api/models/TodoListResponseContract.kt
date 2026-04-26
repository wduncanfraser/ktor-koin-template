package com.example.generated.api.models

import kotlin.String
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A todo list.
 */
@Serializable
public data class TodoListResponseContract(
  @SerialName("id")
  public val id: String,
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
  /**
   * The id of the user who created this todo list.
   */
  @SerialName("createdBy")
  public val createdBy: String,
  /**
   * Date and time which is set automatically when the resource is created.
   */
  @SerialName("createdAt")
  public val createdAt: Instant,
  /**
   * Date and time which updates automatically when the resource is updated.
   */
  @SerialName("updatedAt")
  public val updatedAt: Instant,
)

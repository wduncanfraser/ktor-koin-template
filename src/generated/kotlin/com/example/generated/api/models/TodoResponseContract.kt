package com.example.generated.api.models

import kotlin.Boolean
import kotlin.String
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A todo item.
 */
@Serializable
public data class TodoResponseContract(
  @SerialName("id")
  public val id: String,
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
  /**
   * Date and time when the Todo was marked completed.
   */
  @SerialName("completedAt")
  public val completedAt: Instant? = null,
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

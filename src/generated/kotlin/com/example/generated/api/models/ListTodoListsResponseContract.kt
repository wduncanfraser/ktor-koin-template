package com.example.generated.api.models

import kotlin.collections.List
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class ListTodoListsResponseContract(
  @SerialName("data")
  public val `data`: List<TodoListResponseContract>,
  /**
   * Paginated Results with metadata describing the page
   */
  @SerialName("pagination")
  public val pagination: PaginationMetadataContract,
)

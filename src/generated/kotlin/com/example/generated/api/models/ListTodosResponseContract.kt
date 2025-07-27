package com.example.generated.api.models

import kotlin.collections.List
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class ListTodosResponseContract(
  @SerialName("data")
  public val `data`: List<TodoResponseContract>,
  /**
   * Paginated Results with metadata describing the page
   */
  @SerialName("pagination")
  public val pagination: PaginationMetadataContract,
)

package com.example.generated.api.models

import kotlin.Int
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Paginated Results with metadata describing the page
 */
@Serializable
public data class PaginationMetadataContract(
  @SerialName("page")
  public val page: Int,
  @SerialName("pageSize")
  public val pageSize: Int,
  @SerialName("totalPages")
  public val totalPages: Int,
  @SerialName("totalRows")
  public val totalRows: Int,
)

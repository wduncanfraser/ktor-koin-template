package com.example.generated.api.models

import kotlin.Int
import kotlin.String
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * RFC 9457 compliant error response
 */
@Serializable
public data class ProblemDetailsContract(
  /**
   * URI reference identifying the problem type.
   */
  @SerialName("type")
  public val type: String,
  /**
   * A short, human-readable summary of the problem type.
   */
  @SerialName("title")
  public val title: String,
  /**
   * HTTP status code for this error occurrence.
   */
  @SerialName("status")
  public val status: Int,
  /**
   * Explanation specific to this occurrence of the problem.
   */
  @SerialName("detail")
  public val detail: String,
  /**
   * URI reference identifying the specific occurrence of the problem.
   */
  @SerialName("instance")
  public val instance: String,
)

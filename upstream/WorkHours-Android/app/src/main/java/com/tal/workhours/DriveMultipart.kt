package com.tal.workhours

/** RFC MIME delimiters require actual CRLF bytes, not the literal characters backslash-r. */
internal fun driveMultipartBody(metadata: String, backup: String, boundary: String): String {
    require(boundary.matches(Regex("[A-Za-z0-9_-]{1,70}")))
    return listOf(
        "--$boundary",
        "Content-Type: application/json; charset=UTF-8",
        "",
        metadata,
        "--$boundary",
        "Content-Type: application/json; charset=UTF-8",
        "",
        backup,
        "--$boundary--",
        ""
    ).joinToString("\r\n")
}

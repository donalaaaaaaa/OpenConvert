package com.openconvert.app.domain.converter

object MediaInputResolver {
    fun copiesInput(hasReadableFilePath: Boolean, hasSafParameter: Boolean): Boolean =
        !hasReadableFilePath && !hasSafParameter
}

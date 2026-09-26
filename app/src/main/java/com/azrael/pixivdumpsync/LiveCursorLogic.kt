package com.azrael.pixivdumpsync

object LiveCursorLogic {
    fun newIdsNewestFirst(idsNewestFirst: List<String>, cursor: String?): List<String> {
        if (cursor.isNullOrBlank() || idsNewestFirst.isEmpty()) return emptyList()

        val index = idsNewestFirst.indexOf(cursor)
        if (index >= 0) return idsNewestFirst.take(index)

        val cursorNumber = cursor.toLongOrNull() ?: return emptyList()
        return idsNewestFirst.filter { (it.toLongOrNull() ?: Long.MIN_VALUE) > cursorNumber }
    }

    fun processingOrder(idsNewestFirst: List<String>, cursor: String?): List<String> =
        newIdsNewestFirst(idsNewestFirst, cursor).asReversed()
}

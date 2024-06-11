package com.frugs.yomo.book

import android.content.Context
import android.net.Uri
import com.frugs.yomo.BookyApp
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.util.Collections

class SyosetuBook(context: Context?) : Book(context) {

    companion object {
        private val META_PREFIX = "meta."
        private val ORDERCOUNT = "ordercount"
        private val BOOK_CONTENT_DIR = "bookContentDir"
        private val ORDER = "order."
        private val ITEM = "item."
        private val TOCCOUNT = "toccount"
        private val TOC_LABEL = "toc.label."
        private val TOC_CONTENT = "toc.content."
        private val TOC = "toc"

        private fun getOrderFileName(order: Int): String  {
            return "${ORDER}${order}.html"
        }
    }

    private val docFileOrder: MutableList<String> = ArrayList()

    private val ncode: String
        get() {
            val fileName = file.name

            if (fileName.isEmpty()) {
                return ""
            }

            val split = fileName.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (split.isEmpty()) {
                return ""
            }

            return split[0]
        }

    @Throws(IOException::class)
    override fun load() {
        runBlocking {
            val syosetuService = BookyApp.getSyosetuService(context)

            if (!sharedPreferences.contains(ORDERCOUNT)) {
                val details = syosetuService.getDetails(ncode)
                val pages = details?.pages ?: 1
                for (i in 1..pages) {
                    val text = syosetuService.getText(ncode, i)
                    val orderFileName = getOrderFileName(i)

                    val outFile = File(thisBookDir, orderFileName)
                    if (!outFile.exists()) {
                        outFile.writeText(text)
                    }
                }

                val bookData = sharedPreferences.edit()
                bookData.putInt(ORDERCOUNT, pages)
                bookData.apply()
            }

            val pages = sharedPreferences.getInt(ORDERCOUNT, 1)
            for (i in 1..pages) {
                docFileOrder.add(getOrderFileName(i))
            }
        }
    }

    override fun getToc(): Map<String, String>? {
        return null
    }

    @Throws(IOException::class)
    override fun getMetaData(): BookMetadata? {
        return null
    }

    override fun getSectionIds(): List<String>? {
        return Collections.unmodifiableList(docFileOrder)
    }

    override fun getUriForSectionID(id: String): Uri? {
        return Uri.fromFile(File(thisBookDir, id))
    }

    override fun locateReadPoint(section: String): ReadPoint? {
        return null
    }
}

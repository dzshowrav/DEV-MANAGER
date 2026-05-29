package com.example.devmanager

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

// Models for DOCX content
sealed class DocLine {
    data class Paragraph(
        val text: String,
        val isHeading: Boolean = false,
        val isBold: Boolean = false,
        val isItalic: Boolean = false
    ) : DocLine()
    
    data class Table(val rows: List<List<String>>) : DocLine()
}

// Models for XLSX content
data class ExcelSheet(
    val name: String,
    val cells: Map<String, String>, // Key is address like "A1", value is formatted cell string
    val maxRow: Int,
    val maxCol: Int
)

object DocumentParser {

    /**
     * Parses a Word Document (.docx) file into a list of formatted lines/tables.
     */
    fun parseDocx(file: File): List<DocLine> {
        val result = mutableListOf<DocLine>()
        var zipFile: ZipFile? = null
        try {
            zipFile = ZipFile(file)
            val entry = zipFile.getEntry("word/document.xml") ?: return emptyList()
            zipFile.getInputStream(entry).use { stream ->
                val parser = Xml.newPullParser()
                parser.setInput(stream, "UTF-8")
                
                var eventType = parser.eventType
                var currentPText = StringBuilder()
                var isBold = false
                var isItalic = false
                var isHeading = false
                
                var inTable = false
                val tableRows = mutableListOf<List<String>>()
                var currentRow = mutableListOf<String>()
                var currentCellText = StringBuilder()
                
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val name = parser.name
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (name) {
                                "p" -> {
                                    currentPText.clear()
                                    isHeading = false
                                }
                                "b" -> isBold = true
                                "i" -> isItalic = true
                                "pStyle" -> {
                                    val styleVal = parser.getAttributeValue(null, "val")
                                    if (styleVal != null && styleVal.startsWith("Heading", ignoreCase = true)) {
                                        isHeading = true
                                    }
                                }
                                "tbl" -> {
                                    inTable = true
                                    tableRows.clear()
                                }
                                "tr" -> {
                                    currentRow.clear()
                                }
                                "tc" -> {
                                    currentCellText.clear()
                                }
                            }
                        }
                        XmlPullParser.TEXT -> {
                            val text = parser.text ?: ""
                            if (inTable) {
                                currentCellText.append(text)
                            } else {
                                currentPText.append(text)
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when (name) {
                                "p" -> {
                                    val text = currentPText.toString().trim()
                                    if (text.isNotEmpty()) {
                                        result.add(DocLine.Paragraph(text, isHeading, isBold, isItalic))
                                    }
                                    isBold = false
                                    isItalic = false
                                    isHeading = false
                                }
                                "tc" -> {
                                    currentRow.add(currentCellText.toString().trim())
                                }
                                "tr" -> {
                                    tableRows.add(currentRow.toList())
                                }
                                "tbl" -> {
                                    inTable = false
                                    if (tableRows.isNotEmpty()) {
                                        result.add(DocLine.Table(tableRows.toList()))
                                    }
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Graceful fallback for docx corrupted/unsupported structures: read text files directly
            result.add(DocLine.Paragraph("Failed to load DOCX document fully: ${e.localizedMessage}"))
        } finally {
            try { zipFile?.close() } catch (ex: Exception) {}
        }
        return result
    }

    /**
     * Parses an Excel Document (.xlsx) file into sheets and cells.
     */
    fun parseXlsx(file: File): List<ExcelSheet> {
        val sheets = mutableListOf<ExcelSheet>()
        var zipFile: ZipFile? = null
        try {
            zipFile = ZipFile(file)
            
            // 1. Load Shared Strings XML if exists
            val sharedStrings = loadSharedStrings(zipFile)
            
            // 2. Load Workbook relationship xml to find sheets and their names
            val sheetMetadata = loadWorkbookMetadata(zipFile)
            
            // 3. Parse each worksheet XML
            if (sheetMetadata.isEmpty()) {
                // Try parsing sheet1.xml as fallback
                val sheet1Entry = zipFile.getEntry("xl/worksheets/sheet1.xml")
                if (sheet1Entry != null) {
                    val cellsMap = mutableMapOf<String, String>()
                    val bounds = parseWorksheet(zipFile.getInputStream(sheet1Entry), sharedStrings, cellsMap)
                    sheets.add(ExcelSheet("Sheet 1", cellsMap, bounds.first, bounds.second))
                }
            } else {
                for ((index, meta) in sheetMetadata.withIndex()) {
                    // Try by sheet index: e.g. sheetIndex = index + 1
                    val sheetFileName = "xl/worksheets/sheet${index + 1}.xml"
                    val entry = zipFile.getEntry(sheetFileName)
                    if (entry != null) {
                        val cellsMap = mutableMapOf<String, String>()
                        val bounds = parseWorksheet(zipFile.getInputStream(entry), sharedStrings, cellsMap)
                        sheets.add(ExcelSheet(meta.name, cellsMap, bounds.first, bounds.second))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { zipFile?.close() } catch (ex: Exception) {}
        }
        return sheets
    }

    private fun loadSharedStrings(zipFile: ZipFile): List<String> {
        val result = mutableListOf<String>()
        val entry = zipFile.getEntry("xl/sharedStrings.xml") ?: return result
        try {
            zipFile.getInputStream(entry).use { stream ->
                val parser = Xml.newPullParser()
                parser.setInput(stream, "UTF-8")
                var eventType = parser.eventType
                var inText = false
                val currentText = StringBuilder()
                
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val name = parser.name
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            if (name == "t") {
                                inText = true
                                currentText.clear()
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (inText) {
                                currentText.append(parser.text ?: "")
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (name == "t") {
                                inText = false
                                result.add(currentText.toString())
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private data class SheetMeta(val id: String, val name: String)

    private fun loadWorkbookMetadata(zipFile: ZipFile): List<SheetMeta> {
        val list = mutableListOf<SheetMeta>()
        val entry = zipFile.getEntry("xl/workbook.xml") ?: return list
        try {
            zipFile.getInputStream(entry).use { stream ->
                val parser = Xml.newPullParser()
                parser.setInput(stream, "UTF-8")
                var eventType = parser.eventType
                
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val name = parser.name
                    if (eventType == XmlPullParser.START_TAG && name == "sheet") {
                        val sheetName = parser.getAttributeValue(null, "name") ?: "Sheet"
                        val sheetId = parser.getAttributeValue(null, "sheetId") ?: ""
                        list.add(SheetMeta(sheetId, sheetName))
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun parseWorksheet(
        stream: InputStream,
        sharedStrings: List<String>,
        cellsMap: MutableMap<String, String>
    ): Pair<Int, Int> {
        var maxRow = 1
        var maxCol = 1
        try {
            stream.use { s ->
                val parser = Xml.newPullParser()
                parser.setInput(s, "UTF-8")
                var eventType = parser.eventType
                
                var currentCellRef = ""
                var isSharedString = false
                val currentValue = StringBuilder()
                
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val name = parser.name
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            if (name == "c") {
                                currentCellRef = parser.getAttributeValue(null, "r") ?: ""
                                val type = parser.getAttributeValue(null, "t") ?: ""
                                isSharedString = (type == "s")
                                currentValue.clear()
                            } else if (name == "v") {
                                currentValue.clear()
                            }
                        }
                        XmlPullParser.TEXT -> {
                            currentValue.append(parser.text ?: "")
                        }
                        XmlPullParser.END_TAG -> {
                            if (name == "c" || name == "v") {
                                if (currentCellRef.isNotEmpty() && currentValue.isNotEmpty()) {
                                    val rawVal = currentValue.toString().trim()
                                    var finalVal = rawVal
                                    if (isSharedString) {
                                        val idx = rawVal.toIntOrNull()
                                        if (idx != null && idx in sharedStrings.indices) {
                                            finalVal = sharedStrings[idx]
                                        }
                                    }
                                    cellsMap[currentCellRef] = finalVal
                                    
                                    // Track max row and max column limits
                                    val coords = cellAddressToCoords(currentCellRef)
                                    if (coords.first + 1 > maxRow) maxRow = coords.first + 1
                                    if (coords.second + 1 > maxCol) maxCol = coords.second + 1
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(maxRow, maxCol)
    }

    /**
     * Converts "A1" or "BC12" to zero-indexed (row, col) Pair
     */
    fun cellAddressToCoords(address: String): Pair<Int, Int> {
        var colStr = ""
        var rowStr = ""
        for (char in address) {
            if (char.isLetter()) {
                colStr += char
            } else if (char.isDigit()) {
                rowStr += char
            }
        }
        
        var col = 0
        for (char in colStr.uppercase()) {
            col = col * 26 + (char - 'A' + 1)
        }
        col = (col - 1).coerceAtLeast(0)
        
        val row = ((rowStr.toIntOrNull() ?: 1) - 1).coerceAtLeast(0)
        return Pair(row, col)
    }

    /**
     * Helper to get column letter name from index (e.g. 0 -> "A", 27 -> "AB")
     */
    fun colIndexToLabel(index: Int): String {
        var temp = index
        var label = ""
        while (temp >= 0) {
            label = ('A' + (temp % 26)).toString() + label
            temp = temp / 26 - 1
        }
        return label
    }
}

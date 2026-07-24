package com.example.devmanager.ui.documentviewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.FileProvider
import com.example.devmanager.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.devmanager.ui.filemanager.FileManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    viewModel: FileManagerViewModel,
    filePath: String? = null,
    onBack: () -> Unit = {}
) {
    val file by viewModel.docViewerFile.collectAsStateWithLifecycle()
    val docLines by viewModel.docLines.collectAsStateWithLifecycle()
    val excelSheets by viewModel.excelSheets.collectAsStateWithLifecycle()
    val pdfPageCount by viewModel.pdfPageCount.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    if (file == null) return

    val extension = file!!.extension.lowercase()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Customization states
    var fontSize by remember { mutableStateOf(14) }
    var inDarkSlateMode by remember { mutableStateOf(false) }
    var wordWrapActive by remember { mutableStateOf(true) }
    var pdfInvertedColors by remember { mutableStateOf(false) }
    
    // Selected sheet index for Excel
    var selectedSheetIndex by remember { mutableStateOf(0) }
    
    // Zoom/scale state
    var zoomScale by remember { mutableStateOf(1.0f) }

    // Simple search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Theme definitions based on user selection
    val themeBgColor = if (inDarkSlateMode) Color(0xFF1E1E24) else Color(0xFFFFFEFA) // Premium Cream/Sepia vs Slate Dark
    val themeTextColor = if (inDarkSlateMode) Color(0xFFE5E5E9) else Color(0xFF1C1B1F)

    BackHandler {
        viewModel.closeDocumentViewer()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = file!!.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = extension.uppercase() + " Reader Mode",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeDocumentViewer() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close View")
                    }
                },
                actions = {
                    // Search toggle
                    IconButton(onClick = { isSearchActive = !isSearchActive }) {
                        Icon(if (isSearchActive) Icons.Default.SearchOff else Icons.Default.Search, "Search")
                    }
                    
                    // Reader View Settings BottomSheet
                    var showViewSettings by remember { mutableStateOf(false) }
                    IconButton(onClick = { showViewSettings = true }) {
                        Icon(Icons.Default.Settings, "Viewer Settings")
                    }

                    // Share document
                    IconButton(onClick = {
                        try {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file!!)
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "*/*"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Share"))
                        } catch (_: Exception) {}
                    }) {
                        Icon(Icons.Default.Share, "Share File")
                    }

                    if (showViewSettings) {
                        ModalBottomSheet(
                            onDismissRequest = { showViewSettings = false }
                        ) {
                            Column(modifier = Modifier.padding(24.dp).padding(bottom = 32.dp)) {
                                Text("Document View Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // PDF option
                                if (extension == "pdf") {
                                    ListItem(
                                        headlineContent = { Text("Display Mode / Dark Canvas Mode") },
                                        supportingContent = { Text("Applies dark inverted filter for night reading") },
                                        leadingContent = { Icon(Icons.Default.DarkMode, null) },
                                        trailingContent = {
                                            Switch(
                                                checked = pdfInvertedColors,
                                                onCheckedChange = { pdfInvertedColors = it }
                                            )
                                        }
                                    )
                                } else {
                                    // FontSize controller for docx & spreadsheets
                                    Text("Text Size: ${fontSize}sp", style = MaterialTheme.typography.bodyMedium)
                                    Slider(
                                        value = fontSize.toFloat(),
                                        onValueChange = { fontSize = it.toInt() },
                                        valueRange = 10f..30f,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    ListItem(
                                        headlineContent = { Text("Night Reader Canvas") },
                                        supportingContent = { Text("Cream-light theme vs Slate-dark theme") },
                                        leadingContent = { Icon(Icons.Default.ColorLens, null) },
                                        trailingContent = {
                                            Switch(
                                                checked = inDarkSlateMode,
                                                onCheckedChange = { inDarkSlateMode = it }
                                            )
                                        }
                                    )
                                }

                                if (extension == "docx" || extension != "pdf" && extension != "xlsx" && extension != "csv") {
                                    ListItem(
                                        headlineContent = { Text("Word Wrap") },
                                        supportingContent = { Text("Forces long lines of text to wrap cleanly") },
                                        leadingContent = { Icon(Icons.Default.WrapText, null) },
                                        trailingContent = {
                                            Switch(
                                                checked = wordWrapActive,
                                                onCheckedChange = { wordWrapActive = it }
                                            )
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Render Scale Zoom controller
                                Text("Display Zoom: ${(zoomScale * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    IconButton(
                                        onClick = { zoomScale = (zoomScale - 0.1f).coerceAtLeast(0.5f) }
                                    ) {
                                        Icon(Icons.Default.Remove, "Zoom Out")
                                    }
                                    Slider(
                                        value = zoomScale,
                                        onValueChange = { zoomScale = it },
                                        valueRange = 0.5f..2.5f,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { zoomScale = (zoomScale + 0.1f).coerceAtMost(2.5f) }
                                    ) {
                                        Icon(Icons.Default.Add, "Zoom In")
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(themeBgColor)
        ) {
            // Sliding interactive Search Toolbar
            AnimatedVisibility(
                visible = isSearchActive,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search icon")
                        Spacer(modifier = Modifier.width(12.dp))
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search text inside document...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, "Clear Search")
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Styling & Parsing Document...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    when (extension) {
                        "pdf" -> {
                            PdfBookReader(
                                file = file!!,
                                pageCount = pdfPageCount,
                                scrollInverted = pdfInvertedColors,
                                zoomLevel = zoomScale
                            )
                        }
                        "docx", "doc" -> {
                            val filteredLines = remember(docLines, searchQuery) {
                                if (searchQuery.isBlank()) docLines
                                else docLines.filter {
                                    it is DocLine.Paragraph && it.text.contains(searchQuery, ignoreCase = true)
                                }
                            }
                            WordViewerGrid(
                                docLines = filteredLines,
                                textContrastSize = fontSize,
                                isWhiteContrast = !inDarkSlateMode,
                                scaleZoom = zoomScale,
                                wrapEnabled = wordWrapActive
                            )
                        }
                        "xlsx", "xls" -> {
                            if (excelSheets.isEmpty()) {
                                EmptyDocumentBox("This spreadsheet is empty or format is unsupported.")
                            } else {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Row tabs for switching worksheets
                                    LazyRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        items(excelSheets.size) { idx ->
                                            val currentMeta = excelSheets[idx]
                                            val isSelected = selectedSheetIndex == idx
                                            
                                            Button(
                                                onClick = { selectedSheetIndex = idx },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary 
                                                                    else MaterialTheme.colorScheme.secondaryContainer,
                                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                                                                  else MaterialTheme.colorScheme.onSecondaryContainer
                                                ),
                                                modifier = Modifier.padding(end = 6.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_custom_folder),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp).padding(end = 4.dp),
                                                    tint = Color.Unspecified
                                                )
                                                Text(currentMeta.name, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .weight(1f)
                                    ) {
                                        val activeSheet = excelSheets[selectedSheetIndex]
                                        SpreadsheetViewGrid(
                                            sheet = activeSheet,
                                            baseFontSize = fontSize,
                                            isWhiteContrast = !inDarkSlateMode,
                                            zoomLevel = zoomScale,
                                            highlightFilter = searchQuery
                                        )
                                    }
                                }
                            }
                        }
                        "csv" -> {
                            if (excelSheets.isEmpty()) {
                                EmptyDocumentBox("Failed to load CSV spreadsheet grid.")
                            } else {
                                val activeSheet = excelSheets.first()
                                SpreadsheetViewGrid(
                                    sheet = activeSheet,
                                    baseFontSize = fontSize,
                                    isWhiteContrast = !inDarkSlateMode,
                                    zoomLevel = zoomScale,
                                    highlightFilter = searchQuery
                                )
                            }
                        }
                        else -> {
                            // Render standard Text view for other kinds of documents (txt, code, html, log)
                            TextDocumentReader(
                                file = file!!,
                                baseFontSize = fontSize,
                                bgContrastTheme = !inDarkSlateMode,
                                wrapEnabled = wordWrapActive,
                                zoomLevel = zoomScale,
                                filterWord = searchQuery
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyDocumentBox(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun PdfBookReader(
    file: File,
    pageCount: Int,
    scrollInverted: Boolean,
    zoomLevel: Float
) {
    if (pageCount <= 0) {
        EmptyDocumentBox("No pages detected in this PDF or document is corrupted.")
        return
    }

    val lazyListState = rememberLazyListState()
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(horizontalScrollState)
            .verticalScroll(verticalScrollState)
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .width(550.dp * zoomLevel) // Dynamic width zoom scale
                .fillMaxHeight(),
            contentPadding = PaddingValues(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(pageCount) { pageIndex ->
                PdfPageItem(
                    file = file,
                    pageIndex = pageIndex,
                    invertedFilter = scrollInverted,
                    scaleMultiplier = zoomLevel
                )
            }
        }

        // Overlay page indicator
        val firstVisibleIndex by remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }
        val displayedPage = (firstVisibleIndex + 1).coerceAtMost(pageCount)
        
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .shadow(6.dp, RoundedCornerShape(20.dp))
        ) {
            Text(
                text = "Page $displayedPage of $pageCount",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun PdfPageItem(
    file: File,
    pageIndex: Int,
    invertedFilter: Boolean,
    scaleMultiplier: Float
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(file, pageIndex) {
        withContext(Dispatchers.IO) {
            try {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                if (pageIndex < renderer.pageCount) {
                    val page = renderer.openPage(pageIndex)
                    
                    // Render PDF page with a generous scale for beautiful pristine rendering!
                    val w = (page.width * 2f).toInt()
                    val h = (page.height * 2f).toInt()
                    
                    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap = b
                    page.close()
                }
                renderer.close()
                fd.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .shadow(6.dp, RoundedCornerShape(4.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            val invertMatrix = remember {
                ColorMatrix(floatArrayOf(
                    -1.0f,  0.0f,  0.0f, 0.0f, 255.0f, // Red
                     0.0f, -1.0f,  0.0f, 0.0f, 255.0f, // Green
                     0.0f,  0.0f, -1.0f, 0.0f, 255.0f, // Blue
                     0.0f,  0.0f,  0.0f, 1.0f,   0.0f  // Alpha
                ))
            }
            
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                colorFilter = if (invertedFilter) ColorFilter.colorMatrix(invertMatrix) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bitmap!!.width.toFloat() / bitmap!!.height.toFloat())
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun WordViewerGrid(
    docLines: List<DocLine>,
    textContrastSize: Int,
    isWhiteContrast: Boolean,
    scaleZoom: Float,
    wrapEnabled: Boolean
) {
    if (docLines.isEmpty()) {
        EmptyDocumentBox("No text paragraphs parsed from this file.")
        return
    }

    val textColor = if (isWhiteContrast) Color(0xFF1E1E24) else Color(0xFFE5E5E9)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 60.dp)
    ) {
        items(docLines.size) { index ->
            when (val line = docLines[index]) {
                is DocLine.Paragraph -> {
                    val fontSizeVal = (if (line.isHeading) textContrastSize + 6 else textContrastSize) * scaleZoom
                    Text(
                        text = line.text,
                        fontSize = fontSizeVal.sp,
                        fontWeight = if (line.isHeading || line.isBold) FontWeight.Bold else FontWeight.Medium,
                        fontStyle = if (line.isItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                        color = if (line.isHeading) MaterialTheme.colorScheme.primary else textColor,
                        lineHeight = (fontSizeVal * 1.4f).sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = if (line.isHeading) 12.dp else 6.dp)
                    )
                    if (line.isHeading) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
                is DocLine.Table -> {
                    DocxTableRenderer(
                        table = line,
                        textColor = textColor,
                        fontSize = textContrastSize * scaleZoom
                    )
                }
            }
        }
    }
}

@Composable
fun DocxTableRenderer(table: DocLine.Table, textColor: Color, fontSize: Float) {
    val scrollHorizontalState = rememberScrollState()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.horizontalScroll(scrollHorizontalState).padding(8.dp)) {
            table.rows.forEachIndexed { rIdx, row ->
                val isHeader = rIdx == 0
                Row(
                    modifier = Modifier.background(
                        if (isHeader) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else Color.Transparent
                    )
                ) {
                    row.forEach { cellText ->
                        Box(
                            modifier = Modifier
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                .padding(10.dp)
                                .widthIn(min = 90.dp, max = 220.dp)
                        ) {
                            Text(
                                text = cellText,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = fontSize.sp,
                                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                color = if (isHeader) MaterialTheme.colorScheme.onPrimaryContainer else textColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpreadsheetViewGrid(
    sheet: ExcelSheet,
    baseFontSize: Int,
    isWhiteContrast: Boolean,
    zoomLevel: Float,
    highlightFilter: String
) {
    val scrollH = rememberScrollState()
    val scrollV = rememberScrollState()
    
    val baseCellWidth = 110.dp * zoomLevel
    val baseCellHeight = 38.dp * zoomLevel
    val baseHeaderWidth = 48.dp * zoomLevel
    val baseHeaderHeight = 32.dp * zoomLevel
    
    val textColor = if (isWhiteContrast) Color(0xFF1E1E24) else Color(0xFFE5E5E9)
    val gridBorderColor = if (isWhiteContrast) Color(0xFFE0E0E0) else Color(0xFF3A3A40)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(scrollH)
            .verticalScroll(scrollV)
            .padding(6.dp)
    ) {
        // Heading Columns labels A, B, C...
        Row {
            // Corner spacer cell
            Box(
                modifier = Modifier
                    .size(width = baseHeaderWidth, height = baseHeaderHeight)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(0.5.dp, MaterialTheme.colorScheme.outline),
                contentAlignment = Alignment.Center
            ) {
                Text("", fontSize = (baseFontSize - 2).sp)
            }
            
            for (col in 0 until sheet.maxCol.coerceAtMost(52)) { // Upper boundary is AZ
                Box(
                    modifier = Modifier
                        .size(width = baseCellWidth, height = baseHeaderHeight)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(0.5.dp, MaterialTheme.colorScheme.outline),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = DocumentParser.colIndexToLabel(col),
                        fontSize = (baseFontSize - 1).sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Rows loop
        for (row in 0 until sheet.maxRow.coerceAtMost(600)) { // High boundary of 600 rows
            Row {
                // Left index column cell: 1, 2, 3...
                Box(
                    modifier = Modifier
                        .size(width = baseHeaderWidth, height = baseCellHeight)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(0.5.dp, MaterialTheme.colorScheme.outline),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (row + 1).toString(),
                        fontSize = (baseFontSize - 1).sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                for (col in 0 until sheet.maxCol.coerceAtMost(52)) {
                    val colLabel = DocumentParser.colIndexToLabel(col)
                    val cellAddress = "$colLabel${row + 1}"
                    val cellValue = sheet.cells[cellAddress] ?: ""
                    
                    val isMatchedSearch = highlightFilter.isNotEmpty() && 
                                          cellValue.contains(highlightFilter, ignoreCase = true)
                    
                    Box(
                        modifier = Modifier
                            .size(width = baseCellWidth, height = baseCellHeight)
                            .background(
                                if (isMatchedSearch) MaterialTheme.colorScheme.errorContainer
                                else if (cellValue.isNotEmpty()) MaterialTheme.colorScheme.surface
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.05f)
                            )
                            .border(0.5.dp, gridBorderColor)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = cellValue,
                            fontSize = (baseFontSize).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isMatchedSearch) FontWeight.Bold else FontWeight.Normal,
                            color = if (isMatchedSearch) MaterialTheme.colorScheme.onErrorContainer else textColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TextDocumentReader(
    file: File,
    baseFontSize: Int,
    bgContrastTheme: Boolean,
    wrapEnabled: Boolean,
    zoomLevel: Float,
    filterWord: String
) {
    var fileContent by remember { mutableStateOf<List<String>>(emptyList()) }
    var textLoading by remember { mutableStateOf(true) }
    
    val textContrastColor = if (bgContrastTheme) Color(0xFF1E1E24) else Color(0xFFE5E5E9)

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                fileContent = file.readLines()
            } catch (e: Exception) {
                fileContent = listOf("[Failed to read raw code text document: ${e.localizedMessage}]")
            } finally {
                textLoading = false
            }
        }
    }

    if (textLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val lazyListState = rememberLazyListState()

    // Support line numbers for logs or source codes
    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 60.dp)
    ) {
        items(fileContent.size) { lineIdx ->
            val rawLine = fileContent[lineIdx]
            
            // Search word match highlight
            val isMatch = filterWord.isNotBlank() && rawLine.contains(filterWord, ignoreCase = true)
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isMatch) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else Color.Transparent)
                    .padding(vertical = 2.dp)
            ) {
                // Line count header
                Text(
                    text = String.format("%03d", lineIdx + 1),
                    fontSize = (baseFontSize - 2).sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.width(36.dp)
                )
                
                Text(
                    text = rawLine,
                    fontSize = (baseFontSize * zoomLevel).sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (isMatch) MaterialTheme.colorScheme.error else textContrastColor,
                    maxLines = if (wrapEnabled) Int.MAX_VALUE else 1,
                    overflow = if (wrapEnabled) TextOverflow.Clip else TextOverflow.Ellipsis,
                    fontWeight = if (isMatch) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

package com.example.devmanager

import java.io.File

data class DirNode(
    val name: String,
    var size: Long = 0L,
    val children: MutableList<DirNode> = mutableListOf()
)

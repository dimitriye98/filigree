package com.dimitriye.filigree

import com.dimitriye.filigree.model.MappingSetModelFactoryImpl
import org.cadixdev.lorenz.MappingSet
import org.cadixdev.lorenz.io.TextMappingsReader
import org.cadixdev.lorenz.io.srg.tsrg.TSrgReader

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL

private fun mcpURL(version: String): URL {
	return URL(
		"https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/$version/mcp_config-$version.zip"
	)
}

fun fetchMCPMappings(version: String): MappingSet {
	return streamFileFromZippedURL(mcpURL(version), "config/joined.tsrg")
		.let { InputStreamReader(it) }
		.let { BufferedReader(it) }
		.use { TSrgReader(it).read(MappingSet.create(MappingSetModelFactoryImpl())) }
}

package com.dimitriye.filigree

import org.cadixdev.lorenz.MappingSet
import org.cadixdev.lorenz.io.srg.tsrg.TSrgReader
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL

fun mcpURL(version: String): URL {
	return URL(
		"https://raw.githubusercontent.com/"
			+ "MinecraftForge/MCPConfig/master/versions/release/$version/joined.tsrg"
	)
}

fun readTSrgFromURL(url: URL): MappingSet {
	return url.openStream().use {
		InputStreamReader(it).use {
			BufferedReader(it).use {
				TSrgReader(it).read()
			}
		}
	}
}

fun fetchMCPMappings(version: String): MappingSet {
	return mcpURL(version).let(::readTSrgFromURL)
}
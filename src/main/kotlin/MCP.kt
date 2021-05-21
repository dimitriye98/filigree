package com.dimitriye.filigree

import org.cadixdev.lorenz.MappingSet
import org.cadixdev.lorenz.io.srg.tsrg.TSrgReader
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.IllegalArgumentException
import java.net.URL
import java.util.zip.ZipInputStream

fun mcpURL(version: String): URL {
	return URL(
		"https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/$version/mcp_config-$version.zip"
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

fun readTSrgFromZippedURL(url: URL, path: String): MappingSet {
	url.openStream().use {
		BufferedInputStream(it).use {
			ZipInputStream(it).use {
				var entry = it.nextEntry
				while (entry != null) {
					if (entry.name == path) {
						BufferedReader(InputStreamReader(it)).use {
							return TSrgReader(it).read()
						}
					}
					entry = it.nextEntry
				}
			}
		}
	}

	throw IllegalArgumentException("URL $url does not contain file $path")
}

fun fetchMCPMappings(version: String): MappingSet {
	return readTSrgFromZippedURL(mcpURL(version), "config/joined.tsrg")
}
package com.dimitriye.filigree

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import java.nio.file.Path
import java.util.zip.ZipInputStream

val moshi by lazy {
	Moshi.Builder()
		.addLast(KotlinJsonAdapterFactory())
		.build()
}

fun streamFileFromZippedURL(url: URL, path: String): InputStream {
	url.openStream().use {
		BufferedInputStream(it).use {
			ZipInputStream(it).use {
				var entry = it.nextEntry
				while (entry != null) {
					if (entry.name == path) {
						return it
					}
					entry = it.nextEntry
				}
			}
		}
	}

	throw IllegalArgumentException("URL $url does not contain file $path")
}

fun readClassPathFromFile(file: File): List<Path> {
	val out = ArrayList<Path>()
	file.forEachLine {
		out.add(Path.of(it))
	}
	return out
}